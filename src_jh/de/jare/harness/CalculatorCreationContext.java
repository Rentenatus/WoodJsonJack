/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.impl.DefaultCalculatorFactory;
import de.jare.harness.model.HarnessModel;

/**
 * The creation context handed to every named calculator uniformly: the kind being executed,
 * the recipe model and the main tooling factory (for provider lookup). Calculators are
 * initialized ONLY with this context + their params map - the factory never special-cases
 * a calculator, it stays a factory.
 *
 * @author Janusch Rentenatus
 */
public record CalculatorCreationContext(Kind kind, HarnessModel model, DefaultCalculatorFactory factory) {

    /** Resolves a modelclass by its concrete model name (params "model"), null when absent/unknown. */
    public de.jare.harness.model.Modelclass modelclassByName(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return null;
        }
        return model.getModelclasses().stream()
                .filter(m -> modelName.equals(m.getModel())).findFirst().orElse(null);
    }

    /** First modelclass with this purpose (fallback when no concrete model is given). */
    public de.jare.harness.model.Modelclass modelclassByPurpose(String purpose) {
        return model.modelclass(purpose);
    }

    /** The embedding provider for a rag modelclass (ollama embed model or hash fallback, §11). */
    public de.jare.harness.rag.EmbeddingProvider embeddingProviderFor(de.jare.harness.model.Modelclass mc) {
        return factory.embeddingProviderFor(mc);
    }

    /** The provider registered for this modelclass (e.g. "ollama"), null when none. */
    public de.jare.harness.impl.ModelProvider providerFor(de.jare.harness.model.Modelclass mc) {
        if (mc == null) {
            return null;
        }
        return factory.provider(mc.getProvider() == null ? "ollama" : mc.getProvider());
    }
}
