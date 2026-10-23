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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * <p>The pane is a sandbox: it accepts changes - inline value edits and the
 * results of the list editors - but never writes them back into the edit node,
 * whatever the parse mode of the tree is. The changes live in local overrides
 * keyed by edit id; they survive refreshes of the same object (the soft parse
 * mode refreshes asynchronously, the hard mode after every command) and are
 * dropped when another object or editor is selected.</p>
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

    // Local sandbox overrides, keyed by the edit id of the field (the synthetic value row
    // uses the edit id of the object itself). Never written back into the edit node.
    private final Map<Long, String> valueOverrides = new HashMap<>();
    private final Map<Long, List<InstanceCandidate>> objectListOverrides = new HashMap<>();
    private final Map<Long, List<String>> primitiveListOverrides = new HashMap<>();

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
     * Checks whether the "^" button of the given row can open something: array fields and parsed
     * single object fields open their dedicated editors, every other row below the jsonType
     * header opens the detail dialog when its value is neither null nor empty.
     *
     * @param rowIndex the row index
     * @return true when the button should be enabled
     */
    public boolean isButtonEnabled(int rowIndex) {
        if (rowIndex < 1 || rowIndex >= rows.size()) {
            return false;
        }
        if (isArrayFieldRow(rowIndex) || isSingleObjectFieldRow(rowIndex)) {
            return true;
        }
        Object value = rows.get(rowIndex).value();
        return value != null && !value.toString().isEmpty();
    }

    /**
     * Accepts the change locally only: the new value is kept in the sandbox override of the row
     * and shown in the pane, but never written back into the edit node - neither as a mutation
     * nor as a parse trigger, whatever the parse mode of the tree is.
     */
    @Override
    public void setValueAt(Object aValue, int row, int col) {
        if (col != 1 || row < 1 || row >= rows.size()) {
            return;
        }
        if (currentObject == null) {
            return;
        }
        final long key = overrideKeyOf(row);
        if (key >= 0L) {
            valueOverrides.put(key, aValue == null ? null : aValue.toString());
        }
        final FieldRow oldRow = rows.get(row);
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
     * One selectable instance for the collection editor: its wood identity, its display text, its model type and
     * the node itself (for identity comparison and for copies of identityless instances). The node is an object
     * instance, or - for whole list references - an identified array property.
     */
    public record InstanceCandidate(String woodId, String text, String jsonType, EditNodeAbstract node) {

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
     * Checks whether the object counts as a primitive element in a list: a reference stub looks
     * like a primitive row (no children, the display text as value), but it is an object reference
     * and belongs to the object side of every list handling.
     *
     * @param obj the object node
     * @return true when the object is a primitive list element
     */
    private static boolean isPrimitiveListElement(EditNodeObject obj) {
        return isPrimitiveRowObject(obj) && !obj.isLinkReference();
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
                return isPrimitiveListElement(child);
            }
        }
        return true;
    }

    /**
     * Checks whether the row is a parsed single object field: a combo box row (the field descriptor
     * offers instance candidates) whose node type is OBJECT. Those fields open the collection editor
     * in single mode - the choice replaces the value, an empty choice means null.
     *
     * @param rowIndex the row index
     * @return true when the row is a parsed object reference field
     */
    public boolean isSingleObjectFieldRow(int rowIndex) {
        if (!isComboBoxCell(rowIndex, 1)) {
            return false;
        }
        final EditNodeProperty field = fieldOfRow(rowIndex);
        return field != null && field.getType() == JsonNodeType.OBJECT;
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
        // A whole list reference fits at a list field only: object rows never offer arrays.
        final boolean rowIsListField = descriptor != null && descriptor.isAsListOrArray();
        final List<InstanceCandidate> result = new ArrayList<>();
        final Set<EditNodeAbstract> seen = new HashSet<>();
        if (editTree != null && editTree.getRoot() != null) {
            collectInstanceCandidates(editTree.getRoot(), declared, rowIsListField, result, seen);
        }
        final EditLinkingSet linkingSet = editTree == null ? null : editTree.getLinkingSet();
        if (linkingSet != null) {
            for (EditNodeAbstract held : linkingSet.getAllObjects()) {
                if (seen.add(held) && candidateFits(held, declared, rowIsListField)) {
                    result.add(candidateOf(held));
                }
            }
        }
        return result;
    }

    /**
     * The object elements the row currently shows: the sandbox override of the list editor when
     * one exists, the object elements recorded in the edit node otherwise. The dialog prefills
     * itself from this list, so a reopened editor sees the last sandbox choice.
     *
     * @param rowIndex the row index of the array field
     * @return the current elements in list order, never null
     */
    public List<InstanceCandidate> getCurrentObjectList(int rowIndex) {
        final EditNodeProperty field = fieldOfRow(rowIndex);
        if (field == null) {
            return List.of();
        }
        final List<InstanceCandidate> override = objectListOverrides.get(field.getEditId());
        if (override != null) {
            return override;
        }
        final List<InstanceCandidate> result = new ArrayList<>();
        for (int i = 0; i < field.getChildCount(); i++) {
            if (field.getChildAt(i) instanceof EditNodeObject child && !isPrimitiveListElement(child)) {
                result.add(candidateOf(child));
            }
        }
        return result;
    }

    /**
     * The primitive values the row currently shows: the sandbox override of the list editor when
     * one exists, the primitive rows recorded in the edit node otherwise (primitive lists and
     * array annotations). The dialog prefills itself from this list, so a reopened editor sees
     * the last sandbox choice.
     *
     * @param rowIndex the row index of the array field
     * @return the current values in list order, never null
     */
    public List<String> getCurrentPrimitiveList(int rowIndex) {
        final EditNodeProperty field = fieldOfRow(rowIndex);
        if (field == null) {
            return List.of();
        }
        final List<String> override = primitiveListOverrides.get(field.getEditId());
        if (override != null) {
            return override;
        }
        final List<String> result = new ArrayList<>();
        for (int i = 0; i < field.getChildCount(); i++) {
            if (field.getChildAt(i) instanceof EditNodeObject child && isPrimitiveListElement(child)) {
                result.add(child.getValue());
            }
        }
        return result;
    }

    /**
     * Applies the result of the collection editor to the sandbox only: the chosen instances replace
     * the displayed content of the row in the chosen order - a whole list reference (an identified
     * array candidate) reduces the choice to itself, as before. The edit node is never touched:
     * no element is detached, no reference stub is created, no parse runs - neither in the soft nor
     * in the hard parse mode. The override survives refreshes of the same object and is dropped
     * when another object is selected.
     *
     * @param rowIndex the row index of the array field
     * @param chosen the chosen instances in list order
     */
    public void applyObjectListResult(int rowIndex, List<InstanceCandidate> chosen) {
        final EditNodeProperty field = fieldOfRow(rowIndex);
        if (field == null || rowIndex < 1 || rowIndex >= rows.size()) {
            return;
        }
        // a whole list reference replaces the entire content: only the array candidate counts
        List<InstanceCandidate> effective = chosen;
        for (InstanceCandidate candidate : chosen) {
            if (candidate.node() instanceof EditNodeProperty) {
                effective = List.of(candidate);
                break;
            }
        }
        objectListOverrides.put(field.getEditId(), List.copyOf(effective));
        final FieldRow oldRow = rows.get(rowIndex);
        rows.set(rowIndex, new FieldRow(oldRow.name(), previewTextOf(effective), oldRow.type(),
                oldRow.jsonType(), oldRow.comboBox(), oldRow.comboValues(), oldRow.synthetic()));
        fireTableCellUpdated(rowIndex, 1);
    }

    /**
     * Applies the result of the primitive list editor to the sandbox only: the chosen values
     * replace the displayed content of the row in the given order. The edit node is never touched
     * - the primitive rows of the field stay as they are, no parse runs in any parse mode. The
     * override survives refreshes of the same object and is dropped when another object is
     * selected.
     *
     * @param rowIndex the row index of the array field
     * @param values the values in list order
     */
    public void applyPrimitiveListResult(int rowIndex, List<String> values) {
        final EditNodeProperty field = fieldOfRow(rowIndex);
        if (field == null || rowIndex < 1 || rowIndex >= rows.size()) {
            return;
        }
        primitiveListOverrides.put(field.getEditId(), List.copyOf(values));
        final FieldRow oldRow = rows.get(rowIndex);
        rows.set(rowIndex, new FieldRow(oldRow.name(), String.join(", ", values), oldRow.type(),
                oldRow.jsonType(), oldRow.comboBox(), oldRow.comboValues(), oldRow.synthetic()));
        fireTableCellUpdated(rowIndex, 1);
    }

    private static void collectInstanceCandidates(EditNode node, JsonTypeDescriptor declared, boolean rowIsListField,
            List<InstanceCandidate> result, Set<EditNodeAbstract> seen) {
        if (node instanceof EditNodeAbstract abs && seen.add(abs) && candidateFits(abs, declared, rowIsListField)) {
            result.add(candidateOf(abs));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChildAt(i) instanceof EditNodeAbstract child) {
                collectInstanceCandidates(child, declared, rowIsListField, result, seen);
            }
        }
    }

    /**
     * Checks whether the node fits as a candidate for the edited row: an object instance fits when its type matches
     * the declared element type, an identified array fits as a whole list reference at list fields only.
     */
    private static boolean candidateFits(EditNodeAbstract node, JsonTypeDescriptor declared, boolean rowIsListField) {
        if (node instanceof EditNodeProperty array) {
            return rowIsListField && arrayCandidateFits(array, declared);
        }
        if (!(node instanceof EditNodeObject obj)) {
            return false;
        }
        if (isPrimitiveRowObject(obj) || obj.isLinkReference()) {
            return false; // primitive rows are no object instances, a stub is only a reference
        }
        if (declared == null) {
            return true;
        }
        return typeFits(obj, declared);
    }

    /**
     * An array fits as a whole list reference when it carries a wood identity and its element type matches the
     * declared element type of the edited field.
     */
    private static boolean arrayCandidateFits(EditNodeProperty array, JsonTypeDescriptor declaredElement) {
        if (array.getType() != JsonNodeType.ARRAY || array.getObjektId() == null) {
            return false; // only identified arrays are referencable as a whole
        }
        if (declaredElement == null) {
            return true;
        }
        final JsonFieldDescriptor arrayField = array.getJsonField();
        return arrayField != null && declaredElement.getTypeName().equals(arrayField.getTypeName());
    }

    private static InstanceCandidate candidateOf(EditNodeAbstract node) {
        if (node instanceof EditNodeProperty array) {
            final JsonFieldDescriptor field = array.getJsonField();
            final String jsonType = field == null ? "array" : field.getTypeName() + "[]";
            final String text = array.getName() + " (" + array.getChildCount() + ")";
            return new InstanceCandidate(array.getObjektId(), text, jsonType, array);
        }
        final EditNodeObject obj = (EditNodeObject) node;
        final String woodId = obj.getObjektId() != null ? obj.getObjektId() : linkTargetId(obj);
        final String text = EditStringEvaluator.displayText(obj);
        final String jsonType = obj.getJsonType() == null ? "" : obj.getJsonType().getTypeName();
        return new InstanceCandidate(woodId, text, jsonType, obj);
    }

    /**
     * Extracts the bare target id of a reference stub: the part behind the provider prefix of the link target.
     */
    private static String linkTargetId(EditNodeObject obj) {
        final String target = obj.getLinkTarget();
        if (target == null || target.isEmpty()) {
            return null;
        }
        final int idx = target.lastIndexOf(de.jare.jsoncasted.lang.JsonTerms.COLONCOLON);
        return idx < 0 ? target : target.substring(idx + de.jare.jsoncasted.lang.JsonTerms.COLONCOLON.length());
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
            final EditTree newTree = treeModel.getEditTree();
            if (newTree != this.editTree) {
                // another editor: the sandbox of the previous tree is dropped
                valueOverrides.clear();
                objectListOverrides.clear();
                primitiveListOverrides.clear();
            }
            this.editTree = newTree;
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
        if (owner != currentObject) {
            // another object (or none) is shown: the sandbox of the previous object is dropped.
            // refresh() re-enters with the same object and keeps the overrides - they must
            // survive the refreshes of both parse modes (soft: asynchronous, hard: after
            // every command).
            valueOverrides.clear();
            objectListOverrides.clear();
            primitiveListOverrides.clear();
        }
        this.currentObject = owner;
        this.focusedFieldEditId = focusId;

        List<FieldRow> newRows = new ArrayList<>();
        if (owner != null) {
            newRows.add(new FieldRow("jsonType", typeNameOf(owner), "header", "", false, null, false));
            // A primitive object (an array row, for example) has no field children: its value gets a synthetic row.
            if (isPrimitiveRowObject(owner)) {
                final String cast = owner.getCastName() == null ? "" : owner.getCastName();
                final String value = valueOverrides.containsKey(owner.getEditId())
                        ? valueOverrides.get(owner.getEditId()) : owner.getValue();
                newRows.add(new FieldRow("value", value, "", cast, false, null, true));
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
        final Object value = sandboxedDisplayValue(field, field.getEditId());
        final Object[] candidates = comboBoxCandidates(field);
        final boolean comboBox = candidates.length > 0;
        final String jsonType = field instanceof EditNodeAnnotation
                ? "annotation" : jsonTypeOf(field);
        return new FieldRow(name, value, type, jsonType, comboBox, candidates, false);
    }

    /**
     * The display value of a field: the sandbox override when one exists, the node state otherwise.
     * A list editor override wins over an inline value override - array rows are not inline
     * editable.
     *
     * @param field the field property
     * @param editId the edit id of the field, the key of the sandbox overrides
     * @return the value to show in the Value column
     */
    private Object sandboxedDisplayValue(EditNodeProperty field, long editId) {
        final List<InstanceCandidate> objectList = objectListOverrides.get(editId);
        if (objectList != null) {
            return previewTextOf(objectList);
        }
        final List<String> primitiveList = primitiveListOverrides.get(editId);
        if (primitiveList != null) {
            return String.join(", ", primitiveList);
        }
        if (valueOverrides.containsKey(editId)) {
            return valueOverrides.get(editId);
        }
        return displayValueOf(field);
    }

    /**
     * The preview text of a chosen instance list: the candidate texts in list order, comma
     * separated.
     *
     * @param candidates the chosen instances
     * @return the preview text, empty when nothing is chosen
     */
    private static String previewTextOf(List<InstanceCandidate> candidates) {
        final StringBuilder sb = new StringBuilder();
        for (InstanceCandidate candidate : candidates) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(candidate.text());
        }
        return sb.toString();
    }

    /**
     * The sandbox key of a row: the edit id of the field of the row, or of the object itself for
     * the synthetic value row of a primitive object, or -1 when the row carries no sandbox state.
     *
     * @param rowIndex the model row index
     * @return the edit id of the row owner, or -1
     */
    private long overrideKeyOf(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return -1L;
        }
        if (rows.get(rowIndex).synthetic()) {
            return currentObject.getEditId();
        }
        final EditNodeProperty field = fieldOfRow(rowIndex);
        return field == null ? -1L : field.getEditId();
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
            for (EditNodeAbstract held : linkingSet.getAllObjects()) {
                if (held instanceof EditNodeObject obj && seen.add(obj) && typeFits(obj, declared)) {
                    candidates.add(instanceLabel(obj));
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
