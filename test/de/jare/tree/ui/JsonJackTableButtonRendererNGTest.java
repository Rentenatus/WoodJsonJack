package de.jare.tree.ui;

import javax.swing.JButton;
import javax.swing.JTable;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Tests for the button column renderer: "^" marks an editable row, "-" a read-only row (its
 * dialog opens in read-only mode), a disabled button shows a blank label.
 *
 * @author Janusch Rentenatus
 */
public class JsonJackTableButtonRendererNGTest {

    /**
     * The label follows the editability of the row, not only its enablement.
     */
    @Test
    public void editableRowsShowCaretReadOnlyRowsShowDash() {
        final JsonJackAttrTableModel model = new JsonJackAttrTableModel();
        model.setProperties(java.util.List.of(
                new JsonJackAttrTableModel.PropertyRow("name", "matrix", "String"),
                new JsonJackAttrTableModel.PropertyRow("|cast name", "Scene", "String"),
                new JsonJackAttrTableModel.PropertyRow("infotype", "", "String")));
        final JTable table = new JTable(model);
        final JsonJackTableButtonRenderer renderer = new JsonJackTableButtonRenderer(
                model::isButtonEnabled, model::isAttributeEditable);

        final JButton editable = (JButton) renderer.getTableCellRendererComponent(
                table, null, false, false, 0, 3);
        assertTrue(editable.isEnabled(), "the name row has a value to open");
        assertEquals(editable.getText(), "^", "the editable row shows the caret");

        final JButton readonly = (JButton) renderer.getTableCellRendererComponent(
                table, null, false, false, 1, 3);
        assertTrue(readonly.isEnabled(), "the cast name row has a value to open");
        assertEquals(readonly.getText(), "-", "the read-only row shows the dash");

        final JButton empty = (JButton) renderer.getTableCellRendererComponent(
                table, null, false, false, 2, 3);
        assertFalse(empty.isEnabled(), "a row without value opens nothing");
        assertEquals(empty.getText(), " ", "a disabled button shows a blank label");
    }
}
