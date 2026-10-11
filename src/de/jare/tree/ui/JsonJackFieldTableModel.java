package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditLinkingSet;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditNodeAnnotation;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditStringEvaluator;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.lang.JsonNodeType;
import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import de.jare.tree.control.listeners.ContentListener;
import de.jare.tree.control.listeners.ParseProblemsListener;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import de.jare.tree.control.model.JackTreeModel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Table model for the Fields pane: a view on the model side of the selected
 * object. The first row shows the json type of the object, one row per field
 * (child property) follows with name, value and node type; a trailing "^"
 * column opens the field editor dialogs. Object-typed fields
 * offer their candidate instances through a combo box (see
 * {@link #isComboBoxCell(int, int)}); the selection semantics are a later
 * step - the base version only displays the candidates.
 *
 * <p>When a field is selected in the tree, the pane still shows the owning
 * object and focuses the field's row (matched by edit id, not by name - array
 * fields may repeat names).</p>
 *
 * @author Janusch Rentenatus
 */
public class JsonJackFieldTableModel extends AbstractTableModel
        implements TreeFocusListener, ContentListener, ParseProblemsListener, JsonJackComboCapableModel {

    private static final String[] columnNames = {"Name", "Value", "Type", "JsonType", "^"};

    private List<FieldRow> rows = new ArrayList<>();
    private JsonModelDescriptor jsonModelDescriptor;
    private EditTree editTree;
    private EditNodeObject currentObject;
    private long focusedFieldEditId = -1;

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        FieldRow row = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 ->
                row.name();
            case 1 ->
                row.value();
            case 2 ->
                row.type();
            case 3 ->
                row.jsonType();
            case 4 ->
                isButtonEnabled(rowIndex);
            default ->
                null;
        };
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        // Primitive field values are edited inline; combo box cells (object fields) stay read-only
        // until the selection semantics exist; array fields (primitive lists, object lists and
        // array annotations) open their dedicated editor dialog instead.
        return columnIndex == 1 && rowIndex >= 1
                && !isComboBoxCell(rowIndex, columnIndex)
                && !isArrayFieldRow(rowIndex);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == 4 ? Boolean.class : String.class;
    }

    /**
     * Checks whether the "^" button of the given row can open something: array fields open their
     * dedicated list editor, every other row below the jsonType header opens the detail dialog
     * when its value is neither null nor empty.
     *
     * @param rowIndex the row index
     * @return true when the button should be enabled
     */
    public boolean isButtonEnabled(int rowIndex) {
        if (rowIndex < 1 || rowIndex >= rows.size()) {
            return false;
        }
        if (isArrayFieldRow(rowIndex)) {
            return true;
        }
        Object value = rows.get(rowIndex).value();
        return value != null && !value.toString().isEmpty();
    }

    @Override
    public void setValueAt(Object aValue, int row, int col) {
        if (col != 1 || row < 1 || row >= rows.size()) {
            return;
        }
        FieldRow oldRow = rows.get(row);
        if (currentObject == null) {
            return;
        }
        if (oldRow.synthetic()) {
            // The synthetic value row of a primitive object writes the object value itself.
            currentObject.setName(aValue == null ? null : aValue.toString());
        } else {
            // Write through to the node so the tree and the export see the change.
            EditNode child = currentObject.getChildAt(row - 1);
            if (child instanceof EditNodeProperty prop) {
                prop.setValue(aValue == null ? null : aValue.toString());
            }
        }
        rows.set(row, new FieldRow(oldRow.name(), aValue, oldRow.type(), oldRow.jsonType(),
                oldRow.comboBox(), oldRow.comboValues(), oldRow.synthetic()));
        fireTableCellUpdated(row, col);
    }

    @Override
    public boolean isComboBoxCell(int rowIndex, int columnIndex) {
        return columnIndex == 1 && rowIndex >= 1 && rowIndex < rows.size()
                && rows.get(rowIndex).comboBox();
    }

    @Override
    public Object[] getComboBoxValues(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return new Object[0];
        }
        Object[] values = rows.get(rowIndex).comboValues();
        return values == null ? new Object[0] : values;
    }

    // ========== Array field editors ==========

    /**
     * One selectable object instance for the collection editor of an object list: its wood identity, its display
     * text, its model type and the node itself (for identity comparison and for copies of identityless instances).
     */
    public record InstanceCandidate(String woodId, String text, String jsonType, EditNodeObject node) {

    }

    /**
     * Checks whether the row is a field with an array value: a list of primitives, a list of objects or an array
     * annotation. Array values are not edited inline - the pane opens the dedicated editor dialog.
     *
     * @param rowIndex the row index
     * @return true when the row holds an array field
     */
    public boolean isArrayFieldRow(int rowIndex) {
        final EditNodeProperty field = fieldOfRow(rowIndex);
        return field != null && field.getType() == JsonNodeType.ARRAY;
    }

    /**
     * Checks whether the array field of the row holds primitives. An empty array counts as primitive - an empty
     * object list has no elements to compare against.
     *
     * @param rowIndex the row index
     * @return true when the elements are primitives (or the array is empty)
     */
    public boolean isPrimitiveArrayListRow(int rowIndex) {
        final EditNodeProperty field = fieldOfRow(rowIndex);
        if (field == null || field.getType() != JsonNodeType.ARRAY) {
            return false;
        }
        for (int i = 0; i < field.getChildCount(); i++) {
            if (field.getChildAt(i) instanceof EditNodeObject child) {
                return isPrimitiveRowObject(child);
            }
        }
        return true;
    }

    /**
     * Returns the field property of the row, or null for the header row and the synthetic value row.
     *
     * @param rowIndex the row index
     * @return the property of the row, or null
     */
    public EditNodeProperty fieldOfRow(int rowIndex) {
        if (currentObject == null || rowIndex < 1) {
            return null;
        }
        int row = 1 + (isPrimitiveRowObject(currentObject) ? 1 : 0);
        for (int i = 0; i < currentObject.getChildCount(); i++) {
            if (currentObject.getChildAt(i) instanceof EditNodeProperty field) {
                if (rowIndex == row) {
                    return field;
                }
                row++;
            }
        }
        return null;
    }

    /**
     * The candidate instances for the collection editor of an object list: objects fitting the declared element
     * type (interface types include the implementors), without a declaration every non-primitive object. The walk
     * covers the active tree and the identities of the linking set.
     *
     * @param rowIndex the row index of the array field
     * @return the candidates, never null
     */
    public List<InstanceCandidate> getInstanceCandidates(int rowIndex) {
        final EditNodeProperty field = fieldOfRow(rowIndex);
        if (field == null) {
            return List.of();
        }
        JsonTypeDescriptor declared = null;
        final JsonFieldDescriptor descriptor = field.getJsonField();
        if (descriptor != null && jsonModelDescriptor != null) {
            declared = jsonModelDescriptor.getType(descriptor.getTypeName());
        }
        final List<InstanceCandidate> result = new ArrayList<>();
        final Set<EditNodeObject> seen = new HashSet<>();
        if (editTree != null && editTree.getRoot() != null) {
            collectInstanceCandidates(editTree.getRoot(), declared, result, seen);
        }
        final EditLinkingSet linkingSet = editTree == null ? null : editTree.getLinkingSet();
        if (linkingSet != null) {
            for (EditNodeObject held : linkingSet.getAllObjects()) {
                if (seen.add(held) && candidateFits(held, declared)) {
                    result.add(candidateOf(held));
                }
            }
        }
        return result;
    }

    /**
     * The object elements currently recorded in the list of the row.
     *
     * @param rowIndex the row index of the array field
     * @return the current elements in list order, never null
     */
    public List<InstanceCandidate> getCurrentObjectList(int rowIndex) {
        final EditNodeProperty field = fieldOfRow(rowIndex);
        if (field == null) {
            return List.of();
        }
        final List<InstanceCandidate> result = new ArrayList<>();
        for (int i = 0; i < field.getChildCount(); i++) {
            if (field.getChildAt(i) instanceof EditNodeObject child && !isPrimitiveRowObject(child)) {
                result.add(candidateOf(child));
            }
        }
        return result;
    }

    /**
     * The primitive values currently recorded in the list of the row (primitive lists and array annotations).
     *
     * @param rowIndex the row index of the array field
     * @return the current values in list order, never null
     */
    public List<String> getCurrentPrimitiveList(int rowIndex) {
        final EditNodeProperty field = fieldOfRow(rowIndex);
        if (field == null) {
            return List.of();
        }
        final List<String> result = new ArrayList<>();
        for (int i = 0; i < field.getChildCount(); i++) {
            if (field.getChildAt(i) instanceof EditNodeObject child && isPrimitiveRowObject(child)) {
                result.add(child.getValue());
            }
        }
        return result;
    }

    /**
     * Applies the result of the collection editor: the object elements of the array field are replaced by the
     * chosen instances in the chosen order. A chosen instance with identity becomes a wood reference stub, one
     * without identity a deep copy; an element that is the chosen node itself is kept as it is.
     *
     * @param rowIndex the row index of the array field
     * @param chosen the chosen instances in list order
     */
    public void applyObjectListResult(int rowIndex, List<InstanceCandidate> chosen) {
        final EditNodeProperty field = fieldOfRow(rowIndex);
        if (field == null || editTree == null) {
            return;
        }
        // the elements currently in the list, by identity - they are detached below and their parent
        // reference becomes invalid, so the identity comparison must happen before the removal
        final List<EditNodeObject> toRemove = new ArrayList<>();
        for (int i = 0; i < field.getChildCount(); i++) {
            if (field.getChildAt(i) instanceof EditNodeObject child && !isPrimitiveRowObject(child)) {
                toRemove.add(child);
            }
        }
        final Set<EditNodeObject> formerElements = new HashSet<>(toRemove);
        for (EditNodeObject child : toRemove) {
            editTree.removeChild(field, child);
        }
        final EditLinkingSet linkingSet = editTree.getLinkingSet();
        final String provider = linkingSet == null ? null : linkingSet.getProviderName();
        for (InstanceCandidate candidate : chosen) {
            if (candidate.node() != null && formerElements.contains(candidate.node())) {
                // the element is already in the list (identity match): re-append at the chosen position
                editTree.addChild(field, candidate.node());
                continue;
            }
            if (candidate.woodId() != null && provider != null) {
                final EditNodeObject stub = new EditNodeObject(candidate.text());
                stub.setLinkTarget(provider + de.jare.jsoncasted.lang.JsonTerms.COLONCOLON + candidate.woodId());
                editTree.addChild(field, stub);
            } else if (candidate.node() != null) {
                editTree.addNode(field, candidate.node(), -1, true);
            }
        }
        refresh();
    }

    /**
     * Applies the result of the primitive list editor: the primitive rows of the array field are replaced by the
     * given values in the given order.
     *
     * @param rowIndex the row index of the array field
     * @param values the values in list order
     */
    public void applyPrimitiveListResult(int rowIndex, List<String> values) {
        final EditNodeProperty field = fieldOfRow(rowIndex);
        if (field == null || editTree == null) {
            return;
        }
        final List<EditNodeObject> toRemove = new ArrayList<>();
        for (int i = 0; i < field.getChildCount(); i++) {
            if (field.getChildAt(i) instanceof EditNodeObject child && isPrimitiveRowObject(child)) {
                toRemove.add(child);
            }
        }
        for (EditNodeObject child : toRemove) {
            editTree.removeChild(field, child);
        }
        for (String value : values) {
            editTree.addChild(field, new EditNodeObject(value == null ? "" : value));
        }
        refresh();
    }

    private static void collectInstanceCandidates(EditNode node, JsonTypeDescriptor declared,
            List<InstanceCandidate> result, Set<EditNodeObject> seen) {
        if (node instanceof EditNodeObject obj && seen.add(obj) && candidateFits(obj, declared)) {
            result.add(candidateOf(obj));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChildAt(i) instanceof EditNodeAbstract child) {
                collectInstanceCandidates(child, declared, result, seen);
            }
        }
    }

    private static boolean candidateFits(EditNodeObject obj, JsonTypeDescriptor declared) {
        if (isPrimitiveRowObject(obj)) {
            return false; // primitive rows are no object instances
        }
        if (declared == null) {
            return true;
        }
        return typeFits(obj, declared);
    }

    private static InstanceCandidate candidateOf(EditNodeObject obj) {
        final String woodId = obj.getObjektId();
        final String text = EditStringEvaluator.displayText(obj);
        final String jsonType = obj.getJsonType() == null ? "" : obj.getJsonType().getTypeName();
        return new InstanceCandidate(woodId, text, jsonType, obj);
    }

    // ========== TreeFocusListener ==========

    @Override
    public void onNodeSelected(DefaultMutableTreeNode node, Object trigger, boolean rootSelected) {
        Object actualNode = node == null ? null : node.getUserObject();
        EditNodeObject owner = null;
        long focusId = -1;

        if (actualNode instanceof EditNodeProperty prop) {
            // A clicked field shows its owning object and focuses the field row.
            owner = findOwningObject(prop);
            focusId = prop.getEditId();
        } else if (actualNode instanceof EditNodeObject obj) {
            owner = obj;
        } else if (actualNode instanceof EditNodeAbstract abs) {
            owner = findOwningObject(abs);
        }

        updateRows(owner, focusId);
    }

    @Override
    public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
        if (editor == null) {
            return;
        }
        if (editor.getModel() instanceof JackTreeModel treeModel) {
            this.editTree = treeModel.getEditTree();
            setJsonModelDescriptor(treeModel.getJsonModelDescriptor());
        }
    }

    // ========== ContentListener / ParseProblemsListener ==========

    @Override
    public void onCommand(String commandId, Object trigger) {
        refresh();
    }

    @Override
    public void onParseProblems(TreeFocusComponent source, List<DefaultMutableTreeNode> nodes) {
        // Types resolve asynchronously: re-read the rows when a parse run finished.
        refresh();
    }

    /**
     * Re-reads the rows of the current object, keeping the focused field.
     */
    public void refresh() {
        updateRows(currentObject, focusedFieldEditId);
    }

    // ========== Row construction ==========

    private void updateRows(EditNodeObject owner, long focusId) {
        this.currentObject = owner;
        this.focusedFieldEditId = focusId;

        List<FieldRow> newRows = new ArrayList<>();
        if (owner != null) {
            newRows.add(new FieldRow("jsonType", typeNameOf(owner), "header", "", false, null, false));
            // A primitive object (an array row, for example) has no field children: its value gets a synthetic row.
            if (isPrimitiveRowObject(owner)) {
                final String cast = owner.getCastName() == null ? "" : owner.getCastName();
                newRows.add(new FieldRow("value", owner.getValue(), "", cast, false, null, true));
            }
            for (int i = 0; i < owner.getChildCount(); i++) {
                if (!(owner.getChildAt(i) instanceof EditNodeProperty field)) {
                    continue;
                }
                newRows.add(buildFieldRow(field));
            }
        }
        rows = newRows;
        fireTableDataChanged();
    }

    private FieldRow buildFieldRow(EditNodeProperty field) {
        final String name = field.getName() == null ? "." : field.getName();
        final String type = field.getType() == null ? "" : field.getType().getName();
        final Object value = displayValueOf(field);
        final Object[] candidates = comboBoxCandidates(field);
        final boolean comboBox = candidates.length > 0;
        final String jsonType = field instanceof EditNodeAnnotation
                ? "annotation" : jsonTypeOf(field);
        return new FieldRow(name, value, type, jsonType, comboBox, candidates, false);
    }

    /**
     * Checks whether the object is a primitive row: a scalar value without field children and without the object
     * marker of a nested object (the converter creates nested objects with the info "{...}"). Typical case: a
     * primitive array row.
     *
     * @param obj the object node
     * @return true when the object represents a bare primitive value
     */
    private static boolean isPrimitiveRowObject(EditNodeObject obj) {
        return obj.getChildCount() == 0 && obj.getValue() != null
                && !"{...}".equals(obj.getObjektInfo());
    }

    /**
     * Determines the model side type name of a field:
 the declared type of the field descriptor when the field is
     * cleanly parsed, the resolved type of the object child as fallback, otherwise empty.
     *
     * @param field the field property
     * @return the model type name, or an empty string when nothing is known
     */
    private static String jsonTypeOf(EditNodeProperty field) {
        final JsonFieldDescriptor descriptor = field.getJsonField();
        if (descriptor != null && descriptor.getTypeName() != null && !descriptor.getTypeName().isEmpty()) {
            return descriptor.getTypeName();
        }
        if (field.getType() == JsonNodeType.OBJECT) {
            for (int i = 0; i < field.getChildCount(); i++) {
                if (field.getChildAt(i) instanceof EditNodeObject childObject
                        && childObject.getJsonType() != null) {
                    return childObject.getJsonType().getTypeName();
                }
            }
        }
        return "";
    }

    private static Object displayValueOf(EditNodeProperty field) {
        // An annotation carries its text in its own primitive value, not in child elements.
        if (field instanceof EditNodeAnnotation && field.getValue() != null) {
            return field.getValue();
        }
        switch (field.getType()) {
            case ARRAY -> {
                int count = 0;
                for (int i = 0; i < field.getChildCount(); i++) {
                    if (field.getChildAt(i) instanceof EditNodeAbstract) {
                        count++;
                    }
                }
                return count + " element(s)";
            }
            case OBJECT -> {
                for (int i = 0; i < field.getChildCount(); i++) {
                    if (field.getChildAt(i) instanceof EditNodeObject childObject) {
                        return EditStringEvaluator.displayText(childObject);
                    }
                }
                return "{ }";
            }
            default -> {
                return field.getValue();
            }
        }
    }

    /**
     * Three-tier candidate logic: a cleanly parsed field offers the object
     * instances fitting its declared type (interface types include the
     * implementors), an unparsed primitive falls back to the primitive node
     * types, an unparsed object offers nothing.
     */
    private Object[] comboBoxCandidates(EditNodeProperty field) {
        final JsonFieldDescriptor descriptor = field.getJsonField();
        if (descriptor != null && jsonModelDescriptor != null) {
            final JsonTypeDescriptor declared = jsonModelDescriptor.getType(descriptor.getTypeName());
            if (declared != null && !declared.isPrimitive()) {
                return instanceCandidates(declared);
            }
        }
        // No field description: primitives can still be offered from the node type.
        return switch (field.getType()) {
            case STRING, NUMBER, LONG, BOOLEAN, NULL ->
                new Object[]{"STRING", "NUMBER", "LONG", "BOOLEAN", "NULL"};
            default ->
                new Object[0];
        };
    }

    /**
     * Collects the object instances of the parsed JSON whose type fits the
     * declared field type: a walk over the active tree plus the identities of
     * the linking set (a deleted but referenced object stays selectable).
     */
    private Object[] instanceCandidates(JsonTypeDescriptor declared) {
        List<Object> candidates = new ArrayList<>();
        Set<EditNodeObject> seen = new HashSet<>();
        if (editTree != null && editTree.getRoot() != null) {
            collectFittingInstances(editTree.getRoot(), declared, candidates, seen);
        }
        final EditLinkingSet linkingSet = editTree == null ? null : editTree.getLinkingSet();
        if (linkingSet != null) {
            for (EditNodeObject held : linkingSet.getAllObjects()) {
                if (seen.add(held) && typeFits(held, declared)) {
                    candidates.add(instanceLabel(held));
                }
            }
        }
        return candidates.toArray();
    }

    private static void collectFittingInstances(EditNode node, JsonTypeDescriptor declared,
            List<Object> candidates, Set<EditNodeObject> seen) {
        if (node instanceof EditNodeObject obj && seen.add(obj) && typeFits(obj, declared)) {
            candidates.add(instanceLabel(obj));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChildAt(i) instanceof EditNodeAbstract child) {
                collectFittingInstances(child, declared, candidates, seen);
            }
        }
    }

    private static boolean typeFits(EditNodeObject obj, JsonTypeDescriptor declared) {
        final JsonTypeDescriptor objType = obj.getJsonType();
        if (objType == null) {
            return false;
        }
        if (objType.getTypeName().equals(declared.getTypeName())) {
            return true;
        }
        // Interface field: every implementor of the declared type fits.
        return objType.containsSuper(declared);
    }

    private static String instanceLabel(EditNodeObject obj) {
        final JsonTypeDescriptor type = obj.getJsonType();
        final String id = obj.getObjektId();
        final StringBuilder sb = new StringBuilder(EditStringEvaluator.displayText(obj));
        if (sb.isEmpty()) {
            sb.append('?');
        }
        if (type != null) {
            sb.append(" (").append(type.getTypeName()).append(')');
        }
        if (id != null && !id.isEmpty()) {
            sb.append(" #").append(id);
        }
        return sb.toString();
    }

    private static String typeNameOf(EditNodeObject obj) {
        if (obj.getJsonType() != null) {
            return obj.getJsonType().getTypeName();
        }
        if (obj.getCastName() != null && !obj.getCastName().isEmpty()) {
            return obj.getCastName() + "?";
        }
        return "-";
    }

    /**
     * Walks up from the given node to the next owning EditNodeObject.
     *
     * @param start the node to start from
     * @return the owning object, or null when there is none
     */
    private static EditNodeObject findOwningObject(EditNode start) {
        EditNode current = start;
        while (current != null) {
            if (current instanceof EditNodeObject obj) {
                return obj;
            }
            current = current.getParent();
        }
        return null;
    }

    // ========== Accessors for the pane ==========

    /**
     * Returns the row index of the focused field, or -1 when no field is
     * focused. Rows are matched by edit id, never by name - array fields may
     * repeat names.
     *
     * @return the model row index of the focused field
     */
    public int getFocusedRowIndex() {
        if (currentObject == null || focusedFieldEditId < 0) {
            return -1;
        }
        // row 0 is the jsonType header, a primitive object adds the synthetic value row after it
        int row = 1 + (isPrimitiveRowObject(currentObject) ? 1 : 0);
        for (int i = 0; i < currentObject.getChildCount(); i++) {
            if (!(currentObject.getChildAt(i) instanceof EditNodeProperty)) {
                continue;
            }
            if (currentObject.getChildAt(i).getEditId() == focusedFieldEditId) {
                return row;
            }
            row++;
        }
        return -1;
    }

    /**
     * Returns the object currently shown by the pane.
     *
     * @return the current object, or null
     */
    public EditNodeObject getCurrentObject() {
        return currentObject;
    }

    void setJsonModelDescriptor(JsonModelDescriptor descriptor) {
        this.jsonModelDescriptor = descriptor;
    }

    /**
     * One row of the Fields pane. The combo values are the candidate instances
     * of an object-typed field; a non-empty array marks the row as combo box.
     */
    public record FieldRow(String name, Object value, String type, String jsonType,
            boolean comboBox, Object[] comboValues, boolean synthetic) {

    }
}
