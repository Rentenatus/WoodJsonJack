/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.impl;

import de.jare.harness.CalcContext;
import de.jare.harness.HarnessException;
import de.jare.harness.Status;
import de.jare.harness.model.Modelclass;
import de.jare.jsoncasted.lang.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared machinery of the AI-backed calculators (the strict-JSON answer contract, §1/§2):
 * one place for the provider call (availability + temperature + MODEL_UNAVAILABLE), the
 * strict JSON parse (SCHEMA_VIOLATION), the tolerant status parsing and the small JsonNode
 * accessors - previously copy-pasted into every named calculator.
 *
 * @author Janusch Rentenatus
 */
public final class AiAnswers {

    private AiAnswers() {
    }

    /** A parsed model answer: the root object, the raw answer text, and whether the emphasis retry fired. */
    public record Parsed(JsonNode root, String rawAnswer, boolean emphasisRetry) {
    }

    /**
     * One provider call: availability check, temperature from the config params, and a
     * generic failure becomes {@code MODEL_UNAVAILABLE} (the modelclass is marked, §2).
     */
    public static String call(CalcContext ctx, ModelProvider provider, Modelclass modelclass,
                              List<String> segments) {
        if (provider == null || modelclass != null && !modelclass.isAvailable()) {
            throw new HarnessException(HarnessException.Type.MODEL_UNAVAILABLE,
                    "provider unavailable" + (modelclass == null ? "" : " for " + modelclass.getModel()));
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

    /**
     * Provider call + strict JSON parse, with ONE emphasis retry: when the first answer is
     * not strict JSON (small models sometimes answer with prose or markdown fences first),
     * the {@code emphasisPrompt} is appended to the first (system) segment and the model is
     * asked exactly once more. Still not strict JSON -> SCHEMA_VIOLATION propagates.
     */
    public static Parsed callAndParse(CalcContext ctx, ModelProvider provider, Modelclass modelclass,
                                      List<String> segments, String emphasisPrompt) {
        String answer = call(ctx, provider, modelclass, segments);
        try {
            return new Parsed(parseStrict(answer), answer, false);
        } catch (HarnessException e) {
            if (e.getType() != HarnessException.Type.SCHEMA_VIOLATION
                    || emphasisPrompt == null || emphasisPrompt.isBlank()) {
                throw e;
            }
            List<String> retrySegments = new ArrayList<>(segments);
            retrySegments.set(0, segments.get(0) + "\n\n" + emphasisPrompt);
            answer = call(ctx, provider, modelclass, retrySegments);
            return new Parsed(parseStrict(answer), answer, true); // still not JSON -> SCHEMA_VIOLATION propagates
        }
    }

    /** Strict JSON object parse of a model answer; anything else is a SCHEMA_VIOLATION. */
    public static JsonNode parseStrict(String answer) {
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

    /**
     * Tolerant status parsing for small models: case-insensitive, blanks → underscores,
     * common synonyms (COMPLETED → DONE, IN PROGRESS → RUNNING); anything else is a
     * SCHEMA_VIOLATION (a HarnessException, so the catch-mapping handles it).
     */
    public static Status parseStatus(String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase().replace(' ', '_');
        switch (s) {
            case "COMPLETED":
                return Status.DONE;
            case "IN_PROGRESS":
                return Status.RUNNING;
            default:
                try {
                    return Status.valueOf(s);
                } catch (IllegalArgumentException e) {
                    throw new HarnessException(HarnessException.Type.SCHEMA_VIOLATION,
                            "unknown status in answer: " + raw);
                }
        }
    }

    /** Text value of an object member (default when absent or null) - delegates to {@link de.jare.harness.io.JsonNodes}. */
    public static String text(JsonNode node, String key, String def) {
        return de.jare.harness.io.JsonNodes.text(node, key, def);
    }

    /** Score value of an object member: NUMBER or LONG node, rounded to int; null when absent. */
    public static Integer score(JsonNode node, String key) {
        var v = node == null ? null : node.asObjectValues().get(key);
        if (v == null || v.isNull()) {
            return null;
        }
        Double n = v.asNumber();
        return (int) Math.round(n != null ? n : v.asLong());
    }

    public static String abbrev(String s) {
        return s == null ? "" : (s.length() > 200 ? s.substring(0, 200) + "…" : s);
    }
}
