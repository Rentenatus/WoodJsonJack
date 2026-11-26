/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.TaskCommands;
import de.jare.harness.impl.DefaultCalculatorFactory;
import de.jare.harness.main.actions.LoopSplit;
import de.jare.harness.main.calculator.AskAiDecomposeCalculator;
import de.jare.harness.memory.WorkCache;
import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Loop;
import de.jare.harness.model.LoopItem;
import de.jare.harness.model.Modelclass;
import de.jare.harness.model.Quest;
import de.jare.harness.model.Slot;
import de.jare.harness.model.Trigger;
import java.util.ArrayList;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for the askai.decompose calculator (count &gt; 1 → {@link LoopSplit} loop action with
 * the N atomic sub-prompts, §15.6) and for the engine's split handling: the N subtasks are
 * created as task starts, one subloop ({@code "sub"}, not a fresh {@code "main"}) runs per
 * subtask with the inherited counter state, and after their return the consumption deltas
 * (slot + N children) are summed into the current output.
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
                .params(java.util.Map.of("model", "nemotron3:33b", "systemPrompt", "decompose into {data.count} quests"));
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
        LoopSplit split = (LoopSplit) r.getLoopAction();
        List<String> prompts = split.prompts();
        Assert.assertEquals(prompts, List.of("wie viel ist 2*4?", "wie viel ist 7*8?"));
        Assert.assertEquals(split.loopId(), Loop.SUB, "split steers the subloop, not a fresh main loop");
        Assert.assertEquals(r.getPayload().get("count"), "2");
        Assert.assertTrue(r.getPayload().get("quests").contains("wie viel ist 2*4?"));
        Assert.assertTrue(sentSystem.get(0).contains("decompose into 2 quests"),
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
    public void testLoopSplitCarriesSubloopId() {
        Assert.assertEquals(new LoopSplit(List.of("a")).loopId(), Loop.SUB, "default target is the subloop");
        Assert.assertEquals(new LoopSplit(List.of("a"), null).loopId(), Loop.SUB, "null normalizes to sub");
        Assert.assertEquals(new LoopSplit(List.of("a"), " ").loopId(), Loop.SUB, "blank normalizes to sub");
        Assert.assertEquals(new LoopSplit(List.of("a"), "resolve").loopId(), "resolve", "custom subloop id is kept");
    }

    @Test
    public void testEngineSplitCreatesNSubloopsAndAggregatesConsumption() {
        HarnessModel model = modelWithAllQuests();
        // explicit loops: "main" carries a SUMMARIZE marker that "sub" lacks — if the split
        // children ran a fresh main loop, SUMMARIZE would fire per child; via the subloop it never fires
        Loop main = new Loop(Loop.MAIN);
        main.addItem(new LoopItem("abort", Kind.ABORT).trigger(Trigger.START));
        main.addItem(new LoopItem("split", Kind.SPLIT).trigger(Trigger.EXECUTE));
        main.addItem(new LoopItem("summarize", Kind.SUMMARIZE).trigger(Trigger.EXECUTE));
        main.addItem(new LoopItem("close", Kind.CLOSE).trigger(Trigger.CLOSE));
        model.addLoop(main);
        Loop sub = new Loop(Loop.SUB);
        sub.addItem(new LoopItem("work", Kind.WORK).trigger(Trigger.EXECUTE));
        sub.addItem(new LoopItem("close", Kind.CLOSE).trigger(Trigger.CLOSE));
        model.addLoop(sub);
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
                        c.commands().apply(new TaskCommands.CloseTask("closed"));   // §18
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
        // one subloop per subtask: WORK ran once per child via the "sub" loop (2 children)
        Assert.assertEquals(calls.stream().filter(k -> k == Kind.WORK).count(), 2L,
                "each of the N subloops ran the sub loop");
        // the children did NOT run a fresh main loop: the main-only SUMMARIZE marker fires
        // exactly once - the parent's own summary AFTER the N subloops returned (§15.6,
        // legacy splitInto semantics preserved in handleSplit), never per child
        Assert.assertEquals(calls.stream().filter(k -> k == Kind.SUMMARIZE).count(), 1L,
                "parent summarizes once after the subloops; split children run the subloop, not a new main loop");
        Assert.assertEquals(cache.get("subloopId"), "sub");
        // consumption: slot + N deltas summed into the current output (WorkCache)
        Assert.assertEquals(cache.get("subloops"), "2");
        Assert.assertNotNull(cache.get("consumption"), "summed consumption is the current output");
        Assert.assertTrue(cache.get("consumption").startsWith("iterations+"), cache.get("consumption"));
        Assert.assertNotNull(cache.get("consumptionDeltas"));
        Assert.assertTrue(cache.get("consumptionDeltas").contains("child "), cache.get("consumptionDeltas"));
        // the parent's loop left after the N subloops returned -> CLOSE fired on root (main) + both children (sub)
        Assert.assertEquals(calls.stream().filter(k -> k == Kind.CLOSE).count(), 3L);
        Assert.assertEquals(root.getStatus(), Status.DONE);
    }

    @Test
    public void testPuzzlePropagatesLoopSplitFromMiddleChild() {
        // the SPLIT slot is a puzzle sequence [decompose, printig]: the LoopSplit of the middle
        // child must steer the loop even though the LAST child (printig) returns a plain result
        HarnessModel model = modelWithAllQuests();
        CalculatorNode splitPuzzle = CalculatorNode.puzzle("sequence")
                .addChild(CalculatorNode.atomic(CalculatorNode.IMPL_AI).calculator("askai.decompose")
                        .params(java.util.Map.of("model", "nemotron3:33b", "systemPrompt", "decompose into {data.count} quests")))
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

    /**
     * §3 data-movement projection: an engine run appends one "call" node per executed atomic
     * calculator to the task's "calls" array - input, payload and loopAction included.
     */
    @Test
    public void testEngineRunProjectsAtomicCalculatorCallsIntoTaskTree() {
        HarnessModel model = modelWithAllQuests();
        Loop main = new Loop(Loop.MAIN);
        main.addItem(new LoopItem("work", Kind.WORK).trigger(Trigger.EXECUTE));
        main.addItem(new LoopItem("close", Kind.CLOSE).trigger(Trigger.CLOSE));
        model.addLoop(main);

        DefaultCalculatorFactory factory = new DefaultCalculatorFactory(model);
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        TaskData root = instance.newRootTask(instance.nextTaskId(), "AskAi", "hallo");
        instance.setRoot(root);
        HarnessEngine engine = new HarnessEngine(model, instance, factory);
        engine.setWorkCache(new WorkCache());

        engine.run();

        Assert.assertFalse(root.getCalls().isEmpty(),
                "every atomic calculator step is projected as a call");
        for (TaskData.CalcCall c : root.getCalls()) {
            Assert.assertFalse(c.getLoopActionType().isEmpty(), "each call carries its loop action");
            Assert.assertNotNull(c.getPayload(), "each call carries its payload");
            Assert.assertNotNull(c.getInput(), "each call carries its input prompts");
        }
    }
}
