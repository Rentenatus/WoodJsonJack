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
import de.jare.harness.model.Modelclass;
import de.jare.harness.model.Quest;
import de.jare.harness.model.Slot;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for the evolution step (CONCEPT_Harness.md §7.2): three candidates of an
 * evolvable quest are measured in parallel over a sample window; the winner must beat
 * the base by +15 avg score AND halve the fail rate; the selection is committed as a
 * child lineage version. A candidate that does not clear the threshold loses.
 *
 * @author Janusch Rentenatus
 */
public class HarnessEvolutionNGTest {

    private static HarnessModel evolvableModel() {
        HarnessModel model = new HarnessModel().abortLimits(50, 5, 3, 10);
        model.addModelclass("edit", new Modelclass("ollama", "qwen2.5:7b", 2.0, true));
        for (Kind kind : Kind.values()) {
            if (kind != Kind.WORK) {
                model.addQuest(new Quest(kind).addSlot(new Slot(kind.name().toLowerCase() + ".base")
                        .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC))));
            }
        }
        Quest work = new Quest(Kind.WORK).evolvable(true);
        work.addSlot(new Slot("work.base").calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC)));
        work.addSlot(new Slot("work.m1").mutation("Mutation-Resolve-01")
                .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC)));
        work.addSlot(new Slot("work.m2").mutation("Mutation-Resolve-02")
                .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC)));
        model.addQuest(work);
        return model;
    }

    private static List<String[]> samples() {
        return List.of(
                new String[]{"RESEARCH", "sample one"},
                new String[]{"RESEARCH", "sample two"},
                new String[]{"RESEARCH", "sample three"});
    }

    @Test
    public void testWinnerMustClearScoreDeltaAndFailRate() {
        // all three candidates behave identically (deterministic, score 80) -> base stays
        HarnessModel model = evolvableModel();
        EvolutionRunner runner = new EvolutionRunner(model, new DefaultCalculatorFactory(model));
        EvolutionRunner.Selection sel = runner.run(Kind.WORK, samples());
        Assert.assertNotNull(sel);
        Assert.assertFalse(sel.swapped(), "no candidate clears +15: base stays winner");
        Assert.assertTrue(sel.winner().slot().isBase());
        Assert.assertEquals(sel.losers().size(), 2);
    }

    @Test
    public void testClearWinnerIsSelectedAndCommitted() {
        HarnessModel model = evolvableModel();
        model.addModelclass("edit", new Modelclass("ollama", "qwen2.5:7b", 2.0, true));
        // candidate m1 is a puzzle whose EVALUATE-equivalent path scores high: use a custom
        // factory where the m1 slot's WORK produces score 100 via EVALUATE override is not
        // slot-specific, so instead lower the base: base gets a failing CHECK loop -> ABORTED
        // candidate differentiation via a tool marker on the WORK node: m1 carries "best"
        // and its WORK call scores 100, the others carry no WORK score
        model.questFor(Kind.WORK).getSlots().get(1).getCalculator().tool("best");
        DefaultCalculatorFactory base = new DefaultCalculatorFactory(model);
        CalculatorFactory factory = (node, kind) -> {
            if (kind == Kind.WORK && "best".equals(node.getTool())) {
                return new HarnessCalculator() {
                    @Override public Kind kind() { return Kind.WORK; }
                    @Override public CalcResult calculate(CalcContext ctx) {
                        return CalcResult.of(Kind.WORK, "worked").score(100).status(Status.DONE);
                    }
                };
            }
            return base.createAtomic(node, kind);
        };
        EvolutionRunner runner = new EvolutionRunner(model, factory);
        EvolutionRunner.Selection sel = runner.run(Kind.WORK, samples());
        Assert.assertTrue(sel.swapped(), "m1 (100) beats base (40) by +15 -> swap");
        Assert.assertEquals(sel.winner().slot().getId(), "work.m1");
        Assert.assertEquals(sel.winner().avgScore(), 100.0, 1e-9);
        Assert.assertEquals(sel.losers().size(), 2);

        HarnessLineage lineage = new HarnessLineage(model);
        HarnessLineage.Version child = runner.commit(lineage, Kind.WORK, sel);
        Assert.assertEquals(lineage.getVersions().size(), 2);
        Assert.assertEquals(child.parentId, lineage.getVersions().get(0).versionId);
        Assert.assertEquals(lineage.currentModel().questFor(Kind.WORK).baseSlot().getId(), "work.base");
    }

    @Test
    public void testNonEvolvableQuestReturnsNull() {
        HarnessModel model = evolvableModel();
        EvolutionRunner runner = new EvolutionRunner(model, new DefaultCalculatorFactory(model));
        Assert.assertNull(runner.run(Kind.ABORT, samples()), "ABORT quest is not evolvable");
    }
}
