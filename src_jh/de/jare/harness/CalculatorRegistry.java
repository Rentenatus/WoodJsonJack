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

    /** Creates one calculator instance from its name with full model/factory context and the node's params. */
    @FunctionalInterface
    public interface NamedCalculator {
        HarnessCalculator create(Kind kind, HarnessModel model, CalculatorFactory factory, Map<String, String> params);
    }

    private final Map<String, NamedCalculator> named = new LinkedHashMap<>();

    public CalculatorRegistry register(String name, NamedCalculator creator) {
        named.put(name, creator);
        return this;
    }

    public boolean knows(String name) {
        return name != null && named.containsKey(name);
    }

    /** @return the instance, or null when the name is unknown (caller falls back to impl) */
    public HarnessCalculator create(String name, Kind kind, HarnessModel model, CalculatorFactory factory,
                                    Map<String, String> params) {
        NamedCalculator nc = name == null ? null : named.get(name);
        return nc == null ? null : nc.create(kind, model, factory, params);
    }

    public Map<String, NamedCalculator> getNamed() {
        return named;
    }
}
