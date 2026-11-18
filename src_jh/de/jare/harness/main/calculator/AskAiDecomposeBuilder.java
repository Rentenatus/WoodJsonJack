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
 * The builder of {@link AskAiDecomposeCalculator} - understands exactly its params:
 * {@code "model"} (the strong genAI, e.g. nemotron3:33b, that generates the N atomic user
 * prompts from the big user prompt; fallback purpose "investigation"),
 * {@code "systemPrompt"} (the decompose prompt; mutable purely at config level) and
 * {@code "subloop"} (the target loop the split starts per subtask; default {@code "sub"}).
 *
 * @author Janusch Rentenatus
 */
public class AskAiDecomposeBuilder implements CalculatorBuilder {

    private String model;
    private String systemPrompt;
    private String subloop;

    /** Typed setter: the concrete model, e.g. "nemotron3:33b". */
    public AskAiDecomposeBuilder withModel(String model) {
        this.model = model;
        return this;
    }

    /** Typed setter: the system prompt of the decompose call. */
    public AskAiDecomposeBuilder withSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    /** Typed setter: the target subloop of the split (default "sub"). */
    public AskAiDecomposeBuilder withSubloop(String subloop) {
        this.subloop = subloop;
        return this;
    }

    @Override
    public CalculatorBuilder withParam(String key, String value) {
        if ("model".equals(key)) {
            withModel(value);
        } else if ("systemPrompt".equals(key)) {
            withSystemPrompt(value);
        } else if ("subloop".equals(key)) {
            withSubloop(value);
        } else {
            rejectUnknown(key);
        }
        return this;
    }

    @Override
    public HarnessCalculator build(CalculatorCreationContext ctx) {
        Modelclass mc = ctx.modelclassByName(model);
        if (mc == null) {
            mc = ctx.modelclassByPurpose("investigation");
        }
        return new AskAiDecomposeCalculator(ctx.kind(), mc, ctx.providerFor(mc), systemPrompt, subloop);
    }
}
