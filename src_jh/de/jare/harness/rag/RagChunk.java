/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.rag;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One chunk of the project-local RAG (CONCEPT_Harness.md §11): the chunk table columns
 * id/contentHash, docType (SKILL, RESULT, FILE, …), document, meta
 * ({taskId, aufrufId, taskType, kind, score, ts, scope, source}) and the embedding
 * vector of the active rag modelclass.
 *
 * @author Janusch Rentenatus
 */
public class RagChunk {

    private final long id;
    private final String contentHash;
    private final String docType;
    private final String document;
    private final Map<String, String> meta;
    private final double[] embedding;
    private final long ts;

    public RagChunk(long id, String contentHash, String docType, String document,
                    Map<String, String> meta, double[] embedding, long ts) {
        this.id = id;
        this.contentHash = contentHash;
        this.docType = docType;
        this.document = document;
        this.meta = new LinkedHashMap<>(meta);
        this.embedding = embedding;
        this.ts = ts;
    }

    public long getId() { return id; }
    public String getContentHash() { return contentHash; }
    public String getDocType() { return docType; }
    public String getDocument() { return document; }
    public Map<String, String> getMeta() { return meta; }
    public double[] getEmbedding() { return embedding; }
    public long getTs() { return ts; }
}
