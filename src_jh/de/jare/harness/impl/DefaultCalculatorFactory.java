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
import de.jare.harness.impl.deterministic.CalculatorPrintQuests;
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
    private final de.jare.harness.CalculatorRegistry registry = new de.jare.harness.CalculatorRegistry();

    public DefaultCalculatorFactory(HarnessModel model) {
        this.model = model;
        registerNamedDefaults();
    }

    public de.jare.harness.CalculatorRegistry getRegistry() {
        return registry;
    }

    public DefaultCalculatorFactory registerNamed(String name, de.jare.harness.CalculatorRegistry.NamedCalculator creator) {
        registry.register(name, creator);
        return this;
    }

    /**
     * Built-in named calculators. "scoring.countQuests": counts the quests in the prompt
     * with the scoring modelclass (phi4-mini) before decomposition; "askai.decompose":
     * splits the prompt into the matching subtasks (investigation modelclass).
     */
    private void registerNamedDefaults() {
        registerNamed("scoring.countQuests", (kind, m, self, params) -> {
            // params {"model": "phi4-mini:latest"} pick the concrete model; fallback: purpose "scoring"
            de.jare.harness.model.Modelclass mc = modelByName(m, params.get("model"));
            if (mc == null) {
                mc = m.modelclass("scoring");
            }
            ModelProvider p = mc == null ? null : providers.get(mc.getProvider() == null ? "ollama" : mc.getProvider());
            return new AiCalculator(kind, mc, p);
        });
        registerNamed("askai.decompose", (kind, m, self, params) -> {
            de.jare.harness.model.Modelclass mc = modelByName(m, params.get("model"));
            if (mc == null) {
                mc = m.modelclass("investigation");
            }
            ModelProvider p = mc == null ? null : providers.get(mc.getProvider() == null ? "ollama" : mc.getProvider());
            return new AiCalculator(kind, mc, p);
        });
        // deterministic: prints the N quests with their single prompts to the output
        registerNamed("printig", (kind, m, self, params) -> new CalculatorPrintQuests(kind));
    }

    /** Resolves a modelclass by its concrete model name (params "model"), null when absent/unknown. */
    private static de.jare.harness.model.Modelclass modelByName(HarnessModel model, String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return null;
        }
        return model.getModelclasses().stream()
                .filter(m -> modelName.equals(m.getModel())).findFirst().orElse(null);
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
        // a named calculator wins over impl: "calculator": "scoring.countQuests"
        if (node.getCalculator() != null) {
            HarnessCalculator named = registry.create(node.getCalculator(), kind, model, this, node.getParams());
            if (named != null) {
                return named;
            }
        }
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
