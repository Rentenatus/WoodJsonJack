/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright> 
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;

public class JsonJackAttrTableModel extends AbstractTableModel implements TreeFocusListener {

    private final String[] columnNames = {"Name", "Value", "Typ"};
    private List<PropertyRow> rows = new ArrayList<>();
    private JsonModelDescriptor jsonModelDescriptor;

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
        // Spalte 0 = Name (fix), Spalte 1 = Value (editierbar), Spalte 2 = Typ (nicht editierbar)
        return columnIndex == 1 && rowIndex < rows.size();
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
            Collection<JsonTypeDescriptor> types = jsonModelDescriptor.values();
            List<Object> typeNames = new ArrayList<>();
            for (JsonTypeDescriptor typeDesc : types) {
                typeNames.add(typeDesc.getTypeName());
            }
            return typeNames.toArray();
        } else if ("jsonField".equals(attributeName)) {
            // Return all field names from all types in the descriptor
            List<Object> fieldNames = new ArrayList<>();
            Collection<JsonTypeDescriptor> types = jsonModelDescriptor.values();
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
        return String.class;
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
        // Extrahiere das userObject aus DefaultMutableTreeNode
        Object actualNode = node;
        if (node instanceof DefaultMutableTreeNode treeNode) {
            actualNode = treeNode.getUserObject();
        }

        if (actualNode == null) {
            rows.clear();
            fireTableDataChanged();
            return;
        }

        List<PropertyRow> newRows = new ArrayList<>();

        if (actualNode instanceof EditNode editNode) {
            Map<String, Object> attributes = editNode.getAttributes();
            if (attributes != null) {
                for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                    String name = entry.getKey();
                    Object value = entry.getValue();
                    String type = value == null ? "null" : value.getClass().getSimpleName();
                    newRows.add(new PropertyRow(name, value, type));
                }
            }
        } else {
            // Fallback für nicht-EditNode Objekte
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
     * Record für eine Eigenschaftszeile
     */
    public record PropertyRow(String name, Object value, String type) {

    }

}
