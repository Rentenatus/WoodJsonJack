/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright> 
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.table.AbstractTableModel;

public class PropertyTableModel extends AbstractTableModel implements TreeFocusListener {

    private final String[] columnNames = {"Name", "Value", "Typ"};
    private List<PropertyRow> rows = new ArrayList<>();

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
            case 0 -> row.name();
            case 1 -> row.value();
            case 2 -> row.type();
            default -> null;
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

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }

    @Override
    public void onNodeSelected(Object node, Object trigger, boolean rootSelected) {
        updateProperties(node);
    }

    @Override
    public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
        // NoOp
    }

    private void updateProperties(Object node) {
        if (node == null) {
            rows.clear();
            fireTableDataChanged();
            return;
        }

        List<PropertyRow> newRows = new ArrayList<>();
        
        if (node instanceof EditNode editNode) {
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
            newRows.add(new PropertyRow("toString", node.toString(), "String"));
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
