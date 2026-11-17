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
        instance.newRootTask(instance.nextTaskId(), "RESEARCH", "java streams pipeline");
        new HarnessEngine(model, instance, new DefaultCalculatorFactory(model)).run();

        TaskData root = instance.getRoot();
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
        instance.newRootTask(instance.nextTaskId(), "RESEARCH", "weak task");
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

        // §15.6: the loop runs every item, the RAG_STORE calculator guards itself (minScore param,
        // default 50) - the call is logged with decision "skipped", nothing is stored
        Assert.assertTrue(instance.getRoot().getCalculatorLog().stream()
                .filter(e -> e.getKind() == Kind.RAG_STORE)
                .allMatch(e -> e.getDecision().contains("skipped")),
                "score 20 < 50: RAG_STORE must be skipped by the calculator guard");
        Assert.assertTrue(instance.getRag(EmbeddingProvider.hashEmbedding()).getChunks().isEmpty());
    }

    /**
     * §11/§12: when the model declares a rag modelclass, the ACTIVE store is that model's
     * store (engine.activeRag / ctx.rag) - not the hash fallback. RAG_STORE must land there,
     * so skill chunks stored via skill.match are retrievable in the prompt segments.
     */
    @Test
    public void testActiveRagStoreFollowsRagModelclass() {
        HarnessModel model = modelWithAllQuests();
        model.addModelclass(new de.jare.harness.model.Modelclass(
                "ollama", "test-embed:latest", "rag", 0, 0.2, false));
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        instance.newRootTask(instance.nextTaskId(), "RESEARCH", "java streams pipeline");
        // offline embedder registered under the rag model's name - the store must key on it
        DefaultCalculatorFactory factory = new DefaultCalculatorFactory(model);
        factory.registerEmbeddingProvider("test-embed:latest",
                EmbeddingProvider.hashEmbedding("test-embed:latest"));
        HarnessEngine engine = new HarnessEngine(model, instance, factory);

        Assert.assertEquals(engine.activeRag().getModelName(), "test-embed:latest",
                "activeRag must resolve the rag-purpose modelclass's store");
        engine.run();

        RagStore active = instance.getRags().get("test-embed:latest");
        Assert.assertNotNull(active, "the rag model's store must exist");
        Assert.assertFalse(active.getChunks().isEmpty(), "RAG_STORE must write into the ACTIVE store");
        Assert.assertTrue(instance.getRag(EmbeddingProvider.hashEmbedding()).getChunks().isEmpty(),
                "the hash fallback store must stay empty when a rag modelclass is configured");
    }

    /**
     * Provider "system" is the DECLARED deterministic rag fallback (hash-embedding): when a real
     * rag modelclass is also declared, the real one wins (activeRag prefers non-system, §11);
     * the system entry's embedder is keyed by its declared model name.
     */
    @Test
    public void testActiveRagPrefersRealModelOverSystemFallback() {
        HarnessModel model = modelWithAllQuests();
        model.addModelclass(new de.jare.harness.model.Modelclass(
                "ollama", "nomic-x:latest", "rag", 0, 0.2, false));
        model.addModelclass(new de.jare.harness.model.Modelclass(
                de.jare.harness.model.Modelclass.PROVIDER_SYSTEM, "hash-embedding", "rag", 0, 0.0, false));
        DefaultCalculatorFactory factory = new DefaultCalculatorFactory(model);
        factory.registerEmbeddingProvider("nomic-x:latest",
                EmbeddingProvider.hashEmbedding("nomic-x:latest"));
        HarnessEngine engine = new HarnessEngine(model, new HarnessInstance("m", "1"), factory);

        Assert.assertEquals(engine.activeRag().getModelName(), "nomic-x:latest",
                "a real rag modelclass must win over the declared system fallback");
        var ragClasses = model.modelclassesForPurpose("rag");
        Assert.assertEquals(ragClasses.size(), 2);
        Assert.assertEquals(factory.embeddingProviderFor(ragClasses.get(1)).modelName(), "hash-embedding",
                "the system rag entry resolves to the hash embedding keyed by its declared model name");
    }

    /** With ONLY a system rag modelclass declared, activeRag uses the declared hash store. */
    @Test
    public void testActiveRagFallsBackToDeclaredSystemModel() {
        HarnessModel model = modelWithAllQuests();
        model.addModelclass(new de.jare.harness.model.Modelclass(
                de.jare.harness.model.Modelclass.PROVIDER_SYSTEM, "hash-embedding", "rag", 0, 0.0, false));
        HarnessEngine engine = new HarnessEngine(model, new HarnessInstance("m", "1"),
                new DefaultCalculatorFactory(model));
        Assert.assertEquals(engine.activeRag().getModelName(), "hash-embedding");
    }

    @Test
    public void testRetrieveFeedsSecondRun() {
        HarnessModel model = modelWithAllQuests();
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        instance.newRootTask(instance.nextTaskId(), "RESEARCH", "java streams pipeline");
        HarnessEngine engine = new HarnessEngine(model, instance, new DefaultCalculatorFactory(model));
        engine.run();

        // second run on the same instance RAG: the stored chunk from run 1 is retrievable
        RagStore rag = instance.getRag();
        var hits = rag.retrieve("java streams pipeline", 5, 0.05);
        Assert.assertFalse(hits.isEmpty(), "stored chunk must be retrievable by similarity");
        Assert.assertTrue(hits.get(0).chunk().getDocument().contains("java streams pipeline"));
    }
}
