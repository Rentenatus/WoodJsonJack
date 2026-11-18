/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.impl.deterministic;

import de.jare.harness.CalculatorBuilder;
import de.jare.harness.CalculatorCreationContext;
import de.jare.harness.HarnessCalculator;

/**
 * The builder of the {@code "printig"} calculator (CalculatorPrintQuests) - deterministic,
 * understands no params.
 *
 * @author Janusch Rentenatus
 */
public class PrintQuestsBuilder implements CalculatorBuilder {

    @Override
    public CalculatorBuilder withParam(String key, String value) {
        return rejectUnknown(key);   // no params of its own
    }

    @Override
    public HarnessCalculator build(CalculatorCreationContext ctx) {
        return new CalculatorPrintQuests(ctx.kind());
    }
}
