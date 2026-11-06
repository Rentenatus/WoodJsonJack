/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.io.HarnessModelCodec;
import de.jare.harness.model.HarnessModel;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * End-to-end test of the harness entry point (CONCEPT_Harness.md §3, §5): a task runs
 * through {@link HarnessService} in a temp project dir, the instance persists to
 * {@code .wood/harness.json}, and the bundled default model asset parses to the
 * modelled structure (quests, slots, modelclasses).
 *
 * @author Janusch Rentenatus
 */
public class HarnessServiceNGTest {

    @Test
    public void testRunTaskEndToEndInTempProject() throws Exception {
        Path project = Files.createTempDirectory("harness-project");
        HarnessService service = new HarnessService(project.toFile());

        HarnessInstance instance = service.runTask("RESEARCH", "service end to end");

        Assert.assertEquals(instance.getRoot().getStatus(), Status.DONE);
        File harnessJson = new File(project.toFile(), HarnessService.INSTANCE_FILE);
        Assert.assertTrue(harnessJson.exists(), "instance must persist to .wood/harness.json");
        Assert.assertFalse(new File(project.toFile(), HarnessService.INSTANCE_FILE + ".tmp").exists());

        // reload the persisted instance
        HarnessInstance loaded = de.jare.harness.io.HarnessInstanceCodec.load(harnessJson);
        Assert.assertEquals(loaded.getRoot().getStatus(), Status.DONE);
        Assert.assertFalse(loaded.getCallLog().isEmpty());
    }

    @Test
    public void testBundledDefaultModelAssetParses() throws Exception {
        File asset = new File("assets/harness/harness-model.json");
        Assert.assertTrue(asset.exists(), "bundled default model asset must exist");
        HarnessModel model = HarnessModelCodec.load(asset);

        Assert.assertEquals(model.getName(), "default-harness");
        Assert.assertEquals(model.getModelclasses().size(), 5);
        Assert.assertEquals(model.modelclass("rag").getModel(), "nomic-embed-text");
        Assert.assertTrue(model.questFor(Kind.WORK).isEvolvable());
        Assert.assertEquals(model.questFor(Kind.WORK).getSlots().size(), 4,
                "base + 3 Mutation-Resolve candidates");
        Assert.assertEquals(model.questFor(Kind.WORK).getSlots().get(1).getMutation(), "Mutation-Resolve-01");
        Assert.assertTrue(model.questFor(Kind.WORK).getSlots().get(1).getCalculator().isPuzzle());
        Assert.assertNotNull(model.questFor(Kind.ABORT));
    }

    @Test
    public void testDefaultModelFallbackWhenNoProjectFile() throws Exception {
        Path project = Files.createTempDirectory("harness-project");
        HarnessService service = new HarnessService(project.toFile());
        HarnessModel model = service.loadModel();
        Assert.assertEquals(model.getName(), "default-harness");
        for (Kind kind : Kind.values()) {
            Assert.assertNotNull(model.questFor(kind), "default model covers kind " + kind);
        }
    }
}
