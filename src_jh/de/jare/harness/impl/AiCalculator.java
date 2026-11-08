/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.impl;

import de.jare.harness.CalcContext;
import de.jare.harness.CalcResult;
import de.jare.harness.HarnessCalculator;
import de.jare.harness.HarnessException;
import de.jare.harness.Kind;
import de.jare.harness.Limits;
import de.jare.harness.Status;
import de.jare.harness.model.Modelclass;
import java.util.List;

/**
 * The AI implementation of an atomic calculator (CONCEPT_Harness.md §2, §13):
 * resolves its {@code modelclass} through the model's modelclasses map, asks the
 * provider one stateless question and expects strict JSON. Provider outage raises
 * {@code MODEL_UNAVAILABLE}, a non-JSON answer raises {@code SCHEMA_VIOLATION} - both
 * are caught by the engine's catch-mapping and fall back to the deterministic slot.
 *
 * @author Janusch Rentenatus
 */
public class AiCalculator implements HarnessCalculator {

    private final Kind kind;
    private final Modelclass modelclass;
    private final ModelProvider provider;

    public AiCalculator(Kind kind, Modelclass modelclass, ModelProvider provider) {
        this.kind = kind;
        this.modelclass = modelclass;
        this.provider = provider;
    }

    @Override
    public Kind kind() {
        return kind;
    }

    @Override
    public CalcResult calculate(CalcContext ctx) {
        if (modelclass != null && !modelclass.isAvailable()) {
            throw new HarnessException(HarnessException.Type.MODEL_UNAVAILABLE,
                    "modelclass unavailable: " + modelclass.getModel());
        }
        if (provider == null || !provider.isAvailable()) {
            if (modelclass != null) {
                modelclass.markUnavailable();
            }
            throw new HarnessException(HarnessException.Type.MODEL_UNAVAILABLE,
                    "provider unavailable for modelclass " + (modelclass == null ? "?" : modelclass.getModel()));
        }
        // Bounded ReAct tool-loop (§13): the model may answer with a tool call from the
        // exposed catalog; the driver executes it with the permission check (counted
        // against maxToolCalls) and feeds the result back as the next prompt segment.
        if (modelclass != null && modelclass.isToolCapable() && ctx.tools() != null) {
            return toolLoop(ctx);
        }
        return singleCall(ctx, ctx.prompts());
    }

    private CalcResult toolLoop(CalcContext ctx) {
        List<String> segments = new java.util.ArrayList<>(ctx.prompts());
        int maxCalls = ctx.limits().remaining(Limits.TOOL_CALLS);
        for (int i = 0; i < Math.max(1, maxCalls); i++) {
            String answer = callProvider(ctx, segments);
            de.jare.jsoncasted.lang.JsonNode root = parseStrict(answer);
            String tool = text(root, "tool", null);
            if (tool == null || tool.isEmpty()) {
                return toResult(root);
            }
            // tool call: execute with permission check + limit counting, feed result back
            java.util.Map<String, String> args = new java.util.LinkedHashMap<>();
            de.jare.jsoncasted.lang.JsonNode argsNode = root.asObjectValues().get("args");
            if (argsNode != null && argsNode.isObject()) {
                for (var e : argsNode.asObjectValues().entrySet()) {
                    args.put(e.getKey(), e.getValue().asText());
                }
            }
            final String toolName = tool;
            try {
                var result = ctx.callTool(toolName, args, "task " + ctx.task().getId());
                ctx.emit(de.jare.harness.signals.Signal.TOOL_RESULT);
                segments.add("DATA:tool-result\n[tool " + toolName + "] " + result.output());
            } catch (HarnessException e) {
                ctx.emit(de.jare.harness.signals.Signal.TOOL_ERROR);
                if (e.getType() == HarnessException.Type.TOOL_ERROR) {
                    segments.add("DATA:tool-error\n[tool " + toolName + "] ERROR: " + e.getMessage());
                    continue; // let the model react to the tool error
                }
                throw e;
            }
        }
        throw new HarnessException(HarnessException.Type.LIMIT, "maxToolCalls reached in tool loop");
    }

    private CalcResult singleCall(CalcContext ctx, List<String> segments) {
        String answer = callProvider(ctx, segments);
        return toResult(parseStrict(answer));
    }

    private String callProvider(CalcContext ctx, List<String> segments) {
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
        String trimmed = answer.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new HarnessException(HarnessException.Type.SCHEMA_VIOLATION,
                    "answer is not strict JSON: " + abbrev(trimmed));
        }
        final de.jare.jsoncasted.lang.JsonNode root;
        try {
            root = de.jare.jsoncasted.io.parserservice.JsonParserService.parse(trimmed,
                    de.jare.debug.JsonDebugLevel.SIMPLE).getRoot();
        } catch (Exception e) {
            throw new HarnessException(HarnessException.Type.SCHEMA_VIOLATION,
                    "answer JSON parse failed: " + e.getMessage());
        }
        if (root == null || !root.isObject()) {
            throw new HarnessException(HarnessException.Type.SCHEMA_VIOLATION,
                    "answer is not a JSON object: " + abbrev(trimmed));
        }
        return root;
    }

    private CalcResult toResult(de.jare.jsoncasted.lang.JsonNode root) {
        CalcResult result = CalcResult.of(kind, text(root, "decision", "answered"))
                .status(Status.valueOf(text(root, "status", "DONE")))
                .message(text(root, "message", ""));
        if (root.asObjectValues().get("score") != null) {
            result.score(root.asObjectValues().get("score").asLong().intValue());
        }
        de.jare.jsoncasted.lang.JsonNode payload = root.asObjectValues().get("payload");
        if (payload != null && payload.isObject()) {
            for (var e : payload.asObjectValues().entrySet()) {
                result.put(e.getKey(), e.getValue().asText());
            }
        }
        return result;
    }

    private static String text(de.jare.jsoncasted.lang.JsonNode node, String key, String def) {
        var v = node.asObjectValues().get(key);
        return v == null || v.isNull() ? def : v.asText();
    }

    private static String abbrev(String s) {
        return s.length() <= 120 ? s : s.substring(0, 120) + "...";
    }
}
