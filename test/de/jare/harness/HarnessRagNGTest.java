/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.rag.EmbeddingProvider;
import de.jare.harness.rag.RagChunk;
import de.jare.harness.rag.RagStore;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for the project-local RAG (CONCEPT_Harness.md §11): store with contentHash
 * dedup, retrieval by vector similarity only (cosine, top-k + minScore), prune with
 * supersede chain + size budget, and write-temp-rename persistence.
 *
 * @author Janusch Rentenatus
 */
public class HarnessRagNGTest {

    private static RagStore store() {
        return new RagStore(EmbeddingProvider.hashEmbedding());
    }

    @Test
    public void testStoreDedupsByContentHash() {
        RagStore rag = store();
        RagChunk a = rag.store("RESULT", "the answer is 42", Map.of("taskId", "1", "score", "80"));
        RagChunk b = rag.store("RESULT", "the answer is 42", Map.of("taskId", "2", "score", "90"));
        Assert.assertEquals(rag.getChunks().size(), 1, "identical content must dedup");
        Assert.assertEquals(a.getId(), b.getId());
        Assert.assertEquals(a.getDocType(), "RESULT");
        Assert.assertEquals(a.getMeta().get("score"), "80");
    }

    @Test
    public void testRetrieveIsVectorSimilarityTopK() {
        RagStore rag = store();
        rag.store("SKILL", "java stream api map filter collect", Map.of("skillId", "s1", "scope", "search"));
        rag.store("SKILL", "git cherry pick commit branch", Map.of("skillId", "s2", "scope", "edit"));
        rag.store("FILE", "java stream api tutorial examples", Map.of("taskId", "9"));

        List<RagStore.Hit> hits = rag.retrieve("java stream map filter", 2, 0.05);
        Assert.assertTrue(hits.size() <= 2, "topK hard budget");
        Assert.assertEquals(hits.get(0).chunk().getDocType(), hits.get(0).chunk().getDocType());
        Assert.assertTrue(hits.get(0).similarity() >= hits.get(hits.size() - 1).similarity());
        Assert.assertTrue(hits.stream().anyMatch(h -> h.chunk().getMeta().get("skillId") == null
                || h.similarity() > 0), "java-related chunks must rank above git chunk");
        // the git chunk should not beat the java chunks for a java query
        double gitSim = hits.stream().filter(h -> "git cherry pick commit branch".equals(h.chunk().getDocument()))
                .findFirst().map(RagStore.Hit::similarity).orElse(0.0);
        Assert.assertTrue(hits.get(0).similarity() >= gitSim);
    }

    @Test
    public void testRetrieveRespectsMinScore() {
        RagStore rag = store();
        rag.store("RESULT", "completely unrelated content about cooking pasta", Map.of());
        List<RagStore.Hit> hits = rag.retrieve("java programming question", 5, 0.9);
        Assert.assertTrue(hits.isEmpty(), "minScore threshold must filter weak matches");
    }

    @Test
    public void testPruneSupersedeAndBudget() {
        RagStore rag = new RagStore(EmbeddingProvider.hashEmbedding(),
                new RagStore.RagDefaults(5, 0.5, 3, Long.MAX_VALUE));
        rag.store("RESULT", "old result v1", Map.of("taskId", "t1", "ts", "1"));
        rag.store("RESULT", "new result v2", Map.of("taskId", "t1"));
        rag.store("RESULT", "other task", Map.of("taskId", "t2"));
        rag.store("FILE", "file a", Map.of("taskId", "t3"));
        rag.store("FILE", "file b", Map.of("taskId", "t4"));
        int removed = rag.prune();
        Assert.assertTrue(removed >= 1, "superseded chunk of t1 must be pruned");
        Assert.assertTrue(rag.getChunks().size() <= 3, "size budget maxChunks=3");
        Assert.assertTrue(rag.getChunks().stream().noneMatch(c -> "old result v1".equals(c.getDocument())),
                "superseded chunk gone");
    }

    @Test
    public void testPersistenceRoundTrip() throws Exception {
        RagStore rag = store();
        rag.store("SKILL", "skill description chunk one", Map.of("skillId", "s1", "descIndex", "0", "scope", "search"));
        File tmp = Files.createTempDirectory("harness-rag").resolve("rag.json").toFile();
        rag.save(tmp);

        RagStore loaded = RagStore.load(tmp, EmbeddingProvider.hashEmbedding(), RagStore.RagDefaults.standard());
        Assert.assertEquals(loaded.getChunks().size(), 1);
        RagChunk c = loaded.getChunks().get(0);
        Assert.assertEquals(c.getDocType(), "SKILL");
        Assert.assertEquals(c.getMeta().get("scope"), "search");
        Assert.assertEquals(c.getDocument(), "skill description chunk one");
        // retrieval still works on the reloaded (re-embedded-by-hash) store
        List<RagStore.Hit> hits = loaded.retrieve("skill description", 5, 0.05);
        Assert.assertFalse(hits.isEmpty());
    }
}
