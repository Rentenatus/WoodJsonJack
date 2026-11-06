/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.metrics.MetricsStore;
import de.jare.harness.rag.EmbeddingProvider;
import de.jare.harness.rag.RagStore;
import java.util.ArrayList;
import java.util.List;

/**
 * The running harness instance (CONCEPT_Harness.md §5): task tree + call log +
 * counters, referencing exactly one model version. Lives locally in the project at
 * {@code <projekt>/.wood/harness.json}; the trained master analogously under
 * {@code ~/.wood/harness/}. Persistence via write-temp-rename is added with the
 * JSON loader; this skeleton keeps the instance in memory.
 *
 * @author Janusch Rentenatus
 */
public class HarnessInstance {

    private final String modelName;
    private final String modelVersion;
    private java.io.File projectRoot;   // sandbox root for the fs.* tools (§13)
    private TaskNode root;
    private long nextTaskId = 1;
    private long nextAufrufId = 1;
    private final List<CallLogEntry> callLog = new ArrayList<>();
    private final MetricsStore metrics = new MetricsStore();
    private RagStore rag;   // project-local RAG (§11) - lazily created, never global

    public HarnessInstance(String modelName, String modelVersion) {
        this.modelName = modelName;
        this.modelVersion = modelVersion;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public java.io.File getProjectRoot() {
        return projectRoot;
    }

    public void setProjectRoot(java.io.File projectRoot) {
        this.projectRoot = projectRoot;
    }

    public TaskNode getRoot() {
        return root;
    }

    public void setRoot(TaskNode root) {
        this.root = root;
    }

    /**
     * Persisted counter (§6): after a WJJ restart the persisted nextId is loaded,
     * never re-scanned; defensively we also honor the highest id seen in tree + log.
     */
    public long nextTaskId() {
        return nextTaskId++;
    }

    public long nextAufrufId() {
        return nextAufrufId++;
    }

    public long getNextTaskId() {
        return nextTaskId;
    }

    public long getNextAufrufId() {
        return nextAufrufId;
    }

    /** Defensive max(): nextId = max(persisted nextId, highest id in tree + log) + 1 basis. */
    public void reconcileIds(long maxTaskIdSeen, long maxAufrufIdSeen) {
        if (maxTaskIdSeen >= nextTaskId) {
            nextTaskId = maxTaskIdSeen + 1;
        }
        if (maxAufrufIdSeen >= nextAufrufId) {
            nextAufrufId = maxAufrufIdSeen + 1;
        }
    }

    public void logCall(CallLogEntry entry) {
        callLog.add(entry);
        metrics.recordCall(entry);
    }

    /** The aggregated M x 3 metrics of this instance (§6, §8). */
    public MetricsStore getMetrics() {
        return metrics;
    }

    /** The project-local RAG (§11); created on first use with the given embedder. */
    public RagStore getRag(EmbeddingProvider embedder) {
        if (rag == null) {
            rag = new RagStore(embedder);
        }
        return rag;
    }

    public RagStore getRag() {
        return rag;
    }

    public void setRag(RagStore rag) {
        this.rag = rag;
    }

    public List<CallLogEntry> getCallLog() {
        return callLog;
    }
}
