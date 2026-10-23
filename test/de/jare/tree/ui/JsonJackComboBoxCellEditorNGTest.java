package de.jare.tree.ui;

import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Regression tests for the cell editor of the value columns: the typed text and the combo
 * choice must survive stopCellEditing - the JTable reads getCellEditorValue only afterwards,
 * when the editor components are already released.
 *
 * @author Janusch Rentenatus
 */
public class JsonJackComboBoxCellEditorNGTest {

    /**
     * The JTable calls getCellEditorValue only after stopCellEditing has torn the editor
     * down: the typed text must be captured before, not lost with the released text field.
     */
    @Test
    public void typedTextSurvivesStopCellEditing() {
        final JsonJackAttrTableModel model = new JsonJackAttrTableModel();
        model.setProperties(java.util.List.of(
                new JsonJackAttrTableModel.PropertyRow("name", "matrix", "String")));
        final JTable table = new JTable(model);

        final JsonJackComboBoxCellEditor editor = new JsonJackComboBoxCellEditor();
        final java.awt.Component comp = editor.getTableCellEditorComponent(table, "matrix", true, 0, 1);
        assertTrue(comp instanceof JTextField, "a non-combo cell edits in a plain text field");
        ((JTextField) comp).setText("matrix99");

        assertTrue(editor.stopCellEditing());
        assertEquals(editor.getCellEditorValue(), "matrix99",
                "the typed text reaches setValueAt, not the original value");
    }

    /**
     * The combo choice is captured on stop as well, so a selection made without an action
     * event (e.g. focus loss while the popup is open) still reaches setValueAt.
     */
    @Test
    public void comboChoiceSurvivesStopCellEditing() {
        final JsonModelDescriptor descriptor = new JsonModelDescriptor("sceneModel");
        descriptor.addType(new JsonTypeDescriptor("Scene"));
        final JsonJackAttrTableModel model = new JsonJackAttrTableModel();
        model.setJsonModelDescriptor(descriptor);
        model.setProperties(java.util.List.of(
                new JsonJackAttrTableModel.PropertyRow("jsonType", "", "String")));
        final JTable table = new JTable(model);

        final JsonJackComboBoxCellEditor editor = new JsonJackComboBoxCellEditor();
        final java.awt.Component comp = editor.getTableCellEditorComponent(table, "", true, 0, 1);
        assertTrue(comp instanceof JComboBox, "the jsonType attribute edits in a combo box");
        @SuppressWarnings("unchecked")
        final JComboBox<Object> combo = (JComboBox<Object>) comp;
        combo.setSelectedItem("Scene");

        assertTrue(editor.stopCellEditing());
        assertEquals(editor.getCellEditorValue(), "Scene");
    }
}
