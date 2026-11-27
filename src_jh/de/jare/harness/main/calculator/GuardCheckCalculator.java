/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.main.calculator;

import de.jare.harness.CalcContext;
import de.jare.harness.CalcResult;
import de.jare.harness.ErrorSignal;
import de.jare.harness.HarnessCalculator;
import de.jare.harness.Kind;
import de.jare.harness.Status;
import de.jare.harness.impl.AiAnswers;
import de.jare.harness.impl.ModelProvider;
import de.jare.harness.main.actions.LoopThrowError;
import de.jare.harness.model.Modelclass;
import java.util.ArrayList;
import java.util.List;

/**
 * The named calculator {@code "guard.check"} (registered in the main tooling factory), the
 * first pass of the GUARD quest at the main loop's START (§15.6): classifies the raw user
 * prompt against dangerous content / prompt injection with a small scoring model (params
 * {@code "model"}, e.g. phi4-mini). The system prompt is a config param ({@code "systemPrompt"})
 * so mutations can be tried purely at the config level.
 *
 * Answer contract (strict JSON): {@code {"status":"DONE","decision":"...","score":0-100,
 * "payload":{"verdict":"SAFE|SUSPICIOUS|BLOCKED","reason":"..."}}}. The verdict lands in the
 * WorkCache ({@code guard.verdict}, {@code guard.reason}, {@code guard.score}). A BLOCKED
 * verdict steers via {@link LoopThrowError} with a DOMAIN signal, so the loop's CATCH fires
 * and the run is stopped. SUSPICIOUS is left for {@code "guard.escalate"}.
 *
 * @author Janusch Rentenatus
 */
public class GuardCheckCalculator implements HarnessCalculator {

    protected final Kind kind;
    protected final Modelclass modelclass;
    protected final ModelProvider provider;
    protected final String systemPrompt;
    protected final String emphasisPrompt;

    public GuardCheckCalculator(Kind kind, Modelclass modelclass, ModelProvider provider,
                                String systemPrompt, String emphasisPrompt) {
        this.kind = kind;
        this.modelclass = modelclass;
        this.provider = provider;
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new de.jare.harness.HarnessException(de.jare.harness.HarnessException.Type.PARAMETER,
                    "guard.check needs the config param \"systemPrompt\" (prompts live only in the config JSON)",
                    "systemPrompt");
        }
        this.systemPrompt = systemPrompt;
        this.emphasisPrompt = emphasisPrompt == null || emphasisPrompt.isBlank() ? null : emphasisPrompt;
    }

    // Instances are built by GuardCheckBuilder (Builder pattern): the factory fills the
    // builder with the config params via CalculatorBuilder and calls build(ctx).

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

    public Modelclass getModelclass() {
        return modelclass;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    @Override
    public CalcResult calculate(CalcContext ctx) {
        List<String> segments = new ArrayList<>();
        segments.add("SYSTEM:guard\n" + systemPrompt);
        segments.addAll(ctx.prompts());
        AiAnswers.Parsed parsed = AiAnswers.callAndParse(ctx, provider, modelclass, segments, emphasisPrompt);
        Verdict v = parseVerdict(parsed.root());
        return verdictResult(ctx, v.verdict(), v.reason(), v.score(), "guard.verdict");
    }

    /** A guard verdict answer: {@code {verdict, reason, score}} from the payload/score members. */
    public record Verdict(String verdict, String reason, Integer score) {
    }

    /** Extracts the verdict triple from a parsed guard answer (shared with guard.escalate). */
    protected static Verdict parseVerdict(de.jare.jsoncasted.lang.JsonNode root) {
        var payload = root.asObjectValues().get("payload");
        String verdict = AiAnswers.text(payload, "verdict", "SAFE");
        String reason = AiAnswers.text(payload, "reason", "");
        return new Verdict(verdict, reason, AiAnswers.score(root, "score"));
    }

    /** Shared verdict handling: store in WorkCache, BLOCKED steers a DOMAIN throw. */
    protected CalcResult verdictResult(CalcContext ctx, String verdict, String reason, Integer score,
                                       String cacheKey) {
        String v = verdict == null ? "SAFE" : verdict.trim().toUpperCase().replace(' ', '_');
        if (ctx.workCache() != null) {
            ctx.workCache().put(cacheKey, v);
            ctx.workCache().put(cacheKey + ".reason", reason == null ? "" : reason);
            if (score != null) {
                ctx.workCache().put(cacheKey + ".score", String.valueOf(score));
            }
        }
        CalcResult r = CalcResult.of(kind, v.toLowerCase())
                .status("BLOCKED".equals(v) ? Status.FAILED : Status.DONE)
                .put("verdict", v)
                .put("reason", reason == null ? "" : reason);
        if (score != null) {
            r.score(score);
        }
        if ("BLOCKED".equals(v)) {
            r.loopAction(new LoopThrowError(ErrorSignal.domain(
                    "guard blocked the prompt: " + (reason == null ? v : reason), "guard")));
        }
        return r;
    }
}
