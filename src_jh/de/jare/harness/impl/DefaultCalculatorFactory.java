/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.impl;

import de.jare.harness.CalculatorFactory;
import de.jare.harness.HarnessCalculator;
import de.jare.harness.Kind;
import de.jare.harness.impl.deterministic.DeterministicCalculators;
import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Modelclass;
import java.util.HashMap;
import java.util.Map;

/**
 * Default factory (CONCEPT_Harness.md §2, §5): {@code impl: "deterministic"} maps to
 * the deterministic calculators, {@code impl: "ai"} resolves the {@code modelclass}
 * through the model's modelclasses map and binds the registered provider.
 *
 * @author Janusch Rentenatus
 */
public class DefaultCalculatorFactory implements CalculatorFactory {

    private final HarnessModel model;
    private final Map<String, ModelProvider> providers = new HashMap<>();

    public DefaultCalculatorFactory(HarnessModel model) {
        this.model = model;
    }

    /** Register a provider, keyed by provider name (e.g. "ollama"). */
    public DefaultCalculatorFactory registerProvider(String key, ModelProvider provider) {
        providers.put(key, provider);
        return this;
    }

    public ModelProvider provider(String key) {
        return providers.get(key);
    }

    @Override
    public HarnessCalculator createAtomic(CalculatorNode node, Kind kind) {
        String impl = node.getImpl() == null ? CalculatorNode.IMPL_DETERMINISTIC : node.getImpl();
        if (CalculatorNode.IMPL_AI.equals(impl)) {
            Modelclass mc = node.getModelclass() == null ? null : model.modelclass(node.getModelclass());
            ModelProvider provider = mc == null ? null
                    : providers.get(mc.getProvider() == null ? "ollama" : mc.getProvider());
            return new AiCalculator(kind, mc, provider);
        }
        return DeterministicCalculators.forKind(kind);
    }
}
