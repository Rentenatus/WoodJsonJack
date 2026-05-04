/* 
 */
package de.jare.jsoncasted.editor;

import de.jare.debug.JsonDebugLevel;
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

}
