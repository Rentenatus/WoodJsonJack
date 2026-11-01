package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNode;
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
 * Sandbox contract of the Attributes pane: attribute values leave the pane only through the
 * two write-back exceptions - a rename reaches the node as a RenameNodeCommand in the soft
 * parse mode and without semantics, a primitive value reaches the node as a
 * SetNodeValueCommand in BOTH parse modes (primitives are shape-conform, the parser judges
 * the content). Everything else, and both exceptions in their forbidden mode, stay local.
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

    private static JsonJackAttrTableModel selectModel(EditTree tree, EditNode node,
            JackUndoManager undoMan) {
        final JackTreeModel treeModel = new JackTreeModel(tree);
        final JsonJackAttrTableModel model = new JsonJackAttrTableModel(undoMan);
        if (undoMan != null) {
            undoMan.setActiveModel(treeModel);
        }
        model.onEditorSelected(componentOf(treeModel), null);
        model.onNodeSelected(new DefaultMutableTreeNode(node), null, false);
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
     * A primitive value is shape-conform in every parse mode: the change reaches the node as
     * a command in the soft mode too, and undo restores the old value.
     */
    @Test
    public void testPrimitiveValueReachesTheNodeInSoftMode() throws Exception {
        final EditTree tree = importTree();
        final EditNodeProperty matrix = matrixField(tree);
        final JackUndoManager undoMan = new JackUndoManager();
        final JsonJackAttrTableModel model = selectModel(tree, matrix, undoMan);

        final int valueRow = rowOf(model, "primValue");
        assertTrue(model.isCellEditable(valueRow, 1), "the primValue attribute is editable");
        model.setValueAt("42", valueRow, 1);

        assertEquals(matrix.getValue(), "42", "a primitive value edit reaches the node in soft mode");
        assertEquals(model.getValueAt(valueRow, 1), "42", "the pane shows the change");

        undoMan.undo();
        assertEquals(matrix.getValue(), "5", "the value edit is undoable");
    }

    /**
     * Unlike a rename, a primitive value reaches the node in the hard parse mode as well -
     * the parser judges the content, the shape is always conform.
     */
    @Test
    public void testPrimitiveValueReachesTheNodeInHardMode() throws Exception {
        final EditTree tree = importTree();
        final EditNodeProperty matrix = matrixField(tree);
        // the hard mode accepts only a fully verified tree
        tree.getRoot().setEditStatus(EditStatus.OKAY);
        matrix.setEditStatus(EditStatus.OKAY);
        assertTrue(tree.setParseMode(ParseMode.HARD_PARSE), "the verified tree may switch to hard mode");
        final JackUndoManager undoMan = new JackUndoManager();
        final JsonJackAttrTableModel model = selectModel(tree, matrix, undoMan);

        final int valueRow = rowOf(model, "primValue");
        model.setValueAt("42", valueRow, 1);

        assertEquals(matrix.getValue(), "42", "a primitive value edit reaches the node in hard mode");
        assertEquals(model.getValueAt(valueRow, 1), "42", "the pane shows the change");
    }

    /**
     * The value of a primitive row object (a scalar array row) reaches the node too: the
     * object carries its text in the value attribute.
     */
    @Test
    public void testPrimitiveRowObjectValueReachesTheNode() throws Exception {
        final de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse("{ \"values\": [ \"alpha\" ] }", JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        final EditTree tree = JsonTreeConverter.convertRessourceToEditTree(resource, "root");
        final EditNodeProperty values = (EditNodeProperty) tree.getRoot().getChildAt(0);
        assertTrue(values.getChildAt(0) instanceof de.jare.jsoncasted.editor.core.EditNodeObject);
        final de.jare.jsoncasted.editor.core.EditNodeObject rowObject
                = (de.jare.jsoncasted.editor.core.EditNodeObject) values.getChildAt(0);

        final JackUndoManager undoMan = new JackUndoManager();
        final JsonJackAttrTableModel model = selectModel(tree, rowObject, undoMan);

        final int valueRow = rowOf(model, "value");
        assertTrue(model.isCellEditable(valueRow, 1), "the value of a primitive row object is editable");
        model.setValueAt("beta", valueRow, 1);

        assertEquals(rowObject.getValue(), "beta", "the value of the primitive row object reaches the node");
    }

    /**
     * A structural object (the root carries its name as value but has children): editing its
     * value attribute is no primitive edit and stays in the pane.
     */
    @Test
    public void testStructuralObjectValueStaysLocal() throws Exception {
        final EditTree tree = importTree();
        final JackUndoManager undoMan = new JackUndoManager();
        final JsonJackAttrTableModel model = selectModel(tree, tree.getRoot(), undoMan);

        final int valueRow = rowOf(model, "value");
        model.setValueAt("sandbox", valueRow, 1);

        assertEquals(((de.jare.jsoncasted.editor.core.EditNodeObject) tree.getRoot()).getValue(),
                "root", "the structural object keeps its value");
        assertEquals(model.getValueAt(valueRow, 1), "sandbox", "the pane still shows the change");
    }

    /**
     * Without an undo manager (the local-only constructor) value edits stay in the pane too.
     */
    @Test
    public void testValueEditWithoutUndoManagerStaysLocal() throws Exception {
        final EditTree tree = importTree();
        final EditNodeProperty matrix = matrixField(tree);
        final JsonJackAttrTableModel model = selectModel(tree, matrix, null);

        final int valueRow = rowOf(model, "primValue");
        model.setValueAt("42", valueRow, 1);

        assertEquals(matrix.getValue(), "5", "no undo manager, no write-through");
        assertEquals(model.getValueAt(valueRow, 1), "42", "the pane still shows the change");
    }

    /**
     * A command executed outside the pane - the tree's own rename path, for example -
     * refreshes the rows: the pane follows execute and undo of its own editor.
     */
    @Test
    public void testTreeSideCommandRefreshesThePane() throws Exception {
        final EditTree tree = importTree();
        final EditNodeProperty matrix = matrixField(tree);
        final JackUndoManager undoMan = new JackUndoManager();
        final JsonJackAttrTableModel model = selectModel(tree, matrix, undoMan);
        undoMan.addUndoRedoListener(5, model);

        // a rename from outside - the tree cell editor path
        undoMan.executeCommand(
                new de.jare.jsoncasted.editor.command.RenameNodeCommand(matrix, "matrix99"));
        assertEquals(model.getValueAt(rowOf(model, "name"), 1), "matrix99",
                "the pane follows a tree-side rename");

        undoMan.undo();
        assertEquals(model.getValueAt(rowOf(model, "name"), 1), "matrix",
                "the pane follows the undo");
    }

    /**
     * A pass-through array wrapper (the anonymous "array" node, EditNodePropertyArr) carries no
     * data of its own: its name and primValue rows stay read-only, no rename leaks through a
     * direct setValueAt. A named array field keeps its editable rows - renaming it re-binds the
     * field.
     */
    @Test
    public void testPassThroughArrayRowsStayReadOnly() throws Exception {
        final de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse("{ \"values\": [ [ \"alpha\" ] ] }", JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        final EditTree tree = JsonTreeConverter.convertRessourceToEditTree(resource, "root");
        assertTrue(tree.getRoot().getChildAt(0) instanceof EditNodeProperty);
        final EditNodeProperty values = (EditNodeProperty) tree.getRoot().getChildAt(0);
        assertTrue(values.getChildAt(0) instanceof de.jare.jsoncasted.editor.core.EditNodePropertyArr,
                "an inner anonymous array is a pass-through wrapper");
        final de.jare.jsoncasted.editor.core.EditNodePropertyArr inner
                = (de.jare.jsoncasted.editor.core.EditNodePropertyArr) values.getChildAt(0);

        final JackUndoManager undoMan = new JackUndoManager();
        final JsonJackAttrTableModel model = selectModel(tree, inner, undoMan);

        assertFalse(model.isCellEditable(rowOf(model, "name"), 1),
                "the array wrapper name is read-only");
        assertFalse(model.isCellEditable(rowOf(model, "primValue"), 1),
                "the array wrapper primValue is read-only");

        model.setValueAt("renamed", rowOf(model, "name"), 1);
        assertEquals(inner.getName(), "array", "no rename reaches a pass-through array");
        assertEquals(model.getValueAt(rowOf(model, "name"), 1), "renamed",
                "the pane shows the input locally");

        // the named array field keeps its editable rows
        final JsonJackAttrTableModel fieldModel = selectModel(tree, values, undoMan);
        assertTrue(fieldModel.isCellEditable(rowOf(fieldModel, "name"), 1),
                "a named array field is renamable");
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
