/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright> 
 */
package de.jare.tree.ui;


import de.jare.jsoncasted.editor.command.RenameNodeCommand;
import de.jare.jsoncasted.editor.command.SetNodeValueCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditNodePropertyArr;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.JackAttribut;
import de.jare.jsoncasted.editor.core.ParseMode;
import de.jare.jsoncasted.lang.JsonNodeType;
import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import de.jare.tree.control.JackUndoManager;




import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;


public class JsonJackAttrTableModel extends AbstractJsonJackTableModel
        implements JsonJackComboCapableModel {

    private final String[] columnNames = {"Name", "Value", "Type", "^"};
    private List<PropertyRow> rows = new ArrayList<>();
    private EditNode currentEditNode;

    /**
     * Creates a local-only model: no change ever leaves the pane, renames included.
     */
    public JsonJackAttrTableModel() {
    }

    /**
     * Creates the model with the undo manager of the master control. A rename of the "name"
     * attribute and a primitive value edit are executed as commands through it - provided the
     * parse mode of the active tree allows them (see {@link #setValueAt(Object, int, int)});
     * a refused write-back leaves a note in the fast log.
     *
     * @param undoMan the undo manager of the master control, may be {@code null}
     */
    public JsonJackAttrTableModel(JackUndoManager undoMan) {
        super(undoMan);
    }

    /**
     * Creates the model with the undo manager and the fast log of the master control: refused
     * write-backs (a rename in the hard parse mode, for example) leave a note in the fast log
     * instead of staying silent.
     *
     * @param undoMan the undo manager of the master control, may be {@code null}
     * @param fastLog the fast log of the master control, may be {@code null}
     */
    public JsonJackAttrTableModel(JackUndoManager undoMan, de.jare.tree.control.fastlog.FastLog fastLog) {
        super(undoMan, fastLog);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        PropertyRow row = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 ->
                row.name();
            case 1 ->
                row.value();
            case 2 ->
                row.type();
            case 3 ->
                isButtonEnabled(rowIndex);
            default ->
                null;
        };
    }

    /**
     * Accepts the change locally - with two write-back exceptions of the sandbox:
     * <ul>
     * <li>the <b>name</b> attribute executes a {@link RenameNodeCommand} in the soft parse
     * mode and without semantics (a rename re-binds the field: new name - other field -
     * other type moves in, the soft parser follows asynchronously). The hard parse mode
     * forbids renames so far (parse mode concept, open decision 2): the change stays in the
     * pane there. A blank name is guarded by the pane.</li>
     * <li>a <b>primitive value</b> (text, number, boolean - the primValue of a property or
     * the value of a primitive row object) executes a {@link SetNodeValueCommand} in BOTH
     * parse modes: primitives are shape-conform by nature, the parser judges the content
     * (a text in a number field gets flagged).</li>
     * </ul>
     * Every other attribute never leaves the pane.
     */
    @Override
    public void setValueAt(Object aValue, int row, int col) {
        if (col == 1 && row < rows.size()) {
            PropertyRow oldRow = rows.get(row);
            writeBackIfAllowed(oldRow.name(), asText(aValue));
            PropertyRow newRow = new PropertyRow(oldRow.name(), aValue, oldRow.type());
            rows.set(row, newRow);
            fireTableCellUpdated(row, col);
        }
    }

    /**
     * Executes the write-back exception of the sandbox for the given attribute, if one applies:
     * the rename as a command in the soft parse mode and without semantics only, the primitive
     * value as a command in both parse modes. Without a change nothing runs.
     *
     * @param attributeName the attribute being edited
     * @param newValue the new value
     */
    private void writeBackIfAllowed(String attributeName, String newValue) {
        if (currentEditNode == null || !isWriteBackReady()) {
            return;
        }
        if ("name".equals(attributeName)) {
            final boolean changed = newValue != null && !newValue.isBlank()
                    && !Objects.equals(currentEditNode.getName(), newValue);
            if (!changed) {
                return;
            }
            if (getEditTree() != null && getEditTree().getParseMode() == ParseMode.HARD_PARSE) {
                // the hard parse mode forbids renames so far (parse mode concept, open
                // decision 2): the change stays in the pane - the refusal must not be silent
                tryLog("editor", "[hard parse] rename of '" + currentEditNode.getName()
                        + "' to '" + newValue + "' rejected: the hard parse mode forbids "
                        + "renames - the name change stays in the pane");
                return;
            }
            // a pass-through array wrapper ("array") carries no data of its own: no rename.
            // A blank name is no valid rename either - the pane guards it (the command check
            // exists, but the history manager does not enforce it on execute)
            if (!isPassThroughArray()) {
                getUndoMan().executeCommand(new RenameNodeCommand(currentEditNode, newValue));
            }
            return;
        }
        if (isPrimitiveValueRow(attributeName)
                && !Objects.equals(currentEditNode.getValue(), newValue)) {
            getUndoMan().executeCommand(new SetNodeValueCommand(currentEditNode, newValue));
        }
    }

    /**
     * Checks whether the attribute row carries a primitive value: the primValue of a property
     * with a primitive node type, or the value of a primitive row object (a scalar array row -
     * no children, a value). Structural attributes (objects with children, arrays, annotations)
     * never write through.
     *
     * @param attributeName the attribute being edited
     * @return true when the row is a primitive value carrier
     */
    private boolean isPrimitiveValueRow(String attributeName) {
        if ("primValue".equals(attributeName)
                && currentEditNode instanceof EditNodeProperty property) {
            return isPrimitiveType(property.getType());
        }
        return "value".equals(attributeName)
                && currentEditNode instanceof EditNodeObject object
                && object.getChildCount() == 0
                && object.getValue() != null;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        // column 0 = Name (fixed), column 1 = Value (editable), column 2 = Type (not editable),
        // column 3 = "^" button (the click is intercepted by a mouse listener, not editable).
        // for now only the attributes "value", "name" and "primValue" are editable.
        return columnIndex == 1 && isAttributeEditable(rowIndex);
    }

    /**
     * Checks whether the attribute of the given row is editable for now. Only
     * the attributes "value", "name" and "primValue" may be edited; all others
     * are read-only. A pass-through array wrapper ({@link EditNodePropertyArr},
     * the anonymous "array" node) carries no data of its own - its name and
     * primValue are structural defaults that only route the content, so they
     * stay read-only too. A named array field keeps its editable rows: renaming
     * it re-binds the field in the soft parse mode.
     *
     * @param rowIndex the row index
     * @return true when the attribute is editable
     */
    public boolean isAttributeEditable(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return false;
        }
        final String attributeName = rows.get(rowIndex).name();
        if (isPassThroughArray()
                && ("name".equals(attributeName) || "primValue".equals(attributeName))) {
            return false;
        }
        return "value".equals(attributeName) || "name".equals(attributeName) || "primValue".equals(attributeName);
    }

    /**
     * Checks whether the current node is a pass-through array wrapper: an anonymous array
     * (EditNodePropertyArr) routes its content only, its name ("array") and primValue are
     * structural defaults.
     *
     * @return true when the shown node is a pass-through array
     */
    private boolean isPassThroughArray() {
        return currentEditNode instanceof EditNodePropertyArr;
    }

    /**
     * Checks if the cell at the given row and column should be rendered as a
     * combo box.
     *
     * @param rowIndex the row index
     * @param columnIndex the column index
     * @return true if this cell should be a combo box
     */
    public boolean isComboBoxCell(int rowIndex, int columnIndex) {
        if (columnIndex != 1 || rowIndex >= rows.size()) {
            return false;
        }
        String attributeName = rows.get(rowIndex).name();
        return isComboBoxAttribute(attributeName);
    }

    /**
     * Returns the available values for a combo box cell.
     *
     * @param rowIndex the row index
     * @return array of available values, or empty array if not a combo box
     * attribute
     */
    public Object[] getComboBoxValues(int rowIndex) {
        if (rowIndex >= rows.size()) {
            return new Object[0];
        }
        String attributeName = rows.get(rowIndex).name();
        return getComboBoxValuesForAttribute(attributeName);
    }

    /**
     * Checks if the given attribute name should be rendered as a combo box.
     *
     * @param attributeName the attribute name
     * @return true if this attribute should be a combo box
     */
    private boolean isComboBoxAttribute(String attributeName) {
        return "jsonType".equals(attributeName) || "jsonField".equals(attributeName);
    }

    /**
     * Returns the available values for a combo box attribute.
     *
     * @param attributeName the attribute name
     * @return array of available values
     */
    private Object[] getComboBoxValuesForAttribute(String attributeName) {
        if (getJsonModelDescriptor() == null) {
            return new Object[0];
        }

        if ("jsonType".equals(attributeName)) {
            // Return all type names from the descriptor
            Collection<JsonTypeDescriptor> types = getJsonModelDescriptor().getTypes();
            List<Object> typeNames = new ArrayList<>();
            for (JsonTypeDescriptor typeDesc : types) {
                typeNames.add(typeDesc.getTypeName());
            }
            return typeNames.toArray();
        } else if ("jsonField".equals(attributeName)) {
            // Return all field names from all types in the descriptor
            List<Object> fieldNames = new ArrayList<>();
            Collection<JsonTypeDescriptor> types = getJsonModelDescriptor().getTypes();
            for (JsonTypeDescriptor typeDesc : types) {
                if (typeDesc != null) {
                    List<JsonFieldDescriptor> fields = typeDesc.getFields();
                    if (fields != null) {
                        for (JsonFieldDescriptor field : fields) {
                            fieldNames.add(field.getFieldName());
                        }
                    }
                }
            }
            return fieldNames.toArray();
        }

        return new Object[0];
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == 3 ? Boolean.class : String.class;
    }

    @Override
    public void onNodeSelected(DefaultMutableTreeNode node, Object trigger, boolean rootSelected) {
        updateProperties(node);
    }

    /**
     * Refreshes the attribute rows of the selected node: a command on the pane's own tree -
     * from this pane, the tree or the fields pane - shows its result here.
     */
    @Override
    protected void refreshPane() {
        updateProperties(currentEditNode);
    }

    private void updateProperties(Object node) {
        // extract the userObject from the DefaultMutableTreeNode
        Object actualNode = node;
        if (node instanceof DefaultMutableTreeNode treeNode) {
            actualNode = treeNode.getUserObject();
        }

        if (actualNode == null) {
            currentEditNode = null;
            rows.clear();
            fireTableDataChanged();
            return;
        }

        List<PropertyRow> newRows = new ArrayList<>();

        if (actualNode instanceof EditNode editNode) {
            currentEditNode = editNode;
            Map<String, JackAttribut> attributes = editNode.getAttributes();
            if (attributes != null) {
                for (Map.Entry<String, JackAttribut> entry : attributes.entrySet()) {
                    String name = entry.getKey();
                    JackAttribut jackAttribut = entry.getValue();
                    newRows.add(new PropertyRow(name, jackAttribut.getValue(), jackAttribut.getType()));
                }
                // sort the attributes alphabetically by name
                newRows.sort(Comparator.comparing(PropertyRow::name));
            }
        } else {
            // fallback for non-EditNode objects
            newRows.add(new PropertyRow("toString", actualNode.toString(), "String"));
        }

        rows = newRows;
        fireTableDataChanged();
    }

    public void setProperties(List<PropertyRow> newRows) {
        this.rows = new ArrayList<>(newRows);
        fireTableDataChanged();
    }

    /**
     * Sets the properties from a map of JackAttribut objects.
     *
     * @param attributes the map of JackAttribut objects
     */
    public void setPropertiesFromJackAttribut(Map<String, JackAttribut> attributes) {
        if (attributes == null) {
            rows.clear();
            fireTableDataChanged();
            return;
        }
        List<PropertyRow> newRows = new ArrayList<>();
        for (Map.Entry<String, JackAttribut> entry : attributes.entrySet()) {
            JackAttribut attr = entry.getValue();
            newRows.add(new PropertyRow(entry.getKey(), attr.getValue(), attr.getType()));
        }
        newRows.sort(Comparator.comparing(PropertyRow::name));
        this.rows = newRows;
        fireTableDataChanged();
    }

    /**
     * Checks whether the "^" button is enabled for the given row. The button
     * is only enabled when the attribute value is neither null nor empty.
     *
     * @param rowIndex the row index
     * @return true when the button should be enabled
     */
    public boolean isButtonEnabled(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return false;
        }
        Object value = rows.get(rowIndex).value();
        if (value == null) {
            return false;
        }
        String text = value.toString();
        return !text.isEmpty();
    }

    /**
     * Returns the property row for the given row index.
     *
     * @param rowIndex the row index
     * @return the PropertyRow, or null when the index is invalid
     */
    public PropertyRow getRow(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return null;
        }
        return rows.get(rowIndex);
    }

    /**
     * Returns the currently selected EditNode (node) whose attributes are
     * shown in this table.
     *
     * @return the current EditNode, or null when no node is selected
     */
    public EditNode getCurrentEditNode() {
        return currentEditNode;
    }

    public record PropertyRow(String name, Object value, String type) {

    }

}
