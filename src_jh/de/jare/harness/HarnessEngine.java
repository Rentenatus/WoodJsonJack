/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.main.actions.LoopSplit;
import de.jare.harness.main.actions.LoopSubLoop;
import de.jare.harness.main.actions.LoopThrowError;
import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Loop;
import de.jare.harness.model.LoopItem;
import de.jare.harness.model.Quest;
import de.jare.harness.model.Slot;
import de.jare.harness.model.Trigger;
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
 * The deterministic driver (CONCEPT_Harness.md §1, §3, §15.6): the KI never orchestrates -
 * this engine walks the task tree and consults exactly one encapsulated calculator
 * per step. The loop is NOT hard-coded anymore: it is configured as named loops
 * ({@code loops[]} in the model, DESIGN_Loops.md) - the engine only executes per
 * prescription (START/EXECUTE/CATCH/CLOSE lifecycle) and evaluates the calculator's
 * {@link LoopAction}. State lives only in the task tree; every call lands
 * metrics-only in the call log (§6).
 *
 * @author Janusch Rentenatus
 */
public class HarnessEngine implements HarnessDriver {

    /** Legacy RAG_STORE threshold on the 0-100 score scale (§11); the guard now lives in the RAG_STORE calculator (param {@code minScore}, default this value). */
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
    private de.jare.harness.memory.WorkCache workCache;   // result channel between calculators (§3 layering)
    private de.jare.harness.memory.CommandHistory commandHistory;   // §18: every TaskData mutation goes through here
    private java.util.function.BiFunction<String, List<String>, String> userAskHandler;   // §14 ctx.ui.askUser
    private int loopDepth;                                  // subloop nesting, bounded by maxDepth (§15.6)
    private ErrorSignal pendingTechnical;                   // TECHNICAL unwinds all loops to the top (§15.6)
    private ErrorSignal pendingDomain;                      // DOMAIN thrown inside CATCH unwinds one loop up (§15.6)

    public HarnessEngine(HarnessModel model, HarnessInstance instance, CalculatorFactory factory) {
        this.model = model;
        this.instance = instance;
        this.factory = factory;
        this.limits = new Limits(model.getMaxIterations(), model.getMaxDepth(),
                model.getMaxRetries(), model.getMaxToolCalls());
        this.relevanceRegistry = new RelevanceRegistry();
        Map<PromptSegment.Category, Integer> budgets = new EnumMap<>(PromptSegment.Category.class);
        for (Map.Entry<String, Integer> e : model.getPromptBudgets().entrySet()) {
            // only real categories are budgets (defensive: unknown keys are skipped, not crashed)
            try {
                budgets.put(PromptSegment.Category.valueOf(e.getKey()), e.getValue());
            } catch (IllegalArgumentException notACategory) {
                // e.g. "maxTokensTotal" - a model field, not a per-category budget
            }
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

    /**
     * The ACTIVE RAG store (§11): the store of the first modelclass with purpose {@code "rag"},
     * embedded through the factory's embedding provider for that model; fallback the
     * deterministic hash-embedding store when the model declares no rag modelclass.
     * Stores stay keyed per rag model name - two rag models = two separate stores.
     */
    public de.jare.harness.rag.RagStore activeRag() {
        // a declared "system" rag modelclass (e.g. hash-embedding) is the FALLBACK - a real
        // rag model (ollama/hosted) always wins when both are declared (§11: one active rag model)
        de.jare.harness.model.Modelclass ragMc = model.modelclassesForPurpose("rag").stream()
                .filter(mc -> !mc.isSystem()).findFirst()
                .orElseGet(() -> model.modelclassesForPurpose("rag").stream().findFirst().orElse(null));
        de.jare.harness.rag.EmbeddingProvider ep = ragMc == null
                || !(factory instanceof de.jare.harness.impl.DefaultCalculatorFactory dcf)
                ? de.jare.harness.rag.EmbeddingProvider.hashEmbedding()
                : dcf.embeddingProviderFor(ragMc);
        return instance.getRag(ep);
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

    /** The WorkCache of the current MemoryQuest - the runtime result channel handed to every CalcContext. */
    public void setWorkCache(de.jare.harness.memory.WorkCache workCache) {
        this.workCache = workCache;
    }

    /** The CommandHistory of the current MemoryQuest (§18) - every task mutation is dispatched through it. */
    public void setCommandHistory(de.jare.harness.memory.CommandHistory commandHistory) {
        this.commandHistory = commandHistory;
    }

    /**
     * §18: the single mutation path - applies a {@link TaskCommand} to the task and records
     * it in the CommandHistory with the origin triple {@code loopId/loopItemId/trigger}.
     * Without a wired history (ad-hoc engines) the command executes directly, unrecorded.
     */
    void applyCmd(TaskData task, TaskCommand cmd, String loopId, String loopItemId, String trigger) {
        if (commandHistory != null) {
            commandHistory.apply(cmd, task, loopId, loopItemId, trigger);
        } else {
            de.jare.harness.memory.CommandHistory.applyDirect(cmd, task);
        }
    }

    private de.jare.harness.memory.ProjectMemory projectMemory;   // project-local memory across runs (§17)
    private final java.util.Set<Long> usedMemoryIds = new java.util.LinkedHashSet<>();   // §17 feedback: fed into prompts this run

    /** The ProjectMemory of the project - handed to every CalcContext and fed as HISTORY/DATA segments. */
    public void setProjectMemory(de.jare.harness.memory.ProjectMemory projectMemory) {
        this.projectMemory = projectMemory;
    }

    public de.jare.harness.memory.ProjectMemory getProjectMemory() {
        return projectMemory;
    }

    /** §17: ids of the project-memory entries that were fed into a prompt during this run. */
    public java.util.Set<Long> getUsedMemoryIds() {
        return usedMemoryIds;
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

    /**
     * Runs the whole task tree starting at the instance root. Outermost safety net
     * (§15.6): no Throwable ever leaves the engine - a technical disturbance becomes
     * an ErrorSignal and a clean FAILED on the root task.
     */
    public void run() {
        if (instance.getRoot() == null) {
            throw new IllegalStateException("instance has no root task");
        }
        try {
            process(instance.getRoot());
        } catch (Throwable t) {
            ErrorSignal sig = t instanceof HarnessException he
                    ? ErrorSignal.fromHarnessException(he, "engine")
                    : ErrorSignal.fromThrowable(t, "engine");
            applyCmd(instance.getRoot(), new TaskCommands.SetStatus(Status.FAILED), null, null, null);
            notify(instance.getRoot(), Kind.ABORT, "technical error: " + sig.reason() + " (" + sig.severity() + ")");
        }
        if (pendingTechnical != null) {
            applyCmd(instance.getRoot(), new TaskCommands.SetStatus(Status.FAILED), null, null, null);
            notify(instance.getRoot(), Kind.ABORT, "technical error: " + pendingTechnical.reason());
            pendingTechnical = null;
        }
    }

    /** Starts the configured entry loop ({@code "main"}) for one task (§15.6). */
    private void process(TaskData task) {
        process(task, Loop.MAIN);
    }

    /** Starts the named loop for one task — "main" for the root, the subloop id for split children (§15.6). */
    private void process(TaskData task, String loopId) {
        applyCmd(task, new TaskCommands.SetStatus(Status.RUNNING), loopId, null, null);
        int stackBase = callStack.size();
        try {
            runLoop(task, loopId);
        } finally {
            while (callStack.size() > stackBase) {
                callStack.pop();
            }
        }
    }

    /** What one loop item did to the loop flow (§15.6, DESIGN_Loops.md §3). */
    private enum ItemFlow {
        CONTINUE,   // next item
        RESTART,    // current loop from its first EXECUTE item
        EXIT        // leave the current loop (CLOSE fires in finally)
    }

    /**
     * Executes a configured loop per prescription (CONCEPT_Harness.md §15.6,
     * docs/working/DESIGN_Loops.md §3): START items fire exactly once on entry,
     * EXECUTE items once per iteration in list order, CATCH items on a DOMAIN
     * error, CLOSE items exactly once on exit (finally - they always fire).
     * The engine decides nothing semantically - it only interprets the
     * calculator's {@link LoopAction}.
     */
    private void runLoop(TaskData task, String loopId) {
        Loop loop = model.getLoop(loopId);
        if (loop == null && Loop.MAIN.equals(loopId)) {
            loop = HarnessModel.defaultMainLoop(); // programmatically built model without loops[]
            model.addLoop(loop);
        }
        if (loop == null && Loop.SUB.equals(loopId)) {
            loop = HarnessModel.defaultSubLoop(); // loops[] configured, but no "sub" entry
            model.addLoop(loop);
        }
        if (loop == null) {
            applyCmd(task, new TaskCommands.SetStatus(Status.FAILED), loopId, null, null);
            notify(task, Kind.ABORT, "unknown loop \"" + loopId + "\"");
            return;
        }
        loopDepth++;
        Limits.Snapshot loopEntry = limits.snapshot();   // slot consumption base for SPLIT deltas
        try {
            if (loopDepth > model.getMaxDepth()) {
                applyCmd(task, new TaskCommands.SetStatus(Status.ABORTED), loopId, null, null);
                notify(task, Kind.ABORT, "hard stop: loop depth exceeded (loop \"" + loopId + "\")");
                return;
            }
            // START: exactly once when entering this loop - never again, not even on RESTART
            for (LoopItem item : loop.itemsFor(Trigger.START)) {
                ItemFlow f = runItem(task, loop, item, loopEntry);
                if (f != ItemFlow.CONTINUE || pendingTechnical != null) {
                    return;
                }
            }
            // EXECUTE: one iteration = the whole EXECUTE list in order; RESTART repeats it
            while (true) {
                limits.countIteration();
                if (limits.isAborted(task.depth())) {
                    applyCmd(task, new TaskCommands.SetStatus(Status.ABORTED), loopId, null, null);
                    notify(task, Kind.ABORT, "hard stop: limits exceeded");
                    return;
                }
                boolean restart = false;
                for (LoopItem item : loop.itemsFor(Trigger.EXECUTE)) {
                    ItemFlow f = runItem(task, loop, item, loopEntry);
                    if (f == ItemFlow.RESTART) {
                        restart = true;
                        break;
                    }
                    if (f == ItemFlow.EXIT || pendingTechnical != null) {
                        return;
                    }
                }
                if (!restart) {
                    return; // EXECUTE list completed without RESTART -> leave the loop
                }
            }
        } finally {
            loopDepth--;
            // CLOSE: exactly once on exit - always, also after BREAK, CATCH and technical errors
            for (LoopItem item : loop.itemsFor(Trigger.CLOSE)) {
                try {
                    runItem(task, loop, item, loopEntry);
                } catch (Throwable t) {
                    notify(task, Kind.CLOSE, "close item failed: " + t.getMessage());
                }
            }
        }
    }

    /** Runs one loop item: subloop call or quest execution + action/loopAction evaluation. */
    private ItemFlow runItem(TaskData task, Loop loop, LoopItem item, Limits.Snapshot loopEntry) {
        if (item.isSubLoop()) {
            runLoop(task, item.getLoop());
            return afterSubLoop(task, loop);
        }
        CalcResult r = execute(task, item.getKind(), loop.getId(), item.getId(),
                item.getTrigger() == null ? null : item.getTrigger().name());
        if (r == null) {
            return ItemFlow.CONTINUE; // kind not modelled - skipped deterministically
        }
        LoopAction la = r.getLoopAction();
        // domain action side effects (retry/abort) - object-oriented, §15.5. A LoopSplit carries the
        // split itself as loop action, so the derived domain Split action must NOT also execute
        // (no double split)
        if (la.flow() != LoopAction.Flow.SPLIT) {
            r.getAction().execute(this, task, r);
        }
        if (item.getKind() == Kind.EVALUATE) {
            String trig = item.getTrigger() == null ? null : item.getTrigger().name();
            if (r.getScore() != null) {
                applyCmd(task, new TaskCommands.SetScore(r.getScore()), loop.getId(), item.getId(), trig);
            }
            applyCmd(task, new TaskCommands.SetStatus(r.getStatus()), loop.getId(), item.getId(), trig);
        }
        switch (la.flow()) {
            case RESTART -> {
                return ItemFlow.RESTART;
            }
            case BREAK -> {
                return ItemFlow.EXIT;
            }
            case SUBLOOP -> {
                runLoop(task, ((LoopSubLoop) la).loopId());
                return afterSubLoop(task, loop);
            }
            case SPLIT -> {
                return handleSplit(task, (LoopSplit) la, loopEntry);
            }
            case THROW -> {
                return handleThrow(task, loop, (LoopThrowError) la);
            }
            case CONTINUE -> {
                return ItemFlow.CONTINUE;
            }
        }
        return ItemFlow.CONTINUE;
    }

    /**
     * SPLIT as loop action (§15.6): creates the N subtasks (task starts) from the carried
     * sub-prompts and starts one <b>subloop</b> per subtask — {@link LoopSplit#loopId()}, default
     * {@link Loop#SUB "sub"}, never a fresh {@code "main"} loop. The shared {@link Limits}
     * instance is inherited, so every subloop sees the last counter state; after the N subloops
     * return the consumption deltas are computed: slot delta (loop entry → split point) plus the
     * N per-child deltas are summed and written as the current output (WorkCache + notify).
     */
    private ItemFlow handleSplit(TaskData task, LoopSplit split, Limits.Snapshot loopEntry) {
        List<String> prompts = split.prompts();
        if ((prompts == null || prompts.isEmpty()) && split.parts() <= 0) {
            return ItemFlow.CONTINUE;
        }
        if (prompts == null || prompts.isEmpty()) {
            // parts fallback: generated part titles from the task title
            prompts = new ArrayList<>();
            for (int i = 0; i < Math.max(1, split.parts()); i++) {
                prompts.add(task.getTitle() + " / part " + (i + 1));
            }
        }
        String loopId = split.loopId() == null || split.loopId().isBlank() ? Loop.SUB : split.loopId();
        Limits.Snapshot splitPoint = limits.snapshot();
        // §18.2: ONE composite command - rewriteAsSummary + N x addSubtask as a single
        // history entry with a single change event; the created children come back from it
        TaskCommands.SplitInto splitCmd =
                new TaskCommands.SplitInto(loopId, prompts, task.getTaskType(), instance::nextTaskId);
        applyCmd(task, splitCmd, loopId, null, null);
        List<TaskData> children = splitCmd.children();
        Limits.Delta total = limits.deltaSince(loopEntry);   // slot delta up to the split point
        List<String> childDeltas = new ArrayList<>();
        for (TaskData child : children) {
            Limits.Snapshot childBase = limits.snapshot();   // inherited state = last counters
            process(child, loopId);                           // one subloop start per subtask
            Limits.Delta d = limits.deltaSince(childBase);
            childDeltas.add("child " + child.getId() + ": " + d);
            total = total.plus(d);                            // slot + N deltas summed
        }
        // current output: summed consumption lands in the runtime WorkCache + listener notify
        if (workCache != null) {
            workCache.put("subloops", String.valueOf(children.size()));
            workCache.put("subloopId", loopId);
            workCache.put("consumption", total.toString());
            workCache.put("consumptionDeltas", String.join(" | ", childDeltas));
        }
        notify(task, Kind.SPLIT, "split into " + children.size() + " subloops (loop \"" + loopId
                + "\", task starts created), consumption = slot+children " + total);
        // legacy semantics preserved: after the N subloops the parent summarizes over them
        execute(task, Kind.SUMMARIZE);
        return ItemFlow.EXIT; // the N subloops returned - leave this loop (CLOSE fires in finally)
    }

    /** After a subloop returned: propagate pending error signals to this loop's CATCH. */
    private ItemFlow afterSubLoop(TaskData task, Loop loop) {
        if (pendingTechnical != null) {
            return ItemFlow.EXIT;
        }
        if (pendingDomain != null) {
            ErrorSignal sig = pendingDomain;
            pendingDomain = null;
            return handleThrow(task, loop, new LoopThrowError(sig));
        }
        return ItemFlow.CONTINUE;
    }

    /**
     * THROW handling (§15.6): DOMAIN fires the CATCH items of the current loop once
     * (a THROW inside CATCH unwinds one loop level up via {@link #pendingDomain});
     * TECHNICAL bypasses CATCH, fails the task hard and unwinds to the top via
     * {@link #pendingTechnical}. After CATCH the loop is left.
     */
    private ItemFlow handleThrow(TaskData task, Loop loop, LoopThrowError t) {
        ErrorSignal sig = t.signal() == null ? ErrorSignal.domain("throw", "loop") : t.signal();
        if (!sig.isDomain()) {
            applyCmd(task, new TaskCommands.SetStatus(Status.FAILED), loop.getId(), null, null);
            notify(task, Kind.ABORT, "technical error: " + sig.reason() + " -> unwind (CATCH bypassed)");
            pendingTechnical = sig;
            return ItemFlow.EXIT;
        }
        applyCmd(task, new TaskCommands.SetStatus(Status.FAILED), loop.getId(), null, null);
        List<LoopItem> catchItems = loop.itemsFor(Trigger.CATCH);
        if (catchItems.isEmpty()) {
            // no handler in this loop - unwind one level up so the caller loop's CATCH can handle it
            pendingDomain = sig;
            return ItemFlow.EXIT;
        }
        notify(task, Kind.ABORT, "domain error: " + sig.reason() + " -> CATCH of loop \"" + loop.getId() + "\"");
        for (LoopItem c : catchItems) {
            if (c.isSubLoop()) {
                runLoop(task, c.getLoop());
            } else {
                CalcResult r = execute(task, c.getKind(), loop.getId(), c.getId(),
                        c.getTrigger() == null ? null : c.getTrigger().name());
                if (r != null) {
                    r.getAction().execute(this, task, r);
                    LoopAction cla = r.getLoopAction();
                    if (cla.flow() == LoopAction.Flow.RESTART) {
                        return ItemFlow.RESTART;
                    }
                    if (cla.flow() == LoopAction.Flow.THROW) {
                        // Throw inside CATCH: unwind one loop level up, do not re-fire CATCH here
                        pendingDomain = ((LoopThrowError) cla).signal();
                        return ItemFlow.EXIT;
                    }
                    if (cla.flow() == LoopAction.Flow.SUBLOOP) {
                        runLoop(task, ((LoopSubLoop) cla).loopId());
                    }
                    // CONTINUE / BREAK inside CATCH: BREAK leaves the loop
                    if (cla.flow() == LoopAction.Flow.BREAK) {
                        return ItemFlow.EXIT;
                    }
                }
            }
            if (pendingTechnical != null) {
                return ItemFlow.EXIT;
            }
        }
        return ItemFlow.EXIT; // CATCH handled - leave the loop (CLOSE fires in finally)
    }

    //
    // HarnessDriver - the operations HarnessActions execute (§15.5)
    //

    @Override
    public void addReasoningSubtask(TaskData task) {
        TaskCommands.AddSubtask cmd = new TaskCommands.AddSubtask(instance.nextTaskId(), "REASONING",
                "reasoning about " + task.getTitle().replaceAll("\\?+$", ""));
        applyCmd(task, cmd, null, null, null);
        process(cmd.created());
    }

    @Override
    public void retry(TaskData task, String reason) {
        limits.countRetry();
        applyCmd(task, new TaskCommands.IncrementAttempt(), null, null, null);
        applyCmd(task, new TaskCommands.SetStatus(Status.RETRY), null, null, null);
        applyCmd(task, new TaskCommands.AppendPromptSupplement("retry " + task.getAttempt()), null, null, null);
    }

    @Override
    public void abort(TaskData task, String reason) {
        applyCmd(task, new TaskCommands.SetStatus(Status.ABORTED), null, null, null);
        notify(task, Kind.ABORT, reason);
    }

    /**
     * Executes one calculator call for a task: resolves quest → base slot → calculator
     * node, runs it (puzzle nodes recursively in sequence), and appends the
     * metrics-only log entry with the parent1-redundant / parent2-link chain (§6).
     * A {@link HarnessException} is caught here (catch-mapping §13) and falls back to
     * the deterministic calculator of the same kind.
     */
    private CalcResult execute(TaskData task, Kind kind) {
        return execute(task, kind, null, null, null);
    }

    /**
     * Executes the quest of {@code kind} for one task. The loop context
     * ({@code loopId}/{@code loopItemId}/{@code trigger}) lands on the {@link CallLogEntry}
     * so the metrics can aggregate the loop structure under {@code "loop:…"} keys (§6).
     */
    private CalcResult execute(TaskData task, Kind kind, String loopId, String loopItemId, String trigger) {
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
        ctx.setWorkCache(workCache);
        ctx.setProjectMemory(projectMemory);
        ctx.setLoopContext(loopId, loopItemId, trigger);   // §18 origin triple for commands

        long start = System.currentTimeMillis();
        CalcResult result;
        String decisionSuffix = "";
        String paramError = null;
        try {
            result = runNode(node, kind, ctx);
            ctx.emit(de.jare.harness.signals.Signal.ANSWER_RECEIVED);
        } catch (HarnessException e) {
            decisionSuffix = "catch:" + e.getType();
            if (e.getType() == HarnessException.Type.PARAMETER) {
                // parameter errors are counted per slot + param (PARAM_ERROR metric, §6)
                paramError = e.getParamKey() != null ? e.getParamKey() : e.getMessage();
            }
            notify(task, kind, decisionSuffix + " -> catch-mapping");
            CalculatorNode catchNode = node.getCatchMapping().get(e.getType());
            if (catchNode != null) {
                result = runNode(catchNode, kind, ctx);
                decisionSuffix += "+handled";
            } else {
                result = DeterministicFallback.run(kind, ctx, e);
            }
        } catch (Throwable t) {
            // Fangnetz 1 (§15.6): a raw Throwable never crosses the loop boundary - it becomes
            // a TECHNICAL ErrorSignal carried as LoopAction, so the loop unwinds cleanly
            ErrorSignal sig = ErrorSignal.fromThrowable(t, kind.name());
            notify(task, kind, "technical error: " + sig.reason() + " -> ThrowError(TECHNICAL)");
            result = CalcResult.of(kind, "throw")
                    .status(Status.FAILED)
                    .message(sig.reason())
                    .loopAction(new LoopThrowError(sig));
        }
        long dauerMs = System.currentTimeMillis() - start;

        CallLogEntry entry = new CallLogEntry(aufrufId, task.getId(), task.getTaskType(), kind,
                slot.getId(), model.getVersion(), task.getAttempt(), dauerMs, 0, 0,
                result.getScore(), result.getStatus(),
                result.getDecision() + decisionSuffix, result.getMessage(), result.getPayload(), p1, p2,
                loopId, loopItemId, trigger, paramError);
        // layering (§3): instance.logCall = RUNTIME truth (memory layer), the LogCall command = the
        // bridge write into the PERSISTED PROJECTION (TaskData, recorded in the CommandHistory §18)
        applyCmd(task, new TaskCommands.LogCall(entry), loopId, loopItemId, trigger);
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
            // RAG_RETRIEVE enrichment is best-effort: an empty store or an embedder outage
            // must never kill the run - the DATA/SKILL segments are simply left out
            try {
                var rag = activeRag();   // the active rag model's store (§11), not always the hash fallback
                if (!rag.getChunks().isEmpty()) {
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
            } catch (Throwable ragDown) {
                notify(task, kind, "rag retrieve skipped: " + ragDown.getMessage());
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
        // §17: the project memory across runs - CHAT entries as HISTORY, NUGGETs as DATA,
        // DECISIONs as SYSTEM (standing instructions steer every run) -
        // the assembler enforces the shared per-category budgets
        if (projectMemory != null) {
            long now = System.currentTimeMillis();
            for (de.jare.harness.memory.ProjectMemory.Nugget n : projectMemory.live(now)) {
                usedMemoryIds.add(n.id);   // §17 feedback: this entry went into a prompt
                if (n.kind == de.jare.harness.memory.ProjectMemory.Kind.CHAT) {
                    resolved.add(PromptSegment.of(PromptSegment.Category.HISTORY, "memory:chat-" + n.id,
                            n.role + ": " + n.text));
                } else if (n.kind == de.jare.harness.memory.ProjectMemory.Kind.DECISION) {
                    resolved.add(PromptSegment.of(PromptSegment.Category.SYSTEM, "memory:decision-" + n.id,
                            "decision: " + n.text));
                } else {
                    resolved.add(PromptSegment.of(PromptSegment.Category.DATA, "memory:nugget-" + n.id,
                            n.text + (n.source != null ? " (source: " + n.source + ")" : "")));
                }
            }
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
            ctx.setWorkCache(workCache);
            ctx.setProjectMemory(projectMemory);
            runNode(mapped, kind, ctx);
        }
    }

    /** Runs a calculator node; puzzle nodes run their children in sequence (§5 recursion). */
    private CalcResult runNode(CalculatorNode node, Kind kind, CalcContext ctx) {
        if (node.isPuzzle()) {
            CalcResult last = CalcResult.of(kind, "puzzle");
            LoopAction steering = null;
            for (CalculatorNode child : node.getChildren()) {
                CalcResult r = runNode(child, kind, ctx);
                // result channel (§3 layering): thread each child's CalcResult through the
                // runtime WorkCache - the next sibling reads it via ctx.workCache()
                if (ctx.workCache() != null) {
                    String name = child.getCalculator() != null ? child.getCalculator()
                            : (child.getImpl() != null ? child.getImpl() : child.getType());
                    ctx.workCache().putResult(name, r).setLastResult(r);
                }
                // a steering loop action of ANY child (e.g. the LoopSplit of askai.decompose)
                // must survive the puzzle - the loop acts on it, not on the last child's plain result
                if (steering == null && r.getLoopAction().flow() != LoopAction.Flow.CONTINUE) {
                    steering = r.getLoopAction();
                }
                last = r;
            }
            if (steering != null && last.getLoopAction().flow() == LoopAction.Flow.CONTINUE) {
                last.loopAction(steering);
            }
            return last;
        }
        String calcName = node.getCalculator() != null ? node.getCalculator()
                : (node.getImpl() != null ? node.getImpl() : node.getType());
        CalcResult res = factory.createAtomic(node, kind).calculate(ctx);
        if (ctx.workCache() != null) {
            ctx.workCache().putResult(calcName, res).setLastResult(res);
        }
        // §3 projection: every atomic calculator step lands as a "call" child node in the task
        // tree (input prompts, CalcResult payload, LoopAction) so the JTree shows the data
        // movement - as a command on the ctx channel (§18), recorded with this call's origin
        ctx.commands().apply(new TaskCommands.AppendCalcCall(calcName, kind, ctx.slotId(), res, ctx.prompts()));
        return res;
    }

    /** The deterministic fallback used by the catch-mapping (§13). */
    private static final class DeterministicFallback {
        static CalcResult run(Kind kind, CalcContext ctx, HarnessException e) {
            CalcResult r;
            try {
                r = de.jare.harness.impl.deterministic.DeterministicCalculators.forKind(kind).calculate(ctx);
            } catch (Throwable t) {
                // the fallback itself must never throw - a second failure is just logged
                r = CalcResult.of(kind, "fallback-failed")
                        .message("fallback also failed: " + t.getMessage());
            }
            return r.decision(r.getDecision()).status(Status.FAILED).message("fallback after " + e.getType());
        }
    }
}
