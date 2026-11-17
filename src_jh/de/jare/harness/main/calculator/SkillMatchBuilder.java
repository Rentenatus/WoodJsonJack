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
import de.jare.harness.HarnessException;
import de.jare.harness.model.Modelclass;

/**
 * The builder of the {@code "skill.match"} calculator ({@link SkillMatchCalculator}) -
 * understands the params {@code "filter"} (optional skill-name filter), {@code "minSimilarity"}
 * (cosine acceptance threshold), {@code "topK"} and {@code "embedModel"} (the rag modelclass
 * by name; fallback purpose {@code "rag"}). Invalid numbers throw PARAMETER with the key.
 *
 * @author Janusch Rentenatus
 */
public class SkillMatchBuilder implements CalculatorBuilder {

    private String filter;
    private Double minSimilarity;
    private Integer topK;
    private String embedModel;

    public SkillMatchBuilder withFilter(String filter) {
        this.filter = filter;
        return this;
    }

    public SkillMatchBuilder withMinSimilarity(Double minSimilarity) {
        this.minSimilarity = minSimilarity;
        return this;
    }

    public SkillMatchBuilder withTopK(Integer topK) {
        this.topK = topK;
        return this;
    }

    public SkillMatchBuilder withEmbedModel(String embedModel) {
        this.embedModel = embedModel;
        return this;
    }

    @Override
    public CalculatorBuilder withParam(String key, String value) {
        if ("filter".equals(key)) {
            withFilter(value);
        } else if ("minSimilarity".equals(key)) {
            try {
                withMinSimilarity(Double.valueOf(value.trim()));
            } catch (NumberFormatException e) {
                throw new HarnessException(HarnessException.Type.PARAMETER,
                        "minSimilarity is not a number: \"" + value + "\"", "minSimilarity", e);
            }
        } else if ("topK".equals(key)) {
            try {
                withTopK(Integer.valueOf(value.trim()));
            } catch (NumberFormatException e) {
                throw new HarnessException(HarnessException.Type.PARAMETER,
                        "topK is not an int: \"" + value + "\"", "topK", e);
            }
        } else if ("embedModel".equals(key)) {
            withEmbedModel(value);
        } else {
            rejectUnknown(key);
        }
        return this;
    }

    @Override
    public HarnessCalculator build(CalculatorCreationContext ctx) {
        Modelclass mc = ctx.modelclassByName(embedModel);
        if (mc == null) {
            mc = ctx.modelclassByPurpose("rag");
        }
        return new SkillMatchCalculator(ctx.kind(), ctx.embeddingProviderFor(mc), filter, minSimilarity, topK);
    }
}
