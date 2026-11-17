/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.rag;

import de.jare.debug.JsonDebugLevel;
import de.jare.harness.io.AtomicFileWrite;
import de.jare.jsoncasted.io.JsonNodeWriter;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.lang.JsonNode;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The project-local RAG store (CONCEPT_Harness.md §11): chunks + embedding index under
 * {@code <projekt>/.wood/rag/}, never global. Retrieval is vector similarity only
 * (cosine), top-k with a hard budget; store dedups by contentHash; prune removes
 * superseded/expired chunks and enforces the size budget. Writes go through
 * write-temp-rename. RAG_STORE writes only at score >= minScore (§11) - the gating
 * lives in the caller (EVALUATE result), the store itself just stores.
 *
 * @author Janusch Rentenatus
 */
public class RagStore {

    /** RAG defaults (global default columns/config per RAG, §11). */
    public record RagDefaults(int topK, double minScore, int maxChunks, long ttlMs) {
        public static RagDefaults standard() {
            return new RagDefaults(5, 0.5, 1000, 7L * 24 * 3600 * 1000);
        }
    }

    public record Hit(RagChunk chunk, double similarity) {
    }

    private final EmbeddingProvider embedder;
    private RagDefaults defaults;
    private final List<RagChunk> chunks = new ArrayList<>();
    private long nextId = 1;

    public RagStore(EmbeddingProvider embedder) {
        this(embedder, RagDefaults.standard());
    }

    public RagStore(EmbeddingProvider embedder, RagDefaults defaults) {
        this.embedder = embedder;
        this.defaults = defaults;
    }

    public String getModelName() {
        return embedder.modelName();
    }

    public RagDefaults getDefaults() {
        return defaults;
    }

    public void setDefaults(RagDefaults defaults) {
        this.defaults = defaults;
    }

    /** RAG_STORE: dedup by contentHash - an identical chunk is not stored twice. */
    public RagChunk store(String docType, String document, Map<String, String> meta) {
        String hash = sha256(document);
        for (RagChunk c : chunks) {
            if (c.getContentHash().equals(hash)) {
                return c; // dedup
            }
        }
        RagChunk chunk = new RagChunk(nextId++, hash, docType, document,
                meta == null ? new LinkedHashMap<>() : meta, embedder.embed(document), System.currentTimeMillis());
        chunks.add(chunk);
        return chunk;
    }

    /** RAG_RETRIEVE: vector similarity only (cosine), top-k, minScore threshold, weight score x similarity. */
    public List<Hit> retrieve(String query, int topK, double minScore) {
        return retrieve(query, null, topK, minScore);
    }

    /**
     * Retrieval restricted to one docType (e.g. {@code "SKILL"}): the docType filter is
     * applied BEFORE top-k, so a global top-k can never cut off all hits of one type.
     */
    public List<Hit> retrieve(String query, String docType, int topK, double minScore) {
        double[] q = embedder.embed(query);
        List<Hit> hits = new ArrayList<>();
        for (RagChunk c : chunks) {
            if (docType != null && !docType.equals(c.getDocType())) {
                continue;
            }
            double sim = cosine(q, c.getEmbedding());
            if (sim >= minScore) {
                hits.add(new Hit(c, sim));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::similarity).reversed());
        return hits.size() > topK ? hits.subList(0, topK) : hits;
    }

    /**
     * RAG_PRUNE: contentHash dedup (already at store), supersede chain (a newer chunk
     * for the same taskId makes older ones obsolete), ttl expiry, size budget.
     *
     * @return number of removed chunks
     */
    public int prune() {
        long now = System.currentTimeMillis();
        Map<String, Long> newestByTask = new LinkedHashMap<>();
        for (RagChunk c : chunks) {
            String taskId = c.getMeta().get("taskId");
            if (taskId != null) {
                newestByTask.merge(taskId, c.getTs(), Math::max);
            }
        }
        int before = chunks.size();
        chunks.removeIf(c -> {
            if (now - c.getTs() > defaults.ttlMs()) {
                return true; // expired
            }
            String taskId = c.getMeta().get("taskId");
            if (taskId != null && newestByTask.get(taskId) > c.getTs()) {
                return true; // superseded by a newer chunk of the same task
            }
            return false;
        });
        while (chunks.size() > defaults.maxChunks()) {
            chunks.remove(0); // oldest first under the size budget
        }
        return before - chunks.size();
    }

    public List<RagChunk> getChunks() {
        return chunks;
    }

    public void save(File file) throws IOException, JsonParseException {
        AtomicFileWrite.writeString(file.toPath(), JsonNodeWriter.writeToString(toJson()));
    }

    /**
     * Guard for the RAG model switch (§11): a switch is only allowed while no prompt edit
     * is active - set by the UI/driver around prompt editing.
     */
    private static volatile boolean promptEditActive = false;

    public static void setPromptEditActive(boolean active) {
        promptEditActive = active;
    }

    public static boolean isPromptEditActive() {
        return promptEditActive;
    }

    public static RagStore load(File file, EmbeddingProvider embedder, RagDefaults defaults)
            throws IOException, JsonParseException {
        RagStore store = new RagStore(embedder, defaults);
        if (!file.exists()) {
            return store;
        }
        JsonNode root = JsonParserService.parse(file, JsonDebugLevel.SIMPLE).getRoot();
        // §11 model switch = reset: when the persisted rag model differs from the active
        // embedder, the RAG is emptied and rebuilt - but never while a prompt edit runs
        JsonNode rm = root.asObjectValues().get("ragModel");
        if (rm != null && !rm.asText().equals(embedder.modelName())) {
            if (promptEditActive) {
                throw new IllegalStateException(
                        "RAG model switch refused: a prompt edit is active (§11)");
            }
            return store; // reset: chunks of the old embedding model are dropped
        }
        JsonNode arr = root.asObjectValues().get("chunks");
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr.asArray()) {
                Map<String, String> meta = new LinkedHashMap<>();
                JsonNode mn = n.asObjectValues().get("meta");
                if (mn != null) {
                    for (Map.Entry<String, JsonNode> e : mn.asObjectValues().entrySet()) {
                        meta.put(e.getKey(), e.getValue().asText());
                    }
                }
                double[] emb = new double[0];
                JsonNode en = n.asObjectValues().get("embedding");
                if (en != null && en.isArray()) {
                    emb = new double[en.asArray().size()];
                    for (int i = 0; i < emb.length; i++) {
                        emb[i] = en.asArray().get(i).asNumber();
                    }
                }
                JsonNode idn = n.asObjectValues().get("id");
                long id = idn == null ? store.nextId++ : idn.asLong();
                store.nextId = Math.max(store.nextId, id + 1);
                store.chunks.add(new RagChunk(id, text(n, "contentHash"), text(n, "docType"),
                        text(n, "document"), meta, emb, longOf(n, "ts", 0)));
            }
        }
        return store;
    }

    public JsonNode toJson() {
        JsonNode root = JsonNode.objectNode();
        root.put("ragModel", JsonNode.stringNode(embedder.modelName()));
        JsonNode arr = JsonNode.arrayNode();
        for (RagChunk c : chunks) {
            JsonNode n = JsonNode.objectNode();
            n.put("id", JsonNode.longNode(c.getId()));
            n.put("contentHash", JsonNode.stringNode(c.getContentHash()));
            n.put("docType", JsonNode.stringNode(c.getDocType()));
            n.put("document", JsonNode.stringNode(c.getDocument()));
            JsonNode meta = JsonNode.objectNode();
            for (Map.Entry<String, String> e : c.getMeta().entrySet()) {
                meta.put(e.getKey(), JsonNode.stringNode(e.getValue()));
            }
            n.put("meta", meta);
            JsonNode emb = JsonNode.arrayNode();
            for (double d : c.getEmbedding()) {
                emb.add(JsonNode.numberNode(d));
            }
            n.put("embedding", emb);
            n.put("ts", JsonNode.longNode(c.getTs()));
            arr.add(n);
        }
        root.put("chunks", arr);
        return root;
    }

    public static double cosine(double[] a, double[] b) {
        int n = Math.min(a.length, b.length);
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }

    private static String text(JsonNode n, String key) {
        JsonNode v = n.asObjectValues().get(key);
        return v == null || v.isNull() ? "" : v.asText();
    }

    private static long longOf(JsonNode n, String key, long def) {
        JsonNode v = n.asObjectValues().get(key);
        return v == null || v.isNull() ? def : v.asLong();
    }
}
