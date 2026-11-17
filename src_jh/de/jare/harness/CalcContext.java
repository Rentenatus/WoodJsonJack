/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.signals.Signal;
import de.jare.harness.tools.HarnessTools;
import de.jare.harness.tools.ToolResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The facade context every calculator receives (CONCEPT_Harness.md §14): facades only,
 * never raw engine access. This skeleton implements the pieces the deterministic
 * calculators need today - prompts, tools, rag, skills, mindset, lineage and ui are
 * added with their concept sections.
 *
 * @author Janusch Rentenatus
 */
public class CalcContext {

    private final HarnessEngine engine;
    private final HarnessInstance instance;
    private final HarnessModel model;
    private final TaskData task;
    private final Kind kind;
    private final String slotId;
    private final CalculatorNode node;
    private final Limits limits;
    private final List<String> currentPrompts;
    private HarnessTools tools;
    private de.jare.harness.memory.WorkCache workCache;   // result channel between calculators (§3 layering)
    private de.jare.harness.memory.ProjectMemory projectMemory;   // project-local memory across runs (§17)

    public CalcContext(HarnessEngine engine, HarnessInstance instance, HarnessModel model,
                       TaskData task, Kind kind, String slotId, CalculatorNode node,
                       Limits limits, List<String> currentPrompts) {
        this.engine = engine;
        this.instance = instance;
        this.model = model;
        this.task = task;
        this.kind = kind;
        this.slotId = slotId;
        this.node = node;
        this.limits = limits;
        this.currentPrompts = currentPrompts == null ? Collections.emptyList() : currentPrompts;
    }

    /** ctx.task - the task node of the current call. */
    public TaskData task() {
        return task;
    }

    public Kind kind() {
        return kind;
    }

    public String slotId() {
        return slotId;
    }

    public CalculatorNode calculatorNode() {
        return node;
    }

    /**
     * The sampling temperature from the calculator node's config params
     * ({@code "temperature"}, e.g. {@code 0.0} for deterministic scoring, higher for
     * creative tasks). Null when absent or not parseable - providers then use their default.
     */
    public Double temperature() {
        if (node == null) {
            return null;
        }
        String t = node.getParams().get("temperature");
        if (t == null || t.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(t.trim());
        } catch (NumberFormatException e) {
            // invalid config param: counted as PARAM_ERROR metric per slot (§6)
            throw new HarnessException(HarnessException.Type.PARAMETER,
                    "temperature is not a number: \"" + t + "\"", "temperature", e);
        }
    }

    public HarnessModel model() {
        return model;
    }

    public HarnessInstance instance() {
        return instance;
    }

    /** ctx.limits - the ABORT counters (§14). */
    public Limits limits() {
        return limits;
    }

    /** ctx.prompts.current() - the already assembled prompt list of this call. */
    public List<String> prompts() {
        return new ArrayList<>(currentPrompts);
    }

    /** ctx.ids - the persisted internal counters (§6). */
    public long nextTaskId() {
        return instance.nextTaskId();
    }

    public long nextAufrufId() {
        return instance.nextAufrufId();
    }

    /** ctx.metrics.logCall - local raw log entry + M x 3 aggregate (§6, §8). */
    public void logCall(CallLogEntry entry) {
        instance.logCall(entry);
    }

    /** ctx.metrics - the aggregated metrics facade (§14). */
    public de.jare.harness.metrics.MetricsStore metrics() {
        return instance.getMetrics();
    }

    /**
     * ctx.metrics.globalPriors (§8, §14): read-only global view from
     * {@code ~/.wood/harness/metrics.json} - feeds reliability thresholds and the
     * evolution candidate choice; decisions stay local.
     */
    public Map<de.jare.harness.metrics.MetricsStore.Category, de.jare.harness.metrics.MetricBucket> globalPriors(
            String slotSignature, int depth) {
        de.jare.harness.metrics.MetricsStore global = instance.getGlobalMetrics();
        return global == null ? Map.of() : global.stats(slotSignature, depth);
    }

    /** ctx.mindset (§16, §14): global mindset, categories via get/set; >1024 chars warns only. */
    public de.jare.harness.mindset.Mindset mindset() {
        return engine.getMindset();
    }

    /** ctx.skills (§12, §14): import/approve skills → SKILL chunks in the project RAG. */
    public de.jare.harness.skills.SkillRegistry skills() {
        return engine.getSkillRegistry();
    }

    /** ctx.lineage (§7, §14): only between runs - the service sets it after a run. */
    public HarnessLineage lineage() {
        return engine.getLineage();
    }

    /** ctx.prompts.relevance (§4, §14): resolve the segment list for this call's addressing. */
    public List<de.jare.harness.prompts.PromptSegment> relevance(String taskType, int attempt) {
        return engine.getRelevanceRegistry().resolve(taskType, attempt, kind, model.getVersion(),
                task.getTitle());
    }

    /** ctx.model.modelclass (§2, §14): the modelclass bound to this calculator node. */
    public de.jare.harness.model.Modelclass modelclass() {
        return node != null && node.getModelclass() != null ? model.modelclass(node.getModelclass()) : null;
    }

    /** ctx.model.isProviderAvailable (§2, §14): false after a marked provider outage. */
    public boolean isProviderAvailable() {
        de.jare.harness.model.Modelclass mc = modelclass();
        return mc == null || mc.isAvailable();
    }

    /** ctx.ui.focusTask (§14): focus a task in the harness window. */
    public void focusTask(long taskId) {
        engine.focusTask(task);
    }

    /** ctx.ui.askUser (§14): ask the user a question with options; default picks the first. */
    public String askUser(String question, List<String> options) {
        return engine.askUser(question, options);
    }

    /** ctx.rag - the project-local RAG facade (§11, §14), default hash-embedding store. */
    public de.jare.harness.rag.RagStore rag() {
        return instance.getRag(de.jare.harness.rag.EmbeddingProvider.hashEmbedding());
    }

    /** ctx.rag - the RAG store of the given embedder (one store per rag model name, §11). */
    public de.jare.harness.rag.RagStore rag(de.jare.harness.rag.EmbeddingProvider embedder) {
        return instance.getRag(embedder);
    }

    /** ctx.tools - the bounded tool facade (§13, §14). */
    public HarnessTools tools() {
        return tools;
    }

    public void setTools(HarnessTools tools) {
        this.tools = tools;
    }

    /**
     * ctx.workCache - the runtime result channel (WorkCache of the MemoryQuest, memory package):
     * every atomic calculator's CalcResult is stored here by the engine under the calculator
     * name and as lastResult; a calculator reads predecessor results of the same puzzle
     * sequence from here (e.g. askai.decompose reads scoring.countQuests' questCount).
     */
    public de.jare.harness.memory.WorkCache workCache() {
        return workCache;
    }

    public void setWorkCache(de.jare.harness.memory.WorkCache workCache) {
        this.workCache = workCache;
    }

    /**
     * ctx.projectMemory - the project-local memory (chat history + nuggets) that survives
     * runs (§17): persisted at {@code <projekt>/.wood/harness/memory.json}. Calculators read
     * nuggets via {@link de.jare.harness.memory.ProjectMemory#live(long)} and can write new
     * ones; the uses/hits feedback is recorded by the engine/service.
     */
    public de.jare.harness.memory.ProjectMemory projectMemory() {
        return projectMemory;
    }

    public void setProjectMemory(de.jare.harness.memory.ProjectMemory projectMemory) {
        this.projectMemory = projectMemory;
    }

    /**
     * ctx.tools.call with the driver-side tool-call counting (maxToolCalls limit, §13).
     * Raises TOOL_ERROR via the facade; the engine's catch-mapping handles it.
     */
    public ToolResult callTool(String name, Map<String, String> args, String reason) {
        limits.countToolCall();
        return tools.call(name, args, reason);
    }

    /** ctx.signals.emit - fires a driver signal, the on-mapping of the node answers (§13, §14). */
    public void emit(Signal signal) {
        engine.onSignal(task, kind, slotId, node, signal);
    }

    /**
     * ctx.catch (§13, §14): LIMIT-Ausnahme - bis zu 2 extra Zähler mit Begründung,
     * Begründung landet im Log. Alternativ compact() + abort().
     */
    public boolean approveExtraCounters(int n, String justification) {
        boolean ok = limits.approveExtraCounters(n, justification);
        if (ok) {
            engine.notify(task, kind, "LIMIT exception: +" + n + " counters (" + justification + ")");
        }
        return ok;
    }

    /**
     * ctx.catch.compact (§13): verdichtet die bisherigen Calls + Antworten nachträglich;
     * das Ergebnis landet als [compacted]-HISTORY-Segment im nächsten Versuch.
     */
    public String compact(String condensed) {
        String marked = "[compacted] " + condensed;
        task.appendPromptSupplement(marked);
        return marked;
    }

    /** ctx.catch.abort (§13): kontrollierten Abbruch einleiten. */
    public void abort(String reason) {
        task.setStatus(Status.ABORTED);
        engine.notify(task, kind, "abort: " + reason);
        throw new HarnessException(HarnessException.Type.ABORTED, reason);
    }

    /** ctx.ui.notify - info to the harness window (§14). */
    public void notify(String text) {
        engine.notify(task, kind, text);
    }
}
