/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import javax.swing.table.AbstractTableModel;
import de.jare.tree.control.listeners.TreeFocusListener;

/**
 * Table model for displaying properties of the currently selected node.
 * Supports both generic objects and EditNode-specific properties.
 */
public class PropertyTableModel extends AbstractTableModel implements TreeFocusListener {

    private final String[] columnNames = {"Name", "Value", "Typ"};
    private Object[][] data = {
        {"x", 0, "int"},
        {"y", 0, "int"},
        {"z", 0, "int"}
    };

    @Override
    public int getRowCount() {
        return data.length;
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return data[rowIndex][columnIndex];
    }

    @Override
    public void setValueAt(Object aValue, int row, int col) {
        data[row][col] = aValue;
        fireTableCellUpdated(row, col);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 1;
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
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
            setProperties(new Object[0][0]);
            return;
        }

        EditNode editNode = null;
        if (node instanceof EditNode en) {
            editNode = en;
        } else if (node instanceof javax.swing.tree.DefaultMutableTreeNode dmtn) {
            Object uo = dmtn.getUserObject();
            if (uo instanceof EditNode en) {
                editNode = en;
            }
        }

        if (editNode != null) {
            java.util.List<Object[]> props = new java.util.ArrayList<>();
            
            props.add(new Object[]{"editId", editNode.getEditId(), "long"});
            props.add(new Object[]{"name", editNode.getName(), "String"});
            props.add(new Object[]{"typeKey", editNode.getTypeKey(), "String"});
            props.add(new Object[]{"childCount", editNode.getChildCount(), "int"});
            
            if (editNode instanceof EditNodeObject eno) {
                props.add(new Object[]{"objektInfo", eno.getObjektInfo(), "String"});
                props.add(new Object[]{"primValue", eno.getPrimValue(), "String"});
            }
            
            if (editNode instanceof EditNodeProperty enp) {
                props.add(new Object[]{"propName", enp.getPropName(), "String"});
                props.add(new Object[]{"type", enp.getType(), "String"});
                props.add(new Object[]{"primValue", enp.getPrimValue(), "String"});
            }
            
            setProperties(props.toArray(new Object[0][]));
            return;
        }

        Object[][] arr = {
            {"x", node.toString(), "String"},
            {"y", 0, "int"},
            {"z", 0, "int"}
        };
        setProperties(arr);
    }

    public void setProperties(Object[][] newData) {
        this.data = newData;
        fireTableDataChanged();
    }

}
