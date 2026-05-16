/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor;

import de.jare.jsoncasted.editor.command.AddNodeCommand;
import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.DeleteNodeCommand;
import de.jare.jsoncasted.editor.command.EditCommandEntry;
import de.jare.jsoncasted.editor.command.MoveNodeCommand;
import de.jare.jsoncasted.editor.command.SetValueCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeObject;

import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests for array-based command operations.
 *
 * <p>
 * The tests resolve affected nodes from the tree after structural changes
 * instead of relying on original Java object references.</p>
 */
public class TreeEditorArraysNGTest {

    public TreeEditorArraysNGTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        System.out.println("===============================================");
        System.out.println("## Start TreeEditorArraysNGTest.");
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        System.out.println("## End TreeEditorArraysNGTest.");
        System.out.println("===============================================");
    }

    @Test
    public void testAddThreeNodes() {
        printTestHeader("testAddThreeNodes");

        TreeEditor editor = new TreeEditor();
        EditNode root = editor.getTree().getRoot();
        assertNotNull(root, "Root should not be null");
        assertEquals(root.getChildCount(), 0);

        EditNodeObject node1 = new EditNodeObject("arrayNode1");
        EditNodeObject node2 = new EditNodeObject("arrayNode2");
        EditNodeObject node3 = new EditNodeObject("arrayNode3");

        EditCommandEntry.MovementEntry[] entries = new EditCommandEntry.MovementEntry[]{
            new EditCommandEntry.MovementEntry(node1.getEditId(), root.getEditId(), -1, node1),
            new EditCommandEntry.MovementEntry(node2.getEditId(), root.getEditId(), -1, node2),
            new EditCommandEntry.MovementEntry(node3.getEditId(), root.getEditId(), -1, node3)
        };

        CommandResult result = editor.execute(new AddNodeCommand(entries));
        printCommandResult("addResult", result);
        printSubtree(editor, "root after add", root);

        assertNotNull(result, "AddNodeCommand result should not be null");
        assertEquals(result.getAddedNodes().length, 3, "Three nodes should be reported as added");

        assertEquals(root.getChildCount(), 3, "Root should have 3 children");
        assertNodeNames(root, "arrayNode1", "arrayNode2", "arrayNode3");

        CommandResult undoResult = editor.undo();
        printCommandResult("undoResult", undoResult);
        printSubtree(editor, "root after undo", root);
        assertEquals(root.getChildCount(), 0, "After undo, root should have 0 children");

        CommandResult redoResult = editor.redo();
        printCommandResult("redoResult", redoResult);
        printSubtree(editor, "root after redo", root);
        assertEquals(root.getChildCount(), 3, "After redo, root should have 3 children again");
        assertNodeNames(root, "arrayNode1", "arrayNode2", "arrayNode3");

        printTestFooter();
    }

    @Test
    public void testDeleteThreeNodes() {
        printTestHeader("testDeleteThreeNodes");

        TreeEditor editor = new TreeEditor();
        EditNode root = editor.getTree().getRoot();

        editor.execute(new AddNodeCommand(root.getEditId(), new EditNodeObject("deleteNode1")));
        editor.execute(new AddNodeCommand(root.getEditId(), new EditNodeObject("deleteNode2")));
        editor.execute(new AddNodeCommand(root.getEditId(), new EditNodeObject("deleteNode3")));
        assertEquals(root.getChildCount(), 3);

        EditNode n1 = root.getChildAt(0);
        EditNode n2 = root.getChildAt(1);
        EditNode n3 = root.getChildAt(2);

        editor.clearHistory();
        printSubtree(editor, "root before delete", root);

        CommandResult deleteResult = editor.execute(new DeleteNodeCommand(new EditNode[]{n1, n2, n3}));
        printCommandResult("deleteResult", deleteResult);
        printSubtree(editor, "root after delete", root);

        assertNotNull(deleteResult, "Delete result should not be null");
        assertEquals(deleteResult.getRemovedNodes().length, 3, "Three nodes should be reported as removed");

        assertEquals(root.getChildCount(), 0, "All 3 nodes should be deleted");

        CommandResult undoResult = editor.undo();
        printCommandResult("undoResult", undoResult);
        printSubtree(editor, "root after undo delete", root);

        assertNotNull(undoResult, "Undo delete result should not be null");
        assertEquals(root.getChildCount(), 3, "After undo, all 3 nodes should be restored");
        assertNodeNames(root, "deleteNode1", "deleteNode2", "deleteNode3");

        printTestFooter();
    }

    @Test
    public void testMoveThreeNodes() {
        printTestHeader("testMoveThreeNodes");

        TreeEditor editor = new TreeEditor();
        EditNode root = editor.getTree().getRoot();

        editor.execute(new AddNodeCommand(root.getEditId(), new EditNodeObject("source")));
        EditNode sourceParent = root.getChildAt(0);

        editor.execute(new AddNodeCommand(sourceParent.getEditId(), new EditNodeObject("moveNode1")));
        editor.execute(new AddNodeCommand(sourceParent.getEditId(), new EditNodeObject("moveNode2")));
        editor.execute(new AddNodeCommand(sourceParent.getEditId(), new EditNodeObject("moveNode3")));

        editor.execute(new AddNodeCommand(root.getEditId(), new EditNodeObject("target")));
        EditNode targetParent = root.getChildAt(1);

        EditNode node1 = sourceParent.getChildAt(0);
        EditNode node2 = sourceParent.getChildAt(1);
        EditNode node3 = sourceParent.getChildAt(2);

        editor.clearHistory();

        printEditorState(editor, "before move");
        printSubtree(editor, "source before move", sourceParent);
        printSubtree(editor, "target before move", targetParent);

        CommandResult moveResult = editor.execute(new MoveNodeCommand(
                new EditNode[]{node1, node2, node3},
                targetParent,
                0
        ));
        printCommandResult("moveResult", moveResult);
        printSubtree(editor, "source after move", sourceParent);
        printSubtree(editor, "target after move", targetParent);

        assertNotNull(moveResult, "Move result should not be null");
        assertEquals(moveResult.getAffectedNodes().length, 1, "Three nodes should be reported as moved");

        assertEquals(sourceParent.getChildCount(), 0, "Source should be empty");
        assertEquals(targetParent.getChildCount(), 3, "Target should have 3 nodes");
        assertNodeNames(targetParent, "moveNode1", "moveNode2", "moveNode3");

        CommandResult undoResult = editor.undo();
        printCommandResult("undoResult", undoResult);
        printSubtree(editor, "source after undo", sourceParent);
        printSubtree(editor, "target after undo", targetParent);

        assertEquals(sourceParent.getChildCount(), 3, "After undo, source should have 3 nodes");
        assertEquals(targetParent.getChildCount(), 0, "After undo, target should be empty");
        assertNodeNames(sourceParent, "moveNode1", "moveNode2", "moveNode3");

        printTestFooter();
    }

    @Test
    public void testMoveThreeNodesAtIndex() {
        printTestHeader("testMoveThreeNodesAtIndex");

        TreeEditor editor = new TreeEditor();
        EditNode root = editor.getTree().getRoot();

        editor.execute(new AddNodeCommand(root.getEditId(), new EditNodeObject("source")));
        EditNode sourceParent = root.getChildAt(0);

        editor.execute(new AddNodeCommand(sourceParent.getEditId(), new EditNodeObject("moveNode1")));
        editor.execute(new AddNodeCommand(sourceParent.getEditId(), new EditNodeObject("moveNode2")));
        editor.execute(new AddNodeCommand(sourceParent.getEditId(), new EditNodeObject("moveNode3")));

        editor.execute(new AddNodeCommand(root.getEditId(), new EditNodeObject("target")));
        EditNode targetParent = root.getChildAt(1);

        editor.execute(new AddNodeCommand(targetParent.getEditId(), new EditNodeObject("existing1")));
        editor.execute(new AddNodeCommand(targetParent.getEditId(), new EditNodeObject("existing2")));

        EditNode node1 = sourceParent.getChildAt(0);
        EditNode node2 = sourceParent.getChildAt(1);
        EditNode node3 = sourceParent.getChildAt(2);

        editor.clearHistory();

        printSubtree(editor, "source before indexed move", sourceParent);
        printSubtree(editor, "target before indexed move", targetParent);

        CommandResult moveResult = editor.execute(new MoveNodeCommand(
                new EditNode[]{node1, node2, node3},
                targetParent,
                1
        ));
        printCommandResult("moveResult", moveResult);
        printSubtree(editor, "source after indexed move", sourceParent);
        printSubtree(editor, "target after indexed move", targetParent);

        assertEquals(targetParent.getChildCount(), 5, "Target should have 5 nodes");
        assertNodeNames(targetParent, "existing1", "moveNode1", "moveNode2", "moveNode3", "existing2");

        printTestFooter();
    }

    @Test
    public void testSetValueThreeNodes() {
        printTestHeader("testSetValueThreeNodes");

        TreeEditor editor = new TreeEditor();
        EditNode root = editor.getTree().getRoot();

        editor.execute(new AddNodeCommand(root.getEditId(), new EditNodeObject("valueNode1")));
        editor.execute(new AddNodeCommand(root.getEditId(), new EditNodeObject("valueNode2")));
        editor.execute(new AddNodeCommand(root.getEditId(), new EditNodeObject("valueNode3")));

        EditNode node1 = root.getChildAt(0);
        EditNode node2 = root.getChildAt(1);
        EditNode node3 = root.getChildAt(2);

        editor.clearHistory();

        String[] newValues = {"newValue1", "newValue2", "newValue3"};
        CommandResult setResult = editor.execute(new SetValueCommand(
                new EditNode[]{node1, node2, node3},
                newValues
        ));
        printCommandResult("setResult", setResult);
        printSubtree(editor, "root after set value", root);

        assertNotNull(setResult, "SetValue result should not be null");
        assertEquals(setResult.getUpdatedNodes().length, 3, "Three nodes should be reported as updated");

        assertEquals(root.getChildAt(0).getEditText(), "newValue1");
        assertEquals(root.getChildAt(1).getEditText(), "newValue2");
        assertEquals(root.getChildAt(2).getEditText(), "newValue3");

        CommandResult undoResult = editor.undo();
        printCommandResult("undoResult", undoResult);
        printSubtree(editor, "root after undo set value", root);

        assertNull(root.getChildAt(0).getEditText(), "After undo, node1 should have null text");
        assertNull(root.getChildAt(1).getEditText(), "After undo, node2 should have null text");
        assertNull(root.getChildAt(2).getEditText(), "After undo, node3 should have null text");

        CommandResult redoResult = editor.redo();
        printCommandResult("redoResult", redoResult);
        printSubtree(editor, "root after redo set value", root);

        assertEquals(root.getChildAt(0).getEditText(), "newValue1");
        assertEquals(root.getChildAt(1).getEditText(), "newValue2");
        assertEquals(root.getChildAt(2).getEditText(), "newValue3");

        printTestFooter();
    }

    @Test
    public void testArrayCommandsUndoRedo() {
        printTestHeader("testArrayCommandsUndoRedo");

        TreeEditor editor = new TreeEditor();
        EditNode root = editor.getTree().getRoot();

        EditNodeObject n1 = new EditNodeObject("test1");
        EditNodeObject n2 = new EditNodeObject("test2");
        EditNodeObject n3 = new EditNodeObject("test3");

        CommandResult addResult = editor.execute(new AddNodeCommand(
                new EditCommandEntry.MovementEntry[]{
                    new EditCommandEntry.MovementEntry(n1.getEditId(), root.getEditId(), -1, n1),
                    new EditCommandEntry.MovementEntry(n2.getEditId(), root.getEditId(), -1, n2),
                    new EditCommandEntry.MovementEntry(n3.getEditId(), root.getEditId(), -1, n3)
                }
        ));
        printCommandResult("addResult", addResult);
        printSubtree(editor, "root after add", root);

        assertEquals(root.getChildCount(), 3);
        assertNodeNames(root, "test1", "test2", "test3");

        EditNode node1 = root.getChildAt(0);
        EditNode node2 = root.getChildAt(1);
        EditNode node3 = root.getChildAt(2);

        CommandResult setResult = editor.execute(new SetValueCommand(
                new EditNode[]{node1, node2, node3},
                new String[]{"val1", "val2", "val3"}
        ));
        printCommandResult("setResult", setResult);
        printSubtree(editor, "root after set value", root);

        assertEquals(root.getChildAt(0).getEditText(), "val1");
        assertEquals(root.getChildAt(1).getEditText(), "val2");
        assertEquals(root.getChildAt(2).getEditText(), "val3");

        CommandResult deleteResult = editor.execute(new DeleteNodeCommand(new EditNode[]{
            root.getChildAt(0),
            root.getChildAt(1),
            root.getChildAt(2)
        }));
        printCommandResult("deleteResult", deleteResult);
        printSubtree(editor, "root after delete", root);

        assertEquals(root.getChildCount(), 0);

        CommandResult undoDelete = editor.undo();
        printCommandResult("undoDelete", undoDelete);
        printSubtree(editor, "root after undo delete", root);

        assertEquals(root.getChildCount(), 3);
        assertNodeNames(root, "test1", "test2", "test3");
        assertEquals(root.getChildAt(0).getEditText(), "val1");
        assertEquals(root.getChildAt(1).getEditText(), "val2");
        assertEquals(root.getChildAt(2).getEditText(), "val3");

        CommandResult undoSet = editor.undo();
        printCommandResult("undoSet", undoSet);
        printSubtree(editor, "root after undo set value", root);

        assertNull(root.getChildAt(0).getEditText());
        assertNull(root.getChildAt(1).getEditText());
        assertNull(root.getChildAt(2).getEditText());

        CommandResult undoAdd = editor.undo();
        printCommandResult("undoAdd", undoAdd);
        printSubtree(editor, "root after undo add", root);

        assertEquals(root.getChildCount(), 0);

        CommandResult redoAdd = editor.redo();
        printCommandResult("redoAdd", redoAdd);
        printSubtree(editor, "root after redo add", root);

        assertEquals(root.getChildCount(), 3);
        assertNodeNames(root, "test1", "test2", "test3");

        CommandResult redoSet = editor.redo();
        printCommandResult("redoSet", redoSet);
        printSubtree(editor, "root after redo set value", root);

        assertEquals(root.getChildAt(0).getEditText(), "val1");
        assertEquals(root.getChildAt(1).getEditText(), "val2");
        assertEquals(root.getChildAt(2).getEditText(), "val3");

        CommandResult redoDelete = editor.redo();
        printCommandResult("redoDelete", redoDelete);
        printSubtree(editor, "root after redo delete", root);

        assertEquals(root.getChildCount(), 0);

        printTestFooter();
    }

    private static void assertNodeNames(EditNode parent, String... expectedNames) {
        assertEquals(parent.getChildCount(), expectedNames.length, "Unexpected child count");
        for (int i = 0; i < expectedNames.length; i++) {
            assertEquals(
                    parent.getChildAt(i).getName(),
                    expectedNames[i],
                    "Unexpected node name at index " + i
            );
        }
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
