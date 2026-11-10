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
import de.jare.harness.Status;
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
            "You decompose the user prompt into {data.count} atomic quests ({data.count} = the "
            + "questCount from the scoring call). Answer with strict JSON only: {\"status\":\"DONE\","
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
        // the quest count comes from the predecessor of this puzzle sequence via the runtime
        // result channel / data cache: scoring.countQuests wrote it as payload "questCount"
        // and as data-cache entry "count"
        Integer count = null;
        if (ctx.workCache() != null) {
            String dataCount = ctx.workCache().get("count");
            if (dataCount != null && !dataCount.isBlank()) {
                try {
                    count = Integer.valueOf(dataCount.trim());
                } catch (NumberFormatException ignored) {
                    count = null;
                }
            }
            if (count == null && ctx.workCache().getResult("scoring.countQuests") != null) {
                String q = ctx.workCache().getResult("scoring.countQuests").getPayload().get("questCount");
                if (q != null && !q.isBlank()) {
                    try {
                        count = Integer.valueOf(q.trim());
                    } catch (NumberFormatException ignored) {
                        count = null;
                    }
                }
            }
        }
        if (count == null) {
            // no count available - error status, but no exception: the driver's catch-mapping
            // is not needed, the run continues with the deterministic fallback semantics
            return CalcResult.of(kind, "decompose-nocount").status(Status.FAILED)
                    .message("no quest count in work cache");
        }
        if (count <= 1) {
            // nothing to decompose - no error, the task is simply not splittable
            return CalcResult.of(kind, "nosplit").status(Status.INVALID)
                    .message("quest count " + count + " <= 1, nothing to decompose");
        }
        // TODO: decompose logic - assemble SYSTEM:askai.decompose + prompts, call the provider,
        // parse the strict JSON answer and put the atomic quests into the payload.
        // The system prompt is formatted against the runtime data cache:
        //   String sys = de.jare.harness.prompts.PromptFormatter.format(systemPrompt, ctx.workCache());
        //   -> "{data.count}" is replaced with the count written by scoring.countQuests.
        // count is already parsed above (N): create N atomic subprompts and steer via a
        // HarnessAction:
        //   return CalcResult.of(kind, "split")
        //           .put("quests", String.join("\n", subprompts))
        //           .action(new de.jare.harness.main.actions.Split(subprompts, count));
        // the driver executes the Split action and adds the N subtasks (no if-cascades).
        return CalcResult.of(kind, "decompose-todo");
    }
}
