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

/**
 * The builder of {@link EvaluateAggregateCalculator} - no params (deterministic).
 *
 * @author Janusch Rentenatus
 */
public class EvaluateAggregateBuilder implements CalculatorBuilder {

    @Override
    public CalculatorBuilder withParam(String key, String value) {
        rejectUnknown(key);
        return this;
    }

    @Override
    public HarnessCalculator build(CalculatorCreationContext ctx) {
        return new EvaluateAggregateCalculator(ctx.kind());
    }
}
