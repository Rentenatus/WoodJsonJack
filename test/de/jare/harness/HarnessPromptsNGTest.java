/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.prompts.PromptAssembler;
import de.jare.harness.prompts.PromptSegment;
import de.jare.harness.prompts.PromptSegment.Category;
import de.jare.harness.prompts.RelevanceRegistry;
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
}
