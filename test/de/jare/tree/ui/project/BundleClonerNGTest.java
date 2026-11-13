package de.jare.tree.ui.project;

import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.lang.JsonNode;
import de.jare.jsoncasted.project.BundleDescriptor;
import de.jare.jsoncasted.project.ProjectFileService;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Regression tests of the clone execution (concept: CONCEPT_ProjectView.md, section 5 - phase 6):
 * sibling convention, descriptor relabel, no ID remapping (Strategy B - shallow clones stay
 * byte-identical), the deep relocation of external provider files with their rewritten
 * references, and the rollback of refused clones.
 *
 * @author Janusch Rentenatus
 */
public class BundleClonerNGTest {

    private static final ProjectFileService SERVICE = new ProjectFileService();

    private File tempRoot;

    @BeforeMethod
    public void setUp() throws IOException {
        tempRoot = Files.createTempDirectory("wjj-cloner").toFile();
    }

    @AfterMethod
    public void tearDown() throws IOException {
        deleteRecursively(tempRoot);
    }

    /**
     * Shallow: the sibling convention places the copy next to the original, the folder content
     * copies plainly, the descriptor is relabeled - and nothing else changes: the bundle's main
     * file stays byte-identical (no ID remapping, Strategy B), external references keep pointing
     * at the original's files.
     */
    @Test
    public void testShallowCloneCopiesAndRelabelsOnly() throws Exception {
        buildFixture();

        File source = new File(tempRoot, "config1");
        File target = BundleCloner.clone(source, false);

        assertEquals(target.getName(), "config1_copy");
        assertTrue(new File(target, "config1.json").isFile(), "the bundle files copied");
        assertTrue(new File(target, ".woodbundle.json").isFile(), "the descriptor travels with it");

        BundleDescriptor descriptor = SERVICE.loadBundleDescriptor(target);
        assertEquals(descriptor.getName(), "config1_copy", "the name is rewritten to the folder name");
        assertEquals(descriptor.getMain(), "config1.json", "main and files stay as they are");

        byte[] original = Files.readAllBytes(source.toPath().resolve("config1.json"));
        byte[] cloned = Files.readAllBytes(target.toPath().resolve("config1.json"));
        assertEquals(cloned, original, "Strategy B - no ID remapping, the shallow clone is byte-identical");
    }

    /**
     * Deep: the external provider file moves into the clone and the reference rewrites to the
     * copy - the clone is independent. Internal references stay untouched.
     */
    @Test
    public void testDeepCloneRelocatesExternalProviders() throws Exception {
        buildFixture();

        File source = new File(tempRoot, "config1");
        File external = new File(tempRoot, "sharednode/x.json");
        File target = BundleCloner.clone(source, true);

        File relocated = new File(target, "x.json");
        assertTrue(relocated.isFile(), "the external provider file moved into the clone root");
        byte[] moved = Files.readAllBytes(relocated.toPath());
        byte[] expected = Files.readAllBytes(external.toPath());
        assertEquals(moved, expected, "the content is the copied one");
        assertTrue(external.isFile(), "the original keeps existing - in place, untouched");

        // the rewritten reference resolves inside the clone now
        JsonNode root = JsonParserService.parse(
                new File(target, "config1.json"), JsonDebugLevel.SIMPLE).getRoot();
        JsonNode providers = root.asObjectValues().get("_woodProviders");
        JsonNode first = providers.asArray().get(0);
        assertEquals(first.asObjectValues().get("filename").asText(), "x.json",
                "the reference points at the copy");

        // a second deep clone relocates from the sibling position with the same physics
        File second = BundleCloner.clone(source, true);
        assertEquals(second.getName(), "config1_2");
        assertTrue(new File(second, "x.json").isFile());
    }

    /**
     * A bundle without external references clones identically in both modes - the deep scan
     * finds no provider file outside the folder and leaves the copies untouched.
     */
    @Test
    public void testDeepWithoutExternalReferencesEqualsPlainCopy() throws Exception {
        buildFixture();
        File source = new File(tempRoot, "config1");
        // remove the external reference - the provider points inside the bundle instead
        write("config1/config1.json", "{\"_woodProviders\": ["
                + "{\"synonym\": \"shared\", \"filename\": \"org.json\"}], \"one\": 1}");
        Files.write(tempRoot.toPath().resolve("config1/org.json"),
                "{\"own\": true}".getBytes(StandardCharsets.UTF_8));

        File shallow = BundleCloner.clone(source, false);
        File deep = BundleCloner.clone(source, true);

        assertEquals(deep.getName(), "config1_2", "the sibling convention chains on");
        byte[] shallowMain = Files.readAllBytes(shallow.toPath().resolve("config1.json"));
        byte[] deepMain = Files.readAllBytes(deep.toPath().resolve("config1.json"));
        assertEquals(deepMain, shallowMain, "no external providers - deep equals the plain copy");
        assertTrue(new File(deep, "org.json").isFile(), "the internal file copied with the folder");
    }

    /**
     * A destination taken refuses the clone - and a collision during the deep relocation rolls
     * the partial copy back: no half clone survives.
     */
    @Test
    public void testRefusalAndRollbackLeaveNoHalfClone() throws Exception {
        buildFixture();

        // taken destination: the sibling convention chains to the next free name - never an overwrite
        File target = new File(tempRoot, "config1_copy");
        assertTrue(target.mkdir());
        File second = BundleCloner.clone(new File(tempRoot, "config1"), false);
        assertEquals(second.getName(), "config1_2", "the first free name wins");
        assertEquals(target.listFiles().length, 0, "the taken sibling stays untouched");

        // collision at the relocation: the clone folder carries a file named like the external one
        deleteRecursively(second);
        write("config1/x.json", "{\"inTheWay\": true}");
        try {
            BundleCloner.clone(new File(tempRoot, "config1"), true);
            fail("Expected a refusal for the colliding relocation");
        } catch (IOException expected) {
            File deepTarget = new File(tempRoot, "config1_2");
            assertFalse(deepTarget.exists(), "the partial copy rolled back - no half clone survives");
        }
        File source = new File(tempRoot, "config1");
        assertTrue(new File(source, "config1.json").isFile(), "the original survives everything");
    }

    /**
     * A bundle with references the deep scan cannot relocate stays honest: dangling external
     * references are the validator's case - the cloner leaves them untouched instead of failing.
     */
    @Test
    public void testDanglingExternalReferenceStaysUntouched() throws Exception {
        buildFixture();
        write("config1/dangling.json", "{\"_woodProviders\": ["
                + "{\"synonym\": \"void\", \"filename\": \"../notThere/x.json\"}]}");
        BundleDescriptor descriptor = SERVICE.loadBundleDescriptor(new File(tempRoot, "config1"));
        descriptor.getFiles().add("dangling.json");
        SERVICE.saveBundleDescriptor(new File(tempRoot, "config1"), descriptor);

        File target = BundleCloner.clone(new File(tempRoot, "config1"), true);

        byte[] dangling = Files.readAllBytes(target.toPath().resolve("dangling.json"));
        String asText = new String(dangling, StandardCharsets.UTF_8);
        assertTrue(asText.contains("../notThere/x.json"),
                "the dangling reference stays as it was - the validator's case");
    }

    //
    // helpers
    //

    /**
     * Fixture: the partition folder holds the bundle config1 and the external sharednode folder
     * it references; the bundle declares two files, the main references the external provider.
     */
    private void buildFixture() throws IOException {
        newFolder("config1");
        write("config1/config1.json", "{\"_woodProviders\": ["
                + "{\"synonym\": \"shared\", \"filename\": \"../sharednode/x.json\"}],"
                + " \"ref\": {\"_woodLink\": \"shared::5\"}}");
        write("config1/extra.json", "{\"e\": 1}");
        newFolder("sharednode");
        write("sharednode/x.json", "{\"shared\": {\"_woodObjectId\": \"5\", \"text\": \"B\"}}");

        File config1 = new File(tempRoot, "config1");
        BundleDescriptor descriptor = new BundleDescriptor("config1", "config1.json");
        descriptor.getFiles().add("extra.json");
        try {
            SERVICE.saveBundleDescriptor(config1, descriptor);
        } catch (JsonParseException | de.jare.jsoncasted.io.JsonWriteException e) {
            throw new IOException("fixture failed: " + e.getMessage(), e);
        }
    }

    private void newFolder(String relativePath) {
        assertTrue(new File(tempRoot, relativePath).mkdir(), "test fixture folder: " + relativePath);
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempRoot.toPath().resolve(relativePath);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteRecursively(File folder) {
        File[] entries = folder.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                deleteRecursively(entry);
            }
        }
        folder.delete();
    }

}
