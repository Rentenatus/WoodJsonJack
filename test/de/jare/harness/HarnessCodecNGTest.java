/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.impl.DefaultCalculatorFactory;
import de.jare.harness.io.HarnessInstanceCodec;
import de.jare.harness.io.HarnessModelCodec;
import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Modelclass;
import de.jare.harness.model.Quest;
import de.jare.harness.model.Slot;
import java.io.File;
import java.nio.file.Files;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Round-trip tests for the JSON codecs (CONCEPT_Harness.md §5): the harness model
 * (quest/slot/puzzle structure, modelclasses JsonMap) and the instance (task tree,
 * call log with parent chain, persisted nextId counters) survive a save/load cycle
 * through write-temp-rename files, and a loaded model still drives the engine.
 *
 * @author Janusch Rentenatus
 */
public class HarnessCodecNGTest {

    private static HarnessModel sampleModel() {
        HarnessModel model = new HarnessModel().name("codec-test").version("2.1.0")
                .abortLimits(42, 4, 2, 7);
        model.getTaskTypes().add("RESEARCH");
        model.getTaskTypes().add("CODE_EDIT");
        model.getPromptBudgets().put("SYSTEM", 2);
        model.getPromptBudgets().put("HISTORY", 3);
        model.addModelclass(new Modelclass("ollama", "qwen2.5:7b", "edit", 0, 2.0, true));
        model.addModelclass(new Modelclass("ollama", "nomic-embed-text", "rag", 0, 0.2, false));

        Quest work = new Quest(Kind.WORK).evolvable(true);
        work.addSlot(new Slot("work.base").calculator(
                CalculatorNode.atomic(CalculatorNode.IMPL_AI).modelclass("edit")));
        work.addSlot(new Slot("work.m1").mutation("Mutation-Resolve-01").calculator(
                CalculatorNode.puzzle("sequence")
                        .addChild(CalculatorNode.atomic(CalculatorNode.IMPL_AI).modelclass("edit"))
                        .addChild(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC).tool("fs.read"))));
        model.addQuest(work);

        Quest abort = new Quest(Kind.ABORT);
        abort.addSlot(new Slot("abort").calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC)));
        model.addQuest(abort);
        return model;
    }

    @Test
    public void testModelRoundTripThroughFile() throws Exception {
        File tmp = Files.createTempDirectory("harness-model").resolve("harness-model.json").toFile();
        HarnessModel model = sampleModel();
        HarnessModelCodec.save(model, tmp);
        Assert.assertTrue(tmp.exists());
        Assert.assertFalse(new File(tmp.getParent(), "harness-model.json.tmp").exists(),
                "temp file must be renamed away");

        HarnessModel loaded = HarnessModelCodec.load(tmp);
        Assert.assertEquals(loaded.getName(), "codec-test");
        Assert.assertEquals(loaded.getVersion(), "2.1.0");
        Assert.assertEquals(loaded.getTaskTypes(), model.getTaskTypes());
        Assert.assertEquals(loaded.getPromptBudgets(), model.getPromptBudgets());
        Assert.assertEquals(loaded.getMaxIterations(), 42);
        Assert.assertEquals(loaded.getMaxDepth(), 4);
        Assert.assertEquals(loaded.getMaxRetries(), 2);
        Assert.assertEquals(loaded.getMaxToolCalls(), 7);
        Assert.assertEquals(loaded.modelclass("edit").getModel(), "qwen2.5:7b");
        Assert.assertTrue(loaded.modelclass("edit").isToolCapable());
        Assert.assertFalse(loaded.modelclass("rag").isToolCapable());

        Quest work = loaded.questFor(Kind.WORK);
        Assert.assertTrue(work.isEvolvable());
        Assert.assertEquals(work.getSlots().size(), 2);
        Slot m1 = work.getSlots().get(1);
        Assert.assertEquals(m1.getMutation(), "Mutation-Resolve-01");
        Assert.assertTrue(m1.getCalculator().isPuzzle());
        Assert.assertEquals(m1.getCalculator().getChildren().size(), 2);
        Assert.assertEquals(m1.getCalculator().getChildren().get(1).getTool(), "fs.read");
        Assert.assertEquals(work.baseSlot().getId(), "work.base");
    }

    @Test
    public void testLoadedModelDrivesEngine() throws Exception {
        File tmp = Files.createTempDirectory("harness-model").resolve("harness-model.json").toFile();
        HarnessModel model = sampleModel();
        HarnessModelCodec.save(model, tmp);
        HarnessModel loaded = HarnessModelCodec.load(tmp);

        // complete the model with the remaining deterministic quests so the run can finish
        for (Kind kind : Kind.values()) {
            if (loaded.questFor(kind) == null) {
                loaded.addQuest(new Quest(kind).addSlot(new Slot(kind.name().toLowerCase() + ".base")
                        .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC))));
            }
        }
        // WORK slot: ai on unreachable ollama -> catch fallback to deterministic
        loaded.addModelclass(new Modelclass("ollama", "qwen2.5:7b", "edit", 0, 2.0, true));
        HarnessInstance instance = new HarnessInstance(loaded.getName(), loaded.getVersion());
        instance.setRoot(new TaskNode(instance.nextTaskId(), "CODE_EDIT", "edit via loaded model"));
        DefaultCalculatorFactory factory = new DefaultCalculatorFactory(loaded);
        factory.registerProvider("ollama", new de.jare.harness.impl.ollama.OllamaProvider(
                "http://127.0.0.1:1", loaded.modelclass("edit")));
        new HarnessEngine(loaded, instance, factory).run();

        Assert.assertEquals(instance.getRoot().getStatus(), Status.DONE);
        Assert.assertTrue(instance.getRoot().getCalculatorLog().stream()
                .anyMatch(e -> e.getKind() == Kind.WORK && e.getDecision().contains("catch:MODEL_UNAVAILABLE")));
    }

    @Test
    public void testInstanceRoundTripKeepsTreeLogAndCounters() throws Exception {
        HarnessModel model = sampleModel();
        for (Kind kind : Kind.values()) {
            if (model.questFor(kind) == null) {
                model.addQuest(new Quest(kind).addSlot(new Slot(kind.name().toLowerCase() + ".base")
                        .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC))));
            }
        }
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        instance.setRoot(new TaskNode(instance.nextTaskId(), "RESEARCH", "persist me?"));
        new HarnessEngine(model, instance, new DefaultCalculatorFactory(model)).run();

        File tmp = Files.createTempDirectory("harness-inst").resolve("harness.json").toFile();
        HarnessInstanceCodec.save(instance, tmp);
        HarnessInstance loaded = HarnessInstanceCodec.load(tmp);

        Assert.assertEquals(loaded.getModelName(), model.getName());
        Assert.assertEquals(loaded.getModelVersion(), model.getVersion());
        TaskNode root = loaded.getRoot();
        Assert.assertEquals(root.getStatus(), Status.DONE);
        Assert.assertEquals(root.getChildren().size(), 1, "reasoning subtask must survive");
        Assert.assertEquals(root.getChildren().get(0).getParent(), root, "parent link must be rebuilt");
        Assert.assertEquals(root.getChildren().get(0).depth(), 1);
        Assert.assertFalse(loaded.getCallLog().isEmpty());
        // parent chain intact after reload
        CallLogEntry any = loaded.getCallLog().get(loaded.getCallLog().size() - 1);
        if (any.getParent1() != null) {
            Assert.assertTrue(loaded.getCallLog().stream()
                    .anyMatch(e -> e.getAufrufId() == any.getParent1().aufrufId));
        }
        // counters: loaded instance continues after the highest used id
        long maxAufruf = loaded.getCallLog().stream().mapToLong(CallLogEntry::getAufrufId).max().orElse(0);
        Assert.assertTrue(loaded.getNextAufrufId() > maxAufruf,
                "nextAufrufId must stay ahead of every logged id after reload");
    }
}
