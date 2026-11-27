/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.main.calculator;

import de.jare.harness.Answers;
import de.jare.harness.CalcContext;
import de.jare.harness.CalcResult;
import de.jare.harness.HarnessCalculator;
import de.jare.harness.HarnessException;
import de.jare.harness.Kind;
import de.jare.harness.Status;
import de.jare.harness.impl.AiAnswers;
import de.jare.harness.impl.ModelProvider;
import de.jare.harness.model.Modelclass;
import de.jare.harness.prompts.PromptFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The named calculator {@code "evaluate.score"}: ONE AI evaluator of the EVALUATE slot's
 * puzzle sequence (several of them run chained: completeness, correctness, readability).
 * It scores the ASSISTANT ANSWER against the USER PROMPT on its {@code "dimension"} -
 * the answer itself, never the prompt's completeness (that is COMPLETE's job).
 *
 * Prompt data (config {@code "systemPrompt"}, placeholders via WorkCache):
 * {@code {data.userPrompt}} and {@code {data.answer}} (the summary from workCache, else the
 * joined leaf answers of the task tree).
 *
 * Answer contract (strict JSON): {@code {"status":"DONE","decision":"scored","score":0-100,
 * "payload":{"dimension":"...","verdict":"..."}}} - the score lands on the CalcResult, the
 * engine applies it via SetScore; the aggregate child then takes the minimum.
 *
 * Fallback: on MODEL_UNAVAILABLE / SCHEMA_VIOLATION the old deterministic default 80 is
 * returned, so a down evaluator never fails the run.
 *
 * @author Janusch Rentenatus
 */
public class EvaluateScoreCalculator implements HarnessCalculator {

    /** Old deterministic default when the evaluator model is unavailable. */
    public static final int FALLBACK_SCORE = 80;

    private final Kind kind;
    private final Modelclass modelclass;
    private final ModelProvider provider;
    private final String dimension;
    private final String systemPrompt;

    public EvaluateScoreCalculator(Kind kind, Modelclass modelclass, ModelProvider provider,
                                   String dimension, String systemPrompt) {
        this.kind = kind;
        this.modelclass = modelclass;
        this.provider = provider;
        this.dimension = dimension == null || dimension.isBlank() ? "general" : dimension;
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new HarnessException(HarnessException.Type.PARAMETER,
                    "evaluate.score needs the config param \"systemPrompt\" (prompts live only in the config JSON)",
                    "systemPrompt");
        }
        this.systemPrompt = systemPrompt;
    }

    @Override
    public Kind kind() {
        return kind;
    }

    /**
     * The model call - extracted as an overridable seam: tests subclass and return a canned
     * answer, so no live Ollama is needed. Input assembly (segments) and result processing
     * stay separate steps around this call.
     */
    protected AiAnswers.Parsed callModel(CalcContext ctx, List<String> segments, String emphasisPrompt) {
        return AiAnswers.callAndParse(ctx, provider, modelclass, segments, emphasisPrompt);
    }

    @Override
    public CalcResult calculate(CalcContext ctx) {
        String userPrompt = String.join("\n", PromptFormatter.userPromptOnly(ctx)).strip();
        String answer = ctx.workCache() != null ? ctx.workCache().get("summary") : null;
        if (answer == null || answer.isBlank()) {
            List<String> leaves = Answers.leafAnswers(ctx.task());
            answer = leaves.isEmpty() ? String.valueOf(ctx.task().getArtifact()) : String.join(" | ", leaves);
        }
        if (ctx.workCache() != null) {
            ctx.workCache().put("userPrompt", userPrompt);
            ctx.workCache().put("answer", answer);
            ctx.workCache().put("dimension", dimension);
        }
        try {
            List<String> segments = new ArrayList<>();
            segments.add("SYSTEM:evaluate.score:" + dimension + "\n"
                    + PromptFormatter.format(systemPrompt, ctx.workCache()));
            segments.addAll(ctx.conversation());   // AUFTRAG/ANTWORT/BEWERTUNG/ZUSATZ
            AiAnswers.Parsed parsed = AiAnswers.callAndParse(ctx, provider, modelclass, segments, null);
            var root = parsed.root();
            Integer score = AiAnswers.score(root, "score");
            if (score == null) {
                var payload = root.asObjectValues().get("payload");
                String s = payload == null ? null : AiAnswers.text(payload, "score", null);
                if (s != null) {
                    try {
                        score = Integer.valueOf(s.trim());
                    } catch (NumberFormatException ignored) {
                        score = null;
                    }
                }
            }
            if (score == null) {
                throw new HarnessException(HarnessException.Type.SCHEMA_VIOLATION,
                        "evaluate.score answer carries no score: " + AiAnswers.abbrev(parsed.rawAnswer()));
            }
            score = Math.max(0, Math.min(100, score));
            var payload = root.asObjectValues().get("payload");
            return CalcResult.of(kind, AiAnswers.text(root, "decision", "scored"))
                    .status(Status.DONE)
                    .score(score)
                    .put("dimension", dimension)
                    .put("verdict", payload == null ? "" : AiAnswers.text(payload, "verdict", ""));
        } catch (HarnessException e) {
            if (e.getType() != HarnessException.Type.MODEL_UNAVAILABLE
                    && e.getType() != HarnessException.Type.SCHEMA_VIOLATION) {
                throw e;
            }
            return CalcResult.of(kind, "scored-fallback")
                    .status(Status.DONE)
                    .score(FALLBACK_SCORE)
                    .put("dimension", dimension)
                    .message("fallback score " + FALLBACK_SCORE + " after " + e.getType());
        }
    }
}
