/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.io.HarnessModelCodec;
import de.jare.harness.metrics.MetricsStore;
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
    public void testRunTaskWritesGlobalWoodStores() throws Exception {
        Path project = Files.createTempDirectory("harness-project");
        Path fakeHome = Files.createTempDirectory("harness-wood-home");
        HarnessService service = new HarnessService(project.toFile());
        service.setWoodHome(fakeHome.toFile());

        service.runTask("RESEARCH", "global stores");

        // ~/.wood/harness/metrics.json: the global M x 3 aggregate accumulated this run (§8)
        File metricsFile = GlobalWood.metricsFile(fakeHome.toFile());
        Assert.assertTrue(metricsFile.exists(), "global metrics.json must be written");
        MetricsStore global = MetricsStore.load(metricsFile);
        Assert.assertFalse(global.getNodes().isEmpty(), "global aggregate must carry the run's slots");
        Assert.assertFalse(new File(metricsFile.getPath() + ".tmp").exists(), "no temp file left behind");

        // ~/.wood/harness/lessons.json: written (possibly still empty) and loadable
        File lessonsFile = GlobalWood.lessonsFile(fakeHome.toFile());
        Assert.assertTrue(lessonsFile.exists(), "global lessons.json must be written");
        Assert.assertNotNull(Lessons.load(lessonsFile));

        // ~/.wood/mindset/: directory exists and loads (empty mindset is valid)
        Assert.assertTrue(GlobalWood.mindsetDir(fakeHome.toFile()).toFile().isDirectory(),
                "global mindset dir must exist after the run");
        Assert.assertNotNull(de.jare.harness.mindset.Mindset.load(GlobalWood.mindsetDir(fakeHome.toFile())));
    }

    @Test
    public void testAbortLimitDefaultsAreDecidedPerConcept15() throws Exception {
        // §15 decided: maxIterations 50, maxDepth 5, maxRetries 3, maxToolCalls 10 -
        // code defaults, bundled asset and service default must all carry them
        HarnessModel codeDefaults = new HarnessModel();
        Assert.assertEquals(codeDefaults.getMaxIterations(), 50);
        Assert.assertEquals(codeDefaults.getMaxDepth(), 5);
        Assert.assertEquals(codeDefaults.getMaxRetries(), 3);
        Assert.assertEquals(codeDefaults.getMaxToolCalls(), 10);

        HarnessModel asset = HarnessModelCodec.load(new File("assets/harness/harness-model.json"));
        Assert.assertEquals(asset.getMaxIterations(), 50, "bundled asset carries the decided defaults");
        Assert.assertEquals(asset.getMaxDepth(), 5);
        Assert.assertEquals(asset.getMaxRetries(), 3);
        Assert.assertEquals(asset.getMaxToolCalls(), 10);

        HarnessModel serviceDefault = HarnessService.defaultModel();
        Assert.assertEquals(serviceDefault.getMaxIterations(), 50);
        Assert.assertEquals(serviceDefault.getMaxToolCalls(), 10);
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
