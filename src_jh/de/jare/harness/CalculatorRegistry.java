/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.model.HarnessModel;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The named-calculator registry: a config entry {@code "calculator": "scoring.countQuests"}
 * resolves through this registry to a real Java {@link HarnessCalculator} instance, created
 * by the main tooling factory with the model context. Unknown names fall back to the
 * impl-based creation (deterministic/ai) of the factory.
 *
 * @author Janusch Rentenatus
 */
public class CalculatorRegistry {

    private final Map<String, java.util.function.Supplier<CalculatorBuilder>> builders = new LinkedHashMap<>();

    /** Registers a named calculator by its builder supplier (e.g. {@code ScoringCountQuestsBuilder::new}). */
    public CalculatorRegistry register(String name, java.util.function.Supplier<CalculatorBuilder> builderSupplier) {
        builders.put(name, builderSupplier);
        return this;
    }

    public boolean knows(String name) {
        return name != null && builders.containsKey(name);
    }

    /** A fresh builder for the name, or null when unknown (caller falls back to impl). */
    public CalculatorBuilder newBuilder(String name) {
        java.util.function.Supplier<CalculatorBuilder> s = name == null ? null : builders.get(name);
        return s == null ? null : s.get();
    }

    /**
     * Uniform creation through the builder interface: fill with the params map, then build.
     *
     * @return the instance, or null when the name is unknown (caller falls back to impl)
     */
    public HarnessCalculator create(String name, CalculatorCreationContext ctx, Map<String, String> params) {
        CalculatorBuilder b = newBuilder(name);
        return b == null ? null : b.withParams(params).build(ctx);
    }

    public Map<String, java.util.function.Supplier<CalculatorBuilder>> getBuilders() {
        return builders;
    }
}
