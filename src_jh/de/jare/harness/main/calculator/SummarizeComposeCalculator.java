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
import de.jare.harness.TaskView;
import de.jare.harness.impl.AiAnswers;
import de.jare.harness.impl.ModelProvider;
import de.jare.harness.model.Modelclass;
import de.jare.harness.prompts.PromptFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The named calculator {@code "summarize.compose"}: composes the split sub-answers into ONE
 * readable final answer with a genAI model (param {@code "model"}, fallback purpose "plan").
 * It is a child of the SPLIT slot, so the SPLIT kind is self-contained and reusable - the
 * engine runs this child after the subloops returned (see HarnessEngine.handleSplit).
 *
 * Prompt data (all in the config {@code "systemPrompt"}, placeholders resolved against the
 * runtime WorkCache): {@code {data.userPrompt}}, {@code {data.subPrompts}},
 * {@code {data.answers}} - the calculator fills them before formatting.
 *
 * Answer contract (strict JSON): {@code {"status":"DONE","decision":"summarized","score":0-100,
 * "payload":{"summary":"..."}}}. The summary lands in the CalcResult payload and in
 * workCache {@code "summary"} (the EVALUATE calculators read it from there).
 *
 * Fallback: on MODEL_UNAVAILABLE / SCHEMA_VIOLATION the deterministic join (" | ") is used,
 * so a split run never fails because the composer model is down.
 *
 * @author Janusch Rentenatus
 */
public class SummarizeComposeCalculator implements HarnessCalculator {

    private final Kind kind;
    private final Modelclass modelclass;
    private final ModelProvider provider;
    private final String systemPrompt;

    public SummarizeComposeCalculator(Kind kind, Modelclass modelclass, ModelProvider provider, String systemPrompt) {
        this.kind = kind;
        this.modelclass = modelclass;
        this.provider = provider;
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new HarnessException(HarnessException.Type.PARAMETER,
                    "summarize.compose needs the config param \"systemPrompt\" (prompts live only in the config JSON)",
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
        List<String> answers = Answers.leafAnswers(ctx.task());
        if (ctx.task().getChildren().isEmpty() || answers.isEmpty()) {
            // nothing split - nothing to compose (guard §15.6 moved into the calculator)
            return CalcResult.of(kind, "skipped").put("reason", "no-children").status(Status.DONE);
        }
        String userPrompt = String.join("\n", PromptFormatter.userPromptOnly(ctx));
        StringBuilder subPrompts = new StringBuilder();
        int n = 1;
        for (TaskView c : ctx.task().getChildren()) {
            subPrompts.append(n++).append("> ").append(c.getTitle()).append('\n');
        }
        String joined = String.join(" | ", answers);
        if (ctx.workCache() != null) {
            ctx.workCache().put("userPrompt", userPrompt);
            ctx.workCache().put("subPrompts", subPrompts.toString().strip());
            ctx.workCache().put("answers", joined);
        }
        try {
            List<String> segments = new ArrayList<>();
            segments.add("SYSTEM:summarize.compose\n" + PromptFormatter.format(systemPrompt, ctx.workCache()));
            segments.addAll(ctx.conversation());   // AUFTRAG/ANTWORT/BEWERTUNG/ZUSATZ
            AiAnswers.Parsed parsed = AiAnswers.callAndParse(ctx, provider, modelclass, segments, null);
            var root = parsed.root();
            String summary = null;
            var payload = root.asObjectValues().get("payload");
            if (payload != null) {
                summary = AiAnswers.text(payload, "summary", null);
                if (summary == null) {
                    summary = AiAnswers.text(payload, "answer", null);
                }
            }
            if (summary == null || summary.isBlank()) {
                throw new HarnessException(HarnessException.Type.SCHEMA_VIOLATION,
                        "summarize.compose answer carries no payload.summary: " + AiAnswers.abbrev(parsed.rawAnswer()));
            }
            if (ctx.workCache() != null) {
                ctx.workCache().put("summary", summary);
            }
            CalcResult r = CalcResult.of(kind, AiAnswers.text(root, "decision", "summarized"))
                    .status(AiAnswers.parseStatus(AiAnswers.text(root, "status", "DONE")))
                    .put("summary", summary)
                    .put("artifact", summary);
            Integer score = AiAnswers.score(root, "score");
            if (score != null) {
                r.score(score);
            }
            return r;
        } catch (HarnessException e) {
            if (e.getType() != HarnessException.Type.MODEL_UNAVAILABLE
                    && e.getType() != HarnessException.Type.SCHEMA_VIOLATION) {
                throw e;
            }
            // fallback: deterministic join of the leaf answers
            if (ctx.workCache() != null) {
                ctx.workCache().put("summary", joined);
            }
            return CalcResult.of(kind, "summarized-fallback")
                    .status(Status.DONE)
                    .put("summary", joined)
                    .put("artifact", joined)
                    .message("fallback join after " + e.getType());
        }
    }
}
