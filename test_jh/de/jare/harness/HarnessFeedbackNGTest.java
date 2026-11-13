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
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for the feedback loop between runs (CONCEPT_Harness.md §7.1): after CLOSE of the
 * root task, slots whose average score falls below the threshold over enough samples get
 * a swap proposed as a child version in the lineage - never mid-run. Healthy runs propose
 * nothing.
 *
 * @author Janusch Rentenatus
 */
public class HarnessFeedbackNGTest {

    private static HarnessModel modelWithAllQuests() {
        HarnessModel model = new HarnessModel().abortLimits(50, 5, 3, 10);
        model.addModelclass(new Modelclass("ollama", "qwen2.5:7b", "edit", 0, 2.0, true));
        for (Kind kind : Kind.values()) {
            model.addQuest(new Quest(kind).addSlot(new Slot(kind.name().toLowerCase() + ".base")
                    .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC))));
        }
        return model;
    }

    @Test
    public void testHealthyRunProposesNoSwap() {
        HarnessModel model = modelWithAllQuests();
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        instance.newRootTask(instance.nextTaskId(), "RESEARCH", "healthy run");
        HarnessEngine engine = new HarnessEngine(model, instance, new DefaultCalculatorFactory(model));
        HarnessLineage lineage = new HarnessLineage(model);
        engine.setLineage(lineage);
        engine.run();

        List<HarnessLineage.Version> proposals = engine.feedbackLoop(50.0, 1);
        Assert.assertTrue(proposals.isEmpty(), "avg score 80 >= 50: no swap");
        Assert.assertEquals(lineage.getVersions().size(), 1, "lineage stays at the initial version");
    }

    @Test
    public void testPoorScoreProposesSwapAsChildVersion() {
        HarnessModel model = modelWithAllQuests();
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        instance.newRootTask(instance.nextTaskId(), "RESEARCH", "poor run");
        // factory seam: EVALUATE scores only 20 -> below threshold
        DefaultCalculatorFactory base = new DefaultCalculatorFactory(model);
        CalculatorFactory factory = (node, kind) -> {
            if (kind == Kind.EVALUATE) {
                return new HarnessCalculator() {
                    @Override public Kind kind() { return Kind.EVALUATE; }
                    @Override public CalcResult calculate(CalcContext ctx) {
                        return CalcResult.of(Kind.EVALUATE, "scored").score(20).status(Status.DONE);
                    }
                };
            }
            return base.createAtomic(node, kind);
        };
        HarnessEngine engine = new HarnessEngine(model, instance, factory);
        HarnessLineage lineage = new HarnessLineage(model);
        engine.setLineage(lineage);
        engine.run();

        List<HarnessLineage.Version> proposals = engine.feedbackLoop(50.0, 1);
        Assert.assertFalse(proposals.isEmpty(), "avg score 20 < 50: swap must be proposed");
        Assert.assertEquals(lineage.getVersions().size(), 2);
        HarnessLineage.Version child = lineage.current();
        Assert.assertEquals(child.parentId, proposals.get(0).parentId);
        Assert.assertTrue(child.changeDescription.contains("avg score"));
        // the swapped slot now points at the ai alternative
        Assert.assertEquals(lineage.currentModel().questFor(Kind.EVALUATE).baseSlot()
                .getCalculator().getImpl(), "ai");
        Assert.assertEquals(lineage.currentModel().questFor(Kind.EVALUATE).baseSlot()
                .getCalculator().getModelclass(), "edit");
        // the old version is untouched - swap never rewrites history
        Assert.assertEquals(lineage.getVersions().get(0).model.questFor(Kind.EVALUATE).baseSlot()
                .getCalculator().getImpl(), "deterministic");
    }

    @Test
    public void testServicePersistsLineageAcrossRuns() throws Exception {
        Path project = Files.createTempDirectory("harness-feedback");
        HarnessService service = new HarnessService(project.toFile());
        service.runTask("RESEARCH", "first run");
        File lineageFile = new File(project.toFile(), ".wood/harness/lineage.json");
        Assert.assertTrue(lineageFile.exists(), "lineage.json must persist");

        HarnessLineage loaded = HarnessLineage.load(lineageFile);
        Assert.assertEquals(loaded.getVersions().size(), 1, "healthy run: no swap proposed");
        Assert.assertEquals(loaded.currentModel().getName(), "default-harness");
    }
}
