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
 * The builder of {@link CheckVerifyCalculator} - understands exactly its params:
 * {@code "model"} (fallback purpose "evaluation") and {@code "systemPrompt"} (the
 * close-verification prompt with {data.userPrompt}/{data.answer}/{data.evaluation}/
 * {data.supplement} placeholders; config level only).
 *
 * @author Janusch Rentenatus
 */
public class CheckVerifyBuilder implements CalculatorBuilder {

    private String model;
    private String systemPrompt;

    /** Typed setter: the concrete model, e.g. "glm-4.7-flash:latest". */
    public CheckVerifyBuilder withModel(String model) {
        this.model = model;
        return this;
    }

    /** Typed setter: the system prompt of the close verification. */
    public CheckVerifyBuilder withSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    @Override
    public CalculatorBuilder withParam(String key, String value) {
        if ("model".equals(key)) {
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
        return new CheckVerifyCalculator(ctx.kind(), mc, ctx.providerFor(mc), systemPrompt);
    }
}
