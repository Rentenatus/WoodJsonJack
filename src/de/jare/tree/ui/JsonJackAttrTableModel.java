/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright> 
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.JackAttribut;
import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;

public class JsonJackAttrTableModel extends AbstractTableModel implements TreeFocusListener {

    private final String[] columnNames = {"Name", "Value", "Type", "^"};
    private List<PropertyRow> rows = new ArrayList<>();
    private JsonModelDescriptor jsonModelDescriptor;
    private EditNode currentEditNode;

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

    @Override
    public void setValueAt(Object aValue, int row, int col) {
        if (col == 1 && row < rows.size()) {
            PropertyRow oldRow = rows.get(row);
            PropertyRow newRow = new PropertyRow(oldRow.name(), aValue, oldRow.type());
            rows.set(row, newRow);
            fireTableCellUpdated(row, col);
        }
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
     * are read-only.
     *
     * @param rowIndex the row index
     * @return true when the attribute is editable
     */
    public boolean isAttributeEditable(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return false;
        }
        String name = rows.get(rowIndex).name();
        return "value".equals(name) || "name".equals(name) || "primValue".equals(name);
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
        if (jsonModelDescriptor == null) {
            return new Object[0];
        }

        if ("jsonType".equals(attributeName)) {
            // Return all type names from the descriptor
            Collection<JsonTypeDescriptor> types = jsonModelDescriptor.getTypes();
            List<Object> typeNames = new ArrayList<>();
            for (JsonTypeDescriptor typeDesc : types) {
                typeNames.add(typeDesc.getTypeName());
            }
            return typeNames.toArray();
        } else if ("jsonField".equals(attributeName)) {
            // Return all field names from all types in the descriptor
            List<Object> fieldNames = new ArrayList<>();
            Collection<JsonTypeDescriptor> types = jsonModelDescriptor.getTypes();
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

    /**
     * Sets the JsonModelDescriptor for this model. This is used to populate
     * combo box values for attributes like jsonType and jsonField.
     *
     * @param descriptor the model descriptor to set
     */
    public void setJsonModelDescriptor(JsonModelDescriptor descriptor) {
        this.jsonModelDescriptor = descriptor;
    }

    /**
     * Returns the JsonModelDescriptor for this model.
     *
     * @return the model descriptor, or null if not set
     */
    public JsonModelDescriptor getJsonModelDescriptor() {
        return jsonModelDescriptor;
    }

    @Override
    public void onNodeSelected(DefaultMutableTreeNode node, Object trigger, boolean rootSelected) {
        updateProperties(node);
    }

    @Override
    public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
        if (editor == null) {
            return;
        }
        setJsonModelDescriptor(editor.getModel().getJsonModelDescriptor());
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
