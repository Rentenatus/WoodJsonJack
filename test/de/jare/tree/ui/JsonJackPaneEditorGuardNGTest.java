package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.debug.JsonDebugLevel;
import de.jare.tree.control.JackMasterControl;
import javax.swing.JTable;
import javax.swing.tree.DefaultMutableTreeNode;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Editor guard of the panes: a node switch rebuilds the rows underneath an open cell editor -
 * the stale editor keeps its row index and would write into the wrong node on commit. The pane
 * cancels the editor on every whole-table rebuild.
 *
 * @author Janusch Rentenatus
 */
public class JsonJackPaneEditorGuardNGTest {

    private static EditTree importTree() throws java.io.IOException, de.jare.jsoncasted.io.JsonParseException {
        final de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse("{ \"matrix\": 5, \"a\": { \"name\": \"A\" } }", JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        return JsonTreeConverter.convertRessourceToEditTree(resource, "root");
    }

    private static int rowOf(JTable table, String name) {
        for (int i = 0; i < table.getRowCount(); i++) {
            if (name.equals(table.getValueAt(i, 0))) {
                return i;
            }
        }
        fail("no row named " + name);
        return -1;
    }

    /**
     * The attributes pane cancels its open editor when the selected node changes.
     */
    @Test
    public void testAttributesCancelsEditorOnNodeSwitch() throws Exception {
        final EditTree tree = importTree();
        final JackMasterControl master = new JackMasterControl();
        final JsonJackAttributesPane pane = new JsonJackAttributesPane(master, null);
        final JTable table = pane.getTable();

        master.fireSelection(new DefaultMutableTreeNode(tree.getRoot().getChildAt(0)), null, false);
        assertTrue(table.editCellAt(rowOf(table, "primValue"), 1),
                "the primValue row opens an editor");
        assertTrue(table.isEditing(), "the editor is open");

        master.fireSelection(new DefaultMutableTreeNode(tree.getRoot().getChildAt(1)), null, false);
        assertFalse(table.isEditing(), "the node switch cancels the open editor");
    }

    /**
     * The fields pane cancels its open editor when the selected node changes - the value the
     * stale editor would have written belongs to the wrong node object.
     */
    @Test
    public void testFieldsCancelsEditorOnNodeSwitch() throws Exception {
        final EditTree tree = importTree();
        final JackMasterControl master = new JackMasterControl();
        final JsonJackObjectPane pane = new JsonJackObjectPane(master, null);
        final JTable table = pane.getTable();

        master.fireSelection(new DefaultMutableTreeNode(tree.getRoot()), null, false);
        // the "a" row is an unparsed object field without candidates: inline editable
        assertTrue(table.editCellAt(rowOf(table, "a"), 1),
                "the object field row opens an editor");
        assertTrue(table.isEditing(), "the editor is open");

        master.fireSelection(new DefaultMutableTreeNode(tree.getRoot().getChildAt(1).getChildAt(0)), null, false);
        assertFalse(table.isEditing(), "the node switch cancels the open editor");
    }
}
