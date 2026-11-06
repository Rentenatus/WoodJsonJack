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
import de.jare.harness.Status;
import de.jare.harness.model.Modelclass;

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
        final String answer;
        try {
            answer = provider.complete(ctx.prompts());
        } catch (HarnessException e) {
            throw e;
        } catch (Exception e) {
            if (modelclass != null) {
                modelclass.markUnavailable();
            }
            throw new HarnessException(HarnessException.Type.MODEL_UNAVAILABLE, "provider call failed", e);
        }
        String trimmed = answer.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new HarnessException(HarnessException.Type.SCHEMA_VIOLATION,
                    "answer is not strict JSON: " + abbrev(trimmed));
        }
        // strict JSON parse of the model answer via jsonCasted; a malformed answer raises
        // SCHEMA_VIOLATION and the engine's catch-mapping falls back deterministically (§13)
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
