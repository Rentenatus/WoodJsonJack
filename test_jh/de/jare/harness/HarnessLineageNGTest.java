/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Quest;
import de.jare.harness.model.Slot;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for the lineage / evolution layer (CONCEPT_Harness.md §7, §8): swap proposes a
 * child version with parent link, evolution requires exactly 3 candidates at an
 * evolvable quest, rollback points at an older version, and everything survives the
 * lineage.json round trip. Plus the global lessons export (slot config only).
 *
 * @author Janusch Rentenatus
 */
public class HarnessLineageNGTest {

    private static HarnessModel baseModel() {
        HarnessModel model = new HarnessModel().name("lineage-test").version("1.0.0");
        for (Kind kind : Kind.values()) {
            model.addQuest(new Quest(kind).addSlot(new Slot(kind.name().toLowerCase() + ".base")
                    .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC))));
        }
        model.questFor(Kind.WORK).evolvable(true);
        return model;
    }

    @Test
    public void testProposeSwapCreatesChildVersionWithParentLink() {
        HarnessLineage lineage = new HarnessLineage(baseModel());
        String rootId = lineage.current().versionId;

        HarnessLineage.Version swap = lineage.proposeSwap("work.base",
                CalculatorNode.atomic(CalculatorNode.IMPL_AI).modelclass("edit"),
                "ai slot beat deterministic in metrics");

        Assert.assertEquals(lineage.getVersions().size(), 2);
        Assert.assertEquals(swap.parentId, rootId);
        Assert.assertEquals(lineage.current(), swap);
        Assert.assertEquals(lineage.currentModel().questFor(Kind.WORK).baseSlot().getCalculator().getImpl(), "ai");
        // the root version is untouched
        Assert.assertEquals(lineage.getVersions().get(0).model.questFor(Kind.WORK).baseSlot()
                .getCalculator().getImpl(), "deterministic");
    }

    @Test
    public void testEvolutionRequiresExactlyThreeCandidates() {
        HarnessLineage lineage = new HarnessLineage(baseModel());
        List<Slot> candidates = List.of(
                new Slot("work.base").calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC)),
                new Slot("work.m1").mutation("Mutation-Resolve-01")
                        .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_AI).modelclass("edit")),
                new Slot("work.m2").mutation("Mutation-Resolve-02")
                        .calculator(CalculatorNode.puzzle("sequence")
                                .addChild(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC).tool("fs.read"))));
        HarnessLineage.Version evo = lineage.requestEvolution(Kind.WORK, candidates, "measured window done");
        Assert.assertEquals(evo.model.questFor(Kind.WORK).getSlots().size(), 3);
        Assert.assertEquals(evo.parentId, lineage.getVersions().get(0).versionId);

        try {
            lineage.requestEvolution(Kind.WORK, candidates.subList(0, 2), "only two");
            Assert.fail("evolution must reject != 3 candidates");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void testRollbackToOlderVersion() {
        HarnessLineage lineage = new HarnessLineage(baseModel());
        String rootId = lineage.current().versionId;
        lineage.proposeSwap("work.base", CalculatorNode.atomic(CalculatorNode.IMPL_AI).modelclass("edit"), "swap");
        Assert.assertEquals(lineage.currentModel().questFor(Kind.WORK).baseSlot().getCalculator().getImpl(), "ai");

        lineage.rollbackTo(rootId);
        Assert.assertEquals(lineage.current().versionId, rootId);
        Assert.assertEquals(lineage.currentModel().questFor(Kind.WORK).baseSlot().getCalculator().getImpl(),
                "deterministic");
    }

    @Test
    public void testLineagePersistenceRoundTrip() throws Exception {
        HarnessLineage lineage = new HarnessLineage(baseModel());
        String rootId = lineage.current().versionId;
        lineage.proposeSwap("work.base", CalculatorNode.atomic(CalculatorNode.IMPL_AI).modelclass("edit"), "swap");
        lineage.requestEvolution(Kind.WORK, List.of(
                new Slot("work.base").calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC)),
                new Slot("work.m1").mutation("M1").calculator(CalculatorNode.atomic(CalculatorNode.IMPL_AI).modelclass("plan")),
                new Slot("work.m2").mutation("M2").calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC))), "evo");

        File tmp = Files.createTempDirectory("harness-lineage").resolve("lineage.json").toFile();
        lineage.save(tmp);
        HarnessLineage loaded = HarnessLineage.load(tmp);

        Assert.assertEquals(loaded.getVersions().size(), 3);
        Assert.assertEquals(loaded.current().parentId, lineage.getVersions().get(1).versionId);
        Assert.assertEquals(loaded.getVersions().get(0).versionId, rootId);
        Assert.assertEquals(loaded.currentModel().questFor(Kind.WORK).getSlots().size(), 3);
        // rollback works on the loaded lineage
        loaded.rollbackTo(rootId);
        Assert.assertEquals(loaded.currentModel().questFor(Kind.WORK).baseSlot().getCalculator().getImpl(),
                "deterministic");
    }

    @Test
    public void testLessonsExportSlotConfigOnly() throws Exception {
        Lessons lessons = new Lessons();
        lessons.add(new Lessons.Lesson("WORK", "work.m1",
                CalculatorNode.atomic(CalculatorNode.IMPL_AI).modelclass("edit"), 15.0, "projhash123", 42L));
        File tmp = Files.createTempDirectory("harness-lessons").resolve("lessons.json").toFile();
        lessons.save(tmp);

        Lessons loaded = Lessons.load(tmp);
        Assert.assertEquals(loaded.getLessons().size(), 1);
        Lessons.Lesson l = loaded.getLessons().get(0);
        Assert.assertEquals(l.kind(), "WORK");
        Assert.assertEquals(l.calculator().getImpl(), "ai");
        Assert.assertEquals(l.calculator().getModelclass(), "edit");
        Assert.assertEquals(l.scoreAdvantage(), 15.0, 1e-9);
    }
}
