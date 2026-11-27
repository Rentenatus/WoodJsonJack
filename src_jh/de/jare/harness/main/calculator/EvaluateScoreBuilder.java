/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.main.calculator;

import de.jare.harness.CalculatorBuilder;
import de.jare.harness.CalculatorCreationContext;
import de.jare.harness.HarnessCalculator;
import de.jare.harness.model.Modelclass;

/**
 * The builder of {@link EvaluateScoreCalculator} - understands exactly its params:
 * {@code "dimension"} (completeness/correctness/readability/...), {@code "model"}
 * (fallback purpose "evaluation") and {@code "systemPrompt"} (the scoring prompt with
 * {data.userPrompt}/{data.answer}/{data.dimension} placeholders; config level only).
 *
 * @author Janusch Rentenatus
 */
public class EvaluateScoreBuilder implements CalculatorBuilder {

    private String dimension;
    private String model;
    private String systemPrompt;

    /** Typed setter: the evaluated dimension, e.g. "completeness". */
    public EvaluateScoreBuilder withDimension(String dimension) {
        this.dimension = dimension;
        return this;
    }

    /** Typed setter: the concrete model, e.g. "glm-4.7-flash:latest". */
    public EvaluateScoreBuilder withModel(String model) {
        this.model = model;
        return this;
    }

    /** Typed setter: the system prompt of the scoring call. */
    public EvaluateScoreBuilder withSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    @Override
    public CalculatorBuilder withParam(String key, String value) {
        if ("dimension".equals(key)) {
            withDimension(value);
        } else if ("model".equals(key)) {
            withModel(value);
        } else if ("systemPrompt".equals(key)) {
            withSystemPrompt(value);
        } else {
            rejectUnknown(key);
        }
        return this;
    }

    @Override
    public HarnessCalculator build(CalculatorCreationContext ctx) {
        Modelclass mc = ctx.modelclassByName(model);
        if (mc == null) {
            mc = ctx.modelclassByPurpose("evaluation");
        }
        return new EvaluateScoreCalculator(ctx.kind(), mc, ctx.providerFor(mc), dimension, systemPrompt);
    }
}
