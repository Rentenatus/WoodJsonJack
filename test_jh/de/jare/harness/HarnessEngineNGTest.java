/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.impl.DefaultCalculatorFactory;
import de.jare.harness.impl.ModelProvider;
import de.jare.harness.impl.ollama.OllamaProvider;
import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Modelclass;
import de.jare.harness.model.Quest;
import de.jare.harness.model.Slot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * End-to-end tests of the deterministic driver skeleton (CONCEPT_Harness.md §1-§6):
 * a full run over the task tree, the metrics-only call log with the walkable
 * parent1-redundant / parent2-link chain, the reasoning subtask, the split path and
 * the catch-mapping fallback for an unavailable AI provider.
 *
 * @author Janusch Rentenatus
 */
public class HarnessEngineNGTest {

    private static HarnessModel modelWithAllQuests() {
        HarnessModel model = new HarnessModel().name("test-harness").version("0.1.0")
                .abortLimits(50, 5, 3, 10);
        for (Kind kind : Kind.values()) {
            model.addQuest(new Quest(kind).addSlot(new Slot(kind.name().toLowerCase() + ".base")
                    .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC))));
        }
        return model;
    }

    private static HarnessEngine engineFor(HarnessModel model, String rootTitle) {
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        instance.newRootTask(instance.nextTaskId(), "RESEARCH", rootTitle);
        DefaultCalculatorFactory factory = new DefaultCalculatorFactory(model);
        return new HarnessEngine(model, instance, factory);
    }

    @Test
    public void testFullRunClosesRootAndLogsEveryKind() {
        HarnessEngine engine = engineFor(modelWithAllQuests(), "research topic");
        engine.run();

        TaskData root = engine.getInstance().getRoot();
        Assert.assertEquals(root.getStatus(), Status.DONE);
        Assert.assertEquals(root.getArtifact(), "closed:research topic");
        Assert.assertEquals(root.getScore(), Integer.valueOf(80));

        List<Kind> loggedKinds = engine.getInstance().getCallLog().stream()
                .map(CallLogEntry::getKind).distinct().toList();
        for (Kind expected : new Kind[]{Kind.ABORT, Kind.SPLIT, Kind.COMPLETE, Kind.WORK,
                Kind.EVALUATE, Kind.CHECK, Kind.CLOSE}) {
            Assert.assertTrue(loggedKinds.contains(expected), "missing kind in log: " + expected);
        }
    }

    @Test
    public void testParentChainIsWalkableViaParent2Link() {
        HarnessEngine engine = engineFor(modelWithAllQuests(), "chain check");
        engine.run();

        List<CallLogEntry> log = engine.getInstance().getCallLog();
        Map<Long, CallLogEntry> byId = new HashMap<>();
        for (CallLogEntry e : log) {
            byId.put(e.getAufrufId(), e);
        }
        Assert.assertFalse(log.isEmpty());
        for (CallLogEntry e : log) {
            if (e.getParent1() != null) {
                // parent1 is redundant: the referenced call must exist with same score/status
                CallLogEntry p = byId.get(e.getParent1().aufrufId);
                Assert.assertNotNull(p, "parent1 target missing: " + e.getParent1().aufrufId);
            }
            if (e.getParent2() != null) {
                // parent3+ are not stored: the info must be reachable by walking via parent2.
                // invariant: e.parent2 == (the parent1 call's own parent1).aufrufId
                CallLogEntry p2 = byId.get(e.getParent2());
                Assert.assertNotNull(p2, "parent2 link dangling: " + e.getParent2());
                CallLogEntry parentCall = byId.get(e.getParent1().aufrufId);
                Assert.assertNotNull(parentCall.getParent1());
                Assert.assertEquals(parentCall.getParent1().aufrufId, e.getParent2().longValue(),
                        "parent2 must link to the grandparent call");
            }
        }
    }

    @Test
    public void testIncompleteTaskSpawnsReasoningSubtask() {
        HarnessEngine engine = engineFor(modelWithAllQuests(), "open question?");
        engine.run();

        TaskData root = engine.getInstance().getRoot();
        Assert.assertEquals(root.getChildren().size(), 1);
        TaskData reasoning = root.getChildren().get(0);
        Assert.assertEquals(reasoning.getTaskType(), "REASONING");
        Assert.assertEquals(reasoning.getStatus(), Status.DONE);
        Assert.assertEquals(root.getStatus(), Status.DONE);
    }

    @Test
    public void testSplitRewritesTaskAsSummaryAndClosesChildren() {
        HarnessModel model = modelWithAllQuests();
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        instance.newRootTask(instance.nextTaskId(), "RESEARCH", "split:big topic");
        // custom factory seam: deterministic split when the title starts with "split:"
        DefaultCalculatorFactory base = new DefaultCalculatorFactory(model);
        CalculatorFactory factory = (node, kind) -> {
            if (kind == Kind.SPLIT) {
                return new HarnessCalculator() {
                    @Override
                    public Kind kind() {
                        return Kind.SPLIT;
                    }

                    @Override
                    public CalcResult calculate(CalcContext ctx) {
                        String title = ctx.task().getTitle();
                        return title.startsWith("split:") && !title.contains("/ part")
                                ? CalcResult.of(Kind.SPLIT, "split").put("parts", "3")
                                : CalcResult.of(Kind.SPLIT, "nosplit");
                    }
                };
            }
            return base.createAtomic(node, kind);
        };
        HarnessEngine engine = new HarnessEngine(model, instance, factory);
        engine.run();

        TaskData root = instance.getRoot();
        Assert.assertTrue(root.isSummaryTask());
        Assert.assertEquals(root.getChildren().size(), 3);
        for (TaskData child : root.getChildren()) {
            Assert.assertEquals(child.getStatus(), Status.DONE);
        }
        Assert.assertEquals(root.getStatus(), Status.DONE);
        Assert.assertTrue(root.getCalculatorLog().stream().anyMatch(e -> e.getKind() == Kind.SUMMARIZE));
    }

    @Test
    public void testAiProviderOutageFallsBackToDeterministicViaCatchMapping() {
        HarnessModel model = modelWithAllQuests();
        model.addModelclass(new Modelclass("ollama", "qwen2.5:7b", "edit", 0, 2.0, true));
        // WORK quest uses an AI calculator on an unreachable ollama
        Quest work = model.questFor(Kind.WORK);
        work.getSlots().clear();
        work.addSlot(new Slot("work.ai")
                .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_AI).modelclass("edit")));

        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        instance.newRootTask(instance.nextTaskId(), "CODE_EDIT", "edit something");
        DefaultCalculatorFactory factory = new DefaultCalculatorFactory(model);
        factory.registerProvider("ollama",
                new OllamaProvider("http://127.0.0.1:1", model.modelclass("edit"))); // nothing listens there
        HarnessEngine engine = new HarnessEngine(model, instance, factory);
        engine.run();

        TaskData root = instance.getRoot();
        Assert.assertEquals(root.getStatus(), Status.DONE, "fallback must still close the task");
        CallLogEntry workCall = root.getCalculatorLog().stream()
                .filter(e -> e.getKind() == Kind.WORK).findFirst().orElseThrow();
        Assert.assertTrue(workCall.getDecision().contains("catch:MODEL_UNAVAILABLE"),
                "expected catch fallback in decision, got: " + workCall.getDecision());
        Assert.assertEquals(workCall.getStatus(), Status.FAILED);
        Assert.assertFalse(model.modelclass("edit").isAvailable(), "outage must be marked on the modelclass");
    }

    @Test
    public void testAbortLimitStopsRun() {
        HarnessModel model = modelWithAllQuests().abortLimits(3, 5, 3, 10);
        HarnessEngine engine = engineFor(model, "loop forever?");
        engine.run();

        TaskData root = engine.getInstance().getRoot();
        Assert.assertEquals(root.getStatus(), Status.ABORTED);
        Assert.assertTrue(engine.getLimits().getCounter(Limits.ITERATIONS) >= 3);
    }
}
