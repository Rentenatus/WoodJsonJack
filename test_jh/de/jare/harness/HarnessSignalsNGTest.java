/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.impl.DefaultCalculatorFactory;
import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Quest;
import de.jare.harness.model.Slot;
import de.jare.harness.signals.Signal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for the signal + catch-mapping layer (CONCEPT_Harness.md §13): the on-mapping
 * fires mapped child calculators, the catch-mapping (modelled in the node) handles
 * typed errors before the deterministic fallback, and the LIMIT exception approves
 * at most 2 extra counters with a justification.
 *
 * @author Janusch Rentenatus
 */
public class HarnessSignalsNGTest {

    private static HarnessModel modelWithAllQuests() {
        HarnessModel model = new HarnessModel().abortLimits(50, 5, 3, 10);
        for (Kind kind : Kind.values()) {
            model.addQuest(new Quest(kind).addSlot(new Slot(kind.name().toLowerCase() + ".base")
                    .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC))));
        }
        return model;
    }

    @Test
    public void testOnMappingFiresForSignal() {
        HarnessModel model = modelWithAllQuests();
        AtomicInteger fired = new AtomicInteger();
        // WORK node with on: ANSWER_RECEIVED -> a counting child
        Quest work = model.questFor(Kind.WORK);
        CalculatorNode base = work.baseSlot().getCalculator();
        base.on(Signal.ANSWER_RECEIVED, CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC).tool("count"));

        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        instance.newRootTask(instance.nextTaskId(), "RESEARCH", "signal test");
        HarnessEngine engine = new HarnessEngine(model, instance, new DefaultCalculatorFactory(model) {
            @Override
            public HarnessCalculator createAtomic(CalculatorNode node, Kind kind) {
                if ("count".equals(node.getTool())) {
                    return new HarnessCalculator() {
                        @Override public Kind kind() { return kind; }
                        @Override public CalcResult calculate(CalcContext ctx) {
                            fired.incrementAndGet();
                            return CalcResult.of(kind, "counted");
                        }
                    };
                }
                return super.createAtomic(node, kind);
            }
        });
        engine.run();
        Assert.assertTrue(fired.get() >= 1, "ANSWER_RECEIVED must fire the on-mapped child");
    }

    @Test
    public void testCatchMappingInNodeHandlesTypedError() {
        HarnessModel model = modelWithAllQuests();
        // WORK: ai on unreachable provider, catch MODEL_UNAVAILABLE -> deterministic tool-marked node
        Quest work = model.questFor(Kind.WORK);
        model.addModelclass(new de.jare.harness.model.Modelclass("ollama", "qwen2.5:7b", "edit", 0, 2.0, true));
        CalculatorNode ai = CalculatorNode.atomic(CalculatorNode.IMPL_AI).modelclass("edit");
        ai.catchWith(HarnessException.Type.MODEL_UNAVAILABLE,
                CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC).tool("catch-fallback"));
        work.getSlots().clear();
        work.addSlot(new Slot("work.ai").calculator(ai));

        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        instance.newRootTask(instance.nextTaskId(), "CODE_EDIT", "catch me");
        DefaultCalculatorFactory factory = new DefaultCalculatorFactory(model);
        factory.registerProvider("ollama", new de.jare.harness.impl.ollama.OllamaProvider(
                "http://127.0.0.1:1", model.modelclass("edit")));
        HarnessEngine engine = new HarnessEngine(model, instance, factory);
        engine.run();

        CallLogEntry workCall = instance.getRoot().getCalculatorLog().stream()
                .filter(e -> e.getKind() == Kind.WORK).findFirst().orElseThrow();
        Assert.assertTrue(workCall.getDecision().contains("catch:MODEL_UNAVAILABLE+handled"),
                "modelled catch-mapping must handle it, got: " + workCall.getDecision());
    }

    @Test
    public void testLimitExceptionApprovesAtMostTwoExtraCounters() {
        Limits limits = new Limits(5, 5, 3, 2);
        Assert.assertTrue(limits.approveExtraCounters(2, "begründet: zwei extra Tool-Calls nötig"));
        Assert.assertFalse(limits.approveExtraCounters(1, "noch einer"), "max 2 extra total");
        Assert.assertFalse(limits.approveExtraCounters(3, "zu viel"), "n must be <= 2");
        Assert.assertEquals(limits.getExtraCountersApproved(), 2);
        Assert.assertTrue(limits.getExtraJustification().contains("begründet"));
        // the approved extras extend the tool call budget
        limits.countToolCall();
        limits.countToolCall();
        Assert.assertFalse(limits.isAborted(0), "2 base + 2 extra = 4 allowed");
        limits.countToolCall();
        limits.countToolCall();
        Assert.assertTrue(limits.isAborted(0), "beyond 2+2 the abort fires");
    }

    @Test
    public void testCompactLandsAsMarkedHistorySupplement() {
        HarnessModel model = modelWithAllQuests();
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        TaskData task = TaskData.create(instance.nextTaskId(), "RESEARCH", "compact me");
        instance.setRoot(task);
        HarnessEngine engine = new HarnessEngine(model, instance, new DefaultCalculatorFactory(model));
        CalcContext ctx = new CalcContext(engine, instance, model, task, Kind.WORK, "work.base",
                CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC), engine.getLimits(), List.of());
        String marked = ctx.compact("kurzfassung der bisherigen calls");
        Assert.assertTrue(marked.startsWith("[compacted]"));
        Assert.assertTrue(task.getPromptSupplement().contains("[compacted]"));
    }
}
