/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import java.util.Map;

/**
 * The builder interface every named calculator provides: its own builder understands the
 * params of that calculator. The factory only uses this interface - it fills the builder
 * with the params map from the config and calls {@link #build}:
 *
 * <pre>{@code
 * CalculatorBuilder b = registry.newBuilder(name);   // uniform
 * b.withParams(node.getParams());                      // config params
 * HarnessCalculator calc = b.build(ctx);              // instance
 * }</pre>
 *
 * @author Janusch Rentenatus
 */
public interface CalculatorBuilder {

    /** One param (the builder decides which keys it understands). */
    CalculatorBuilder withParam(String key, String value);

    /** Generic params every calculator accepts - read via {@code CalcContext}, not by the builder. */
    static boolean isGenericParam(String key) {
        return "temperature".equals(key);
    }

    /**
     * Strict tail of {@link #withParam}: throws a {@link HarnessException.Type#PARAMETER}
     * (counted as PARAM_ERROR metric per slot + param, §6) for a key the builder does not
     * understand and that is not generic.
     */
    default CalculatorBuilder rejectUnknown(String key) {
        if (!isGenericParam(key)) {
            throw new HarnessException(HarnessException.Type.PARAMETER, "unknown param \"" + key + "\"", key);
        }
        return this;
    }

    /** All params from the config node. */
    default CalculatorBuilder withParams(Map<String, String> params) {
        if (params != null) {
            params.forEach(this::withParam);
        }
        return this;
    }

    /** Builds the calculator instance from the accumulated params + creation context. */
    HarnessCalculator build(CalculatorCreationContext ctx);
}
