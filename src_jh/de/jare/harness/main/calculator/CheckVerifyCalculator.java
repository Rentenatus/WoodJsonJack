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
import de.jare.harness.TaskCommands;
import de.jare.harness.TaskData;
import de.jare.harness.impl.AiAnswers;
import de.jare.harness.impl.ModelProvider;
import de.jare.harness.main.actions.LoopRestart;
import de.jare.harness.model.Modelclass;
import de.jare.harness.prompts.PromptFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The named calculator {@code "check.verify"}: the AI verifier of CHECK. It runs on the
 * WHOLE answer (also after a split - check/evaluate are NOT skipped after "split") and
 * decides {@code close:true|false}: is the answer correct and complete enough to close
 * the run against the user's original request?
 *
 * Prompt data (config {@code "systemPrompt"}, placeholders via WorkCache):
 * {@code {data.userPrompt}}, {@code {data.answer}}, {@code {data.evaluation}} (the
 * chained EVALUATE results dimension=score/verdict) and {@code {data.supplement}} (the
 * improvement note of a previous failed round, if any).
 *
 * Answer contract (strict JSON): {@code {"status":"DONE","decision":"close|retry",
 * "score":0-100,"payload":{"close":"true|false","reason":"..."}}}.
 *
 * close=false -> an {@link TaskCommands.AppendPromptSupplement} command carries the
 * improvement instruction into the next loop iteration (the model then sees the original
 * prompt + supplement) and the result steers {@link LoopRestart}. close=true -> plain
 * DONE, the loop may finish and CLOSE fires.
 *
 * Fallback: on MODEL_UNAVAILABLE / SCHEMA_VIOLATION the old deterministic gate is used
 * (task score >= 50 -> close, else retry).
 *
 * @author Janusch Rentenatus
 */
public class CheckVerifyCalculator implements HarnessCalculator {

    /** Deterministic fallback threshold (old CHECK behavior). */
    public static final int MIN_SCORE = 50;

    private final Kind kind;
    private final Modelclass modelclass;
    private final ModelProvider provider;
    private final String systemPrompt;

    public CheckVerifyCalculator(Kind kind, Modelclass modelclass, ModelProvider provider, String systemPrompt) {
        this.kind = kind;
        this.modelclass = modelclass;
        this.provider = provider;
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new HarnessException(HarnessException.Type.PARAMETER,
                    "check.verify needs the config param \"systemPrompt\" (prompts live only in the config JSON)",
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
     * answer, so no live Ollama is needed.
     */
    protected AiAnswers.Parsed callModel(CalcContext ctx, List<String> segments, String emphasisPrompt) {
        return AiAnswers.callAndParse(ctx, provider, modelclass, segments, emphasisPrompt);
    }

    @Override
    public CalcResult calculate(CalcContext ctx) {
        String userPrompt = String.join("\n", PromptFormatter.userPromptOnly(ctx)).strip();
        String answer = ctx.workCache() != null ? ctx.workCache().get("summary") : null;
        if (answer == null || answer.isBlank()) {
            answer = Answers.answerOf(ctx.task());
        }
        StringBuilder eval = new StringBuilder();
        for (TaskData.CalcCall c : ctx.task().getCalls()) {
            if ("EVALUATE".equalsIgnoreCase(c.getKind())) {
                eval.append(c.getPayload().getOrDefault("dimension", c.getDecision()))
                        .append("=score ").append(c.getScore())
                        .append(" ").append(c.getPayload().getOrDefault("verdict", ""))
                        .append('\n');
            }
        }
        String supplement = ctx.task().getPromptSupplement() == null ? "" : ctx.task().getPromptSupplement();
        if (ctx.workCache() != null) {
            ctx.workCache().put("userPrompt", userPrompt);
            ctx.workCache().put("answer", answer);
            ctx.workCache().put("evaluation", eval.toString().strip());
            ctx.workCache().put("supplement", supplement.strip());
        }
        try {
            List<String> segments = new ArrayList<>();
            segments.add("SYSTEM:check.verify\n" + PromptFormatter.format(systemPrompt, ctx.workCache()));
            segments.addAll(ctx.conversation());   // AUFTRAG/ANTWORT/BEWERTUNG/ZUSATZ
            AiAnswers.Parsed parsed = callModel(ctx, segments, null);
            var root = parsed.root();
            var payload = root.asObjectValues().get("payload");
            String closeText = payload == null ? null : AiAnswers.text(payload, "close", "true");
            // NB: AiAnswers.text returns null for non-text (boolean) JSON nodes - null-safe
            boolean close = closeText == null || closeText.equalsIgnoreCase("true");
            String reason = payload == null ? "" : AiAnswers.text(payload, "reason", "");
            if (reason == null) {
                reason = "";
            }
            CalcResult r = CalcResult.of(kind, close ? "close" : "retry")
                    .status(Status.DONE)
                    .put("close", String.valueOf(close))
                    .put("reason", reason);
            Integer score = AiAnswers.score(root, "score");
            if (score != null) {
                r.score(score);
            }
            if (!close) {
                // feedback for the next iteration: the model sees original prompt + this note
                ctx.commands().apply(new TaskCommands.AppendPromptSupplement(
                        "Previous answer was rejected: " + reason));
                r.loopAction(new LoopRestart());
            }
            return r;
        } catch (HarnessException e) {
            if (e.getType() != HarnessException.Type.MODEL_UNAVAILABLE
                    && e.getType() != HarnessException.Type.SCHEMA_VIOLATION) {
                throw e;
            }
            // fallback: old deterministic gate on the task score
            Integer score = ctx.task().getScore();
            boolean close = score != null && score >= MIN_SCORE;
            CalcResult r = CalcResult.of(kind, close ? "close" : "retry-fallback")
                    .status(Status.DONE)
                    .put("close", String.valueOf(close))
                    .message("fallback after " + e.getType());
            if (!close) {
                ctx.commands().apply(new TaskCommands.AppendPromptSupplement(
                        "fallback: score " + score + " < " + MIN_SCORE));
                r.loopAction(new LoopRestart());
            }
            return r;
        }
    }
}
