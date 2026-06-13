package de.jare.jsoncasted.editor;

import de.jare.jsoncasted.editor.clipboard.ClipboardManager;
import de.jare.jsoncasted.editor.command.CopyToStashCommand;
import de.jare.jsoncasted.editor.command.CutToStashCommand;
import de.jare.jsoncasted.editor.command.PasteFromStashCommand;
import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.MoveNodeCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

/**
 * Integration test for clipboard behavior with mixed edits in between:
 * copy/cut/paste of at least two nodes, followed by move and value change, then
 * undo/redo of the paste.
 */
public class ClipboardEditMixTestNG implements ATestTools {

    public ClipboardEditMixTestNG() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        System.out.println("===============================================");
        System.out.println("## Start ClipboardEditMixTestNG.");
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        System.out.println("## End ClipboardEditMixTestNG.");
        System.out.println("===============================================");
    }
    private ClipboardManager clipboard;

    private EditTree treeA;
    private EditTree treeB;

    private TreeEditor editorA;
    private TreeEditor editorB;

    private EditNodeAbstract rootA;
    private EditNodeAbstract rootB;

    private EditNode a1;
    private EditNode a2;
    private EditNode a3;

    private EditNode b1;
    private EditNode b2;

    @BeforeMethod
    public void setUp() {
        clipboard = new ClipboardManager();
        editorA = new TreeEditor();
        editorB = new TreeEditor();

        treeA = editorA.getTree();
        rootA = treeA.getRoot();
        treeB = editorB.getTree();
        rootB = treeB.getRoot();

        a1 = treeA.addNewChild(rootA, "a1");
        a2 = treeA.addNewChild(rootA, "a2");
        a3 = treeA.addNewChild(rootA, "a3");
        b1 = treeB.addNewChild(rootB, "b1");
        b2 = treeB.addNewChild(rootB, "b2");
    }

    @Test
    public void testCopyPasteWithMoveAndValueChangeInBetweenRedo() {
        printTestHeader("testCopyPasteWithMoveAndValueChangeInBetweenRedo");

        SoftAssert softly = new SoftAssert();

        printEditorState(editorA, "Initial editorA");
        printEditorState(editorB, "Initial editorB");
        printSubtree(editorA, "Initial subtree A/root", rootA);
        printSubtree(editorB, "Initial subtree B/root", rootB);

        long[] copyIds = {a1.getEditId(), a2.getEditId()};

        CopyToStashCommand copyCmd = new CopyToStashCommand(
                clipboard,
                ClipboardManager.CLIPBOARD_STASH_NAME,
                copyIds
        );
        CommandResult copyExecute = copyCmd.execute(treeA);
        printCommandResult("COPY[2] execute", copyExecute);

        PasteFromStashCommand pasteCmd = new PasteFromStashCommand(
                clipboard,
                ClipboardManager.CLIPBOARD_STASH_NAME,
                rootB.getEditId(),
                -1
        );
        CommandResult pasteExecute = pasteCmd.execute(treeB);
        printCommandResult("PASTE(copy)[2] execute", pasteExecute);
        printEditorState(editorB, "After PASTE(copy)[2]");
        printSubtree(editorB, "Subtree B/root after paste(copy)[2]", rootB);

        softly.assertEquals(rootB.getChildCount(), 4,
                "Tree B should contain b1, b2 and two pasted nodes");

        long[] pastedIds = pasteCmd.getPastedNodeIds();
        softly.assertEquals(pastedIds.length, 2,
                "Exactly two nodes should be pasted");

        EditNodeAbstract pasted1 = treeB.findNodeById(pastedIds[0]);
        EditNodeAbstract pasted2 = treeB.findNodeById(pastedIds[1]);
        softly.assertNotNull(pasted1, "First pasted node must exist");
        softly.assertNotNull(pasted2, "Second pasted node must exist");

        // ------------------------------------------------------------
        // Zwischenänderungen: Move + Value/Text change
        // ------------------------------------------------------------
        MoveNodeCommand moveCmd = new MoveNodeCommand(pasted2, 0);
        CommandResult moveExecute = moveCmd.execute(treeB);
        printCommandResult("MOVE pasted2 -> index 0 execute", moveExecute);
        printEditorState(editorB, "After MOVE on editorB");
        printSubtree(editorB, "Subtree B/root after move", rootB);

        pasted1.setName("a1_changed_after_paste");
        System.out.println("VALUE CHANGE: pasted1 renamed to " + pasted1.getName());
        printEditorState(editorB, "After VALUE CHANGE on editorB");

        // ------------------------------------------------------------
        // Undo paste trotz Zwischenänderungen
        // ------------------------------------------------------------
        CommandResult pasteUndo = pasteCmd.undo(treeB);
        printCommandResult("PASTE(copy)[2] undo", pasteUndo);
        printEditorState(editorB, "After undo PASTE(copy)[2]");
        printSubtree(editorB, "Subtree B/root after undo paste(copy)[2]", rootB);

        softly.assertEquals(rootB.getChildCount(), 2,
                "Undo paste should remove both pasted nodes even after move/value-change");
        softly.assertNull(treeB.findNodeById(pastedIds[0]),
                "First pasted node must be gone after undo");
        softly.assertNull(treeB.findNodeById(pastedIds[1]),
                "Second pasted node must be gone after undo");

        // ------------------------------------------------------------
        // Redo paste nach dazwischenhängenden Änderungen
        // ------------------------------------------------------------
        CommandResult pasteRedo = pasteCmd.execute(treeB);
        printCommandResult("PASTE(copy)[2] redo", pasteRedo);
        printEditorState(editorB, "After redo PASTE(copy)[2]");
        printSubtree(editorB, "Subtree B/root after redo paste(copy)[2]", rootB);

        softly.assertEquals(rootB.getChildCount(), 4,
                "Redo paste should restore two pasted nodes");

        long[] redoIds = pasteCmd.getPastedNodeIds();
        softly.assertEquals(redoIds.length, 2,
                "Redo should again produce two pasted node IDs");
        softly.assertNotNull(treeB.findNodeById(redoIds[0]),
                "Redo pasted node 1 must exist");
        softly.assertNotNull(treeB.findNodeById(redoIds[1]),
                "Redo pasted node 2 must exist");

        softly.assertAll();

        printTestFooter();
    }

    @Test
    public void testCutPasteWithMoveAndValueChangeInBetweenRedo() {
        printTestHeader("testCutPasteWithMoveAndValueChangeInBetweenRedo");

        SoftAssert softly = new SoftAssert();

        printEditorState(editorA, "Initial editorA");
        printEditorState(editorB, "Initial editorB");

        long[] cutIds = {a1.getEditId(), a2.getEditId()};

        CutToStashCommand cutCmd = new CutToStashCommand(
                clipboard,
                ClipboardManager.CLIPBOARD_STASH_NAME,
                cutIds
        );
        CommandResult cutExecute = cutCmd.execute(treeA);
        printCommandResult("CUT[2] execute", cutExecute);
        printEditorState(editorA, "After CUT[2] on editorA");
        printSubtree(editorA, "Subtree A/root after cut[2]", rootA);

        softly.assertEquals(rootA.getChildCount(), 1,
                "After cut of two nodes, only one child should remain in tree A");
        softly.assertNull(treeA.findNodeById(a1.getEditId()), "a1 must be removed from tree A");
        softly.assertNull(treeA.findNodeById(a2.getEditId()), "a2 must be removed from tree A");

        PasteFromStashCommand pasteCmd = new PasteFromStashCommand(
                clipboard,
                ClipboardManager.CLIPBOARD_STASH_NAME,
                rootB.getEditId(),
                -1
        );
        CommandResult pasteExecute = pasteCmd.execute(treeB);
        printCommandResult("PASTE(cut)[2] execute", pasteExecute);
        printEditorState(editorB, "After PASTE(cut)[2] on editorB");
        printSubtree(editorB, "Subtree B/root after paste(cut)[2]", rootB);

        softly.assertEquals(rootB.getChildCount(), 4,
                "Tree B should contain b1, b2 and two pasted cut nodes");

        long[] pastedIds = pasteCmd.getPastedNodeIds();
        EditNodeAbstract pasted1 = treeB.findNodeById(pastedIds[0]);
        EditNodeAbstract pasted2 = treeB.findNodeById(pastedIds[1]);

        softly.assertNotNull(pasted1, "First pasted cut node must exist");
        softly.assertNotNull(pasted2, "Second pasted cut node must exist");

        // ------------------------------------------------------------
        // Zwischenänderungen: Move + Value/Text change
        // ------------------------------------------------------------
        MoveNodeCommand moveCmd = new MoveNodeCommand(pasted1, 0);
        CommandResult moveExecute = moveCmd.execute(treeB);
        printCommandResult("MOVE pasted1 -> index 0 execute", moveExecute);
        printEditorState(editorB, "After MOVE in cut scenario");
        printSubtree(editorB, "Subtree B/root after move in cut scenario", rootB);

        pasted2.setName("a2_changed_after_cut_paste");
        System.out.println("VALUE CHANGE: pasted2 renamed to " + pasted2.getName());
        printEditorState(editorB, "After VALUE CHANGE in cut scenario");

        // ------------------------------------------------------------
        // Undo paste
        // ------------------------------------------------------------
        CommandResult pasteUndo = pasteCmd.undo(treeB);
        printCommandResult("PASTE(cut)[2] undo", pasteUndo);
        printEditorState(editorB, "After undo PASTE(cut)[2]");
        printSubtree(editorB, "Subtree B/root after undo paste(cut)[2]", rootB);

        softly.assertEquals(rootB.getChildCount(), 2,
                "Undo paste should remove both pasted cut nodes");
        softly.assertNull(treeB.findNodeById(pastedIds[0]),
                "First pasted cut node must be gone after undo");
        softly.assertNull(treeB.findNodeById(pastedIds[1]),
                "Second pasted cut node must be gone after undo");

        // ------------------------------------------------------------
        // Redo paste
        // ------------------------------------------------------------
        CommandResult pasteRedo = pasteCmd.execute(treeB);
        printCommandResult("PASTE(cut)[2] redo", pasteRedo);
        printEditorState(editorB, "After redo PASTE(cut)[2]");
        printSubtree(editorB, "Subtree B/root after redo paste(cut)[2]", rootB);

        softly.assertEquals(rootB.getChildCount(), 4,
                "Redo paste should restore both cut nodes into tree B");

        long[] redoIds = pasteCmd.getPastedNodeIds();
        softly.assertNotNull(treeB.findNodeById(redoIds[0]),
                "Redo pasted cut node 1 must exist");
        softly.assertNotNull(treeB.findNodeById(redoIds[1]),
                "Redo pasted cut node 2 must exist");

        // Optional zusätzlich: Undo des Cuts im Source-Tree
        CommandResult cutUndo = cutCmd.undo(treeA);
        printCommandResult("CUT[2] undo", cutUndo);
        printEditorState(editorA, "After undo CUT[2] on editorA");
        printSubtree(editorA, "Subtree A/root after undo cut[2]", rootA);

        softly.assertEquals(rootA.getChildCount(), 3,
                "Undo cut should restore the two cut nodes in tree A");

        softly.assertAll();

        printTestFooter();
    }

}
