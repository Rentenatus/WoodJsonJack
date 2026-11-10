/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.impl.deterministic;

import de.jare.harness.CalcContext;
import de.jare.harness.CalcResult;
import de.jare.harness.HarnessCalculator;
import de.jare.harness.HarnessEngine;
import de.jare.harness.Kind;
import de.jare.harness.Status;
import de.jare.harness.TaskData;
import java.util.ArrayList;
import java.util.List;

/**
 * The deterministic implementations of all twelve kinds (CONCEPT_Harness.md §2).
 * Deterministic stays always as fallback - also for AI calculators on schema
 * violation or provider outage (§2, §13 catch-mapping).
 *
 * @author Janusch Rentenatus
 */
public final class DeterministicCalculators {

    private DeterministicCalculators() {
    }

    public static HarnessCalculator forKind(Kind kind) {
        return switch (kind) {
            case RELEVANCE -> calc(kind, DeterministicCalculators::relevance);
            case SPLIT -> calc(kind, DeterministicCalculators::split);
            case COMPLETE -> calc(kind, DeterministicCalculators::complete);
            case WORK -> calc(kind, DeterministicCalculators::work);
            case EVALUATE -> calc(kind, DeterministicCalculators::evaluate);
            case CHECK -> calc(kind, DeterministicCalculators::check);
            case ABORT -> calc(kind, DeterministicCalculators::abort);
            case SUMMARIZE -> calc(kind, DeterministicCalculators::summarize);
            case CLOSE -> calc(kind, DeterministicCalculators::close);
            case RAG_STORE -> calc(kind, DeterministicCalculators::ragStore);
            case RAG_RETRIEVE -> calc(kind, DeterministicCalculators::ragRetrieve);
            case RAG_PRUNE -> calc(kind, DeterministicCalculators::ragPrune);
        };
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
        de.jare.harness.rag.RagChunk chunk = ctx.rag().store("RESULT", document, meta);
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
