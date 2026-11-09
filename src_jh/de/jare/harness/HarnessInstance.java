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
    private TaskTree taskTree = new TaskTree();  // the task tree as a real EditTree (§3)
    private TaskData root;
    private long nextTaskId = 1;
    private long nextAufrufId = 1;
    private final List<CallLogEntry> callLog = new ArrayList<>();
    private final MetricsStore metrics = new MetricsStore();
    private MetricsStore globalMetrics;   // ~/.wood/harness/metrics.json aggregate (§8) - optional, read priors + accumulate
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

    /** The task tree of this instance as a real EditTree - the same instances the editor shows. */
    public TaskTree getTaskTree() {
        return taskTree;
    }

    /** Adopts a loaded task tree (codec) and sets its root task. */
    public void adoptTaskTree(TaskTree taskTree) {
        this.taskTree = taskTree;
        this.root = taskTree.getRootTask();
    }

    /** Creates the root task on this instance's own task tree. */
    public TaskData newRootTask(long id, String taskType, String title) {
        this.root = taskTree.newRoot(id, taskType, title);
        return this.root;
    }

    public TaskData getRoot() {
        return root;
    }

    public void setRoot(TaskData root) {
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
        if (globalMetrics != null) {
            globalMetrics.recordCall(entry); // global aggregate: accumulate only, never events (§8)
        }
    }

    /** The aggregated M x 3 metrics of this instance (§6, §8). */
    public MetricsStore getMetrics() {
        return metrics;
    }

    /** The global aggregate from {@code ~/.wood/harness/metrics.json} (§8); null when not wired. */
    public MetricsStore getGlobalMetrics() {
        return globalMetrics;
    }

    public void setGlobalMetrics(MetricsStore globalMetrics) {
        this.globalMetrics = globalMetrics;
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
