/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Quest;
import de.jare.harness.model.Slot;
import de.jare.harness.prompts.PromptAssembler;
import de.jare.harness.prompts.PromptSegment;
import de.jare.harness.prompts.RelevanceRegistry;
import de.jare.harness.tools.EditorTools;
import de.jare.harness.tools.FsTools;
import de.jare.harness.tools.GitTools;
import de.jare.harness.tools.HarnessTools;
import de.jare.harness.tools.PermissionPolicy;
import de.jare.harness.tools.ProcessTools;
import de.jare.harness.tools.SearchTools;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The deterministic driver (CONCEPT_Harness.md §1, §3): the KI never orchestrates -
 * this engine walks the task tree and consults exactly one encapsulated calculator
 * per step. Pipeline per task: ABORT (every loop) → SPLIT → COMPLETE → WORK →
 * EVALUATE → CHECK (retry | subtasks) → SUMMARIZE → CLOSE. State lives only in the
 * task tree; every call lands metrics-only in the call log (§6).
 *
 * @author Janusch Rentenatus
 */
public class HarnessEngine {

    /** RAG_STORE threshold on the 0-100 score scale (§11, konfigurierbar am RAG-Default). */
    public static final int RAG_STORE_MIN_SCORE = 50;

    private final HarnessModel model;
    private final HarnessInstance instance;
    private final CalculatorFactory factory;
    private final Limits limits;
    private final Deque<CallLogEntry> callStack = new ArrayDeque<>();
    private final List<BiConsumer<TaskData, String>> listeners = new ArrayList<>();
    private final RelevanceRegistry relevanceRegistry;
    private final PromptAssembler promptAssembler;
    private final HarnessTools tools;
    private HarnessLineage lineage;   // set between runs; enables the feedback loop (§7.1)
    private de.jare.harness.mindset.Mindset mindset = new de.jare.harness.mindset.Mindset();   // global ~/.wood/mindset (§16)
    private de.jare.harness.skills.SkillRegistry skillRegistry = new de.jare.harness.skills.SkillRegistry();   // §12
    private java.util.function.BiFunction<String, List<String>, String> userAskHandler;   // §14 ctx.ui.askUser

    public HarnessEngine(HarnessModel model, HarnessInstance instance, CalculatorFactory factory) {
        this.model = model;
        this.instance = instance;
        this.factory = factory;
        this.limits = new Limits(model.getMaxIterations(), model.getMaxDepth(),
                model.getMaxRetries(), model.getMaxToolCalls());
        this.relevanceRegistry = new RelevanceRegistry();
        Map<PromptSegment.Category, Integer> budgets = new EnumMap<>(PromptSegment.Category.class);
        for (Map.Entry<String, Integer> e : model.getPromptBudgets().entrySet()) {
            budgets.put(PromptSegment.Category.valueOf(e.getKey()), e.getValue());
        }
        this.promptAssembler = new PromptAssembler(budgets, model.getMaxTokensTotal(),
                "sticky: harness " + model.getName() + " v" + model.getVersion());
        this.tools = new HarnessTools(new PermissionPolicy());
        if (instance.getProjectRoot() != null) {
            java.nio.file.Path root = instance.getProjectRoot().toPath();
            tools.register(FsTools.read(root));
            tools.register(FsTools.list(root));
            tools.register(FsTools.write(root));
            tools.register(FsTools.delete(root));
            tools.register(ProcessTools.run(root));
            tools.register(GitTools.status(root));
            tools.register(GitTools.diff(root));
            tools.register(GitTools.log(root));
            tools.register(GitTools.add(root));
            tools.register(GitTools.commit(root));
            tools.register(SearchTools.grep(root));
            tools.register(EditorTools.open(root, rel -> {
                if (editorOpenHandler != null) {
                    editorOpenHandler.accept(rel);
                }
            }));
        }
    }

    private java.util.function.Consumer<String> editorOpenHandler;

    /** The UI installs this so editor.open really opens a tab (HarnessController). */
    public void setEditorOpenHandler(java.util.function.Consumer<String> editorOpenHandler) {
        this.editorOpenHandler = editorOpenHandler;
    }

    public HarnessTools getTools() {
        return tools;
    }

    public void setLineage(HarnessLineage lineage) {
        this.lineage = lineage;
    }

    public HarnessLineage getLineage() {
        return lineage;
    }

    /** The global AI mindset loaded from {@code ~/.wood/mindset/} (§16); set by the service. */
    public de.jare.harness.mindset.Mindset getMindset() {
        return mindset;
    }

    public void setMindset(de.jare.harness.mindset.Mindset mindset) {
        this.mindset = mindset == null ? new de.jare.harness.mindset.Mindset() : mindset;
    }

    /** The skill registry (§12); loaded by the service from the global + project skill dirs. */
    public de.jare.harness.skills.SkillRegistry getSkillRegistry() {
        return skillRegistry;
    }

    public void setSkillRegistry(de.jare.harness.skills.SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry == null ? new de.jare.harness.skills.SkillRegistry() : skillRegistry;
    }

    /** §14 ctx.ui.askUser - the UI installs this; default answers with the first option. */
    public void setUserAskHandler(java.util.function.BiFunction<String, List<String>, String> handler) {
        this.userAskHandler = handler;
    }

    /** §14 ctx.ui.focusTask - notifies the harness window to focus a task node. */
    public void focusTask(TaskData task) {
        notify(task, null, "focus");
    }

    /** §14 ctx.ui.askUser - asks the user; default (no UI bound) picks the first option. */
    public String askUser(String question, List<String> options) {
        if (userAskHandler != null) {
            return userAskHandler.apply(question, options);
        }
        return options.isEmpty() ? "" : options.get(0);
    }

    /**
     * The feedback loop after a run (CONCEPT_Harness.md §7.1): only between runs, never
     * mid-run - the running tasks always finish the old model version. When a base slot's
     * average score over at least minSamples calls falls below the threshold, a swap is
     * proposed as a child version in the lineage, effective from the next run (the old
     * model is then unloaded).
     *
     * @return the proposed swap versions (empty when all slots are healthy)
     */
    public List<HarnessLineage.Version> feedbackLoop(double scoreThreshold, int minSamples) {
        List<HarnessLineage.Version> proposals = new ArrayList<>();
        if (lineage == null) {
            return proposals;
        }
        for (Quest quest : model.getQuests()) {
            for (Slot slot : quest.getSlots()) {
                if (!slot.isBase() || slot.getCalculator() == null) {
                    continue;
                }
                var stats = instance.getMetrics().stats(slot.getId(), 1);
                var bucket = stats.get(de.jare.harness.metrics.MetricsStore.Category.SCORE);
                if (bucket == null || bucket.getCount() < minSamples || bucket.getAvg() >= scoreThreshold) {
                    continue;
                }
                CalculatorNode alternative = alternativeFor(slot.getCalculator());
                proposals.add(lineage.proposeSwap(slot.getId(), alternative,
                        "avg score " + String.format("%.1f", bucket.getAvg()) + " over " + bucket.getCount()
                                + " calls < threshold " + scoreThreshold));
            }
        }
        return proposals;
    }

    /** The swap alternative: deterministic ↔ ai (first tool-capable modelclass). */
    private CalculatorNode alternativeFor(CalculatorNode current) {
        if (current.isPuzzle() || CalculatorNode.IMPL_DETERMINISTIC.equals(current.getImpl())) {
            String mc = model.getModelclasses().stream()
                    .filter(de.jare.harness.model.Modelclass::isToolCapable)
                    .map(de.jare.harness.model.Modelclass::getPurpose).findFirst().orElse("edit");
            return CalculatorNode.atomic(CalculatorNode.IMPL_AI).modelclass(mc);
        }
        return CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC);
    }

    public RelevanceRegistry getRelevanceRegistry() {
        return relevanceRegistry;
    }

    public HarnessModel getModel() {
        return model;
    }

    public HarnessInstance getInstance() {
        return instance;
    }

    public Limits getLimits() {
        return limits;
    }

    public void addListener(BiConsumer<TaskData, String> listener) {
        listeners.add(listener);
    }

    public void notify(TaskData task, Kind kind, String text) {
        String msg = "[task " + task.getId() + " / " + kind + "] " + text;
        for (BiConsumer<TaskData, String> l : listeners) {
            l.accept(task, msg);
        }
    }

    /** Runs the whole task tree starting at the instance root. */
    public void run() {
        if (instance.getRoot() == null) {
            throw new IllegalStateException("instance has no root task");
        }
        process(instance.getRoot());
    }

    /** The deterministic pipeline for one task (§3). */
    private void process(TaskData task) {
        task.setStatus(Status.RUNNING);
        int stackBase = callStack.size();
        try {
            processLoop(task);
        } finally {
            while (callStack.size() > stackBase) {
                callStack.pop();
            }
        }
    }

    private void processLoop(TaskData task) {
        while (true) {
            limits.countIteration();
            if (limits.isAborted(task.depth())) {
                task.setStatus(Status.ABORTED);
                notify(task, Kind.ABORT, "hard stop: limits exceeded");
                return;
            }
            CalcResult abort = execute(task, Kind.ABORT);
            if (abort != null && "abort".equals(abort.getDecision())) {
                task.setStatus(Status.ABORTED);
                notify(task, Kind.ABORT, abort.getMessage());
                return;
            }

            CalcResult split = execute(task, Kind.SPLIT);
            if (split != null && "split".equals(split.getDecision())) {
                task.rewriteAsSummary();
                int parts = Integer.parseInt(split.getPayload().getOrDefault("parts", "2"));
                for (int i = 1; i <= parts; i++) {
                    process(task.addSubtask(instance.nextTaskId(), task.getTaskType(),
                            task.getTitle() + " / part " + i));
                }
                execute(task, Kind.SUMMARIZE);
                execute(task, Kind.CLOSE);
                execute(task, Kind.RAG_PRUNE);
                return;
            }

            CalcResult complete = execute(task, Kind.COMPLETE);
            if (complete != null && "incomplete".equals(complete.getDecision())) {
                process(task.addSubtask(instance.nextTaskId(), "REASONING",
                        "reasoning about " + task.getTitle().replaceAll("\\?+$", "")));
                continue;
            }

            execute(task, Kind.WORK);
            CalcResult eval = execute(task, Kind.EVALUATE);
            if (eval != null) {
                if (eval.getScore() != null) {
                    task.setScore(eval.getScore());
                }
                task.setStatus(eval.getStatus());
            }
            // RAG_STORE only on success (§11): EVALUATE score ≥ Schwelle (0-100 scale)
            if (task.getScore() != null && task.getScore() >= RAG_STORE_MIN_SCORE) {
                execute(task, Kind.RAG_STORE);
            }

            CalcResult check = execute(task, Kind.CHECK);
            if (check != null && "retry".equals(check.getDecision())) {
                limits.countRetry();
                task.incrementAttempt();
                task.setStatus(Status.RETRY);
                task.appendPromptSupplement("retry " + task.getAttempt());
                continue;
            }

            if (!task.getChildren().isEmpty()) {
                execute(task, Kind.SUMMARIZE);
            }
            execute(task, Kind.CLOSE);
            execute(task, Kind.RAG_PRUNE); // prune runs e.g. after each CLOSE (§11)
            return;
        }
    }

    /**
     * Executes one calculator call for a task: resolves quest → base slot → calculator
     * node, runs it (puzzle nodes recursively in sequence), and appends the
     * metrics-only log entry with the parent1-redundant / parent2-link chain (§6).
     * A {@link HarnessException} is caught here (catch-mapping §13) and falls back to
     * the deterministic calculator of the same kind.
     */
    private CalcResult execute(TaskData task, Kind kind) {
        Quest quest = model.questFor(kind);
        if (quest == null) {
            return null; // kind not modelled - skipped deterministically
        }
        Slot slot = quest.baseSlot();
        if (slot == null || slot.getCalculator() == null) {
            return null;
        }
        CalculatorNode node = slot.getCalculator();
        long aufrufId = instance.nextAufrufId();
        CallLogEntry parent = callStack.peek();
        CallLogEntry.Parent1 p1 = parent == null ? null
                : new CallLogEntry.Parent1(parent.getAufrufId(), parent.getScore(), parent.getStatus());
        Long p2 = parent == null || parent.getParent1() == null ? null : parent.getParent1().aufrufId;

        String auftrag = task.getTitle()
                + (task.getPromptSupplement().isEmpty() ? "" : "\n" + task.getPromptSupplement());
        List<PromptSegment> resolved = buildSegments(task, kind, auftrag);
        List<String> prompts = PromptAssembler.toTexts(promptAssembler.assemble(resolved));
        CalcContext ctx = new CalcContext(this, instance, model, task, kind, slot.getId(), node, limits, prompts);
        ctx.setTools(tools);

        long start = System.currentTimeMillis();
        CalcResult result;
        String decisionSuffix = "";
        try {
            result = runNode(node, kind, ctx);
            ctx.emit(de.jare.harness.signals.Signal.ANSWER_RECEIVED);
        } catch (HarnessException e) {
            decisionSuffix = "catch:" + e.getType();
            notify(task, kind, decisionSuffix + " -> catch-mapping");
            CalculatorNode catchNode = node.getCatchMapping().get(e.getType());
            if (catchNode != null) {
                result = runNode(catchNode, kind, ctx);
                decisionSuffix += "+handled";
            } else {
                result = DeterministicFallback.run(kind, ctx, e);
            }
        }
        long dauerMs = System.currentTimeMillis() - start;

        CallLogEntry entry = new CallLogEntry(aufrufId, task.getId(), task.getTaskType(), kind,
                slot.getId(), model.getVersion(), task.getAttempt(), dauerMs, 0, 0,
                result.getScore(), result.getStatus(),
                result.getDecision() + decisionSuffix, p1, p2);
        task.logCall(entry);
        instance.logCall(entry);
        callStack.push(entry); // stays on the stack until the owning task's process() ends
        return result;
    }

    /**
     * Builds the prompt segment list for one call: the relevance registry's refs, then
     * RAG_RETRIEVE (§11) and SKILL matching (§12) for WORK/CHECK/COMPLETE - DATA segments
     * carry the non-skill chunk texts, SKILL segments only the reference to the skill id
     * (the full text stays in the RAG) - and finally the mindset category as a SYSTEM
     * segment (§16). The assembler enforces the hard budgets afterwards (§4).
     */
    private List<PromptSegment> buildSegments(TaskData task, Kind kind, String auftrag) {
        List<PromptSegment> resolved = new ArrayList<>(relevanceRegistry.resolve(task.getTaskType(),
                task.getAttempt(), kind, model.getVersion(), auftrag));
        if (kind == Kind.WORK || kind == Kind.CHECK || kind == Kind.COMPLETE) {
            var rag = instance.getRag(de.jare.harness.rag.EmbeddingProvider.hashEmbedding());
            var hits = rag.retrieve(auftrag, rag.getDefaults().topK(), rag.getDefaults().minScore());
            for (var h : hits) {
                var chunk = h.chunk();
                if (de.jare.harness.skills.SkillRegistry.DOC_TYPE_SKILL.equals(chunk.getDocType())) {
                    // §12: reference only, never the full skill text in the prompt
                    resolved.add(PromptSegment.of(PromptSegment.Category.SKILL,
                            "skill:" + chunk.getMeta().get("skillId"),
                            "skill " + chunk.getMeta().get("skillId") + " (scope "
                                    + chunk.getMeta().get("scope") + ") - see RAG chunk " + chunk.getId()));
                } else {
                    resolved.add(PromptSegment.of(PromptSegment.Category.DATA,
                            "rag:chunk-" + chunk.getId(), chunk.getDocument()));
                }
            }
        }
        // §16: the relevance calculator may pull a mindset category as SYSTEM segment
        String mindsetCategory = (kind == Kind.RELEVANCE || kind == Kind.EVALUATE || kind == Kind.CHECK)
                ? "analysis" : "answer";
        String mindsetText = mindset.get(mindsetCategory);
        if (mindsetText != null && !mindsetText.isEmpty()) {
            resolved.add(PromptSegment.of(PromptSegment.Category.SYSTEM,
                    "mindset:" + mindsetCategory, mindsetText));
        }
        // §4 anti-forgetting: HISTORY is never raw history but condensed one-line summaries
        // from the task's calculator log - max 3 segments, the assembler enforces the shared
        // HISTORY budget (SUMMARIZE of the previous round and compact compete for it)
        List<CallLogEntry> log = task.getCalculatorLog();
        int from = Math.max(0, log.size() - 3);
        for (int i = from; i < log.size(); i++) {
            CallLogEntry e = log.get(i);
            resolved.add(PromptSegment.of(PromptSegment.Category.HISTORY, "history:call-" + e.getAufrufId(),
                    e.getKind() + " #" + e.getAttempt() + " -> " + e.getDecision()
                            + (e.getScore() != null ? " (score " + e.getScore() + ")" : "")));
        }
        return resolved;
    }

    /**
     * Fires a driver signal: when the node has an on-mapping for it, the mapped child
     * calculator runs (§13) - e.g. TOOL_ERROR → CHECK for a retry.
     */
    public void onSignal(TaskData task, Kind kind, String slotId, CalculatorNode node,
                         de.jare.harness.signals.Signal signal) {
        CalculatorNode mapped = node == null ? null : node.getOn().get(signal);
        if (mapped != null) {
            notify(task, kind, "signal " + signal + " -> on-mapped calculator");
            Quest quest = model.questFor(kind);
            Slot slot = quest == null ? null : quest.baseSlot();
            List<String> prompts = PromptAssembler.toTexts(promptAssembler.assemble(
                    buildSegments(task, kind, task.getTitle())));
            CalcContext ctx = new CalcContext(this, instance, model, task, kind, slotId, mapped, limits, prompts);
            ctx.setTools(tools);
            runNode(mapped, kind, ctx);
        }
    }

    /** Runs a calculator node; puzzle nodes run their children in sequence (§5 recursion). */
    private CalcResult runNode(CalculatorNode node, Kind kind, CalcContext ctx) {
        if (node.isPuzzle()) {
            CalcResult last = CalcResult.of(kind, "puzzle");
            for (CalculatorNode child : node.getChildren()) {
                last = runNode(child, kind, ctx);
            }
            return last;
        }
        return factory.createAtomic(node, kind).calculate(ctx);
    }

    /** The deterministic fallback used by the catch-mapping (§13). */
    private static final class DeterministicFallback {
        static CalcResult run(Kind kind, CalcContext ctx, HarnessException e) {
            CalcResult r = de.jare.harness.impl.deterministic.DeterministicCalculators.forKind(kind).calculate(ctx);
            return r.decision(r.getDecision()).status(Status.FAILED).message("fallback after " + e.getType());
        }
    }
}
