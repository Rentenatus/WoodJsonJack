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
import de.jare.harness.impl.AiCalculator;
import de.jare.harness.model.Modelclass;

/**
 * The builder of the {@code "askai.decompose"} calculator (AskAiDecompose): understands the
 * param {@code "model"} - the strong genAI (e.g. nemotron3:33b) that generates the N atomic
 * user prompts from the big user prompt. Fallback: purpose "investigation".
 *
 * @author Janusch Rentenatus
 */
public class AskAiDecomposeBuilder implements CalculatorBuilder {

    private String model;

    public AskAiDecomposeBuilder withModel(String model) {
        this.model = model;
        return this;
    }

    @Override
    public CalculatorBuilder withParam(String key, String value) {
        if ("model".equals(key)) {
            withModel(value);
        }
        return this;
    }

    @Override
    public HarnessCalculator build(CalculatorCreationContext ctx) {
        Modelclass mc = ctx.modelclassByName(model);
        if (mc == null) {
            mc = ctx.modelclassByPurpose("investigation");
        }
        return new AiCalculator(ctx.kind(), mc, ctx.providerFor(mc));
    }
}
