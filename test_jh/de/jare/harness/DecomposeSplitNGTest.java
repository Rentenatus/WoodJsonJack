/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.impl.DefaultCalculatorFactory;
import de.jare.harness.main.actions.LoopSplit;
import de.jare.harness.main.calculator.AskAiDecomposeCalculator;
import de.jare.harness.memory.WorkCache;
import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Modelclass;
import de.jare.harness.model.Quest;
import de.jare.harness.model.Slot;
import java.util.ArrayList;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for the askai.decompose calculator (count &gt; 1 → {@link LoopSplit} loop action with
 * the N atomic sub-prompts, §15.6) and for the engine's split handling: the N subtasks are
 * created as task starts, one loop runs per subtask with the inherited counter state, and after
 * their return the consumption deltas (slot + N children) are summed into the current output.
 *
 * @author Janusch Rentenatus
 */
public class DecomposeSplitNGTest {

    private static HarnessModel modelWithAllQuests() {
        HarnessModel model = new HarnessModel().name("decompose-test").abortLimits(50, 5, 3, 10);
        for (Kind kind : Kind.values()) {
            model.addQuest(new Quest(kind).addSlot(new Slot(kind.name().toLowerCase() + ".base")
                    .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC))));
        }
        model.addModelclass(new Modelclass("ollama", "nemotron3:33b", "investigation", 33, 1.5, true));
        return model;
    }

    private static CalcContext ctxFor(HarnessModel model, DefaultCalculatorFactory factory, WorkCache cache, String userPrompt) {
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        TaskData root = instance.newRootTask(instance.nextTaskId(), "AskAi", userPrompt);
        instance.setRoot(root);
        HarnessEngine engine = new HarnessEngine(model, instance, factory);
        engine.setWorkCache(cache);
        CalculatorNode node = CalculatorNode.atomic(CalculatorNode.IMPL_AI).calculator("askai.decompose")
                .params(java.util.Map.of("model", "nemotron3:33b"));
        return new CalcContext(engine, instance, model, root, Kind.SPLIT, "askai.base",
                node, engine.getLimits(), List.of("AUFTRAG:inline:task\n" + userPrompt));
    }

    private static DefaultCalculatorFactory factoryWithDecomposeAnswer(HarnessModel model, List<String> sentSystem, String answer) {
        DefaultCalculatorFactory factory = new DefaultCalculatorFactory(model);
        Modelclass mc = model.modelclass("investigation");
        factory.registerProvider("ollama", new de.jare.harness.impl.ModelProvider() {
            @Override public Modelclass modelclass() { return mc; }
            @Override public boolean isAvailable() { return true; }
            @Override public String complete(List<String> segments) {
                sentSystem.add(segments.get(0));
                return answer;
            }
        });
        return factory;
    }

    @Test
    public void testDecomposeReturnsLoopSplitWithSubprompts() {
        HarnessModel model = modelWithAllQuests();
        List<String> sentSystem = new ArrayList<>();
        DefaultCalculatorFactory factory = factoryWithDecomposeAnswer(model, sentSystem,
                "{\"status\":\"DONE\",\"decision\":\"decomposed\",\"score\":100,"
                        + "\"payload\":{\"quests\":[\"wie viel ist 2*4?\",\"wie viel ist 7*8?\"]}}");
        WorkCache cache = new WorkCache();
        cache.put("count", "2");
        CalcContext ctx = ctxFor(model, factory, cache, "wie viel ist 2*4 und wie viel 7*8 ?");
        ctx.setWorkCache(cache);

        HarnessCalculator calc = factory.createAtomic(ctx.calculatorNode(), Kind.SPLIT);
        Assert.assertTrue(calc instanceof AskAiDecomposeCalculator);
        CalcResult r = calc.calculate(ctx);

        Assert.assertEquals(r.getDecision(), "split");
        Assert.assertEquals(r.getStatus(), Status.DONE);
        Assert.assertTrue(r.getLoopAction() instanceof LoopSplit, "result steers via LoopSplit loop action");
        List<String> prompts = ((LoopSplit) r.getLoopAction()).prompts();
        Assert.assertEquals(prompts, List.of("wie viel ist 2*4?", "wie viel ist 7*8?"));
        Assert.assertEquals(r.getPayload().get("count"), "2");
        Assert.assertTrue(r.getPayload().get("quests").contains("wie viel ist 2*4?"));
        Assert.assertTrue(sentSystem.get(0).contains("into 2 atomic quests"),
                "{data.count} was resolved by the PromptFormatter: " + sentSystem.get(0));
    }

    @Test
    public void testDecomposeGuards() {
        HarnessModel model = modelWithAllQuests();
        List<String> sentSystem = new ArrayList<>();
        DefaultCalculatorFactory factory = factoryWithDecomposeAnswer(model, sentSystem, "{}");

        // count == null -> FAILED, no provider call needed
        WorkCache emptyCache = new WorkCache();
        CalcResult r1 = factory.createAtomic(ctxFor(model, factory, emptyCache, "prompt").calculatorNode(), Kind.SPLIT)
                .calculate(ctxFor(model, factory, emptyCache, "prompt"));
        Assert.assertEquals(r1.getStatus(), Status.FAILED);
        Assert.assertEquals(r1.getDecision(), "decompose-nocount");

        // count <= 1 -> INVALID, nothing to decompose
        WorkCache oneCache = new WorkCache();
        oneCache.put("count", "1");
        CalcContext ctx1 = ctxFor(model, factory, oneCache, "prompt");
        ctx1.setWorkCache(oneCache);
        CalcResult r2 = factory.createAtomic(ctx1.calculatorNode(), Kind.SPLIT).calculate(ctx1);
        Assert.assertEquals(r2.getStatus(), Status.INVALID);
        Assert.assertEquals(r2.getDecision(), "nosplit");
    }

    @Test
    public void testEngineSplitCreatesNSubloopsAndAggregatesConsumption() {
        HarnessModel model = modelWithAllQuests();
        // SPLIT calculator: first call (parent) returns LoopSplit with 2 prompts, children get "done"
        DefaultCalculatorFactory factory = new DefaultCalculatorFactory(model);
        List<Kind> calls = new ArrayList<>();
        CalculatorFactory scripted = (node, kind) -> {
            calls.add(kind);
            if (kind == Kind.SPLIT && calls.stream().filter(k -> k == Kind.SPLIT).count() == 1) {
                return new HarnessCalculator() {
                    @Override public Kind kind() { return Kind.SPLIT; }
                    @Override public CalcResult calculate(CalcContext c) {
                        return CalcResult.of(Kind.SPLIT, "split")
                                .loopAction(new LoopSplit(List.of("rechne 2*4", "rechne 7*8")));
                    }
                };
            }
            return new HarnessCalculator() {
                @Override public Kind kind() { return kind; }
                @Override public CalcResult calculate(CalcContext c) {
                    if (kind == Kind.CLOSE) {
                        c.task().close("closed");
                    }
                    return CalcResult.of(kind, "done");
                }
            };
        };

        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        TaskData root = instance.newRootTask(instance.nextTaskId(), "AskAi", "wie viel ist 2*4 und wie viel 7*8 ?");
        instance.setRoot(root);
        HarnessEngine engine = new HarnessEngine(model, instance, scripted);
        WorkCache cache = new WorkCache();
        engine.setWorkCache(cache);
        engine.run();

        // N task starts created from the sub-prompts
        Assert.assertEquals(root.getChildren().size(), 2);
        Assert.assertEquals(root.getChildren().get(0).getTitle(), "rechne 2*4");
        Assert.assertEquals(root.getChildren().get(1).getTitle(), "rechne 7*8");
        // one loop per subtask: WORK ran once per child (2 children) + 0 for the parent (split exits before WORK)
        Assert.assertEquals(calls.stream().filter(k -> k == Kind.WORK).count(), 2L,
                "each of the N subloops ran its own loop");
        // consumption: slot + N deltas summed into the current output (WorkCache)
        Assert.assertEquals(cache.get("subloops"), "2");
        Assert.assertNotNull(cache.get("consumption"), "summed consumption is the current output");
        Assert.assertTrue(cache.get("consumption").startsWith("iterations+"), cache.get("consumption"));
        Assert.assertNotNull(cache.get("consumptionDeltas"));
        Assert.assertTrue(cache.get("consumptionDeltas").contains("child "), cache.get("consumptionDeltas"));
        // the parent's loop left after the N subloops returned -> CLOSE fired on root and children
        Assert.assertTrue(calls.contains(Kind.CLOSE));
        Assert.assertEquals(root.getStatus(), Status.DONE);
    }

    @Test
    public void testPuzzlePropagatesLoopSplitFromMiddleChild() {
        // the SPLIT slot is a puzzle sequence [decompose, printig]: the LoopSplit of the middle
        // child must steer the loop even though the LAST child (printig) returns a plain result
        HarnessModel model = modelWithAllQuests();
        CalculatorNode splitPuzzle = CalculatorNode.puzzle("sequence")
                .addChild(CalculatorNode.atomic(CalculatorNode.IMPL_AI).calculator("askai.decompose")
                        .params(java.util.Map.of("model", "nemotron3:33b")))
                .addChild(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC).calculator("printig"));
        model.questFor(Kind.SPLIT).baseSlot().calculator(splitPuzzle);

        List<String> sentSystem = new ArrayList<>();
        DefaultCalculatorFactory factory = new DefaultCalculatorFactory(model);
        Modelclass mc = model.modelclass("investigation");
        factory.registerProvider("ollama", new de.jare.harness.impl.ModelProvider() {
            @Override public Modelclass modelclass() { return mc; }
            @Override public boolean isAvailable() { return true; }
            @Override public String complete(List<String> segments) {
                sentSystem.add(segments.get(0));
                return "{\"status\":\"DONE\",\"decision\":\"decomposed\",\"score\":100,"
                        + "\"payload\":{\"quests\":[\"rechne 2*4\",\"rechne 7*8\"]}}";
            }
        });

        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        TaskData root = instance.newRootTask(instance.nextTaskId(), "AskAi", "wie viel ist 2*4 und wie viel 7*8 ?");
        instance.setRoot(root);
        HarnessEngine engine = new HarnessEngine(model, instance, factory);
        WorkCache cache = new WorkCache();
        cache.put("count", "2");   // written by scoring.countQuests in the real sequence
        engine.setWorkCache(cache);
        engine.run();

        Assert.assertEquals(root.getChildren().size(), 2, "LoopSplit of the puzzle's middle child steered the loop");
        Assert.assertEquals(root.getChildren().get(0).getTitle(), "rechne 2*4");
        Assert.assertEquals(root.getChildren().get(1).getTitle(), "rechne 7*8");
        Assert.assertEquals(cache.get("subloops"), "2");
        Assert.assertNotNull(cache.get("consumption"));
    }
}
