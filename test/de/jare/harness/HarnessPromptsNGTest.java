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
import de.jare.harness.prompts.PromptAssembler;
import de.jare.harness.prompts.PromptSegment;
import de.jare.harness.prompts.PromptSegment.Category;
import de.jare.harness.prompts.RelevanceRegistry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for the prompt list separation with hard budgets (CONCEPT_Harness.md §4):
 * fixed category order, deterministic drop on budget overflow, sticky facts in the
 * first SYSTEM segment, and the deterministic relevance registry with its fallbacks.
 *
 * @author Janusch Rentenatus
 */
public class HarnessPromptsNGTest {

    private static PromptAssembler assembler(int system, int history, int auftrag, int maxTokens) {
        Map<Category, Integer> budgets = new EnumMap<>(Category.class);
        budgets.put(Category.SYSTEM, system);
        budgets.put(Category.SKILL, 1);
        budgets.put(Category.HISTORY, history);
        budgets.put(Category.DATA, 2);
        budgets.put(Category.AUFTRAG, auftrag);
        return new PromptAssembler(budgets, maxTokens, "sticky: facts");
    }

    @Test
    public void testFixedOrderIsEnforcedRegardlessOfInputOrder() {
        List<PromptSegment> in = List.of(
                PromptSegment.of(Category.AUFTRAG, "a", "task"),
                PromptSegment.of(Category.SYSTEM, "s", "sys"),
                PromptSegment.of(Category.DATA, "d", "data"));
        List<PromptSegment> out = assembler(2, 3, 1, 4000).assemble(in);
        Assert.assertEquals(out.stream().map(PromptSegment::cat).toList(),
                List.of(Category.SYSTEM, Category.DATA, Category.AUFTRAG));
    }

    @Test
    public void testBudgetOverflowDropsDeterministically() {
        List<PromptSegment> in = List.of(
                PromptSegment.of(Category.SYSTEM, "s1", "one"),
                PromptSegment.of(Category.SYSTEM, "s2", "two"),
                PromptSegment.of(Category.SYSTEM, "s3", "three"),
                PromptSegment.of(Category.AUFTRAG, "a", "task"));
        List<PromptSegment> out = assembler(2, 3, 1, 4000).assemble(in);
        Assert.assertEquals(out.stream().map(PromptSegment::ref).toList(),
                List.of("s1", "s2", "a"), "third SYSTEM segment must be dropped, AUFTRAG must survive");
    }

    @Test
    public void testTotalTokenDeckelDropsToo() {
        // 4 chars/token: each segment here is ~2500 tokens; total deckel 4000 keeps only one
        List<PromptSegment> in = List.of(
                PromptSegment.of(Category.HISTORY, "h1", "x".repeat(10000)),
                PromptSegment.of(Category.HISTORY, "h2", "y".repeat(10000)),
                PromptSegment.of(Category.AUFTRAG, "a", "task"));
        List<PromptSegment> out = assembler(2, 3, 1, 4000).assemble(in);
        Assert.assertEquals(out.stream().map(PromptSegment::ref).toList(), List.of("h1", "a"));
    }

    @Test
    public void testStickyFactsRideInFirstSystemSegment() {
        List<PromptSegment> out = assembler(2, 3, 1, 4000).assemble(
                List.of(PromptSegment.of(Category.SYSTEM, "s", "sys"),
                        PromptSegment.of(Category.AUFTRAG, "a", "task")));
        Assert.assertTrue(out.get(0).text().contains("sticky: facts"));
        Assert.assertFalse(out.get(out.size() - 1).text().contains("sticky: facts"));
    }

    @Test
    public void testRelevanceRegistryFallbacks() {
        RelevanceRegistry registry = new RelevanceRegistry(List.of("prompts/system/orchestrator.v1.md"));
        registry.register("RESEARCH", -1, Kind.WORK, "*", List.of("prompts/system/research.v2.md"));

        // exact taskType fallback hits
        List<PromptSegment> segs = registry.resolve("RESEARCH", 0, Kind.WORK, "1.0.0", "do it");
        Assert.assertTrue(segs.stream().anyMatch(s -> s.ref().equals("prompts/system/research.v2.md")));
        // AUFTRAG always present with the task text
        PromptSegment auftrag = segs.stream().filter(s -> s.cat() == Category.AUFTRAG).findFirst().orElseThrow();
        Assert.assertEquals(auftrag.text(), "do it");
        // unknown taskType falls back to default refs
        List<PromptSegment> def = registry.resolve("UNKNOWN", 3, Kind.EVALUATE, "1.0.0", "x");
        Assert.assertTrue(def.stream().anyMatch(s -> s.ref().equals("prompts/system/orchestrator.v1.md")));
    }

    @Test
    public void testHistorySegmentsAreCondensedFromCalculatorLog() {
        HarnessModel model = new HarnessModel().abortLimits(50, 5, 3, 10);
        model.getPromptBudgets().put("SYSTEM", 2);
        model.getPromptBudgets().put("SKILL", 1);
        model.getPromptBudgets().put("HISTORY", 3);
        model.getPromptBudgets().put("DATA", 2);
        model.getPromptBudgets().put("AUFTRAG", 1);
        for (Kind kind : Kind.values()) {
            model.addQuest(new Quest(kind).addSlot(new Slot(kind.name().toLowerCase() + ".base")
                    .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC))));
        }
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        instance.setRoot(new TaskNode(instance.nextTaskId(), "RESEARCH", "history check"));

        List<List<String>> workPrompts = new ArrayList<>();
        DefaultCalculatorFactory base = new DefaultCalculatorFactory(model);
        CalculatorFactory capturing = (node, kind) -> {
            HarnessCalculator inner = base.createAtomic(node, kind);
            if (kind != Kind.WORK) {
                return inner;
            }
            return new HarnessCalculator() {
                @Override public Kind kind() { return Kind.WORK; }
                @Override public CalcResult calculate(CalcContext ctx) {
                    workPrompts.add(ctx.prompts());
                    return inner.calculate(ctx);
                }
            };
        };
        new HarnessEngine(model, instance, capturing).run();

        Assert.assertFalse(workPrompts.isEmpty(), "WORK must have run");
        List<String> prompts = workPrompts.get(0);
        List<String> history = prompts.stream()
                .filter(p -> p.startsWith("HISTORY:")).toList();
        Assert.assertFalse(history.isEmpty(), "HISTORY segments condensed from the calculator log (§4)");
        Assert.assertTrue(history.size() <= 3, "max 3 HISTORY segments (shared budget, §4): " + history);
        Assert.assertTrue(history.stream().anyMatch(p -> p.contains("->")),
                "condensed one-line summaries, never raw history: " + history);
    }
}
