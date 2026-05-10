/*
 * Simple integration tests for TreeEditor import/export and history.
 */
package de.jare.jsoncasted.editor;

import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.editor.command.AddNodeCommand;
import de.jare.jsoncasted.editor.command.CommandResult;
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

public class TreeEditorSimpleNGTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        System.out.println("===============================================");
        System.out.println("## Start TreeEditorSimpleNGTest.");
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        System.out.println("## End TreeEditorSimpleNGTest.");
        System.out.println("===============================================");
    }

    /**
     * Test: Load JsonResource, import to TreeEditor, export back to JsonNode,
     * and verify that import/export does not lose the root structure.
     */
    @Test
    public void testImportExport() {
        File configFile = new File("assets/config/config1.json");

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

        TreeEditor editor = TreeEditor.fromJsonResource(resource);
        assertNotNull(editor, "TreeEditor should not be null");
        assertNotNull(editor.getTree(), "Tree should not be null");
        assertNotNull(editor.getTree().getRoot(), "Tree should have a root node");

        JsonNode exportedNode = editor.exportToJsonNode();
        assertNotNull(exportedNode, "Exported JsonNode should not be null");

        JsonResource exportedResource = editor.exportToJsonResource();
        assertNotNull(exportedResource, "Exported JsonResource should not be null");
        assertNotNull(exportedResource.getRoot(), "Exported JsonResource should have a root");

        // Grobe Gleichheit über String-Repräsentation prüfen
        assertEquals(
                exportedNode.toString(),
                originalRoot.toString(),
                "Import/Export should preserve JSON structure"
        );
    }

    /**
     * Test roundtrip: import -> export -> import -> export. Verify that
     * repeated roundtrips do not change the JSON representation.
     */
    @Test
    public void testRoundtrip() {
        File configFile = new File("assets/config/config1.json");

        JsonResource resource = null;
        try {
            resource = JsonParserService.parse(configFile, JsonDebugLevel.INFO);
        } catch (JsonParseException | IOException | NullPointerException ex) {
            Logger.getGlobal().log(Level.SEVERE, null, ex);
            fail("Failed to parse JSON file: " + ex.getMessage(), ex);
        }

        TreeEditor editor1 = TreeEditor.fromJsonResource(resource);
        JsonNode exported = editor1.exportToJsonNode();
        assertNotNull(exported, "Exported JsonNode should not be null");

        TreeEditor editor2 = TreeEditor.fromJsonNode(exported);
        JsonNode reExported = editor2.exportToJsonNode();
        assertNotNull(reExported, "Re-exported JsonNode should not be null");

        assertEquals(
                reExported.toString(),
                exported.toString(),
                "Roundtrip import/export should be stable"
        );
    }

    /**
     * Helper method to print command info with editId if available.
     */
    private String getCommandInfo(EditCommand cmd) {
        String info = cmd.toString();
        if (cmd instanceof AddNodeCommand add) {
            info += " (editId: " + add.getEditId() + ")";
        }
        return info;
    }

    /**
     * Helper method to print history stacks for debugging.
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
     * Test: Makes 3 changes, verifies history sizes und Baumzustand, does
     * undo/redo and verifies both again.
     */
    @Test
    public void testUndoRedoHistory() {
        TreeEditor editor = new TreeEditor();
        EditNode root = editor.getTree().getRoot();
        assertNotNull(root, "Root should not be null");
        assertEquals(root.getChildCount(), 0, "Initial root should have no children");

        EditNodeObject node1 = new EditNodeObject("node1");
        EditNodeObject node2 = new EditNodeObject("node2");
        EditNodeObject node3 = new EditNodeObject("node3");

        CommandResult r1 = editor.execute(new AddNodeCommand(root.getEditId(), node1));
        CommandResult r2 = editor.execute(new AddNodeCommand(root.getEditId(), node2));
        CommandResult r3 = editor.execute(new AddNodeCommand(root.getEditId(), node3));

        assertNotNull(r1);
        assertNotNull(r2);
        assertNotNull(r3);
        assertEquals(root.getChildCount(), 3, "After 3 adds, root should have 3 children");

        assertEquals(editor.getHistoryManager().getUndoSize(), 3);
        assertEquals(editor.getHistoryManager().getRedoSize(), 0);

        CommandResult undoResult = editor.undo();
        assertNotNull(undoResult, "Undo result should not be null");
        assertEquals(root.getChildCount(), 2, "After undo, root should have 2 children");
        assertEquals(editor.getHistoryManager().getUndoSize(), 2);
        assertEquals(editor.getHistoryManager().getRedoSize(), 1);

        CommandResult redoResult = editor.redo();
        assertNotNull(redoResult, "Redo result should not be null");
        assertEquals(root.getChildCount(), 3, "After redo, root should have 3 children");
        assertEquals(editor.getHistoryManager().getUndoSize(), 3);
        assertEquals(editor.getHistoryManager().getRedoSize(), 0);
    }

    /**
     * Test: Makes 3 changes, then undo, undo, skipRedo, redo, and a 4th change.
     * Verifies history and tree state at each step.
     */
    @Test
    public void testSkipRedo() {
        TreeEditor editor = new TreeEditor();
        EditNode root = editor.getTree().getRoot();
        assertNotNull(root, "Root should not be null");
        assertEquals(root.getChildCount(), 0, "Initial root should have no children");

        EditNodeObject node1 = new EditNodeObject("node1");
        EditNodeObject node2 = new EditNodeObject("node2");
        EditNodeObject node3 = new EditNodeObject("node3");

        editor.execute(new AddNodeCommand(root.getEditId(), node1));
        editor.execute(new AddNodeCommand(root.getEditId(), node2));
        editor.execute(new AddNodeCommand(root.getEditId(), node3));

        assertEquals(root.getChildCount(), 3);
        assertEquals(editor.getHistoryManager().getUndoSize(), 3);
        assertEquals(editor.getHistoryManager().getRedoSize(), 0);

        editor.undo(); // undo node3
        assertEquals(root.getChildCount(), 2);
        assertEquals(editor.getHistoryManager().getUndoSize(), 2);
        assertEquals(editor.getHistoryManager().getRedoSize(), 1);

        editor.undo(); // undo node2
        assertEquals(root.getChildCount(), 1);
        assertEquals(editor.getHistoryManager().getUndoSize(), 1);
        assertEquals(editor.getHistoryManager().getRedoSize(), 2);

        editor.skipRedo(); // skip node2
        assertEquals(root.getChildCount(), 1, "skipRedo must not change tree");
        assertEquals(editor.getHistoryManager().getUndoSize(), 2);
        assertEquals(editor.getHistoryManager().getRedoSize(), 1);

        editor.redo(); // redo node3
        assertEquals(root.getChildCount(), 2);
        assertEquals(editor.getHistoryManager().getUndoSize(), 3);
        assertEquals(editor.getHistoryManager().getRedoSize(), 0);

        EditNodeObject node4 = new EditNodeObject("node4");
        editor.execute(new AddNodeCommand(root.getEditId(), node4));
        assertEquals(root.getChildCount(), 3);
        assertEquals(editor.getHistoryManager().getUndoSize(), 4);
        assertEquals(editor.getHistoryManager().getRedoSize(), 0);
    }
}
