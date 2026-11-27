/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.main.calculator;

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
 * The named calculator {@code "complete.check"} - COMPLETE reads "Ist unsere Frage komplett?":
 * it judges the USER PROMPT (never the answer - that is EVALUATE's job), whether the request
 * is understandable and answerable as-is or what is missing. "Nachhelfen" (asking back /
 * supplementing from history) is deliberately not implemented yet - the calculator only
 * records the verdict ({@code payload.complete}, {@code payload.missing}).
 *
 * Answer contract (strict JSON): {@code {"status":"DONE","decision":"complete|incomplete",
 * "score":0-100,"payload":{"complete":true|false,"missing":"..."}}}.
 *
 * Fallback: on MODEL_UNAVAILABLE / SCHEMA_VIOLATION a blank prompt is incomplete, anything
 * else complete (the old deterministic behavior).
 *
 * @author Janusch Rentenatus
 */
public class CompleteCheckCalculator implements HarnessCalculator {

    private final Kind kind;
    private final Modelclass modelclass;
    private final ModelProvider provider;
    private final String systemPrompt;

    public CompleteCheckCalculator(Kind kind, Modelclass modelclass, ModelProvider provider, String systemPrompt) {
        this.kind = kind;
        this.modelclass = modelclass;
        this.provider = provider;
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new HarnessException(HarnessException.Type.PARAMETER,
                    "complete.check needs the config param \"systemPrompt\" (prompts live only in the config JSON)",
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
        try {
            List<String> segments = new ArrayList<>();
            segments.add("SYSTEM:complete.check\n" + systemPrompt);
            segments.addAll(ctx.conversation());   // AUFTRAG/ANTWORT/BEWERTUNG/ZUSATZ
            AiAnswers.Parsed parsed = AiAnswers.callAndParse(ctx, provider, modelclass, segments, null);
            var root = parsed.root();
            var payload = root.asObjectValues().get("payload");
            // NB: AiAnswers.text returns null for non-text (e.g. boolean) JSON nodes - null-safe
            String completeText = payload == null ? null : AiAnswers.text(payload, "complete", "true");
            boolean complete = completeText != null && completeText.equalsIgnoreCase("true");
            String missing = payload == null ? "" : AiAnswers.text(payload, "missing", "");
            CalcResult r = CalcResult.of(kind, complete ? "complete" : "incomplete")
                    .status(Status.DONE)
                    .put("complete", String.valueOf(complete))
                    .put("missing", missing == null ? "" : missing);
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
            // fallback: the deterministic COMPLETE impl (the "?"-title heuristic)
            CalcResult fb = de.jare.harness.impl.deterministic.DeterministicCalculators
                    .forKind(Kind.COMPLETE).calculate(ctx);
            return fb.put("missing", "fallback after " + e.getType());
        }
    }
}
