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
        // structured parse of the model answer lands here with the jsonCasted loader;
        // the skeleton accepts any JSON object and reports it as a DONE answer
        return CalcResult.of(kind, "answered").message(abbrev(trimmed)).status(Status.DONE);
    }

    private static String abbrev(String s) {
        return s.length() <= 120 ? s : s.substring(0, 120) + "...";
    }
}
