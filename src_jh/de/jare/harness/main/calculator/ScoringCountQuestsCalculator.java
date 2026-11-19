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
import de.jare.harness.Kind;
import de.jare.harness.Status;
import de.jare.harness.impl.AiAnswers;
import de.jare.harness.impl.ModelProvider;
import de.jare.harness.model.Modelclass;
import java.util.ArrayList;
import java.util.List;

/**
 * The named calculator {@code "scoring.countQuests"} (registered in the main tooling factory).
 * Counts how many distinct quests the user prompt contains, using a small scoring model
 * (params {@code "model"}, e.g. phi4-mini). The system prompt is a config param
 * ({@code "systemPrompt"}) so mutations can be tried purely at the config level.
 *
 * Answer contract (strict JSON): {@code {"status":"DONE","decision":"counted","score":100,
 * "payload":{"questCount":N}}} - the count lands in the CalcResult payload + log.
 *
 * @author Janusch Rentenatus
 */
public class ScoringCountQuestsCalculator implements HarnessCalculator {

    private final Kind kind;
    private final Modelclass modelclass;
    private final ModelProvider provider;
    private final String systemPrompt;
    private final String emphasisPrompt;

    public ScoringCountQuestsCalculator(Kind kind, Modelclass modelclass, ModelProvider provider,
                                        String systemPrompt, String emphasisPrompt) {
        this.kind = kind;
        this.modelclass = modelclass;
        this.provider = provider;
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new de.jare.harness.HarnessException(de.jare.harness.HarnessException.Type.PARAMETER,
                    "scoring.countQuests needs the config param \"systemPrompt\" (prompts live only in the config JSON)",
                    "systemPrompt");
        }
        this.systemPrompt = systemPrompt;
        this.emphasisPrompt = emphasisPrompt == null || emphasisPrompt.isBlank() ? null : emphasisPrompt;
    }

    // Instances are built by ScoringCountQuestsBuilder (Builder pattern): the factory fills the
    // builder with the config params via CalculatorBuilder and calls build(ctx).

    @Override
    public Kind kind() {
        return kind;
    }

    public Modelclass getModelclass() {
        return modelclass;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getEmphasisPrompt() {
        return emphasisPrompt;
    }

    @Override
    public CalcResult calculate(CalcContext ctx) {
        List<String> segments = new ArrayList<>();
        segments.add("SYSTEM:scoring.countQuests\n" + systemPrompt);   // system prompt as config param
        segments.addAll(ctx.prompts());
        // provider call + strict JSON parse with one emphasis retry (shared in AiAnswers)
        AiAnswers.Parsed parsed = AiAnswers.callAndParse(ctx, provider, modelclass, segments, emphasisPrompt);
        var root = parsed.root();

        int questCount = 0;
        var payload = root.asObjectValues().get("payload");
        if (payload != null && payload.asObjectValues().get("questCount") != null) {
            questCount = Integer.parseInt(String.valueOf(payload.asObjectValues().get("questCount").asLong()).trim());
        }

        // data cache: the count is also written into the runtime WorkCache so a following
        // calculator can format it into its prompt template ({data.count}, PromptFormatter)
        if (ctx.workCache() != null) {
            ctx.workCache().put("count", String.valueOf(questCount));
        }
        return CalcResult.of(kind, AiAnswers.text(root, "decision", "counted"))
                .status(AiAnswers.parseStatus(AiAnswers.text(root, "status", "DONE")))
                .put("questCount", String.valueOf(questCount))
                .put("emphasisRetry", String.valueOf(parsed.emphasisRetry()))
                .put("rawAnswer", AiAnswers.abbrev(parsed.rawAnswer()));
    }
}
