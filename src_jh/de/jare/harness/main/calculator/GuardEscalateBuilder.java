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
 * The builder of the {@code "guard.escalate"} calculator ({@link GuardEscalateCalculator}) -
 * understands the params {@code "model"} (the bigger evaluation model, e.g. granite3.3:8b;
 * fallback purpose "evaluation"), {@code "systemPrompt"} and {@code "emphasisPrompt"}.
 *
 * @author Janusch Rentenatus
 */
public class GuardEscalateBuilder implements CalculatorBuilder {

    private String model;
    private String systemPrompt;
    private String emphasisPrompt;

    public GuardEscalateBuilder withModel(String model) {
        this.model = model;
        return this;
    }

    public GuardEscalateBuilder withSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    public GuardEscalateBuilder withEmphasisPrompt(String emphasisPrompt) {
        this.emphasisPrompt = emphasisPrompt;
        return this;
    }

    @Override
    public CalculatorBuilder withParam(String key, String value) {
        if ("model".equals(key)) {
            withModel(value);
        } else if ("systemPrompt".equals(key)) {
            withSystemPrompt(value);
        } else if ("emphasisPrompt".equals(key)) {
            withEmphasisPrompt(value);
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
        return new GuardEscalateCalculator(ctx.kind(), mc, ctx.providerFor(mc), systemPrompt, emphasisPrompt);
    }
}
