/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.main.calculator;

import de.jare.harness.CalcContext;
import de.jare.harness.CalcResult;
import de.jare.harness.Kind;
import de.jare.harness.Status;
import de.jare.harness.impl.AiAnswers;
import de.jare.harness.impl.ModelProvider;
import de.jare.harness.model.Modelclass;
import java.util.ArrayList;
import java.util.List;

/**
 * The named calculator {@code "guard.escalate"} (registered in the main tooling factory), the
 * second pass of the GUARD quest (§15.6): only runs when {@code "guard.check"} left the verdict
 * {@code SUSPICIOUS} in the WorkCache ({@code guard.verdict}); otherwise it skips with
 * {@code "no-escalation"}. On escalation it re-judges the prompt with the bigger evaluation
 * model (params {@code "model"}, e.g. granite3.3:8b), the first verdict + reason included in
 * the prompt. The final verdict lands as {@code guard.finalVerdict}; BLOCKED steers a DOMAIN
 * throw (CATCH fires, run stops).
 *
 * @author Janusch Rentenatus
 */
public class GuardEscalateCalculator extends GuardCheckCalculator {

    public GuardEscalateCalculator(Kind kind, Modelclass modelclass, ModelProvider provider,
                                   String systemPrompt, String emphasisPrompt) {
        super(kind, modelclass, provider, systemPrompt, emphasisPrompt);
    }

    @Override
    public CalcResult calculate(CalcContext ctx) {
        String first = ctx.workCache() == null ? null : ctx.workCache().get("guard.verdict");
        if (first == null || "SAFE".equalsIgnoreCase(first) || "BLOCKED".equalsIgnoreCase(first)) {
            // nothing to escalate (BLOCKED already threw in guard.check)
            return CalcResult.of(kind, "no-escalation").status(Status.DONE)
                    .put("guardVerdict", first == null ? "" : first);
        }
        String firstReason = ctx.workCache().get("guard.verdict.reason");
        List<String> segments = new ArrayList<>();
        segments.add("SYSTEM:guard.escalate\n" + systemPrompt);
        segments.addAll(ctx.prompts());
        segments.add("FIRST-VERDICT:scoring model said " + first
                + (firstReason == null || firstReason.isBlank() ? "" : " (" + firstReason + ")"));
        // shared call + strict parse + emphasis retry; the first verdict rides along in the segments
        AiAnswers.Parsed parsed = AiAnswers.callAndParse(ctx, provider, modelclass, segments, emphasisPrompt);
        Verdict v = parseVerdict(parsed.root());
        return verdictResult(ctx, v.verdict(), v.reason(), v.score(), "guard.finalVerdict");
    }
}
