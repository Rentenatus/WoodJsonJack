/*
 */
package de.jare.jsoncasted.editor;

import de.jare.jsoncasted.editor.command.AddNodeCommand;
import de.jare.jsoncasted.editor.command.DeleteNodeCommand;
import de.jare.jsoncasted.editor.command.EditCommand;
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
 * Each command always operates on exactly 3 nodes.
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

    /**
     * Test: Add 3 nodes using array-based AddNodeCommand.
     */
    @Test
    public void testAddThreeNodes() {
        System.out.println("===============================================");
        System.out.println("testAddThreeNodes");
        System.out.println("===============================================");

        TreeEditor editor = new TreeEditor();
        EditNode root = editor.getTree().getRoot();
        assertNotNull(root, "Root should not be null");

        // Create 3 nodes
        EditNodeObject node1 = new EditNodeObject("arrayNode1");
        EditNodeObject node2 = new EditNodeObject("arrayNode2");
        EditNodeObject node3 = new EditNodeObject("arrayNode3");

        // Create entries for all 3 nodes
        EditCommandEntry.MovementEntry[] entries = new EditCommandEntry.MovementEntry[] {
            new EditCommandEntry.MovementEntry(root.getEditId(), -1, node1),
            new EditCommandEntry.MovementEntry(root.getEditId(), -1, node2),
            new EditCommandEntry.MovementEntry(root.getEditId(), -1, node3)
        };

        // Execute array-based command
        editor.execute(new AddNodeCommand(entries));

        // Verify all 3 nodes were added
        assertEquals(root.getChildCount(), 3, "Root should have 3 children");
        assertEquals(root.getChildAt(0).getName(), "arrayNode1");
        assertEquals(root.getChildAt(1).getName(), "arrayNode2");
        assertEquals(root.getChildAt(2).getName(), "arrayNode3");

        // Undo and verify
        editor.undo();
        assertEquals(root.getChildCount(), 0, "After undo, root should have 0 children");

        // Redo and verify
        editor.redo();
        assertEquals(root.getChildCount(), 3, "After redo, root should have 3 children again");

        System.out.println("===============================================");
    }

    /**
     * Test: Delete 3 nodes using array-based DeleteNodeCommand.
     */
    @Test
    public void testDeleteThreeNodes() {
        System.out.println("===============================================");
        System.out.println("testDeleteThreeNodes");
        System.out.println("===============================================");

        TreeEditor editor = new TreeEditor();
        EditNode root = editor.getTree().getRoot();

        // Add 3 nodes first
        EditNodeObject node1 = new EditNodeObject("deleteNode1");
        EditNodeObject node2 = new EditNodeObject("deleteNode2");
        EditNodeObject node3 = new EditNodeObject("deleteNode3");

        editor.execute(new AddNodeCommand(root.getEditId(), node1));
        editor.execute(new AddNodeCommand(root.getEditId(), node2));
        editor.execute(new AddNodeCommand(root.getEditId(), node3));
        assertEquals(root.getChildCount(), 3);

        // Clear history
        editor.clearHistory();

        // Delete all 3 nodes using array constructor
        editor.execute(new DeleteNodeCommand(new EditNode[] { node1, node2, node3 }));

        assertEquals(root.getChildCount(), 0, "All 3 nodes should be deleted");

        // Undo - all 3 should be restored
        editor.undo();
        assertEquals(root.getChildCount(), 3, "After undo, all 3 nodes should be restored");

        System.out.println("===============================================");
    }

    /**
     * Test: Move 3 nodes to a new parent using array-based MoveNodeCommand.
     */
    @Test
    public void testMoveThreeNodes() {
        System.out.println("===============================================");
        System.out.println("testMoveThreeNodes");
        System.out.println("===============================================");

        TreeEditor editor = new TreeEditor();
        EditNode root = editor.getTree().getRoot();

        // Create source parent with 3 nodes
        EditNodeObject sourceParent = new EditNodeObject("source");
        editor.execute(new AddNodeCommand(root.getEditId(), sourceParent));

        EditNodeObject node1 = new EditNodeObject("moveNode1");
        EditNodeObject node2 = new EditNodeObject("moveNode2");
        EditNodeObject node3 = new EditNodeObject("moveNode3");

        editor.execute(new AddNodeCommand(sourceParent.getEditId(), node1));
        editor.execute(new AddNodeCommand(sourceParent.getEditId(), node2));
        editor.execute(new AddNodeCommand(sourceParent.getEditId(), node3));

        // Create target parent
        EditNodeObject targetParent = new EditNodeObject("target");
        editor.execute(new AddNodeCommand(root.getEditId(), targetParent));

        // Clear history
        editor.clearHistory();

        // Move all 3 nodes to target parent starting at index 0
        editor.execute(new MoveNodeCommand(
            new EditNode[] { node1, node2, node3 },
            targetParent.getEditId(),
            0
        ));

        // Verify source is empty and target has 3 nodes
        assertEquals(sourceParent.getChildCount(), 0, "Source should be empty");
        assertEquals(targetParent.getChildCount(), 3, "Target should have 3 nodes");
        assertEquals(targetParent.getChildAt(0).getName(), "moveNode1");
        assertEquals(targetParent.getChildAt(1).getName(), "moveNode2");
        assertEquals(targetParent.getChildAt(2).getName(), "moveNode3");

        // Undo
        editor.undo();
        assertEquals(sourceParent.getChildCount(), 3, "After undo, source should have 3 nodes");
        assertEquals(targetParent.getChildCount(), 0, "After undo, target should be empty");

        System.out.println("===============================================");
    }

    /**
     * Test: Move 3 nodes to a new parent at specific starting index.
     */
    @Test
    public void testMoveThreeNodesAtIndex() {
        System.out.println("===============================================");
        System.out.println("testMoveThreeNodesAtIndex");
        System.out.println("===============================================");

        TreeEditor editor = new TreeEditor();
        EditNode root = editor.getTree().getRoot();

        // Create source parent with 3 nodes
        EditNodeObject sourceParent = new EditNodeObject("source");
        editor.execute(new AddNodeCommand(root.getEditId(), sourceParent));

        EditNodeObject node1 = new EditNodeObject("moveNode1");
        EditNodeObject node2 = new EditNodeObject("moveNode2");
        EditNodeObject node3 = new EditNodeObject("moveNode3");

        editor.execute(new AddNodeCommand(sourceParent.getEditId(), node1));
        editor.execute(new AddNodeCommand(sourceParent.getEditId(), node2));
        editor.execute(new AddNodeCommand(sourceParent.getEditId(), node3));

        // Create target parent with 2 existing nodes
        EditNodeObject existingNode1 = new EditNodeObject("existing1");
        EditNodeObject existingNode2 = new EditNodeObject("existing2");
        EditNodeObject targetParent = new EditNodeObject("target");
        editor.execute(new AddNodeCommand(root.getEditId(), targetParent));
        editor.execute(new AddNodeCommand(targetParent.getEditId(), existingNode1));
        editor.execute(new AddNodeCommand(targetParent.getEditId(), existingNode2));

        // Clear history
        editor.clearHistory();

        // Move 3 nodes to target at index 1 (between existing nodes)
        editor.execute(new MoveNodeCommand(
            new EditNode[] { node1, node2, node3 },
            targetParent.getEditId(),
            1
        ));

        // Verify order: existing1, moveNode1, moveNode2, moveNode3, existing2
        assertEquals(targetParent.getChildCount(), 5, "Target should have 5 nodes");
        assertEquals(targetParent.getChildAt(0).getName(), "existing1");
        assertEquals(targetParent.getChildAt(1).getName(), "moveNode1");
        assertEquals(targetParent.getChildAt(2).getName(), "moveNode2");
        assertEquals(targetParent.getChildAt(3).getName(), "moveNode3");
        assertEquals(targetParent.getChildAt(4).getName(), "existing2");

        System.out.println("===============================================");
    }

    /**
     * Test: Set values for 3 nodes using array-based SetValueCommand.
     */
    @Test
    public void testSetValueThreeNodes() {
        System.out.println("===============================================");
        System.out.println("testSetValueThreeNodes");
        System.out.println("===============================================");

        TreeEditor editor = new TreeEditor();
        EditNode root = editor.getTree().getRoot();

        // Create 3 nodes with initial values
        EditNodeObject node1 = new EditNodeObject("valueNode1");
        EditNodeObject node2 = new EditNodeObject("valueNode2");
        EditNodeObject node3 = new EditNodeObject("valueNode3");

        editor.execute(new AddNodeCommand(root.getEditId(), node1));
        editor.execute(new AddNodeCommand(root.getEditId(), node2));
        editor.execute(new AddNodeCommand(root.getEditId(), node3));

        // Clear history
        editor.clearHistory();

        // Set new values for all 3 nodes
        String[] newValues = { "newValue1", "newValue2", "newValue3" };
        editor.execute(new SetValueCommand(
            new EditNode[] { node1, node2, node3 },
            newValues
        ));

        // Verify new values
        assertEquals(node1.getEditText(), "newValue1");
        assertEquals(node2.getEditText(), "newValue2");
        assertEquals(node3.getEditText(), "newValue3");

        // Undo
        editor.undo();
        assertNull(node1.getEditText(), "After undo, node1 should have null text");
        assertNull(node2.getEditText(), "After undo, node2 should have null text");
        assertNull(node3.getEditText(), "After undo, node3 should have null text");

        // Redo
        editor.redo();
        assertEquals(node1.getEditText(), "newValue1");
        assertEquals(node2.getEditText(), "newValue2");
        assertEquals(node3.getEditText(), "newValue3");

        System.out.println("===============================================");
    }

    /**
     * Test: Complex sequence with array commands and undo/redo.
     */
    @Test
    public void testArrayCommandsUndoRedo() {
        System.out.println("===============================================");
        System.out.println("testArrayCommandsUndoRedo");
        System.out.println("===============================================");

        TreeEditor editor = new TreeEditor();
        EditNode root = editor.getTree().getRoot();

        // Create 3 nodes
        EditNodeObject n1 = new EditNodeObject("test1");
        EditNodeObject n2 = new EditNodeObject("test2");
        EditNodeObject n3 = new EditNodeObject("test3");

        // Add all 3 in one command
        editor.execute(new AddNodeCommand(
            new EditCommandEntry.MovementEntry[] {
                new EditCommandEntry.MovementEntry(root.getEditId(), -1, n1),
                new EditCommandEntry.MovementEntry(root.getEditId(), -1, n2),
                new EditCommandEntry.MovementEntry(root.getEditId(), -1, n3)
            }
        ));
        assertEquals(root.getChildCount(), 3);

        // Set values for all 3 in one command
        editor.execute(new SetValueCommand(
            new EditNode[] { n1, n2, n3 },
            new String[] { "val1", "val2", "val3" }
        ));
        assertEquals(n1.getEditText(), "val1");

        // Delete all 3 in one command
        editor.execute(new DeleteNodeCommand(new EditNode[] { n1, n2, n3 }));
        assertEquals(root.getChildCount(), 0);

        // Undo delete - nodes should be back with their values
        editor.undo();
        assertEquals(root.getChildCount(), 3);
        assertEquals(n1.getEditText(), "val1");

        // Undo set values
        editor.undo();
        assertNull(n1.getEditText());

        // Undo add
        editor.undo();
        assertEquals(root.getChildCount(), 0);

        // Redo all
        editor.redo();
        assertEquals(root.getChildCount(), 3);
        editor.redo();
        assertEquals(n1.getEditText(), "val1");
        editor.redo();
        assertEquals(root.getChildCount(), 0);

        System.out.println("===============================================");
    }

}
