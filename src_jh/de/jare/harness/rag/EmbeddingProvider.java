/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.rag;

/**
 * Embedding source for the project RAG (CONCEPT_Harness.md §11): the active
 * {@code rag} modelclass (e.g. nomic-embed-text via ollama). Only one rag model is
 * active at a time; a model change resets the RAG (only allowed while no prompt
 * editing runs). The deterministic {@link HashEmbedding} is the offline fallback
 * used when no rag provider is reachable.
 *
 * @author Janusch Rentenatus
 */
public interface EmbeddingProvider {

    String modelName();

    double[] embed(String text);

    /** Deterministic bag-of-words hashing embedding - offline fallback, cosine-comparable. */
    static EmbeddingProvider hashEmbedding() {
        return new EmbeddingProvider() {
            @Override
            public String modelName() {
                return "hash-embedding";
            }

            @Override
            public double[] embed(String text) {
                double[] v = new double[256];
                if (text == null) {
                    return v;
                }
                for (String tok : text.toLowerCase().split("[^a-z0-9äöüß]+")) {
                    if (tok.isEmpty()) {
                        continue;
                    }
                    int h = tok.hashCode() & 0xffff;
                    v[h % 256] += 1.0;
                }
                return v;
            }
        };
    }
}
