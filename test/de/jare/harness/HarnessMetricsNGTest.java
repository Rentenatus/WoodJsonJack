/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.impl.DefaultCalculatorFactory;
import de.jare.harness.metrics.MetricBucket;
import de.jare.harness.metrics.MetricsStore;
import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Quest;
import de.jare.harness.model.Slot;
import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for the aggregated metrics store (CONCEPT_Harness.md §6, §8): the M x 3 shape
 * per slot signature, the EMA fade math, the clone-with-reset inheritance for
 * evolution, and the write-temp-rename persistence of metrics.json.
 *
 * @author Janusch Rentenatus
 */
public class HarnessMetricsNGTest {

    private static HarnessModel modelWithAllQuests() {
        HarnessModel model = new HarnessModel().abortLimits(50, 5, 3, 10);
        for (Kind kind : Kind.values()) {
            model.addQuest(new Quest(kind).addSlot(new Slot(kind.name().toLowerCase() + ".base")
                    .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC))));
        }
        return model;
    }

    @Test
    public void testEmaFadeMath() {
        MetricBucket b = new MetricBucket();
        b.update(100);
        Assert.assertEquals(b.getEma(), 100.0, 1e-9);
        b.update(0);
        Assert.assertEquals(b.getEma(), 0.9 * 100 + 0.1 * 0, 1e-9);
        Assert.assertEquals(b.getCount(), 2);
        Assert.assertEquals(b.getMax(), 100.0, 1e-9);
        Assert.assertEquals(b.getMin(), 0.0, 1e-9);
    }

    @Test
    public void testRunProducesMx3AggregatePerSlot() {
        HarnessModel model = modelWithAllQuests();
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        instance.setRoot(new TaskNode(instance.nextTaskId(), "RESEARCH", "metrics check"));
        new HarnessEngine(model, instance, new DefaultCalculatorFactory(model)).run();

        MetricsStore store = instance.getMetrics();
        // WORK carries no score - the SCORE bucket must stay empty for it (no false swaps, §7.1)
        Assert.assertTrue(store.stats("work.base", 1).get(MetricsStore.Category.SCORE) == null
                || store.stats("work.base", 1).get(MetricsStore.Category.SCORE).getCount() == 0);
        // EVALUATE carries the deterministic score 80 and has all M categories
        Map<MetricsStore.Category, MetricBucket> evalStats = store.stats("evaluate.base", 1);
        Assert.assertEquals(evalStats.size(), MetricsStore.Category.values().length, "M categories present");
        for (int depth = 1; depth <= MetricsStore.DEPTHS; depth++) {
            Assert.assertFalse(store.stats("abort.base", depth).isEmpty(), "depth " + depth + " measured");
        }
        Assert.assertEquals(evalStats.get(MetricsStore.Category.SCORE).getAvg(), 80.0, 1e-9);
    }

    @Test
    public void testCloneWithResetInheritsAndResets() {
        MetricsStore store = new MetricsStore();
        store.recordCall(entry("a.base", 80));
        store.recordCall(entry("a.base", 90));
        store.recordCall(entry("b.base", 10));

        MetricsStore clone = store.cloneWithReset(Set.of("b.base"));
        Assert.assertEquals(clone.stats("a.base", 1).get(MetricsStore.Category.SCORE).getCount(), 2,
                "unmutated slot inherits its buckets");
        Assert.assertTrue(clone.stats("b.base", 1).isEmpty(), "mutated slot starts unmeasured");
    }

    @Test
    public void testMetricsPersistenceRoundTrip() throws Exception {
        MetricsStore store = new MetricsStore();
        store.recordCall(entry("work.base", 80));
        store.recordCall(entry("work.base", 60));
        File tmp = Files.createTempDirectory("harness-metrics").resolve("metrics.json").toFile();
        store.save(tmp);

        MetricsStore loaded = MetricsStore.load(tmp);
        MetricBucket score = loaded.stats("work.base", 2).get(MetricsStore.Category.SCORE);
        Assert.assertEquals(score.getCount(), 2);
        // ema after 80 then 60: first=80, then 0.9*80+0.1*60 = 78
        Assert.assertEquals(score.getEma(), 78.0, 1e-6);
    }

    private static CallLogEntry entry(String slotId, int score) {
        return new CallLogEntry(1, 1, "RESEARCH", Kind.WORK, slotId, "1.0.0", 0,
                5, 10, 20, score, Status.DONE, "worked", null, null);
    }
}
