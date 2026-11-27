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
 * The named calculator {@code "work.answer"} (registered in the main tooling factory).
 * This is the calculator that actually ANSWERS the (sub-)prompt: it calls a genAI model
 * (param {@code "model"}, fallback purpose {@code "investigation"}) with the config
 * {@code "systemPrompt"} and the standardized in-run conversation segments
 * ({@code ctx.conversation()} - AUFTRAG/ANTWORT/BEWERTUNG/ZUSATZ), and carries the
 * model's answer in the CalcResult payload as {@code "answer"} and {@code "artifact"} -
 * the CLOSE calculator picks the artifact up, so the answer flows into SUMMARIZE and the
 * WorkResponse instead of a bare "closed:..." placeholder.
 *
 * Answer contract (strict JSON): {@code {"status":"DONE","decision":"answered","score":0-100,
 * "payload":{"answer":"..."}}}.
 *
 * @author Janusch Rentenatus
 */
public class WorkAnswerCalculator implements HarnessCalculator {

    private final Kind kind;
    private final Modelclass modelclass;
    private final ModelProvider provider;
    private final String systemPrompt;

    public WorkAnswerCalculator(Kind kind, Modelclass modelclass, ModelProvider provider, String systemPrompt) {
        this.kind = kind;
        this.modelclass = modelclass;
        this.provider = provider;
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new HarnessException(HarnessException.Type.PARAMETER,
                    "work.answer needs the config param \"systemPrompt\" (prompts live only in the config JSON)",
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
        List<String> segments = new ArrayList<>();
        segments.add("SYSTEM:work.answer\n" + systemPrompt);
        segments.addAll(ctx.conversation());   // AUFTRAG/ANTWORT/BEWERTUNG/ZUSATZ
        AiAnswers.Parsed parsed = AiAnswers.callAndParse(ctx, provider, modelclass, segments, null);
        var root = parsed.root();
        String answer = null;
        var payload = root.asObjectValues().get("payload");
        if (payload != null) {
            answer = AiAnswers.text(payload, "answer", null);
            if (answer == null) {
                answer = AiAnswers.text(payload, "text", null);
            }
        }
        if (answer == null || answer.isBlank()) {
            throw new HarnessException(HarnessException.Type.SCHEMA_VIOLATION,
                    "work.answer answer carries no payload.answer: " + AiAnswers.abbrev(parsed.rawAnswer()));
        }
        CalcResult r = CalcResult.of(kind, AiAnswers.text(root, "decision", "answered"))
                .status(AiAnswers.parseStatus(AiAnswers.text(root, "status", "DONE")))
                .put("answer", answer)
                .put("artifact", answer)   // CLOSE picks this up as the task artifact
                .put("rawAnswer", AiAnswers.abbrev(parsed.rawAnswer()));
        Integer score = AiAnswers.score(root, "score");
        if (score != null) {
            r.score(score);
        }
        return r;
    }
}
