/* 
 */
package de.jare.jsoncasted.editor;

import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.lang.JsonNode;
import de.jare.jsoncasted.lang.JsonResource;
import de.jare.jsoncasted.parserservice.JsonParserService;
import de.jare.jsoncasted.parserwriter.JsonParseException;
import de.jare.jsonconfig.def.JsonConfigDefinition;
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
     * Test of getTree method, of class TreeEditor.
     */
    @Test
    public void test1() {
        System.out.println("===============================================");
        System.out.println("test1");
        System.out.println("===============================================");
        TreeEditor instance = new TreeEditor();

        JsonConfigDefinition definition = JsonConfigDefinition.getInstance();
        File configFile = new File("./assets/config/config1.json");
        System.out.println("Target=============================================== File");
        System.out.println(configFile.getAbsolutePath());

        JsonNode node = null;
        try {
            final JsonResource res = JsonParserService.parse(configFile, JsonDebugLevel.INFO);

            node = res.getRoot();

        } catch (JsonParseException | IOException | NullPointerException ex) {
            Logger.getGlobal().log(Level.SEVERE, null, ex);
            fail(ex.getMessage(), ex);
        }
        assertNotNull(node);

    }

}
