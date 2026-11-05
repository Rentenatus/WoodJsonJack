package de.jare.tree.ui.project;

import de.jare.jsoncasted.project.BundleDescriptor;
import de.jare.jsoncasted.project.Partition;
import de.jare.jsoncasted.project.PartitionType;
import de.jare.jsoncasted.project.ProjectFileService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javax.swing.tree.DefaultTreeModel;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Regression tests of the bundle grouping (concept: CONCEPT_ProjectView.md, section 4): the
 * scan discovers bundles inside json partitions only, the bundle subtree renders the
 * descriptor's meaning (declared files first, the main file leads) and the single-file
 * fallback materializes into an explicit bundle.
 *
 * @author Janusch Rentenatus
 */
public class ProjectTreeNodeNGTest {

    private File tempRoot;
    private File profilesFolder;
    private File assetsFolder;
    private ProjectFileService service;

    @BeforeMethod
    public void setUp() throws Exception {
        tempRoot = Files.createTempDirectory("wjj-projectview").toFile();
        service = new ProjectFileService();

        profilesFolder = newFolder("profiles");
        assetsFolder = newFolder("assets");

        // config1: complete bundle - declared files plus an undeclared file and a subfolder
        File config1 = newFolder("profiles/config1");
        writeFile("profiles/config1/config1.json", "{\"a\": 1}");
        writeFile("profiles/config1/configDesc.json", "{\"b\": 2}");
        writeFile("profiles/config1/ConfigTemplate.json", "{\"c\": 3}");
        writeFile("profiles/config1/extraNote.txt", "undeclared");
        newFolder("profiles/config1/subthings");
        saveDescriptor(config1, "config1", "config1.json",
                "config1.json", "configDesc.json", "ConfigTemplate.json");

        // devConfig: the descriptor name wins over the folder name
        File devConfig = newFolder("profiles/devConfig");
        writeFile("profiles/devConfig/dev.json", "{\"d\": 4}");
        saveDescriptor(devConfig, "devConf", "dev.json", "dev.json");

        // brokenMain: the declared main file does not exist - the fallback applies
        File brokenMain = newFolder("profiles/brokenMain");
        writeFile("profiles/brokenMain/orphan.json", "{\"e\": 5}");
        saveDescriptor(brokenMain, "brokenMain", "doesNotExist.json", "orphan.json");

        // plainFolder inside the json partition: no descriptor, no bundle
        newFolder("profiles/plainFolder");

        // stray.json: the single-file fallback candidate
        writeFile("profiles/stray.json", "{\"f\": 6}");

        // a descriptor outside a json partition must not create a bundle
        File notABundle = newFolder("assets/notABundle");
        writeFile("assets/notABundle/x.json", "{\"g\": 7}");
        saveDescriptor(notABundle, "notABundle", "x.json", "x.json");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        deleteRecursively(tempRoot);
    }

    /**
     * The scan of a json partition discovers bundles (a folder with a readable descriptor)
     * and leaves plain folders and files alone. The descriptor name is the label (rule 3).
     */
    @Test
    public void testJsonPartitionScanDiscoversBundles() {
        ProjectTreeNode partition = partitionNode(profilesFolder, PartitionType.JSON);
        partition.ensureLoaded(new DefaultTreeModel(partition));

        ProjectTreeNode config1 = childByName(partition, "config1");
        assertEquals(config1.getKind(), ProjectTreeNode.Kind.BUNDLE);

        ProjectTreeNode devConfig = childByName(partition, "devConf");
        assertEquals(devConfig.getKind(), ProjectTreeNode.Kind.BUNDLE);
        assertEquals(devConfig.getFile().getName(), "devConfig");

        assertEquals(childByName(partition, "plainFolder").getKind(), ProjectTreeNode.Kind.FOLDER);
        assertEquals(childByName(partition, "stray.json").getKind(), ProjectTreeNode.Kind.FILE);
    }

    /**
     * Bundles are a json partition mechanism only: a folder with a descriptor below an assets
     * partition renders as a plain folder.
     */
    @Test
    public void testNonJsonPartitionIgnoresDescriptors() {
        ProjectTreeNode partition = partitionNode(assetsFolder, PartitionType.ASSETS);
        partition.ensureLoaded(new DefaultTreeModel(partition));

        ProjectTreeNode notABundle = childByName(partition, "notABundle");
        assertEquals(notABundle.getKind(), ProjectTreeNode.Kind.FOLDER);
    }

    /**
     * The bundle subtree renders the descriptor's meaning: the main file leads, then the
     * declared files in descriptor order, then the undeclared files, then the subfolders.
     */
    @Test
    public void testBundleChildrenRenderDeclarationsFirst() {
        ProjectTreeNode partition = partitionNode(profilesFolder, PartitionType.JSON);
        partition.ensureLoaded(new DefaultTreeModel(partition));

        ProjectTreeNode config1 = childByName(partition, "config1");
        DefaultTreeModel model = new DefaultTreeModel(config1);
        config1.ensureLoaded(model);

        assertEquals(config1.getChildCount(), 5);
        assertEquals(config1.getChildAt(0).toString(), "config1.json");
        assertEquals(((ProjectTreeNode) config1.getChildAt(0)).getKind(), ProjectTreeNode.Kind.FILE);
        assertEquals(config1.getChildAt(1).toString(), "configDesc.json");
        assertEquals(config1.getChildAt(2).toString(), "ConfigTemplate.json");
        assertEquals(config1.getChildAt(3).toString(), "extraNote.txt");
        assertEquals(((ProjectTreeNode) config1.getChildAt(4)).getKind(), ProjectTreeNode.Kind.FOLDER);
    }

    /**
     * The main file of a bundle falls back to the first declared file that exists when the
     * declared main file is not readable - the entry point stays usable.
     */
    @Test
    public void testMainFileFallsBackToFirstDeclared() {
        ProjectTreeNode partition = partitionNode(profilesFolder, PartitionType.JSON);
        partition.ensureLoaded(new DefaultTreeModel(partition));

        ProjectTreeNode brokenMain = childByName(partition, "brokenMain");
        assertEquals(brokenMain.getMainFile().getName(), "orphan.json");

        ProjectTreeNode config1 = childByName(partition, "config1");
        assertEquals(config1.getMainFile().getName(), "config1.json");
    }

    /**
     * The materialization (single-file fallback, section 4 rule 4) creates the sibling bundle
     * folder, moves the file into it and writes a descriptor that the service reads back.
     */
    @Test
    public void testMaterializeCreatesFolderDescriptorAndMovesFile() throws Exception {
        File stray = new File(profilesFolder, "stray.json");
        File bundleFolder = BundleMaterializer.materialize(stray);

        assertFalse(stray.isFile());
        assertTrue(new File(bundleFolder, "stray.json").isFile());
        BundleDescriptor descriptor = service.loadBundleDescriptor(bundleFolder);
        assertEquals(descriptor.getName(), "stray");
        assertEquals(descriptor.getMain(), "stray.json");
        assertEquals(descriptor.getFiles().size(), 1);
        assertEquals(descriptor.getFiles().get(0), "stray.json");

        // the re-projected partition shows the bundle instead of the bare file
        ProjectTreeNode partition = partitionNode(profilesFolder, PartitionType.JSON);
        partition.ensureLoaded(new DefaultTreeModel(partition));
        assertEquals(childByName(partition, "stray").getKind(), ProjectTreeNode.Kind.BUNDLE);
        assertNull(childByName(partition, "stray.json"));
    }

    /**
     * The materialization refuses to touch an existing folder - no implicit state survives an
     * explicit action, and neither does an explicit folder survive it silently.
     */
    @Test
    public void testMaterializeRefusesExistingFolder() throws Exception {
        assertTrue(new File(profilesFolder, "stray").mkdir());
        File stray = new File(profilesFolder, "stray.json");
        try {
            BundleMaterializer.materialize(stray);
            fail("Expected an IOException for the existing folder.");
        } catch (IOException expected) {
            // the file must be untouched
            assertTrue(stray.isFile());
        }
    }

    //
    // helpers
    //

    private ProjectTreeNode partitionNode(File folder, PartitionType type) {
        return new ProjectTreeNode(new Partition(folder.getName(), type, "./" + folder.getName(), null), folder);
    }

    private ProjectTreeNode childByName(ProjectTreeNode parent, String label) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (label.equals(parent.getChildAt(i).toString())) {
                return (ProjectTreeNode) parent.getChildAt(i);
            }
        }
        return null;
    }

    private void saveDescriptor(File bundleFolder, String name, String main, String... files)
            throws Exception {
        BundleDescriptor descriptor = new BundleDescriptor(name, main);
        for (String file : files) {
            if (!descriptor.getFiles().contains(file)) {
                descriptor.getFiles().add(file);
            }
        }
        service.saveBundleDescriptor(bundleFolder, descriptor);
    }

    private File newFolder(String relativePath) {
        File folder = new File(tempRoot, relativePath);
        assertTrue(folder.mkdirs(), "test fixture folder: " + folder);
        return folder;
    }

    private void writeFile(String relativePath, String content) throws IOException {
        File file = new File(tempRoot, relativePath);
        Files.write(file.toPath(), content.getBytes("UTF-8"));
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
