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
import de.jare.harness.model.Quest;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        Assert.assertEquals(model.getModelclasses().size(), 11, "5 roles + local extras + phi4-mini (scoring)");
        Assert.assertEquals(model.modelclass("scoring").getModel(), "phi4-mini:latest");
        Assert.assertEquals(model.modelclass("scoring").getSize(), 4, "phi4-mini ~3.8B -> size 4 (integer B)");
        Assert.assertEquals(model.modelclass("rag").getModel(), "manutic/nomic-embed-code:latest");
        Assert.assertEquals(model.modelclass("edit").getModel(), "devstral-small-2:24b");
        // purpose is an attribute, not the key: several modelclasses share one purpose
        Assert.assertEquals(model.modelclassesForPurpose("investigation").size(), 3);
        Assert.assertEquals(model.modelclassesForPurpose("edit").size(), 2);
        // size in B, integer; glm-4.7-flash is a 30B MoE (explicit attribute, name has no "Nb")
        Assert.assertEquals(model.modelclass("plan").getSize(), 30);
        Assert.assertEquals(model.modelclass("edit").getSize(), 24);
        Assert.assertEquals(de.jare.harness.model.Modelclass.sizeOf("qwen3.8:27b"), 27);
        Assert.assertEquals(de.jare.harness.model.Modelclass.sizeOf("granite3.3:8b"), 8);
        Assert.assertEquals(de.jare.harness.model.Modelclass.sizeOf("glm-4.7-flash:latest"), 0);
        // the main AskAi quest: SPLIT with the named puzzle children scoring -> decompose -> printig
        Assert.assertEquals(model.getTaskTypes().get(0), "AskAi");
        Quest askAi = model.questFor(Kind.SPLIT);
        Assert.assertNotNull(askAi, "AskAi quest (SPLIT) must be in the bundled asset");
        List<String> childNames = askAi.baseSlot().getCalculator().getChildren().stream()
                .map(c -> c.getCalculator()).toList();
        Assert.assertEquals(childNames, List.of("scoring.countQuests", "askai.decompose", "printig"));
        // the scoring child carries its params: model + systemPrompt (mutable at config level)
        java.util.Map<String, String> scoringParams = askAi.baseSlot().getCalculator().getChildren().get(0).getParams();
        Assert.assertEquals(scoringParams.get("model"), "phi4-mini:latest");
        Assert.assertTrue(scoringParams.get("systemPrompt").contains("questCount"),
                "systemPrompt is a config param of scoring.countQuests");
        // the decompose child uses the strongest local genAI: nemotron3:33b, and its
        // systemPrompt is a config param too (mutable at config level)
        java.util.Map<String, String> decomposeParams = askAi.baseSlot().getCalculator().getChildren().get(1).getParams();
        Assert.assertEquals(decomposeParams.get("model"), "nemotron3:33b");
        Assert.assertTrue(decomposeParams.get("systemPrompt").contains("quests"),
                "systemPrompt is a config param of askai.decompose");
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
    public void testRunTaskCreatesMemoryQuestWithWorkOrder() throws Exception {
        Path project = Files.createTempDirectory("harness-project");
        HarnessService service = new HarnessService(project.toFile());

        HarnessInstance inst = service.runTask("RESEARCH", "the user prompt of this run");

        de.jare.harness.memory.MemoryQuest mq = service.getLastMemoryQuest();
        Assert.assertNotNull(mq, "each run creates a de.jare.harness.memory.MemoryQuest in memory (DESIGN_Quest_WorkOrder.md)");
        Assert.assertNotNull(mq.getRecipe(), "references the recipe quest");
        Assert.assertEquals(mq.getWorkOrder().getId(), "work.base");
        Assert.assertEquals(mq.getWorkOrder().getUserPrompt(), "the user prompt of this run",
                "the user prompt is its own field on the WorkOrder");
        Assert.assertSame(mq.getWorkOrder().getRoot(), inst.getRoot(), "WorkOrder wraps the run's task tree");
        Assert.assertNotNull(mq.getWorkCache());
        Assert.assertEquals(mq.getWorkResponse().getStatus(), Status.DONE, "live answer after the run");
        Assert.assertEquals(mq.getWorkOrder().getStatus(), Status.DONE);
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

        assertMainLoop(asset, "bundled asset");
        assertMainLoop(serviceDefault, "service default model");
    }

    /** Guard (§15.6): both default models carry the configured entry loop "main". */
    private static void assertMainLoop(HarnessModel model, String what) {
        de.jare.harness.model.Loop main = model.getLoop(de.jare.harness.model.Loop.MAIN);
        Assert.assertNotNull(main, what + " must carry loops.main");
        Assert.assertEquals(main.itemsFor(de.jare.harness.model.Trigger.START).get(0).getKind(), Kind.ABORT);
        Assert.assertEquals(main.itemsFor(de.jare.harness.model.Trigger.CATCH).get(0).getKind(), Kind.ABORT);
        Assert.assertEquals(main.itemsFor(de.jare.harness.model.Trigger.CLOSE).stream()
                .map(i -> i.getKind()).toList(), List.of(Kind.CLOSE, Kind.RAG_PRUNE));
        Assert.assertEquals(main.itemsFor(de.jare.harness.model.Trigger.EXECUTE).stream()
                .map(i -> i.getKind()).toList(),
                List.of(Kind.SPLIT, Kind.COMPLETE, Kind.WORK, Kind.EVALUATE,
                        Kind.RAG_STORE, Kind.CHECK, Kind.SUMMARIZE));
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

    /**
     * §17: the project-local memory - each run appends a user + an assistant CHAT entry and
     * persists to {@code .wood/harness/memory.json}; a second run loads the file again, so the
     * chat history accumulates across runs in the same project.
     */
    @Test
    public void testProjectMemoryAccumulatesAcrossRuns() throws Exception {
        Path project = Files.createTempDirectory("harness-project-mem");
        HarnessService service = new HarnessService(project.toFile());

        service.runTask("RESEARCH", "erste frage");
        File memoryJson = new File(project.toFile(), HarnessService.MEMORY_FILE);
        Assert.assertTrue(memoryJson.exists(), "memory must persist to .wood/harness/memory.json");
        Assert.assertNotNull(service.getProjectMemory());
        Assert.assertEquals(service.getProjectMemory().size(), 2, "user + assistant entry after run 1");

        service.runTask("RESEARCH", "zweite frage");
        // reloaded from disk: the chat history survived the "restart"
        de.jare.harness.memory.ProjectMemory reloaded =
                de.jare.harness.memory.ProjectMemory.load(memoryJson);
        Assert.assertEquals(reloaded.size(), 4, "chat history accumulates across runs (2 runs x user+assistant)");
        List<de.jare.harness.memory.ProjectMemory.Nugget> entries = reloaded.getEntries();
        Assert.assertEquals(entries.get(0).role, "user");
        Assert.assertEquals(entries.get(0).text, "erste frage");
        Assert.assertEquals(entries.get(1).role, "assistant");
        Assert.assertEquals(entries.get(2).text, "zweite frage");
        Assert.assertEquals(entries.get(0).kind, de.jare.harness.memory.ProjectMemory.Kind.CHAT);
    }

    /**
     * §17 feedback hook: a nugget in the project memory is fed into the run's prompts (DATA
     * segment) - after the run it carries uses (+hits when the run scored >= threshold), and the
     * global metrics get a per-source-calculator SCORE entry ("nugget:&lt;source&gt;"), so the
     * nugget-producing calculator is scoreable like any slot (§9 analogy: constructed gradient).
     */
    @Test
    public void testNuggetFeedbackUsesHitsAndMetrics() throws Exception {
        Path project = Files.createTempDirectory("harness-project-feedback");
        Path wood = Files.createTempDirectory("harness-wood-feedback");
        HarnessService service = new HarnessService(project.toFile());
        service.setWoodHome(wood.toFile());
        de.jare.harness.memory.ProjectMemory mem = de.jare.harness.memory.ProjectMemory.load(service.memoryFile());
        de.jare.harness.memory.ProjectMemory.Nugget n = mem.addNugget("User bevorzugt kurze Antworten", "nugget.extract");
        mem.save(service.memoryFile());

        HarnessInstance instance = service.runTask("RESEARCH", "frage mit nugget im speicher");

        de.jare.harness.memory.ProjectMemory after = de.jare.harness.memory.ProjectMemory.load(service.memoryFile());
        de.jare.harness.memory.ProjectMemory.Nugget n2 = after.get(n.id);
        Assert.assertNotNull(n2, "nugget survived the run");
        Assert.assertTrue(n2.uses >= 1, "nugget was fed into a prompt -> uses counted");
        if (instance.getRoot().getScore() != null && instance.getRoot().getScore() >= HarnessService.NUGGET_HIT_SCORE_THRESHOLD) {
            Assert.assertTrue(n2.hits >= 1, "run scored >= threshold -> hit counted");
        }
        // per-source-calculator metric in the global store (kennzahlen-only, §8)
        de.jare.harness.metrics.MetricsStore gm =
                de.jare.harness.metrics.MetricsStore.load(de.jare.harness.GlobalWood.metricsFile(wood.toFile()));
        Assert.assertTrue(gm.stats("nugget:nugget.extract", 1)
                        .containsKey(de.jare.harness.metrics.MetricsStore.Category.SCORE),
                "nugget feedback lands in the global metrics under the source calculator");
    }
}
