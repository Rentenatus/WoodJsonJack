/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.impl.ollama;

import de.jare.harness.HarnessException;
import de.jare.harness.rag.EmbeddingProvider;
import de.jare.jsonpost.PostJsonClient;
import java.io.IOException;

/**
 * The ollama embedding provider for a {@code purpose: "rag"} modelclass (e.g.
 * {@code manutic/nomic-embed-code:latest}): one stateless call = one POST to
 * {@code /api/embed} with {@code {"model": …, "input": text}} → {@code {"embedding": […]}}.
 * The store is keyed by {@link #modelName()}, so two rag models get two separate RAG
 * stores (§11). On outage a {@link HarnessException} MODEL_UNAVAILABLE is thrown - the
 * caller falls back to the deterministic hash embedding store.
 *
 * @author Janusch Rentenatus
 */
public class OllamaEmbeddingProvider implements EmbeddingProvider {

    private final String baseUrl;
    private final String model;
    private final PostJsonClient client;

    public OllamaEmbeddingProvider(String baseUrl, String model) {
        this(baseUrl, model, new PostJsonClient());
    }

    public OllamaEmbeddingProvider(String baseUrl, String model, PostJsonClient client) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.client = client;
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public double[] embed(String text) {
        String body = "{\"model\":\"" + model + "\",\"input\":" + JsonEsc.quote(text == null ? "" : text) + "}";
        String raw;
        try {
            raw = client.postRaw(body, baseUrl + "/api/embed", 120000);
        } catch (IOException e) {
            throw new HarnessException(HarnessException.Type.MODEL_UNAVAILABLE,
                    "ollama embed failed for " + model + ": " + e.getMessage(), e);
        }
        try {
            var root = de.jare.jsoncasted.io.parserservice.JsonParserService.parse(raw,
                    de.jare.debug.JsonDebugLevel.SIMPLE).getRoot();
            // /api/embed answers {"embeddings": [[...], ...]} (one array per input) - the first
            // is our single input's vector; the legacy /api/embeddings field "embedding" is NOT sent
            var embs = root.asObjectValues().get("embeddings");
            var emb = embs != null && embs.isArray() && !embs.asArray().isEmpty()
                    ? embs.asArray().get(0) : root.asObjectValues().get("embedding");   // legacy fallback
            if (emb == null || !emb.isArray() || emb.asArray().isEmpty()) {
                throw new HarnessException(HarnessException.Type.SCHEMA_VIOLATION,
                        "embed answer carries no embedding array");
            }
            double[] v = new double[emb.asArray().size()];
            for (int i = 0; i < v.length; i++) {
                v[i] = emb.asArray().get(i).asNumber();
            }
            return v;
        } catch (HarnessException e) {
            throw e;
        } catch (Exception e) {
            throw new HarnessException(HarnessException.Type.SCHEMA_VIOLATION,
                    "embed answer parse failed: " + e.getMessage(), e);
        }
    }

}
