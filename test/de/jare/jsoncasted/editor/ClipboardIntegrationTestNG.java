package de.jare.jsoncasted.editor;

import de.jare.jsoncasted.editor.clipboard.ClipboardManager;
import de.jare.jsoncasted.editor.clipboard.ClipboardStash;
import de.jare.jsoncasted.editor.clipboard.CopyToStashCommand;
import de.jare.jsoncasted.editor.clipboard.CutToStashCommand;
import de.jare.jsoncasted.editor.clipboard.PasteFromStashCommand;
import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ClipboardIntegrationTestNG {

    public ClipboardIntegrationTestNG() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        System.out.println("===============================================");
        System.out.println("## Start ClipboardIntegrationTestNG.");
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        System.out.println("## End ClipboardIntegrationTestNG.");
        System.out.println("===============================================");
    }
    private ClipboardManager clipboard;

    private EditTree treeA;
    private EditTree treeB;

    private TreeEditor editorA;
    private TreeEditor editorB;

    private EditNodeAbstract rootA;
    private EditNodeAbstract rootB;

    private EditNode childA1;
    private EditNode childA2;
    private EditNode childB1;

    @BeforeMethod
    public void setUp() {
        clipboard = new ClipboardManager();
        editorA = new TreeEditor();
        editorB = new TreeEditor();

        treeA = editorA.getTree();
        rootA = treeA.getRoot();
        treeB = editorB.getTree();
        rootB = treeB.getRoot();

        childA1 = treeA.addNewChild(rootA, "a1");
        childA2 = treeA.addNewChild(rootA, "a2");
        childB1 = treeB.addNewChild(rootB, "b1");
    }

    @Test
    public void testCopyCutPasteAcrossTwoTreesWithUndoRedo() {
        printTestHeader("testCopyCutPasteAcrossTwoTreesWithUndoRedo");

        printEditorState(editorA, "Initial editorA");
        printEditorState(editorB, "Initial editorB");
        printSubtree(editorA, "Initial subtree A/root", rootA);
        printSubtree(editorB, "Initial subtree B/root", rootB);

        long a1Id = childA1.getEditId();
        long a2Id = childA2.getEditId();

        // ============================================================
        // 1) COPY a1 from treeA into stash, then PASTE into treeB
        // ============================================================
        CopyToStashCommand copyCmd = new CopyToStashCommand(
                clipboard,
                ClipboardManager.CLIPBOARD_STASH_NAME,
                new long[]{a1Id}
        );
        CommandResult copyResult = copyCmd.execute(treeA);
        ClipboardStash stash = clipboard.getStash(ClipboardManager.CLIPBOARD_STASH_NAME);
        System.out.println("stash = " + stash);
        System.out.println("stash nodeCount = " + stash.getNodeCount());
        System.out.println("stash nodes = " + java.util.Arrays.toString(stash.getNodes()));
        printCommandResult("COPY execute", copyResult);
        printEditorState(editorA, "After COPY on editorA");

        PasteFromStashCommand pasteCopyCmd = new PasteFromStashCommand(
                clipboard,
                ClipboardManager.CLIPBOARD_STASH_NAME,
                rootB.getEditId(),
                -1
        );
        CommandResult pasteCopyExecuteResult = pasteCopyCmd.execute(treeB);
        printCommandResult("PASTE(copy) execute", pasteCopyExecuteResult);
        printEditorState(editorB, "After PASTE(copy) on editorB");
        printSubtree(editorB, "Subtree B/root after paste(copy)", rootB);

        assertEquals(rootB.getChildCount(), 2,
                "Tree B should contain original child plus copied node");

        long copiedIntoBId = pasteCopyCmd.getPastedNodeIds()[0];
        EditNode copiedIntoB = treeB.findNodeById(copiedIntoBId);
        assertNotNull(copiedIntoB, "Copied node must exist in tree B");

        CommandResult pasteCopyUndoResult = pasteCopyCmd.undo(treeB);
        printCommandResult("PASTE(copy) undo", pasteCopyUndoResult);
        printEditorState(editorB, "After undo PASTE(copy) on editorB");
        assertEquals(rootB.getChildCount(), 1,
                "Undo of paste(copy) should remove copied node");

        CommandResult pasteCopyRedoResult = pasteCopyCmd.execute(treeB);
        printCommandResult("PASTE(copy) redo", pasteCopyRedoResult);
        printEditorState(editorB, "After redo PASTE(copy) on editorB");
        assertEquals(rootB.getChildCount(), 2,
                "Redo of paste(copy) should add copied node again");

        // ============================================================
        // 2) CUT a2 from treeA
        // ============================================================
        CutToStashCommand cutCmd = new CutToStashCommand(
                clipboard,
                ClipboardManager.CLIPBOARD_STASH_NAME,
                new long[]{a2Id}
        );
        CommandResult cutExecuteResult = cutCmd.execute(treeA);
        printCommandResult("CUT execute", cutExecuteResult);
        printEditorState(editorA, "After CUT on editorA");
        printSubtree(editorA, "Subtree A/root after cut", rootA);

        assertEquals(rootA.getChildCount(), 1,
                "After cut, tree A should have only one child");
        assertNull(treeA.findNodeById(a2Id),
                "Cut node should be removed from tree A");

        CommandResult cutUndoResult = cutCmd.undo(treeA);
        printCommandResult("CUT undo", cutUndoResult);
        printEditorState(editorA, "After undo CUT on editorA");
        printSubtree(editorA, "Subtree A/root after undo cut", rootA);

        assertEquals(rootA.getChildCount(), 2,
                "Undo of cut should restore the node");
        assertNotNull(treeA.findNodeById(a2Id),
                "Undo of cut should restore node in tree A");

        CommandResult cutRedoResult = cutCmd.execute(treeA);
        printCommandResult("CUT redo", cutRedoResult);
        printEditorState(editorA, "After redo CUT on editorA");
        printSubtree(editorA, "Subtree A/root after redo cut", rootA);

        assertEquals(rootA.getChildCount(), 1,
                "Redo of cut should remove the node again");
        assertNull(treeA.findNodeById(a2Id),
                "Redo of cut should remove node from tree A again");

        // ============================================================
        // 3) PASTE cut content into treeB
        // ============================================================
        PasteFromStashCommand pasteCutCmd = new PasteFromStashCommand(
                clipboard,
                ClipboardManager.CLIPBOARD_STASH_NAME,
                rootB.getEditId(),
                -1
        );
        CommandResult pasteCutExecuteResult = pasteCutCmd.execute(treeB);
        printCommandResult("PASTE(cut) execute", pasteCutExecuteResult);
        printEditorState(editorB, "After PASTE(cut) on editorB");
        printSubtree(editorB, "Subtree B/root after paste(cut)", rootB);

        assertEquals(rootB.getChildCount(), 3,
                "Tree B should contain original child, copied node and pasted cut node");

        long pastedCutId = pasteCutCmd.getPastedNodeIds()[0];
        EditNode pastedCutNode = treeB.findNodeById(pastedCutId);
        assertNotNull(pastedCutNode,
                "Pasted cut node must exist in tree B");

        CommandResult pasteCutUndoResult = pasteCutCmd.undo(treeB);
        printCommandResult("PASTE(cut) undo", pasteCutUndoResult);
        printEditorState(editorB, "After undo PASTE(cut) on editorB");
        printSubtree(editorB, "Subtree B/root after undo paste(cut)", rootB);

        assertEquals(rootB.getChildCount(), 2,
                "Undo of paste(cut) should remove the inserted node");

        CommandResult pasteCutRedoResult = pasteCutCmd.execute(treeB);
        printCommandResult("PASTE(cut) redo", pasteCutRedoResult);
        printEditorState(editorB, "After redo PASTE(cut) on editorB");
        printSubtree(editorB, "Subtree B/root after redo paste(cut)", rootB);

        assertEquals(rootB.getChildCount(), 3,
                "Redo of paste(cut) should insert the node again");

        printTestFooter();
    }

    private static void printTestHeader(String testName) {
        System.out.println("===============================================");
        System.out.println(testName);
        System.out.println("===============================================");
    }

    private static void printTestFooter() {
        System.out.println("===============================================");
    }

    private static void printCommandResult(String label, CommandResult result) {
        System.out.println(label + ": " + result);
    }

    private static void printEditorState(TreeEditor editor, String label) {
        System.out.println(label + ": " + editor.toDebugString());
        System.out.println(editor.toHistoryString());
    }

    private static void printSubtree(TreeEditor editor, String label, EditNode node) {
        System.out.println(label + ":");
        System.out.println(editor.toTreeString(node));
    }
}
