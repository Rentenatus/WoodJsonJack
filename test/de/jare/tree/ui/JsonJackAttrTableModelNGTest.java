package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditStatus;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import de.jare.jsoncasted.editor.core.ParseMode;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.debug.JsonDebugLevel;
import de.jare.tree.control.JackUndoManager;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.model.JackTreeModel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Sandbox contract of the Attributes pane: attribute values never leave the pane, with the one
 * exception of the "name" attribute - a rename reaches the node as a RenameNodeCommand when the
 * parse mode allows renaming (soft parse mode, without semantics), stays local in the hard parse
 * mode and without an undo manager.
 *
 * @author Janusch Rentenatus
 */
public class JsonJackAttrTableModelNGTest {

    private static EditTree importTree() throws java.io.IOException, de.jare.jsoncasted.io.JsonParseException {
        final de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse("{ \"matrix\": 5 }", JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        return JsonTreeConverter.convertRessourceToEditTree(resource, "root");
    }

    /**
     * Minimal focus component: the model only reads the tree model from it.
     */
    private static TreeFocusComponent componentOf(JackTreeModel treeModel) {
        return new TreeFocusComponent() {
            @Override
            public JTree getTree() {
                return null;
            }

            @Override
            public JackTreeModel getModel() {
                return treeModel;
            }

            @Override
            public de.jare.tree.control.JackMasterControl getJackMaster() {
                return null;
            }
        };
    }

    private static JsonJackAttrTableModel selectModel(EditTree tree, EditNodeProperty field,
            JackUndoManager undoMan) {
        final JackTreeModel treeModel = new JackTreeModel(tree);
        final JsonJackAttrTableModel model = new JsonJackAttrTableModel(undoMan);
        if (undoMan != null) {
            undoMan.setActiveModel(treeModel);
        }
        model.onEditorSelected(componentOf(treeModel), null);
        model.onNodeSelected(new DefaultMutableTreeNode(field), null, false);
        return model;
    }

    private static EditNodeProperty matrixField(EditTree tree) {
        assertTrue(tree.getRoot().getChildAt(0) instanceof EditNodeProperty);
        return (EditNodeProperty) tree.getRoot().getChildAt(0);
    }

    private static int rowOf(JsonJackAttrTableModel model, String name) {
        for (int i = 0; i < model.getRowCount(); i++) {
            if (name.equals(model.getValueAt(i, 0))) {
                return i;
            }
        }
        fail("no row named " + name);
        return -1;
    }

    /**
     * The soft parse mode allows renaming: the change reaches the node as an undoable command,
     * so the tree shows it.
     */
    @Test
    public void testSoftModeRenameReachesTheNode() throws Exception {
        final EditTree tree = importTree();
        final EditNodeProperty matrix = matrixField(tree);
        assertEquals(tree.getParseMode(), ParseMode.SOFT_PARSE, "a fresh tree is in soft mode");
        final JackUndoManager undoMan = new JackUndoManager();
        final JsonJackAttrTableModel model = selectModel(tree, matrix, undoMan);

        final int nameRow = rowOf(model, "name");
        assertTrue(model.isCellEditable(nameRow, 1), "the name attribute is editable");
        model.setValueAt("matrix99", nameRow, 1);

        assertEquals(matrix.getName(), "matrix99", "the rename reaches the node as a command");
        assertEquals(model.getValueAt(nameRow, 1), "matrix99", "the pane shows the change");

        undoMan.undo();
        assertEquals(matrix.getName(), "matrix", "the pane rename is undoable like a tree rename");
    }

    /**
     * The hard parse mode forbids renames so far (parse mode concept, open decision 2): the
     * change stays in the pane.
     */
    @Test
    public void testHardModeRenameStaysLocal() throws Exception {
        final EditTree tree = importTree();
        final EditNodeProperty matrix = matrixField(tree);
        // the hard mode accepts only a fully verified tree
        tree.getRoot().setEditStatus(EditStatus.OKAY);
        matrix.setEditStatus(EditStatus.OKAY);
        assertTrue(tree.setParseMode(ParseMode.HARD_PARSE), "the verified tree may switch to hard mode");
        assertEquals(tree.getParseMode(), ParseMode.HARD_PARSE);
        final JackUndoManager undoMan = new JackUndoManager();
        final JsonJackAttrTableModel model = selectModel(tree, matrix, undoMan);

        final int nameRow = rowOf(model, "name");
        model.setValueAt("matrix99", nameRow, 1);

        assertEquals(matrix.getName(), "matrix", "the hard parse mode forbids the rename");
        assertEquals(model.getValueAt(nameRow, 1), "matrix99", "the pane still shows the change");
    }

    /**
     * A blank rename is disallowed by the command check: the node keeps its name, the pane
     * shows the blank input locally.
     */
    @Test
    public void testBlankRenameStaysLocal() throws Exception {
        final EditTree tree = importTree();
        final EditNodeProperty matrix = matrixField(tree);
        final JackUndoManager undoMan = new JackUndoManager();
        final JsonJackAttrTableModel model = selectModel(tree, matrix, undoMan);

        final int nameRow = rowOf(model, "name");
        model.setValueAt("   ", nameRow, 1);

        assertEquals(matrix.getName(), "matrix", "a blank name is disallowed by the command check");
        assertEquals(model.getValueAt(nameRow, 1), "   ", "the pane shows the input anyway");
    }

    /**
     * Value edits never leave the pane, whatever the attribute and the parse mode are.
     */
    @Test
    public void testValueEditStaysLocal() throws Exception {
        final EditTree tree = importTree();
        final EditNodeProperty matrix = matrixField(tree);
        final JackUndoManager undoMan = new JackUndoManager();
        final JsonJackAttrTableModel model = selectModel(tree, matrix, undoMan);

        final int valueRow = rowOf(model, "primValue");
        assertTrue(model.isCellEditable(valueRow, 1), "the primValue attribute is editable");
        model.setValueAt("42", valueRow, 1);

        assertEquals(matrix.getValue(), "5", "value edits never leave the pane");
        assertEquals(model.getValueAt(valueRow, 1), "42", "the pane shows the change");
    }

    /**
     * Without an undo manager (the local-only constructor) renames stay in the pane too.
     */
    @Test
    public void testRenameWithoutUndoManagerStaysLocal() throws Exception {
        final EditTree tree = importTree();
        final EditNodeProperty matrix = matrixField(tree);
        final JsonJackAttrTableModel model = selectModel(tree, matrix, null);

        final int nameRow = rowOf(model, "name");
        model.setValueAt("matrix99", nameRow, 1);

        assertEquals(matrix.getName(), "matrix", "no undo manager, no write-through");
        assertEquals(model.getValueAt(nameRow, 1), "matrix99", "the pane still shows the change");
    }
}
