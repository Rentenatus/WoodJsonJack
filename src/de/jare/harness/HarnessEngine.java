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

    private final HarnessModel model;
    private final HarnessInstance instance;
    private final CalculatorFactory factory;
    private final Limits limits;
    private final Deque<CallLogEntry> callStack = new ArrayDeque<>();
    private final List<BiConsumer<TaskNode, String>> listeners = new ArrayList<>();
    private final RelevanceRegistry relevanceRegistry;
    private final PromptAssembler promptAssembler;

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

    public void addListener(BiConsumer<TaskNode, String> listener) {
        listeners.add(listener);
    }

    public void notify(TaskNode task, Kind kind, String text) {
        String msg = "[task " + task.getId() + " / " + kind + "] " + text;
        for (BiConsumer<TaskNode, String> l : listeners) {
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
    private void process(TaskNode task) {
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

    private void processLoop(TaskNode task) {
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
    private CalcResult execute(TaskNode task, Kind kind) {
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
        List<PromptSegment> resolved = relevanceRegistry.resolve(task.getTaskType(), task.getAttempt(),
                kind, model.getVersion(), auftrag);
        List<String> prompts = PromptAssembler.toTexts(promptAssembler.assemble(resolved));
        CalcContext ctx = new CalcContext(this, instance, model, task, kind, slot.getId(), node, limits, prompts);

        long start = System.currentTimeMillis();
        CalcResult result;
        String decisionSuffix = "";
        try {
            result = runNode(node, kind, ctx);
        } catch (HarnessException e) {
            decisionSuffix = "catch:" + e.getType();
            notify(task, kind, decisionSuffix + " -> deterministic fallback");
            result = DeterministicFallback.run(kind, ctx, e);
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
