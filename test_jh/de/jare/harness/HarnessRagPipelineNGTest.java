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
import de.jare.harness.rag.EmbeddingProvider;
import de.jare.harness.rag.RagStore;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for the RAG wiring in the driver pipeline (CONCEPT_Harness.md §11): RAG_STORE
 * runs after EVALUATE when the score clears the threshold, RAG_RETRIEVE feeds DATA
 * segments into WORK prompts, RAG_PRUNE runs after CLOSE.
 *
 * @author Janusch Rentenatus
 */
public class HarnessRagPipelineNGTest {

    private static HarnessModel modelWithAllQuests() {
        HarnessModel model = new HarnessModel().abortLimits(50, 5, 3, 10);
        for (Kind kind : Kind.values()) {
            model.addQuest(new Quest(kind).addSlot(new Slot(kind.name().toLowerCase() + ".base")
                    .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC))));
        }
        return model;
    }

    @Test
    public void testStoreAfterEvaluateAndPruneAfterClose() {
        HarnessModel model = modelWithAllQuests();
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        instance.setRoot(new TaskNode(instance.nextTaskId(), "RESEARCH", "java streams pipeline"));
        new HarnessEngine(model, instance, new DefaultCalculatorFactory(model)).run();

        TaskNode root = instance.getRoot();
        Assert.assertTrue(root.getCalculatorLog().stream().anyMatch(e -> e.getKind() == Kind.RAG_STORE),
                "score 80 >= 50: RAG_STORE must run");
        Assert.assertTrue(root.getCalculatorLog().stream().anyMatch(e -> e.getKind() == Kind.RAG_PRUNE),
                "RAG_PRUNE must run after CLOSE");

        RagStore rag = instance.getRag();
        Assert.assertFalse(rag.getChunks().isEmpty(), "a RESULT chunk must be stored");
        Assert.assertTrue(rag.getChunks().stream().allMatch(c -> "RESULT".equals(c.getDocType())));
        Assert.assertEquals(rag.getChunks().get(0).getMeta().get("taskType"), "RESEARCH");
        Assert.assertEquals(rag.getChunks().get(0).getMeta().get("score"), "80");
    }

    @Test
    public void testNoStoreBelowThreshold() {
        HarnessModel model = modelWithAllQuests();
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        instance.setRoot(new TaskNode(instance.nextTaskId(), "RESEARCH", "weak task"));
        DefaultCalculatorFactory base = new DefaultCalculatorFactory(model);
        CalculatorFactory lowScore = (node, kind) -> {
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
        new HarnessEngine(model, instance, lowScore).run();

        Assert.assertTrue(instance.getRoot().getCalculatorLog().stream()
                .noneMatch(e -> e.getKind() == Kind.RAG_STORE), "score 20 < 50: no RAG_STORE");
        Assert.assertTrue(instance.getRag(EmbeddingProvider.hashEmbedding()).getChunks().isEmpty());
    }

    @Test
    public void testRetrieveFeedsSecondRun() {
        HarnessModel model = modelWithAllQuests();
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        instance.setRoot(new TaskNode(instance.nextTaskId(), "RESEARCH", "java streams pipeline"));
        HarnessEngine engine = new HarnessEngine(model, instance, new DefaultCalculatorFactory(model));
        engine.run();

        // second run on the same instance RAG: the stored chunk from run 1 is retrievable
        RagStore rag = instance.getRag();
        var hits = rag.retrieve("java streams pipeline", 5, 0.05);
        Assert.assertFalse(hits.isEmpty(), "stored chunk must be retrievable by similarity");
        Assert.assertTrue(hits.get(0).chunk().getDocument().contains("java streams pipeline"));
    }
}
