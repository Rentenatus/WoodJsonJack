package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditStatus;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import de.jare.jsoncasted.editor.core.ParseMode;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.debug.JsonDebugLevel;
import de.jare.tree.control.JackUndoManager;
import de.jare.tree.control.fastlog.FastLog;
import de.jare.tree.control.fastlog.FastLogType;
import de.jare.tree.control.model.JackTreeModel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Hard mode contract of the tree cell editor: renames are forbidden (parse mode concept, open
 * decision 2 - delete and add the field instead), the tree keeps the old name, and the refusal
 * leaves a note in the fast log instead of silently vanishing. The soft mode still renames.
 *
 * @author Janusch Rentenatus
 */
public class JsonJackTreeCellEditorNGTest {

    private static FastLog fastLogWithEditorType() {
        final FastLog fastLog = new FastLog();
        fastLog.registerType(new FastLogType("editor", "Editor", true));
        return fastLog;
    }

    /**
     * The hard parse mode refuses the rename: no command runs, the node keeps its name, the
     * fast log explains the refusal.
     */
    @Test
    public void testHardModeRefusesRenameAndLogs() throws Exception {
        final de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse("{ \"matrix\": 5 }", JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        final EditTree tree = JsonTreeConverter.convertRessourceToEditTree(resource, "root");
        final EditNodeProperty matrix = (EditNodeProperty) tree.getRoot().getChildAt(0);
        // the hard mode accepts only a fully verified tree
        tree.getRoot().setEditStatus(EditStatus.OKAY);
        matrix.setEditStatus(EditStatus.OKAY);
        assertTrue(tree.setParseMode(ParseMode.HARD_PARSE), "the verified tree may switch to hard mode");

        final FastLog fastLog = fastLogWithEditorType();
        final JsonJackTreeCellEditor editor = new JsonJackTreeCellEditor(null, fastLog);
        final JTree jtree = new JTree(new JackTreeModel(tree));
        final java.awt.Component comp = editor.getTreeCellEditorComponent(
                jtree, new DefaultMutableTreeNode(matrix), true, true, false, 0);
        assertTrue(comp instanceof JTextField, "the tree edits names in a text field");
        ((JTextField) comp).setText("gibtsnicht");

        editor.getCellEditorValue(); // commits the edit

        assertEquals(matrix.getName(), "matrix", "the hard parse mode refuses the rename");
        assertEquals(fastLog.getEntries("editor").size(), 1,
                "the refused rename leaves a note in the fast log: " + fastLog.getEntries("editor"));
    }

    /**
     * The soft parse mode still renames through the editor, undoable like every command.
     */
    @Test
    public void testSoftModeStillRenames() throws Exception {
        final de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse("{ \"matrix\": 5 }", JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        final EditTree tree = JsonTreeConverter.convertRessourceToEditTree(resource, "root");
        final EditNodeProperty matrix = (EditNodeProperty) tree.getRoot().getChildAt(0);

        final JackTreeModel treeModel = new JackTreeModel(tree);
        final JackUndoManager undoMan = new JackUndoManager();
        undoMan.setActiveModel(treeModel);
        final JsonJackTreeCellEditor editor = new JsonJackTreeCellEditor(undoMan, fastLogWithEditorType());
        final JTree jtree = new JTree(treeModel);
        final java.awt.Component comp = editor.getTreeCellEditorComponent(
                jtree, new DefaultMutableTreeNode(matrix), true, true, false, 0);
        ((JTextField) comp).setText("matrix99");

        editor.getCellEditorValue(); // commits the edit

        assertEquals(matrix.getName(), "matrix99", "the soft parse mode renames through the editor");
    }
}
