/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.impl.deterministic;

import de.jare.harness.CalcContext;
import de.jare.harness.CalcResult;
import de.jare.harness.ErrorSignal;
import de.jare.harness.HarnessCalculator;
import de.jare.harness.HarnessEngine;
import de.jare.harness.Kind;
import de.jare.harness.Status;
import de.jare.harness.TaskData;
import de.jare.harness.main.actions.LoopThrowError;
import de.jare.harness.model.Modelclass;
import de.jare.harness.skills.Skill;
import java.util.ArrayList;
import java.util.List;

/**
 * The deterministic implementations of all fourteen kinds (CONCEPT_Harness.md §2).
 * Deterministic stays always as fallback - also for AI calculators on schema
 * violation or provider outage (§2, §13 catch-mapping).
 *
 * @author Janusch Rentenatus
 */
public final class DeterministicCalculators {

    private DeterministicCalculators() {
    }

    private static final java.util.Map<Kind, java.util.function.Function<CalcContext, CalcResult>> IMPLS =
            new java.util.LinkedHashMap<>();

    static {
        IMPLS.put(Kind.RELEVANCE, DeterministicCalculators::relevance);
        IMPLS.put(Kind.SPLIT, DeterministicCalculators::split);
        IMPLS.put(Kind.COMPLETE, DeterministicCalculators::complete);
        IMPLS.put(Kind.WORK, DeterministicCalculators::work);
        IMPLS.put(Kind.EVALUATE, DeterministicCalculators::evaluate);
        IMPLS.put(Kind.CHECK, DeterministicCalculators::check);
        IMPLS.put(Kind.ABORT, DeterministicCalculators::abort);
        IMPLS.put(Kind.SUMMARIZE, DeterministicCalculators::summarize);
        IMPLS.put(Kind.CLOSE, DeterministicCalculators::close);
        IMPLS.put(Kind.RAG_STORE, DeterministicCalculators::ragStore);
        IMPLS.put(Kind.RAG_RETRIEVE, DeterministicCalculators::ragRetrieve);
        IMPLS.put(Kind.RAG_PRUNE, DeterministicCalculators::ragPrune);
        IMPLS.put(Kind.GUARD, DeterministicCalculators::guard);
        IMPLS.put(Kind.SKILL, DeterministicCalculators::collectSkills);
    }

    public static HarnessCalculator forKind(Kind kind) {
        // custom kinddefs have no built-in impl - the generic deterministic fallback answers "done"
        java.util.function.Function<CalcContext, CalcResult> fn =
                IMPLS.getOrDefault(kind, DeterministicCalculators::generic);
        return calc(kind, fn);
    }

    /** Generic deterministic answer for kinds declared only via {@code kinddefs[]} (no built-in impl). */
    private static CalcResult generic(CalcContext ctx) {
        return CalcResult.of(ctx.kind(), "done").status(Status.DONE);
    }

    private static HarnessCalculator calc(Kind kind,
            java.util.function.Function<CalcContext, CalcResult> fn) {
        return new HarnessCalculator() {
            @Override
            public Kind kind() {
                return kind;
            }

            @Override
            public CalcResult calculate(CalcContext ctx) {
                return fn.apply(ctx);
            }
        };
    }

    private static CalcResult relevance(CalcContext ctx) {
        return CalcResult.of(Kind.RELEVANCE, "segments")
                .put("count", String.valueOf(ctx.prompts().size()));
    }

    private static CalcResult split(CalcContext ctx) {
        return CalcResult.of(Kind.SPLIT, "nosplit");
    }

    private static CalcResult complete(CalcContext ctx) {
        // deterministic default: a task whose title ends with "?" is not yet complete -
        // unless a reasoning subtask already exists, then the loop must not repeat
        boolean complete = !ctx.task().getTitle().endsWith("?") || !ctx.task().getChildren().isEmpty();
        return CalcResult.of(Kind.COMPLETE, complete ? "complete" : "incomplete");
    }

    private static CalcResult work(CalcContext ctx) {
        TaskData t = ctx.task();
        String artifact = "worked:" + t.getTitle();
        return CalcResult.of(Kind.WORK, "worked").put("artifact", artifact).status(Status.DONE);
    }

    private static CalcResult evaluate(CalcContext ctx) {
        int score = ctx.task().getChildren().isEmpty() ? 80 : 70;
        return CalcResult.of(Kind.EVALUATE, "scored").score(score).status(Status.DONE);
    }

    private static CalcResult check(CalcContext ctx) {
        Integer score = ctx.task().getScore();
        boolean ok = score != null && score >= 50;
        return CalcResult.of(Kind.CHECK, ok ? "done" : "retry");
    }

    private static CalcResult abort(CalcContext ctx) {
        boolean aborted = ctx.limits().isAborted(ctx.task().depth());
        return CalcResult.of(Kind.ABORT, aborted ? "abort" : "continue");
    }

    /**
     * GUARD (§15.6): the deterministic entry guard of the main loop's START item. A blank
     * prompt is a DOMAIN {@link ErrorSignal} carried as {@link LoopThrowError}, so the loop's
     * CATCH fires; likewise when modelclasses are configured but none is available (a model
     * without any modelclass is a deterministic-only test model and passes). Otherwise DONE
     * with the count of available models.
     */
    private static CalcResult guard(CalcContext ctx) {
        if (ctx.task().getTitle() == null || ctx.task().getTitle().isBlank()) {
            return CalcResult.of(Kind.GUARD, "guard-failed").status(Status.FAILED)
                    .message("blank prompt")
                    .loopAction(new LoopThrowError(ErrorSignal.domain("blank prompt", "guard")));
        }
        long available = ctx.model().getModelclasses().stream().filter(Modelclass::isAvailable).count();
        if (!ctx.model().getModelclasses().isEmpty() && available == 0) {
            return CalcResult.of(Kind.GUARD, "guard-failed").status(Status.FAILED)
                    .message("no model available")
                    .loopAction(new LoopThrowError(ErrorSignal.domain("no model available", "guard")));
        }
        return CalcResult.of(Kind.GUARD, "guards-ok").status(Status.DONE)
                .put("modelsAvailable", String.valueOf(available));
    }

    /**
     * SKILL (§12, §15.6): collects the current skills of the run's {@code SkillRegistry}
     * (global + project-local, loaded by HarnessService) into the WorkCache, so following
     * slots/prompts can reference them ({@code skills.count}, {@code skills.ids},
     * {@code skills.descriptions}).
     */
    public static CalcResult collectSkills(CalcContext ctx) {
        List<Skill> skills = ctx.skills() == null ? List.of() : ctx.skills().all();
        List<String> ids = new ArrayList<>();
        int descriptions = 0;
        for (Skill s : skills) {
            ids.add(s.getSkillId());
            descriptions += s.getDescriptions().size();
        }
        if (ctx.workCache() != null) {
            ctx.workCache().put("skills.count", String.valueOf(skills.size()));
            ctx.workCache().put("skills.ids", String.join(",", ids));
            ctx.workCache().put("skills.descriptions", String.valueOf(descriptions));
        }
        return CalcResult.of(Kind.SKILL, "collected").status(Status.DONE)
                .put("count", String.valueOf(skills.size()))
                .put("ids", String.join(",", ids));
    }

    private static CalcResult summarize(CalcContext ctx) {
        // guard moved from the engine into the calculator (§15.6): nothing to summarize without children
        if (ctx.task().getChildren().isEmpty()) {
            return CalcResult.of(Kind.SUMMARIZE, "skipped").put("reason", "no-children").status(Status.DONE);
        }
        List<String> parts = new ArrayList<>();
        for (TaskData c : ctx.task().getChildren()) {
            if (c.getArtifact() != null) {
                parts.add(c.getArtifact());
            }
        }
        return CalcResult.of(Kind.SUMMARIZE, "summarized")
                .put("summary", String.join(" | ", parts));
    }

    /**
     * RAG_STORE (§11): writes the task's result as a RESULT chunk with meta
     * {taskId, taskType, kind, score, ts}. Guard (§15.6): only on success - score ≥ minScore
     * (slot param {@code minScore}, default 50 on the 0-100 scale); the loop runs every item,
     * so the calculator decides. The store itself just stores + dedups by contentHash.
     */
    private static CalcResult ragStore(CalcContext ctx) {
        TaskData t = ctx.task();
        int minScore = HarnessEngine.RAG_STORE_MIN_SCORE;
        String p = ctx.calculatorNode() == null ? null : ctx.calculatorNode().getParams().get("minScore");
        if (p != null) {
            try {
                minScore = Integer.parseInt(p);
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        if (t.getScore() == null || t.getScore() < minScore) {
            return CalcResult.of(Kind.RAG_STORE, "skipped")
                    .put("reason", "below-minScore").status(Status.DONE);
        }
        String document = (t.getArtifact() == null ? "worked:" + t.getTitle() : t.getArtifact())
                + (t.getPromptSupplement().isEmpty() ? "" : "\n" + t.getPromptSupplement());
        java.util.Map<String, String> meta = new java.util.LinkedHashMap<>();
        meta.put("taskId", String.valueOf(t.getId()));
        meta.put("taskType", t.getTaskType());
        meta.put("kind", ctx.kind().name());
        if (t.getScore() != null) {
            meta.put("score", String.valueOf(t.getScore()));
        }
        meta.put("ts", String.valueOf(System.currentTimeMillis()));
        de.jare.harness.rag.RagChunk chunk;
        try {
            chunk = ctx.rag().store("RESULT", document, meta);
        } catch (Throwable storeFail) {
            // the RAG store is a side channel - an embedder outage must not fail the run
            return CalcResult.of(Kind.RAG_STORE, "store-failed").status(Status.DONE)
                    .put("reason", String.valueOf(storeFail.getMessage()));
        }
        return CalcResult.of(Kind.RAG_STORE, "stored").put("chunkId", String.valueOf(chunk.getId()));
    }

    /** RAG_RETRIEVE (§11): vector similarity only, top-k with hard budget → DATA segments. */
    private static CalcResult ragRetrieve(CalcContext ctx) {
        var defaults = ctx.rag().getDefaults();
        var hits = ctx.rag().retrieve(ctx.task().getTitle(), defaults.topK(), defaults.minScore());
        StringBuilder sb = new StringBuilder();
        for (var h : hits) {
            sb.append(h.chunk().getDocument()).append('\n');
        }
        return CalcResult.of(Kind.RAG_RETRIEVE, "retrieved")
                .put("count", String.valueOf(hits.size()))
                .put("documents", sb.toString().trim());
    }

    /** RAG_PRUNE (§11): dedup/supersede/ttl/size budget - runs e.g. after each CLOSE. */
    private static CalcResult ragPrune(CalcContext ctx) {
        int removed = ctx.rag().prune();
        return CalcResult.of(Kind.RAG_PRUNE, "pruned").put("removed", String.valueOf(removed));
    }

    private static CalcResult close(CalcContext ctx) {
        String artifact = ctx.task().getChildren().isEmpty()
                ? "closed:" + ctx.task().getTitle()
                : "closed:summary";
        ctx.task().close(artifact);
        return CalcResult.of(Kind.CLOSE, "closed").put("artifact", artifact).status(Status.DONE);
    }
}
