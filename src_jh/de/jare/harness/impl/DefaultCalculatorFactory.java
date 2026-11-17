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
import de.jare.harness.impl.deterministic.PrintQuestsBuilder;
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

    public DefaultCalculatorFactory registerNamed(String name, java.util.function.Supplier<de.jare.harness.CalculatorBuilder> builderSupplier) {
        registry.register(name, builderSupplier);
        return this;
    }

    /**
     * Built-in named calculators - registered as builder suppliers only. The factory fills each
     * builder with the config params via the CalculatorBuilder interface and builds; it never
     * knows a calculator's internals.
     */
    private void registerNamedDefaults() {
        registerNamed("scoring.countQuests", de.jare.harness.main.calculator.ScoringCountQuestsBuilder::new);
        registerNamed("askai.decompose", de.jare.harness.main.calculator.AskAiDecomposeBuilder::new);
        registerNamed("printig", PrintQuestsBuilder::new);
        registerNamed("skill.collect", de.jare.harness.main.calculator.SkillCollectBuilder::new);
        registerNamed("skill.match", de.jare.harness.main.calculator.SkillMatchBuilder::new);
        registerNamed("guard.check", de.jare.harness.main.calculator.GuardCheckBuilder::new);
        registerNamed("guard.escalate", de.jare.harness.main.calculator.GuardEscalateBuilder::new);
    }

    /** Register a provider, keyed by provider name (e.g. "ollama"). */
    public DefaultCalculatorFactory registerProvider(String key, ModelProvider provider) {
        providers.put(key, provider);
        return this;
    }

    public ModelProvider provider(String key) {
        return providers.get(key);
    }

    /**
     * The embedding provider for a {@code purpose: "rag"} modelclass: an ollama-backed
     * {@link de.jare.harness.impl.ollama.OllamaEmbeddingProvider} when the registered
     * provider is an {@link de.jare.harness.impl.ollama.OllamaProvider}, else the
     * deterministic hash embedding. The RAG store is keyed by {@code modelName()}, so
     * two rag models get two separate stores (§11).
     */
    public de.jare.harness.rag.EmbeddingProvider embeddingProviderFor(Modelclass mc) {
        if (mc != null && "ollama".equals(mc.getProvider())) {
            ModelProvider p = providers.get("ollama");
            if (p instanceof de.jare.harness.impl.ollama.OllamaProvider op) {
                return new de.jare.harness.impl.ollama.OllamaEmbeddingProvider(op.getBaseUrl(), mc.getModel());
            }
        }
        return de.jare.harness.rag.EmbeddingProvider.hashEmbedding();
    }

    @Override
    public HarnessCalculator createAtomic(CalculatorNode node, Kind kind) {
        // a named calculator wins over impl: "calculator": "scoring.countQuests"
        if (node.getCalculator() != null) {
            HarnessCalculator named = registry.create(node.getCalculator(),
                    new de.jare.harness.CalculatorCreationContext(kind, model, this), node.getParams());
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
