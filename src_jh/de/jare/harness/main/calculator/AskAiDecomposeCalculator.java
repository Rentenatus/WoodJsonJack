/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.main.calculator;

import de.jare.harness.CalcContext;
import de.jare.harness.CalcResult;
import de.jare.harness.HarnessCalculator;
import de.jare.harness.Kind;
import de.jare.harness.impl.ModelProvider;
import de.jare.harness.model.Modelclass;

/**
 * The named calculator {@code "askai.decompose"} (registered in the main tooling factory).
 * Decomposes the big user prompt into N atomic user prompts (quests), using a strong genAI
 * model (params {@code "model"}, e.g. nemotron3:33b). The system prompt is a config param
 * ({@code "systemPrompt"}) so mutations can be tried purely at the config level.
 *
 * Answer contract (strict JSON): {@code {"status":"DONE","decision":"decomposed","score":100,
 * "payload":{"quests":["...","..."]}}} - the atomic prompts land in the CalcResult payload.
 *
 * TODO: the decompose logic is not implemented yet - calculate() is an empty stub.
 *
 * @author Janusch Rentenatus
 */
public class AskAiDecomposeCalculator implements HarnessCalculator {

    public static final String DEFAULT_SYSTEM_PROMPT =
            "You decompose the user prompt into N atomic quests (N = the questCount from the scoring "
            + "call in the history). Answer with strict JSON only: {\"status\":\"DONE\","
            + "\"decision\":\"decomposed\",\"score\":100,\"payload\":{\"quests\":[\"...\",\"...\"]}} "
            + "where quests is the list of atomic user prompts.";

    private final Kind kind;
    private final Modelclass modelclass;
    private final ModelProvider provider;
    private final String systemPrompt;

    public AskAiDecomposeCalculator(Kind kind, Modelclass modelclass, ModelProvider provider, String systemPrompt) {
        this.kind = kind;
        this.modelclass = modelclass;
        this.provider = provider;
        this.systemPrompt = systemPrompt == null || systemPrompt.isBlank()
                ? DEFAULT_SYSTEM_PROMPT : systemPrompt;
    }

    // Instances are built by AskAiDecomposeBuilder (Builder pattern): the factory fills the
    // builder with the config params via CalculatorBuilder and calls build(ctx).

    @Override
    public Kind kind() {
        return kind;
    }

    public Modelclass getModelclass() {
        return modelclass;
    }

    public ModelProvider getProvider() {
        return provider;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    @Override
    public CalcResult calculate(CalcContext ctx) {
        // TODO: decompose logic - assemble SYSTEM:askai.decompose + prompts, call the provider,
        // parse the strict JSON answer and put the atomic quests into the payload
        return CalcResult.of(kind, "decompose-todo");
    }
}
