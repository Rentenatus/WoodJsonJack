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
 * The builder of {@link ScoringCountQuestsCalculator} - understands exactly its params:
 * {@code "model"} (concrete model, fallback purpose "scoring"), {@code "systemPrompt"}
 * (the scoring prompt; mutable purely at config level for mutation trials) and
 * {@code "emphasisPrompt"} (appended for one retry when the first answer is not strict JSON).
 *
 * @author Janusch Rentenatus
 */
public class ScoringCountQuestsBuilder implements CalculatorBuilder {

    private String model;
    private String systemPrompt;
    private String emphasisPrompt;

    /** Typed setter: the concrete model, e.g. "phi4-mini:latest". */
    public ScoringCountQuestsBuilder withModel(String model) {
        this.model = model;
        return this;
    }

    /** Typed setter: the system prompt of the scoring call. */
    public ScoringCountQuestsBuilder withSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    /** Typed setter: appended to the system prompt for the single emphasis retry on non-JSON answers. */
    public ScoringCountQuestsBuilder withEmphasisPrompt(String emphasisPrompt) {
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
        }
        return this;
    }

    @Override
    public HarnessCalculator build(CalculatorCreationContext ctx) {
        Modelclass mc = ctx.modelclassByName(model);
        if (mc == null) {
            mc = ctx.modelclassByPurpose("scoring");
        }
        return new ScoringCountQuestsCalculator(ctx.kind(), mc, ctx.providerFor(mc), systemPrompt, emphasisPrompt);
    }
}
