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

    public static final String DEFAULT_SYSTEM_PROMPT =
            "Count the distinct questions or tasks in the user prompt. "
            + "Each clause that asks for something or requests an action is ONE quest, even when "
            + "clauses are joined with \"and\"/\"und\"/\",\". Never merge joined clauses into one. "
            + "Pure greetings or statements without a question or instruction count as 0. "
            + "Answer with strict JSON only, no explanation: {\"status\":\"DONE\",\"decision\":\"counted\","
            + "\"score\":100,\"payload\":{\"questCount\":N}} where N is the total number of quests. "
            // tuned against phi4-mini (3.8B): rule-based, NO few-shot examples - a tiny model
            // tends to parrot examples instead of analysing the user prompt
            ;

    /**
     * Appended to the system prompt for ONE retry when the first answer is not strict JSON
     * (small models sometimes answer with prose or markdown fences first). Config param
     * {@code "emphasisPrompt"} overrides it.
     */
    public static final String DEFAULT_EMPHASIS_PROMPT =
            "Attention: You must strictly adhere to the JSON structure! "
            + "Answer with strict JSON only, no other text, no markdown fences.";

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
        this.systemPrompt = systemPrompt == null || systemPrompt.isBlank()
                ? DEFAULT_SYSTEM_PROMPT : systemPrompt;
        this.emphasisPrompt = emphasisPrompt == null || emphasisPrompt.isBlank()
                ? DEFAULT_EMPHASIS_PROMPT : emphasisPrompt;
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
        if (provider == null || modelclass != null && !modelclass.isAvailable()) {
            throw new HarnessException(HarnessException.Type.MODEL_UNAVAILABLE,
                    "scoring provider unavailable");
        }
        List<String> segments = new ArrayList<>();
        segments.add("SYSTEM:scoring.countQuests\n" + systemPrompt);   // system prompt as config param
        segments.addAll(ctx.prompts());
        String answer = callProvider(segments);
        boolean emphasisRetry = false;
        de.jare.jsoncasted.lang.JsonNode root;
        try {
            root = parseStrict(answer);
        } catch (HarnessException e) {
            if (e.getType() != HarnessException.Type.SCHEMA_VIOLATION) {
                throw e;
            }
            // emphasis retry: small models sometimes answer with prose/fences first - append the
            // emphasisPrompt to the system segment and ask exactly once more
            emphasisRetry = true;
            List<String> retrySegments = new ArrayList<>(segments);
            retrySegments.set(0, segments.get(0) + "\n\n" + emphasisPrompt);
            answer = callProvider(retrySegments);
            root = parseStrict(answer);   // still not strict JSON -> SCHEMA_VIOLATION propagates
        }
        int questCount = 0;
        var payload = root.asObjectValues().get("payload");
        if (payload != null && payload.asObjectValues().get("questCount") != null) {
            questCount = Integer.parseInt(String.valueOf(payload.asObjectValues().get("questCount").asLong()).trim());
        }
        String status = text(root, "status", "DONE");
        // data cache: the count is also written into the runtime WorkCache so a following
        // calculator can format it into its prompt template ({data.count}, PromptFormatter)
        if (ctx.workCache() != null) {
            ctx.workCache().put("count", String.valueOf(questCount));
        }
        return CalcResult.of(kind, text(root, "decision", "counted"))
                .status(parseStatus(status))
                .put("questCount", String.valueOf(questCount))
                .put("emphasisRetry", String.valueOf(emphasisRetry))
                .put("rawAnswer", abbrev(answer));
    }

    private String callProvider(List<String> segments) {
        try {
            return provider.complete(segments);
        } catch (HarnessException e) {
            throw e;
        } catch (Exception e) {
            if (modelclass != null) {
                modelclass.markUnavailable();
            }
            throw new HarnessException(HarnessException.Type.MODEL_UNAVAILABLE, "provider call failed", e);
        }
    }

    private static de.jare.jsoncasted.lang.JsonNode parseStrict(String answer) {
        String trimmed = answer == null ? "" : answer.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new HarnessException(HarnessException.Type.SCHEMA_VIOLATION,
                    "answer is not strict JSON: " + abbrev(trimmed));
        }
        try {
            var root = de.jare.jsoncasted.io.parserservice.JsonParserService.parse(trimmed,
                    de.jare.debug.JsonDebugLevel.SIMPLE).getRoot();
            if (root == null || !root.isObject()) {
                throw new HarnessException(HarnessException.Type.SCHEMA_VIOLATION, "answer not a JSON object");
            }
            return root;
        } catch (HarnessException e) {
            throw e;
        } catch (Exception e) {
            throw new HarnessException(HarnessException.Type.SCHEMA_VIOLATION, "JSON parse failed: " + e.getMessage());
        }
    }

    private static Status parseStatus(String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase().replace(' ', '_');
        switch (s) {
            case "COMPLETED": return Status.DONE;
            case "IN_PROGRESS": return Status.RUNNING;
            default:
                try {
                    return Status.valueOf(s);
                } catch (IllegalArgumentException e) {
                    throw new HarnessException(HarnessException.Type.SCHEMA_VIOLATION, "unknown status: " + raw);
                }
        }
    }

    private static String text(de.jare.jsoncasted.lang.JsonNode n, String key, String def) {
        var v = n.asObjectValues().get(key);
        return v == null || v.isNull() ? def : v.asText();
    }

    private static String abbrev(String s) {
        return s == null ? "" : (s.length() > 200 ? s.substring(0, 200) + "…" : s);
    }
}
