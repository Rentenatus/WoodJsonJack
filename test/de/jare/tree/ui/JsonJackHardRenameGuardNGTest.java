package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditStatus;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import de.jare.jsoncasted.editor.core.ParseMode;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import de.jare.debug.JsonDebugLevel;
import de.jare.tree.control.JackUndoManager;
import de.jare.tree.control.fastlog.FastLog;
import de.jare.tree.control.fastlog.FastLogType;
import de.jare.tree.control.model.JackTreeModel;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Hard mode contract of the rename guard: the rename executes, the synchronous hard parse
 * judges it - an ERROR on the renamed node or in its subtree rolls the rename back (the
 * tree keeps the old name), and the fast log explains the refusal. An accepted rename
 * survives untouched.
 *
 * @author Janusch Rentenatus
 */
public class JsonJackHardRenameGuardNGTest {

    /**
     * Builds the guard fixture: a verified hard mode tree whose model descriptor sits on
     * the tree itself, so the synchronous parse resolves the fields of the typed root.
     */
    private static EditTree hardTree() throws java.io.IOException, de.jare.jsoncasted.io.JsonParseException {
        final de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse("{ \"matrix\": 5 }", JsonDebugLevel.SIMPLE);
        assertNotNull(resource);
        final EditTree tree = JsonTreeConverter.convertRessourceToEditTree(resource, "root");
        final EditNodeProperty matrix = (EditNodeProperty) tree.getRoot().getChildAt(0);

        final JsonModelDescriptor descriptor = new JsonModelDescriptor("guardModel");
        final JsonTypeDescriptor rootType = new JsonTypeDescriptor("Root");
        rootType.addField(new JsonFieldDescriptor("matrix", "Root"));
        rootType.addField(new JsonFieldDescriptor("count", "Root"));
        descriptor.addType(rootType);
        ((EditNodeObject) tree.getRoot()).setJsonType(rootType);

        // the hard mode accepts only a fully verified tree; the descriptor on the tree
        // lets the synchronous parse resolve the model
        tree.getRoot().setEditStatus(EditStatus.OKAY);
        matrix.setEditStatus(EditStatus.OKAY);
        assertTrue(tree.setParseMode(ParseMode.HARD_PARSE), "the verified tree may switch to hard mode");
        tree.setJsonModelDescriptor(descriptor);
        return tree;
    }

    private static FastLog fastLogWithEditorType() {
        final FastLog fastLog = new FastLog();
        fastLog.registerType(new FastLogType("editor", "Editor", true));
        return fastLog;
    }

    /**
     * A rename the synchronous hard parse flags as ERROR is rolled back: the tree keeps
     * the old name, the fast log explains the refusal.
     */
    @Test
    public void testParseErrorRollsTheRenameBack() throws Exception {
        final EditTree tree = hardTree();
        final EditNodeProperty matrix = (EditNodeProperty) tree.getRoot().getChildAt(0);
        final JackUndoManager undoMan = new JackUndoManager();
        undoMan.setActiveModel(new JackTreeModel(tree));
        final FastLog fastLog = fastLogWithEditorType();

        final boolean survived = JsonJackHardRenameGuard.executeGuarded(
                undoMan, fastLog, matrix, "phantom");

        assertFalse(survived, "the hard parse refused the rename");
        assertEquals(matrix.getName(), "matrix", "the rollback restored the old name");
        assertEquals(fastLog.getEntries("editor").size(), 1,
                "the refusal leaves a note in the fast log: " + fastLog.getEntries("editor"));
        assertTrue(fastLog.getEntries("editor").get(0).getMessage().contains("rolled back"),
                "the note explains the rollback: " + fastLog.getEntries("editor").get(0).getMessage());
    }

    /**
     * A rename the hard parse accepts survives the guard: no rollback, no log entry.
     */
    @Test
    public void testAcceptedRenameSurvives() throws Exception {
        final EditTree tree = hardTree();
        final EditNodeProperty matrix = (EditNodeProperty) tree.getRoot().getChildAt(0);
        final JackUndoManager undoMan = new JackUndoManager();
        undoMan.setActiveModel(new JackTreeModel(tree));
        final FastLog fastLog = fastLogWithEditorType();

        final boolean survived = JsonJackHardRenameGuard.executeGuarded(
                undoMan, fastLog, matrix, "count");

        assertTrue(survived, "the hard parse accepted the declared name");
        assertEquals(matrix.getName(), "count", "the rename stays");
        assertTrue(fastLog.getEntries("editor").isEmpty(), "nothing to complain about");
    }

    /**
     * A rename onto a name the parent already carries is no parse verdict but an invalid
     * object state: the guard checks the cardinality itself and rolls the rename back -
     * the parser alone would accept it (the rename parses the node, never the parent).
     */
    @Test
    public void testDuplicateFieldNameRollsTheRenameBack() throws Exception {
        final EditTree tree = hardTree();
        final EditNodeProperty matrix = (EditNodeProperty) tree.getRoot().getChildAt(0);
        // a sibling that already carries the target name
        tree.addNewChild((de.jare.jsoncasted.editor.core.EditNodeAbstract) tree.getRoot(), "count", false);
        final JackUndoManager undoMan = new JackUndoManager();
        undoMan.setActiveModel(new JackTreeModel(tree));
        final FastLog fastLog = fastLogWithEditorType();

        final boolean survived = JsonJackHardRenameGuard.executeGuarded(
                undoMan, fastLog, matrix, "count");

        assertFalse(survived, "the duplicate field name is no valid object state");
        assertEquals(matrix.getName(), "matrix", "the rollback restored the old name");
        assertEquals(tree.getRoot().getChildCount(), 2, "the sibling is untouched");
        assertTrue(fastLog.getEntries("editor").get(0).getMessage().contains("duplicate"),
                "the note names the duplicate: " + fastLog.getEntries("editor").get(0).getMessage());
    }
}
