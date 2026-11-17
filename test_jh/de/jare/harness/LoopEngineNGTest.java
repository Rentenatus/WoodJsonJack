/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.main.actions.LoopBreak;
import de.jare.harness.main.actions.LoopRestart;
import de.jare.harness.main.actions.LoopThrowError;
import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Loop;
import de.jare.harness.model.LoopItem;
import de.jare.harness.model.Quest;
import de.jare.harness.model.Slot;
import de.jare.harness.model.Trigger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests of the configurable loop execution in {@link HarnessEngine} (CONCEPT_Harness.md
 * §15.6, docs/working/DESIGN_Loops.md §3): START fires once (also across RESTART),
 * EXECUTE order, BREAK leaves the loop with CLOSE in finally, subloop items and the
 * depth guard, CATCH on DOMAIN errors (incl. unwind one level up), TECHNICAL bypasses
 * CATCH and fails hard, and a raw Throwable never crosses the loop boundary.
 *
 * @author Janusch Rentenatus
 */
public class LoopEngineNGTest {

    /** Scripted factory: per kind a queue of calculators, consumed per call; records the call order. */
    private static final class ScriptedFactory implements CalculatorFactory {
        final Map<Kind, Queue<HarnessCalculator>> scripts = new java.util.HashMap<>();
        final List<Kind> calls = new ArrayList<>();

        ScriptedFactory on(Kind kind, HarnessCalculator... calcs) {
            Queue<HarnessCalculator> q = new ArrayDeque<>(List.of(calcs));
            scripts.merge(kind, q, (a, b) -> {
                a.addAll(b);
                return a;
            });
            return this;
        }

        @Override
        public HarnessCalculator createAtomic(CalculatorNode node, Kind kind) {
            calls.add(kind);
            Queue<HarnessCalculator> q = scripts.get(kind);
            if (q != null && !q.isEmpty()) {
                return q.poll();
            }
            return calc(kind, ctx -> CalcResult.of(kind, "done"));
        }

        long count(Kind kind) {
            return calls.stream().filter(k -> k == kind).count();
        }
    }

    private static HarnessCalculator calc(Kind kind, java.util.function.Function<CalcContext, CalcResult> fn) {
        return new HarnessCalculator() {
            @Override public Kind kind() { return kind; }
            @Override public CalcResult calculate(CalcContext ctx) { return fn.apply(ctx); }
        };
    }

    private static HarnessModel modelWithLoop(Loop... loops) {
        HarnessModel model = new HarnessModel().name("loop-engine-test").abortLimits(50, 4, 3, 10);
        for (Kind kind : Kind.values()) {
            model.addQuest(new Quest(kind).addSlot(new Slot(kind.name().toLowerCase() + ".base")
                    .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC))));
        }
        for (Loop l : loops) {
            model.addLoop(l);
        }
        return model;
    }

    private static HarnessEngine engineFor(HarnessModel model, ScriptedFactory factory) {
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        instance.newRootTask(instance.nextTaskId(), "RESEARCH", "loop test");
        return new HarnessEngine(model, instance, factory);
    }

    private static Loop mainLoop(LoopItem... items) {
        Loop main = new Loop(Loop.MAIN);
        for (LoopItem i : items) {
            main.addItem(i);
        }
        return main;
    }

    @Test
    public void testStartFiresOnceAcrossRestartAndCloseFiresOnce() {
        ScriptedFactory factory = new ScriptedFactory()
                .on(Kind.WORK,
                        calc(Kind.WORK, ctx -> CalcResult.of(Kind.WORK, "worked").loopAction(new LoopRestart())),
                        calc(Kind.WORK, ctx -> CalcResult.of(Kind.WORK, "worked")));
        HarnessModel model = modelWithLoop(mainLoop(
                new LoopItem("abort", Kind.ABORT).trigger(Trigger.START),
                new LoopItem("work", Kind.WORK),
                new LoopItem("eval", Kind.EVALUATE),
                new LoopItem("close", Kind.CLOSE).trigger(Trigger.CLOSE)));
        HarnessEngine engine = engineFor(model, factory);
        engine.run();

        Assert.assertEquals(factory.count(Kind.ABORT), 1, "START must fire exactly once, not per RESTART");
        Assert.assertEquals(factory.count(Kind.WORK), 2, "RESTART must repeat the EXECUTE items");
        Assert.assertEquals(factory.count(Kind.EVALUATE), 1);
        Assert.assertEquals(factory.count(Kind.CLOSE), 1, "CLOSE must fire exactly once");
        // order: START first, CLOSE last
        Assert.assertEquals(factory.calls.get(0), Kind.ABORT);
        Assert.assertEquals(factory.calls.get(factory.calls.size() - 1), Kind.CLOSE);
    }

    @Test
    public void testBreakLeavesLoopAndCloseStillFires() {
        ScriptedFactory factory = new ScriptedFactory()
                .on(Kind.WORK, calc(Kind.WORK, ctx -> CalcResult.of(Kind.WORK, "worked").loopAction(new LoopBreak())));
        HarnessModel model = modelWithLoop(mainLoop(
                new LoopItem("work", Kind.WORK),
                new LoopItem("eval", Kind.EVALUATE),
                new LoopItem("close", Kind.CLOSE).trigger(Trigger.CLOSE)));
        HarnessEngine engine = engineFor(model, factory);
        engine.run();

        Assert.assertEquals(factory.count(Kind.WORK), 1);
        Assert.assertEquals(factory.count(Kind.EVALUATE), 0, "BREAK must skip the remaining items");
        Assert.assertEquals(factory.count(Kind.CLOSE), 1, "CLOSE fires in finally, also after BREAK");
    }

    @Test
    public void testSubLoopItemRunsNestedLoop() {
        ScriptedFactory factory = new ScriptedFactory();
        Loop resolve = new Loop("resolve");
        resolve.addItem(new LoopItem("rwork", Kind.WORK));
        HarnessModel model = modelWithLoop(
                mainLoop(new LoopItem("sub", Kind.WORK).loop("resolve")),
                resolve);
        HarnessEngine engine = engineFor(model, factory);
        engine.run();

        Assert.assertEquals(factory.count(Kind.WORK), 1, "the subloop's WORK item runs instead of the quest");
    }

    @Test
    public void testSelfRecursionHitsDepthGuard() {
        ScriptedFactory factory = new ScriptedFactory();
        Loop rec = new Loop(Loop.MAIN);
        rec.addItem(new LoopItem("again", Kind.WORK).loop(Loop.MAIN)); // calls itself
        HarnessModel model = modelWithLoop(rec);
        HarnessEngine engine = engineFor(model, factory);
        engine.run();

        Assert.assertEquals(engine.getInstance().getRoot().getStatus(), Status.ABORTED,
                "unbounded subloop recursion must hit the maxDepth guard");
    }

    @Test
    public void testCatchFiresOnDomainError() {
        ScriptedFactory factory = new ScriptedFactory()
                .on(Kind.WORK, calc(Kind.WORK, ctx -> CalcResult.of(Kind.WORK, "throw")
                        .status(Status.FAILED)
                        .loopAction(new LoopThrowError(ErrorSignal.domain("no count", "test")))));
        HarnessModel model = modelWithLoop(mainLoop(
                new LoopItem("work", Kind.WORK),
                new LoopItem("eval", Kind.EVALUATE),
                new LoopItem("err", Kind.ABORT).trigger(Trigger.CATCH),
                new LoopItem("close", Kind.CLOSE).trigger(Trigger.CLOSE)));
        HarnessEngine engine = engineFor(model, factory);
        engine.run();

        Assert.assertEquals(factory.count(Kind.WORK), 1);
        Assert.assertEquals(factory.count(Kind.EVALUATE), 0, "THROW skips the remaining EXECUTE items");
        Assert.assertEquals(factory.count(Kind.ABORT), 1, "CATCH fires on DOMAIN errors");
        Assert.assertEquals(factory.count(Kind.CLOSE), 1, "CLOSE fires also after CATCH");
        Assert.assertEquals(engine.getInstance().getRoot().getStatus(), Status.FAILED);
    }

    @Test
    public void testTechnicalErrorBypassesCatchAndFailsHard() {
        ScriptedFactory factory = new ScriptedFactory()
                .on(Kind.WORK, calc(Kind.WORK, ctx -> CalcResult.of(Kind.WORK, "throw")
                        .status(Status.FAILED)
                        .loopAction(new LoopThrowError(ErrorSignal.technical("db gone", "test")))));
        HarnessModel model = modelWithLoop(mainLoop(
                new LoopItem("work", Kind.WORK),
                new LoopItem("err", Kind.ABORT).trigger(Trigger.CATCH),
                new LoopItem("close", Kind.CLOSE).trigger(Trigger.CLOSE)));
        HarnessEngine engine = engineFor(model, factory);
        engine.run();

        Assert.assertEquals(factory.count(Kind.ABORT), 0, "TECHNICAL must bypass CATCH");
        Assert.assertEquals(factory.count(Kind.CLOSE), 1, "CLOSE still fires (finally)");
        Assert.assertEquals(engine.getInstance().getRoot().getStatus(), Status.FAILED);
    }

    @Test
    public void testRawThrowableBecomesTechnicalSignal() {
        ScriptedFactory factory = new ScriptedFactory()
                .on(Kind.WORK, calc(Kind.WORK, ctx -> {
                    throw new NullPointerException("boom");
                }));
        HarnessModel model = modelWithLoop(mainLoop(
                new LoopItem("work", Kind.WORK),
                new LoopItem("err", Kind.ABORT).trigger(Trigger.CATCH),
                new LoopItem("close", Kind.CLOSE).trigger(Trigger.CLOSE)));
        HarnessEngine engine = engineFor(model, factory);
        engine.run(); // must not throw

        Assert.assertEquals(factory.count(Kind.ABORT), 0, "a raw Throwable is TECHNICAL - CATCH bypassed");
        Assert.assertEquals(factory.count(Kind.CLOSE), 1);
        Assert.assertEquals(engine.getInstance().getRoot().getStatus(), Status.FAILED);
    }

    @Test
    public void testDomainThrowInSubLoopUnwindsToCallerCatch() {
        ScriptedFactory factory = new ScriptedFactory()
                .on(Kind.WORK, calc(Kind.WORK, ctx -> CalcResult.of(Kind.WORK, "throw")
                        .status(Status.FAILED)
                        .loopAction(new LoopThrowError(ErrorSignal.domain("sub failed", "test")))));
        Loop sub = new Loop("sub"); // no CATCH of its own, but its own CLOSE
        sub.addItem(new LoopItem("work", Kind.WORK));
        sub.addItem(new LoopItem("close", Kind.CLOSE).trigger(Trigger.CLOSE));
        HarnessModel model = modelWithLoop(
                mainLoop(
                        new LoopItem("sub", Kind.WORK).loop("sub"),
                        new LoopItem("err", Kind.ABORT).trigger(Trigger.CATCH),
                        new LoopItem("close", Kind.CLOSE).trigger(Trigger.CLOSE)),
                sub);
        HarnessEngine engine = engineFor(model, factory);
        engine.run();

        Assert.assertEquals(factory.count(Kind.WORK), 1);
        Assert.assertEquals(factory.count(Kind.ABORT), 1,
                "DOMAIN throw in a subloop without CATCH must reach the caller loop's CATCH");
        Assert.assertEquals(factory.count(Kind.CLOSE), 2, "CLOSE fires in the subloop and in main");
    }

    @Test
    public void testProgrammaticModelWithoutLoopsUsesLegacyDefault() {
        ScriptedFactory factory = new ScriptedFactory();
        HarnessModel model = modelWithLoop(); // no loops at all
        HarnessEngine engine = engineFor(model, factory);
        engine.run();

        TaskData root = engine.getInstance().getRoot();
        Assert.assertEquals(root.getStatus(), Status.DONE);
        Assert.assertNotNull(model.getLoop(Loop.MAIN), "the default main loop is materialized");
        // main START fires GUARD + SKILL exactly once (§15.6), then the legacy EXECUTE order:
        // SPLIT, COMPLETE, WORK, EVALUATE, RAG_STORE, CHECK, SUMMARIZE
        Assert.assertEquals(factory.calls.get(0), Kind.GUARD);
        Assert.assertEquals(factory.calls.get(1), Kind.SKILL);
        Assert.assertEquals(factory.count(Kind.GUARD), 1);
        Assert.assertEquals(factory.count(Kind.SKILL), 1);
        Assert.assertEquals(factory.count(Kind.ABORT), 0, "ABORT is no longer at main START (CATCH only)");
        Assert.assertTrue(factory.calls.indexOf(Kind.WORK) < factory.calls.indexOf(Kind.EVALUATE));
        Assert.assertTrue(factory.calls.indexOf(Kind.EVALUATE) < factory.calls.indexOf(Kind.CHECK));
        Assert.assertEquals(factory.calls.get(factory.calls.size() - 2), Kind.CLOSE, "CLOSE before RAG_PRUNE");
        Assert.assertEquals(factory.calls.get(factory.calls.size() - 1), Kind.RAG_PRUNE);
    }

    @Test
    public void testCallLogCarriesLoopContextAndMetricsHaveLoopKeys() {
        ScriptedFactory factory = new ScriptedFactory();
        HarnessModel model = modelWithLoop(mainLoop(
                new LoopItem("work", Kind.WORK),
                new LoopItem("close", Kind.CLOSE).trigger(Trigger.CLOSE)));
        HarnessEngine engine = engineFor(model, factory);
        engine.run();

        // every call log entry carries the loop context of the fired item
        Assert.assertFalse(engine.getInstance().getCallLog().isEmpty());
        Assert.assertTrue(engine.getInstance().getCallLog().stream()
                .allMatch(e -> "main".equals(e.getLoopId()) && e.getLoopItemId() != null && e.getTrigger() != null));
        // loop-structure keys are aggregated in the metrics next to the slot keys
        Assert.assertFalse(engine.getInstance().getMetrics()
                .stats("loop:main/work@EXECUTE", 1).isEmpty(), "loop item measurable via loop: key");
        Assert.assertFalse(engine.getInstance().getMetrics()
                .stats("loop:main/close@CLOSE", 1).isEmpty());
        // JSON round trip of an entry keeps the loop context
        CallLogEntry back = CallLogEntry.fromJson(engine.getInstance().getCallLog().get(0).toJson());
        Assert.assertEquals(back.getLoopId(), "main");
        Assert.assertNotNull(back.getLoopItemId());
        Assert.assertNotNull(back.getTrigger());
    }

    @Test
    public void testParamErrorEndToEndIsCountedInMetrics() {
        // SPLIT slot uses the named "printig" calculator with an unknown param -> PARAMETER
        // error -> the call entry carries paramError and the metrics count it per slot@param
        ScriptedFactory factory = new ScriptedFactory();
        HarnessModel model = modelWithLoop(mainLoop(
                new LoopItem("split", Kind.SPLIT),
                new LoopItem("close", Kind.CLOSE).trigger(Trigger.CLOSE)));
        model.questFor(Kind.SPLIT).baseSlot().calculator(
                de.jare.harness.model.CalculatorNode.atomic(de.jare.harness.model.CalculatorNode.IMPL_DETERMINISTIC)
                        .calculator("printig").params(java.util.Map.of("bogus", "1")));
        // named calculator wins over the scripted factory: use a real DefaultCalculatorFactory
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        instance.newRootTask(instance.nextTaskId(), "RESEARCH", "param error run");
        HarnessEngine engine = new HarnessEngine(model, instance, new de.jare.harness.impl.DefaultCalculatorFactory(model));
        engine.run();

        Assert.assertTrue(engine.getInstance().getCallLog().stream()
                .anyMatch(e -> "bogus".equals(e.getParamError())), "entry carries the offending param key");
        Assert.assertEquals(engine.getInstance().getMetrics().stats("split.base", 1)
                .get(de.jare.harness.metrics.MetricsStore.Category.PARAM_ERROR).getCount(), 1L);
        Assert.assertEquals(engine.getInstance().getMetrics().stats("split.base@bogus", 1)
                .get(de.jare.harness.metrics.MetricsStore.Category.PARAM_ERROR).getCount(), 1L);
    }
}
