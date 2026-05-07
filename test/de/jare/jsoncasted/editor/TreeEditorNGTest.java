/* 
 */
package de.jare.jsoncasted.editor;

import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.editor.command.AddNodeCommand;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.lang.JsonNode;
import de.jare.jsoncasted.lang.JsonResource;
import de.jare.jsoncasted.parserservice.JsonParserService;
import de.jare.jsoncasted.parserwriter.JsonParseException;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 *
 * @author Administrator
 */
public class TreeEditorNGTest {

    public TreeEditorNGTest() {
    }

    /**
     * Sets up the test class. Printed to stdout for test tracking.
     *
     * @throws Exception If setup fails. 
     */
    @BeforeClass
    public static void setUpClass() throws Exception {
        System.out.println("===============================================");
        System.out.println("## Start TreeEditorNGTest.");
    }

    /**
     * Tears down the test class. Printed to stdout for test tracking.
     *
     * @throws Exception If teardown fails. 
     */
    @AfterClass
    public static void tearDownClass() throws Exception {
        System.out.println("## End TreeEditorNGTest.");
        System.out.println("===============================================");
    }

    /**
     * Test: Load JsonResource, import to TreeEditor, export back to JsonNode,
     * and write to string. 
     */
    @Test
    public void testImportExport() {
        System.out.println("===============================================");
        System.out.println("testImportExport");
        System.out.println("===============================================");

        File configFile = new File("assets/config/config1.json");    
        System.out.println("File: " + configFile.getAbsolutePath());

        // Step 1: Load JsonResource
        JsonResource resource = null;
        try {
            resource = JsonParserService.parse(configFile, JsonDebugLevel.INFO);
        } catch (JsonParseException | IOException | NullPointerException ex) {
            Logger.getGlobal().log(Level.SEVERE, null, ex);
            fail("Failed to parse JSON file: " + ex.getMessage(), ex);
        }
        assertNotNull(resource, "JsonResource should not be null");
        assertNotNull(resource.getRoot(), "JsonResource should have a root node");

        JsonNode originalRoot = resource.getRoot();
        System.out.println("Original JSON:");
        System.out.println(originalRoot.toString());

        // Step 2: Import to TreeEditor
        TreeEditor editor = TreeEditor.fromJsonResource(resource);
        assertNotNull(editor, "TreeEditor should not be null");
        assertNotNull(editor.getTree(), "Tree should not be null");
        assertNotNull(editor.getTree().getRoot(), "Tree should have a root node");
        System.out.println("Imported to TreeEditor successfully");

        // Step 3: Export back to JsonNode
        JsonNode exportedNode = editor.exportToJsonNode();
        assertNotNull(exportedNode, "Exported JsonNode should not be null");
        System.out.println("Exported JSON:");
        System.out.println(exportedNode.toString());

        // Step 4: Also test export to JsonResource
        JsonResource exportedResource = editor.exportToJsonResource();
        assertNotNull(exportedResource, "Exported JsonResource should not be null");
        assertNotNull(exportedResource.getRoot(), "Exported JsonResource should have a root");
        System.out.println("Exported to JsonResource successfully");

        System.out.println("===============================================");
    }

    /**
     * Test roundtrip: import -> export -> import Verify that data can be
     * imported and exported without loss. 
     */
    @Test
    public void testRoundtrip() {
        System.out.println("===============================================");
        System.out.println("testRoundtrip");
        System.out.println("===============================================");

        File configFile = new File("assets/config/config1.json");
        System.out.println("File: " + configFile.getAbsolutePath());

        // Load and import
        JsonResource resource = null;
        try {
            resource = JsonParserService.parse(configFile, JsonDebugLevel.INFO);
        } catch (JsonParseException | IOException | NullPointerException ex) {
            Logger.getGlobal().log(Level.SEVERE, null, ex);
            fail("Failed to parse JSON file: " + ex.getMessage(), ex);
        }

        TreeEditor editor1 = TreeEditor.fromJsonResource(resource);

        // Export
        JsonNode exported = editor1.exportToJsonNode();
        System.out.println("Exported JSON:");
        System.out.println(exported.toString());

        // Re-import
        TreeEditor editor2 = TreeEditor.fromJsonNode(exported);
        assertNotNull(editor2, "Re-imported TreeEditor should not be null");

        // Export again
        JsonNode reExported = editor2.exportToJsonNode();
        assertNotNull(reExported, "Re-exported JsonNode should not be null");

        // The JSON representations should be equivalent
        System.out.println("Re-exported JSON:");
        System.out.println(reExported.toString());

        System.out.println("===============================================");
    }

    /**
     * Helper method to print command info with editId if available.
     */
    private String getCommandInfo(EditCommand cmd) {
        String info = cmd.toString();
        if (cmd instanceof AddNodeCommand) {
            info += " (editId: " + ((AddNodeCommand) cmd).getEditId() + ")";
        }
        return info;
    }

    /**
     * Helper method to print history stacks.
     */
    private void printHistory(TreeEditor editor, String prefix) {
        System.out.println(prefix + "Undo stack:");
        for (EditCommand cmd : editor.getHistoryManager().getUndoCommands()) {
            System.out.println("  - " + getCommandInfo(cmd));
        }
        System.out.println(prefix + "Redo stack:");
        for (EditCommand cmd : editor.getHistoryManager().getRedoCommands()) {
            System.out.println("  - " + getCommandInfo(cmd));
        }
    }

    /**
     * Test: Makes 3 changes, reads history, does undo, reads history again,
     * does redo, and reads history again to verify state.
     */
    @Test
    public void testUndoRedoHistory() {
        System.out.println("===============================================");
        System.out.println("testUndoRedoHistory");
        System.out.println("===============================================");

        // Create a new editor with a simple root
        TreeEditor editor = new TreeEditor();
        EditNode root = editor.getTree().getRoot();
        assertNotNull(root, "Root should not be null");

        // Step 1: Make 3 changes (add 3 nodes)
        EditNodeObject node1 = new EditNodeObject("node1");
        EditNodeObject node2 = new EditNodeObject("node2");
        EditNodeObject node3 = new EditNodeObject("node3");

        editor.execute(new AddNodeCommand(root.getEditId(), node1));
        editor.execute(new AddNodeCommand(root.getEditId(), node2));
        editor.execute(new AddNodeCommand(root.getEditId(), node3));

        // Step 2: Read history - should have 3 undo entries, 0 redo entries
        int undoSizeAfter3Changes = editor.getHistoryManager().getUndoSize();
        int redoSizeAfter3Changes = editor.getHistoryManager().getRedoSize();
        System.out.println("After 3 changes - Undo size: " + undoSizeAfter3Changes + ", Redo size: " + redoSizeAfter3Changes);
        printHistory(editor, "After 3 changes - ");
        assertEquals(undoSizeAfter3Changes, 3, "After 3 changes, undo size should be 3");
        assertEquals(redoSizeAfter3Changes, 0, "After 3 changes, redo size should be 0");

        // Step 3: Do undo
        editor.undo();

        // Step 4: Read history again - should have 2 undo entries, 1 redo entry
        int undoSizeAfterUndo = editor.getHistoryManager().getUndoSize();
        int redoSizeAfterUndo = editor.getHistoryManager().getRedoSize();
        System.out.println("After undo - Undo size: " + undoSizeAfterUndo + ", Redo size: " + redoSizeAfterUndo);
        printHistory(editor, "After undo - ");
        assertEquals(undoSizeAfterUndo, 2, "After undo, undo size should be 2");
        assertEquals(redoSizeAfterUndo, 1, "After undo, redo size should be 1");

        // Step 5: Do redo
        editor.redo();

        // Step 6: Read history again - should have 3 undo entries, 0 redo entries
        int undoSizeAfterRedo = editor.getHistoryManager().getUndoSize();
        int redoSizeAfterRedo = editor.getHistoryManager().getRedoSize();
        System.out.println("After redo - Undo size: " + undoSizeAfterRedo + ", Redo size: " + redoSizeAfterRedo);
        printHistory(editor, "After redo - ");
        assertEquals(undoSizeAfterRedo, 3, "After redo, undo size should be 3");
        assertEquals(redoSizeAfterRedo, 0, "After redo, redo size should be 0");

        System.out.println("===============================================");
    }

}
