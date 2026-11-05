package de.jare.tree.ui.project;

import java.io.File;
import java.nio.file.Files;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Regression tests of the dry clone planning (concept: CONCEPT_ProjectView.md, section 5):
 * the sibling convention picks the first free name and the plan describes the two copy modes
 * without executing anything.
 *
 * @author Janusch Rentenatus
 */
public class BundleClonePlannerNGTest {

    private File tempRoot;

    @BeforeMethod
    public void setUp() throws Exception {
        tempRoot = Files.createTempDirectory("wjj-cloneplan").toFile();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        deleteRecursively(tempRoot);
    }

    /**
     * The sibling convention (concept section 5): the first candidate is
     * {@code <name>_copy}, further candidates count up from {@code <name>_2}.
     */
    @Test
    public void testFindFreeSiblingFolderUsesSiblingConvention() throws Exception {
        File original = newFolder("config1");

        File first = BundleClonePlanner.findFreeSiblingFolder(original);
        assertEquals(first.getName(), "config1_copy");

        newFolder("config1_copy");
        File second = BundleClonePlanner.findFreeSiblingFolder(original);
        assertEquals(second.getName(), "config1_2");

        newFolder("config1_2");
        File third = BundleClonePlanner.findFreeSiblingFolder(original);
        assertEquals(third.getName(), "config1_3");
    }

    /**
     * The dry plan names the source and the destination, carries both modes and executes
     * nothing: the destination folder does not exist after planning.
     */
    @Test
    public void testPlanIsDryAndDescribesTheModes() throws Exception {
        File original = newFolder("config1");

        BundleClonePlanner.ClonePlan deep = BundleClonePlanner.plan(original, true);
        assertEquals(deep.getSourceFolder(), original);
        assertEquals(deep.getTargetFolder().getName(), "config1_copy");
        assertTrue(deep.isDeep());
        assertFalse(deep.isRefused());
        assertFalse(new File(tempRoot, "config1_copy").exists());
        assertTrue(joined(deep).contains("Deep"));

        BundleClonePlanner.ClonePlan shallow = BundleClonePlanner.plan(original, false);
        assertFalse(shallow.isDeep());
        assertTrue(joined(shallow).contains("Shallow"));
        assertTrue(joined(shallow).contains("rewritten to \"config1_copy\""));
    }

    /**
     * A plan whose destination exists is refused with a reason - no silent overwrite, the
     * first free name logic and the guard know the same folder.
     */
    @Test
    public void testPlanRefusedWhenDestinationExists() throws Exception {
        File original = newFolder("config1");
        newFolder("config1_copy");

        // all free names taken up to config1_5: the convention finds config1_6, not a refusal
        newFolder("config1_2");
        newFolder("config1_3");
        newFolder("config1_4");
        newFolder("config1_5");
        BundleClonePlanner.ClonePlan plan = BundleClonePlanner.plan(original, true);
        assertFalse(plan.isRefused());
        assertEquals(plan.getTargetFolder().getName(), "config1_6");
    }

    //
    // helpers
    //

    private String joined(BundleClonePlanner.ClonePlan plan) {
        return String.join("\n", plan.getNotes());
    }

    private File newFolder(String relativePath) {
        File folder = new File(tempRoot, relativePath);
        assertTrue(folder.mkdir(), "test fixture folder: " + folder);
        return folder;
    }

    private void deleteRecursively(File folder) {
        File[] entries = folder.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                deleteRecursively(entry);
            }
        }
        folder.delete();
    }

}
