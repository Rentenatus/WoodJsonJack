package de.jare.tree.ui;

import java.awt.Component;
import java.util.EventObject;
import javax.swing.AbstractCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.TableCellEditor;

/**
 * Cell editor for value columns: builds a combo box when the table model
 * marks the cell as a combo box cell (with candidates), a plain text field
 * otherwise. Works with every model implementing
 * {@link JsonJackComboCapableModel} - the Attributes and the Fields pane use
 * the same editor.
 *
 * @author Janusch Rentenatus
 */
public class JsonJackComboBoxCellEditor extends AbstractCellEditor implements TableCellEditor {

    private static final long serialVersionUID = 1L;

    private JComboBox<Object> comboBox;
    private JTextField textField;
    private Object currentValue;

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
            int row, int column) {
        currentValue = value;
        if (table.getModel() instanceof JsonJackComboCapableModel model
                && model.isComboBoxCell(row, column)) {
            final Object[] values = model.getComboBoxValues(row);
            if (values.length > 0) {
                comboBox = new JComboBox<>(values);
                comboBox.setSelectedItem(value);
                comboBox.addActionListener(e -> {
                    currentValue = comboBox.getSelectedItem();
                    stopCellEditing();
                });
                return comboBox;
            }
        }
        textField = new JTextField(value == null ? "" : value.toString());
        return textField;
    }

    @Override
    public Object getCellEditorValue() {
        if (textField != null) {
            return textField.getText();
        }
        if (comboBox != null) {
            return comboBox.getSelectedItem();
        }
        return currentValue;
    }

    @Override
    public boolean shouldSelectCell(EventObject anEvent) {
        return true;
    }

    /**
     * Captures the live value before the editor is torn down: the JTable reads
     * {@link #getCellEditorValue()} only after this method returned, when the
     * components are already released. The captured value becomes
     * {@code currentValue}, so the typed text (or the combo choice made without
     * an action event, e.g. on focus loss) reaches setValueAt.
     */
    @Override
    public boolean stopCellEditing() {
        if (textField != null) {
            currentValue = textField.getText();
        } else if (comboBox != null) {
            currentValue = comboBox.getSelectedItem();
        }
        textField = null;
        comboBox = null;
        return super.stopCellEditing();
    }
}
