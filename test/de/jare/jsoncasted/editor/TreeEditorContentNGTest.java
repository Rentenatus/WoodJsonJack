/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import de.jare.jsoncasted.editor.command.AddNodeCommand;
import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.command.MoveNodeCommand;
import de.jare.jsoncasted.editor.command.RenameNodeCommand;
import de.jare.jsoncasted.editor.command.SetValueCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditNodeObject;

/**
 * Tests for content-oriented command operations such as rename and set value.
 */
public class TreeEditorContentNGTest {

    public TreeEditorContentNGTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        System.out.println("===============================================");
        System.out.println("## Start TreeEditorContentNGTest.");
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        System.out.println("## End TreeEditorContentNGTest.");
        System.out.println("===============================================");
    }

    /**
     * Test: Rename 3 nodes using array-based RenameNodeCommand.
     */
    @Test
    public void testRenameThreeNodes() {
        printTestHeader("testRenameThreeNodes");

        TreeEditor editor = new TreeEditor();
        EditNode root = editor.getTree().getRoot();

        EditNodeObject node1 = new EditNodeObject("oldName1");
        EditNodeObject node2 = new EditNodeObject("oldName2");
        EditNodeObject node3 = new EditNodeObject("oldName3");

        editor.execute(new AddNodeCommand(root.getEditId(), node1));
        editor.execute(new AddNodeCommand(root.getEditId(), node2));
        editor.execute(new AddNodeCommand(root.getEditId(), node3));

        EditNode treeNode1 = root.getChildAt(0);
        EditNode treeNode2 = root.getChildAt(1);
        EditNode treeNode3 = root.getChildAt(2);

        editor.clearHistory();
        printEditorState(editor, "before rename");

        CommandResult renameResult = editor.execute(new RenameNodeCommand(
                new EditNode[]{treeNode1, treeNode2, treeNode3},
                new String[]{"newName1", "newName2", "newName3"}
        ));
        printCommandResult("renameResult", renameResult);

        assertEquals(treeNode1.getName(), "newName1");
        assertEquals(treeNode2.getName(), "newName2");
        assertEquals(treeNode3.getName(), "newName3");

        printSubtree(editor, "tree after rename", root);

        CommandResult undoResult = editor.undo();
        printCommandResult("undoResult", undoResult);

        assertEquals(treeNode1.getName(), "oldName1");
        assertEquals(treeNode2.getName(), "oldName2");
        assertEquals(treeNode3.getName(), "oldName3");

        CommandResult redoResult = editor.redo();
        printCommandResult("redoResult", redoResult);

        assertEquals(treeNode1.getName(), "newName1");
        assertEquals(treeNode2.getName(), "newName2");
        assertEquals(treeNode3.getName(), "newName3");

        printSubtree(editor, "tree after redo", root);
        printTestFooter();
    }

    /**
     * Test: Set values for 3 nodes using array-based SetValueCommand.
     */
    @Test
    public void testSetValueThreeNodes() {
        printTestHeader("testSetValueThreeNodes");

        TreeEditor editor = new TreeEditor();
        EditNode root = editor.getTree().getRoot();

        EditNodeObject node1 = new EditNodeObject("valueNode1");
        EditNodeObject node2 = new EditNodeObject("valueNode2");
        EditNodeObject node3 = new EditNodeObject("valueNode3");

        editor.execute(new AddNodeCommand(root.getEditId(), node1));
        editor.execute(new AddNodeCommand(root.getEditId(), node2));
        editor.execute(new AddNodeCommand(root.getEditId(), node3));

        EditNode treeNode1 = root.getChildAt(0);
        EditNode treeNode2 = root.getChildAt(1);
        EditNode treeNode3 = root.getChildAt(2);

        editor.clearHistory();
        printEditorState(editor, "before setValue");

        CommandResult setValueResult = editor.execute(new SetValueCommand(
                new EditNode[]{treeNode1, treeNode2, treeNode3},
                new String[]{"value1", "value2", "value3"}
        ));
        printCommandResult("setValueResult", setValueResult);

        assertEquals(treeNode1.getEditText(), "value1");
        assertEquals(treeNode2.getEditText(), "value2");
        assertEquals(treeNode3.getEditText(), "value3");

        printSubtree(editor, "tree after setValue", root);

        CommandResult undoResult = editor.undo();
        printCommandResult("undoResult", undoResult);

        assertNull(treeNode1.getEditText(), "After undo, node1 should have null text");
        assertNull(treeNode2.getEditText(), "After undo, node2 should have null text");
        assertNull(treeNode3.getEditText(), "After undo, node3 should have null text");

        CommandResult redoResult = editor.redo();
        printCommandResult("redoResult", redoResult);

        assertEquals(treeNode1.getEditText(), "value1");
        assertEquals(treeNode2.getEditText(), "value2");
        assertEquals(treeNode3.getEditText(), "value3");

        printSubtree(editor, "tree after redo", root);
        printTestFooter();
    }

    /**
     * Test: Combine rename and set value on the same nodes.
     */
    @Test
    public void testRenameAndSetValueCombined() {
        printTestHeader("testRenameAndSetValueCombined");

        TreeEditor editor = new TreeEditor();
        EditNode root = editor.getTree().getRoot();

        EditNodeObject node1 = new EditNodeObject("node1");
        EditNodeObject node2 = new EditNodeObject("node2");
        EditNodeObject node3 = new EditNodeObject("node3");

        editor.execute(new AddNodeCommand(root.getEditId(), node1));
        editor.execute(new AddNodeCommand(root.getEditId(), node2));
        editor.execute(new AddNodeCommand(root.getEditId(), node3));

        EditNode treeNode1 = root.getChildAt(0);
        EditNode treeNode2 = root.getChildAt(1);
        EditNode treeNode3 = root.getChildAt(2);

        editor.clearHistory();
        printEditorState(editor, "before combined content commands");

        CommandResult renameResult = editor.execute(new RenameNodeCommand(
                new EditNode[]{treeNode1, treeNode2, treeNode3},
                new String[]{"renamed1", "renamed2", "renamed3"}
        ));
        printCommandResult("renameResult", renameResult);

        CommandResult setValueResult = editor.execute(new SetValueCommand(
                new EditNode[]{treeNode1, treeNode2, treeNode3},
                new String[]{"text1", "text2", "text3"}
        ));
        printCommandResult("setValueResult", setValueResult);

        assertEquals(treeNode1.getName(), "renamed1");
        assertEquals(treeNode2.getName(), "renamed2");
        assertEquals(treeNode3.getName(), "renamed3");

        assertEquals(treeNode1.getEditText(), "text1");
        assertEquals(treeNode2.getEditText(), "text2");
        assertEquals(treeNode3.getEditText(), "text3");

        printSubtree(editor, "tree after rename + setValue", root);

        CommandResult undoSetValueResult = editor.undo();
        printCommandResult("undoSetValueResult", undoSetValueResult);

        assertNull(treeNode1.getEditText());
        assertNull(treeNode2.getEditText());
        assertNull(treeNode3.getEditText());

        assertEquals(treeNode1.getName(), "renamed1");
        assertEquals(treeNode2.getName(), "renamed2");
        assertEquals(treeNode3.getName(), "renamed3");

        CommandResult undoRenameResult = editor.undo();
        printCommandResult("undoRenameResult", undoRenameResult);

        assertEquals(treeNode1.getName(), "node1");
        assertEquals(treeNode2.getName(), "node2");
        assertEquals(treeNode3.getName(), "node3");

        CommandResult redoRenameResult = editor.redo();
        printCommandResult("redoRenameResult", redoRenameResult);

        CommandResult redoSetValueResult = editor.redo();
        printCommandResult("redoSetValueResult", redoSetValueResult);

        assertEquals(treeNode1.getName(), "renamed1");
        assertEquals(treeNode2.getName(), "renamed2");
        assertEquals(treeNode3.getName(), "renamed3");

        assertEquals(treeNode1.getEditText(), "text1");
        assertEquals(treeNode2.getEditText(), "text2");
        assertEquals(treeNode3.getEditText(), "text3");

        printSubtree(editor, "tree after redo chain", root);
        printTestFooter();
    }

    /**
     * Test: Undo to the beginning, skip redo of move, then redo rename and set
     * value.
     */
    @Test
    public void testSkipRedoMoveThenRedoContentCommands() {
        printTestHeader("testSkipRedoMoveThenRedoContentCommands");

        TreeEditor editor = new TreeEditor();
        EditNodeAbstract root = editor.getTree().getRoot();

        EditNodeObject sourceParentSeed = new EditNodeObject("source");
        EditNodeObject targetParentSeed = new EditNodeObject("target");

        editor.execute(new AddNodeCommand(root.getEditId(), sourceParentSeed));
        editor.execute(new AddNodeCommand(root.getEditId(), targetParentSeed));

        EditNodeAbstract sourceParent = root.getChildAt(0);
        EditNodeAbstract targetParent = root.getChildAt(1);

        editor.execute(new AddNodeCommand(sourceParent.getEditId(), new EditNodeObject("node1")));
        editor.execute(new AddNodeCommand(sourceParent.getEditId(), new EditNodeObject("node2")));
        editor.execute(new AddNodeCommand(sourceParent.getEditId(), new EditNodeObject("node3")));

        EditNodeAbstract node1 = sourceParent.getChildAt(0);
        EditNodeAbstract node2 = sourceParent.getChildAt(1);
        EditNodeAbstract node3 = sourceParent.getChildAt(2);

        editor.clearHistory();
        printEditorState(editor, "before move/rename/setValue");
        printSubtree(editor, "source before move", sourceParent);
        printSubtree(editor, "target before move", targetParent);

        CommandResult moveCmd = editor.execute(new MoveNodeCommand(
                new EditNodeAbstract[]{node1, node2, node3},
                targetParent,
                0
        ));
        assertNotNull(moveCmd);
        printCommandResult("moveResult", moveCmd);

        node1 = targetParent.getChildAt(0);
        node2 = targetParent.getChildAt(1);
        node3 = targetParent.getChildAt(2);

        CommandResult renameCmd = editor.execute(new RenameNodeCommand(
                new EditNode[]{node1, node2, node3},
                new String[]{"renamed1", "renamed2", "renamed3"}
        ));
        assertNotNull(renameCmd);
        printCommandResult("renameResult", renameCmd);

        CommandResult setValueCmd = editor.execute(new SetValueCommand(
                new EditNode[]{node1, node2, node3},
                new String[]{"value1", "value2", "value3"}
        ));
        assertNotNull(setValueCmd);
        printCommandResult("setValueResult", setValueCmd);

        assertEquals(sourceParent.getChildCount(), 0);
        assertEquals(targetParent.getChildCount(), 3);
        assertEquals(node1.getName(), "renamed1");
        assertEquals(node2.getName(), "renamed2");
        assertEquals(node3.getName(), "renamed3");
        assertEquals(node1.getEditText(), "value1");
        assertEquals(node2.getEditText(), "value2");
        assertEquals(node3.getEditText(), "value3");

        printSubtree(editor, "source after move/rename/setValue", sourceParent);
        printSubtree(editor, "target after move/rename/setValue", targetParent);

        CommandResult undoSetValue = editor.undo();
        printCommandResult("undoSetValue", undoSetValue);

        CommandResult undoRename = editor.undo();
        printCommandResult("undoRename", undoRename);

        CommandResult undoMove = editor.undo();
        printCommandResult("undoMove", undoMove);

        node1 = sourceParent.getChildAt(0);
        node2 = sourceParent.getChildAt(1);
        node3 = sourceParent.getChildAt(2);

        assertEquals(sourceParent.getChildCount(), 3, "After undo to start, nodes must be back in source");
        assertEquals(targetParent.getChildCount(), 0, "After undo to start, target must be empty");

        assertEquals(node1.getName(), "node1");
        assertEquals(node2.getName(), "node2");
        assertEquals(node3.getName(), "node3");

        assertNull(node1.getEditText());
        assertNull(node2.getEditText());
        assertNull(node3.getEditText());

        printSubtree(editor, "source after undo chain", sourceParent);
        printSubtree(editor, "target after undo chain", targetParent);
        printEditorState(editor, "after undo chain");

        EditCommand skipped = editor.skipRedo();
        assertNotNull(skipped, "Skipped command should not be null");
        assertTrue(skipped instanceof MoveNodeCommand, "First redo entry should be the move command");
        System.out.println("skippedRedo: " + skipped);

        assertEquals(sourceParent.getChildCount(), 3, "Skip redo must not execute the move");
        assertEquals(targetParent.getChildCount(), 0, "Skip redo must leave target unchanged");

        CommandResult redoneRename = editor.redo();
        assertNotNull(redoneRename);
        assertTrue(redoneRename.getTrigger() instanceof RenameNodeCommand);
        printCommandResult("redoneRename", redoneRename);

        assertEquals(node1.getName(), "renamed1");
        assertEquals(node2.getName(), "renamed2");
        assertEquals(node3.getName(), "renamed3");

        assertEquals(sourceParent.getChildCount(), 3, "Rename redo should not move nodes");
        assertEquals(targetParent.getChildCount(), 0);

        CommandResult redoneSetValue = editor.redo();
        assertNotNull(redoneSetValue);
        assertTrue(redoneSetValue.getTrigger() instanceof SetValueCommand);
        printCommandResult("redoneSetValue", redoneSetValue);

        assertEquals(node1.getEditText(), "value1");
        assertEquals(node2.getEditText(), "value2");
        assertEquals(node3.getEditText(), "value3");

        assertEquals(sourceParent.getChildCount(), 3, "SetValue redo should not move nodes");
        assertEquals(targetParent.getChildCount(), 0);

        printSubtree(editor, "source after redo rename/setValue", sourceParent);
        printSubtree(editor, "target after redo rename/setValue", targetParent);
        printEditorState(editor, "final editor state");

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
