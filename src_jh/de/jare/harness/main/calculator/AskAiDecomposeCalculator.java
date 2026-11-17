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
import de.jare.harness.main.actions.LoopSplit;
import de.jare.harness.main.actions.None;
import de.jare.harness.model.Modelclass;
import de.jare.harness.prompts.PromptFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The named calculator {@code "askai.decompose"} (registered in the main tooling factory).
 * Decomposes the big user prompt into N atomic user prompts (quests), using a strong genAI
 * model (params {@code "model"}, e.g. nemotron3:33b). The system prompt is a config param
 * ({@code "systemPrompt"}) so mutations can be tried purely at the config level.
 *
 * Answer contract (strict JSON): {@code {"status":"DONE","decision":"decomposed","score":100,
 * "payload":{"quests":["...","..."]}}} - the atomic prompts land in the CalcResult payload.
 *
 * For count &gt; 1 the result steers via a {@link LoopSplit} loop action (§15.6): the driver
 * creates the N subtasks (task starts) from the sub-prompts and starts one subloop
 * ({@code subloop} param, default {@code "sub"}) per subtask with the inherited counter
 * state — not a fresh {@code "main"} loop — and after their return aggregates the
 * consumption deltas.
 *
 * @author Janusch Rentenatus
 */
public class AskAiDecomposeCalculator implements HarnessCalculator {

    public static final String DEFAULT_SYSTEM_PROMPT =
            "You decompose the user prompt into {data.count} atomic quests ({data.count} = the "
            + "questCount from the scoring call). Answer with strict JSON only: {\"status\":\"DONE\","
            + "\"decision\":\"decomposed\",\"score\":100,\"payload\":{\"quests\":[\"...\",\"...\"]}} "
            + "where quests is the list of atomic user prompts.";

    private final Kind kind;
    private final Modelclass modelclass;
    private final ModelProvider provider;
    private final String systemPrompt;
    private final String subloop;

    public AskAiDecomposeCalculator(Kind kind, Modelclass modelclass, ModelProvider provider, String systemPrompt) {
        this(kind, modelclass, provider, systemPrompt, null);
    }

    public AskAiDecomposeCalculator(Kind kind, Modelclass modelclass, ModelProvider provider,
                                    String systemPrompt, String subloop) {
        this.kind = kind;
        this.modelclass = modelclass;
        this.provider = provider;
        this.systemPrompt = systemPrompt == null || systemPrompt.isBlank()
                ? DEFAULT_SYSTEM_PROMPT : systemPrompt;
        this.subloop = subloop;
    }

    // Instances are built by AskAiDecomposeBuilder (Builder pattern): the factory fills the
    // builder with the config params via CalculatorBuilder and calls build(ctx).

    @Override
    public Kind kind() {
        return kind;
    }

    public Modelclass getModelclass() {
        return modelclass;
    }

    public ModelProvider getProvider() {
        return provider;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    @Override
    public CalcResult calculate(CalcContext ctx) {
        // the quest count comes from the predecessor of this puzzle sequence via the runtime
        // result channel / data cache: scoring.countQuests wrote it as payload "questCount"
        // and as data-cache entry "count"
        Integer count = null;
        if (ctx.workCache() != null) {
            String dataCount = ctx.workCache().get("count");
            if (dataCount != null && !dataCount.isBlank()) {
                try {
                    count = Integer.valueOf(dataCount.trim());
                } catch (NumberFormatException ignored) {
                    count = null;
                }
            }
            if (count == null && ctx.workCache().getResult("scoring.countQuests") != null) {
                String q = ctx.workCache().getResult("scoring.countQuests").getPayload().get("questCount");
                if (q != null && !q.isBlank()) {
                    try {
                        count = Integer.valueOf(q.trim());
                    } catch (NumberFormatException ignored) {
                        count = null;
                    }
                }
            }
        }
        if (count == null) {
            // no count available - error status, but no exception: the driver's catch-mapping
            // is not needed, the run continues with the deterministic fallback semantics
            return CalcResult.of(kind, "decompose-nocount").status(Status.FAILED)
                    .message("no quest count in work cache");
        }
        if (count <= 1) {
            // nothing to decompose - no error, the task is simply not splittable
            return CalcResult.of(kind, "nosplit").status(Status.INVALID)
                    .message("quest count " + count + " <= 1, nothing to decompose");
        }
        // count > 1: decompose. The system prompt template is formatted against the runtime data
        // cache - "{data.count}" becomes the count written by scoring.countQuests.
        String sys = PromptFormatter.format(systemPrompt, ctx.workCache());
        List<String> segments = new ArrayList<>();
        segments.add("SYSTEM:askai.decompose\n" + sys);
        segments.addAll(ctx.prompts());
        String answer = callProvider(ctx, segments);
        de.jare.jsoncasted.lang.JsonNode root = parseStrict(answer);
        List<String> quests = new ArrayList<>();
        var payload = root.asObjectValues().get("payload");
        if (payload != null && payload.asObjectValues().get("quests") != null
                && payload.asObjectValues().get("quests").isArray()) {
            for (de.jare.jsoncasted.lang.JsonNode q : payload.asObjectValues().get("quests").asArray()) {
                if (!q.asText().isBlank()) {
                    quests.add(q.asText());
                }
            }
        }
        if (quests.isEmpty()) {
            throw new HarnessException(HarnessException.Type.SCHEMA_VIOLATION,
                    "decompose answer carries no quests: " + abbrev(answer));
        }
        // the result steers via the LoopSplit loop action: the driver creates the N subtasks and
        // starts one subloop (subloop param, default "sub") per subtask — not a fresh "main" —
        // then aggregates the consumption deltas (§15.6). The domain action stays None so the
        // legacy Split action is not executed a second time.
        return CalcResult.of(kind, "split")
                .status(parseStatus(text(root, "status", "DONE")))
                .put("quests", String.join("\n", quests))
                .put("count", String.valueOf(count))
                .put("subloop", subloop == null ? de.jare.harness.model.Loop.SUB : subloop)
                .put("rawAnswer", abbrev(answer))
                .action(new None())
                .loopAction(new LoopSplit(quests, subloop));
    }

    private String callProvider(CalcContext ctx, List<String> segments) {
        if (provider == null || modelclass != null && !modelclass.isAvailable()) {
            throw new HarnessException(HarnessException.Type.MODEL_UNAVAILABLE,
                    "decompose provider unavailable");
        }
        try {
            return provider.complete(segments, ctx.temperature());
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
