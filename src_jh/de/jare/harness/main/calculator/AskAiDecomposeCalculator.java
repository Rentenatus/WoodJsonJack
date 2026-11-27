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
import de.jare.harness.HarnessException;
import de.jare.harness.Kind;
import de.jare.harness.Status;
import de.jare.harness.impl.AiAnswers;
import de.jare.harness.impl.ModelProvider;
import de.jare.harness.main.actions.LoopSplit;
import de.jare.harness.main.actions.None;
import de.jare.harness.model.Modelclass;
import de.jare.harness.prompts.PromptFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The named calculator {@code "askai.decompose"} (registered in the main tooling factory).
 * Decomposes the big user prompt into N atomic user prompts (quests), using a strong genAI
 * model (params {@code "model"}, e.g. nemotron3:33b). The system prompt is a config param
 * ({@code "systemPrompt"}) so mutations can be tried purely at the config level.
 *
 * Answer contract (strict JSON): {@code {"status":"DONE","decision":"decomposed","score":100,
 * "payload":{"quests":["...","..."]}}} - the atomic prompts land in the CalcResult payload.
 *
 * For count &gt; 1 the result steers via a {@link LoopSplit} loop action (§15.6): the driver
 * creates the N subtasks (task starts) from the sub-prompts and starts one subloop
 * ({@code subloop} param, default {@code "sub"}) per subtask with the inherited counter
 * state — not a fresh {@code "main"} loop — and after their return aggregates the
 * consumption deltas. The optional {@code "connectors"} param (comma-separated, e.g.
 * {@code "und,and,unf,,"}) lists connectors that the prompt asks to absorb INTO the sub-quests;
 * sub-quests that consist of a connector only are dropped, and when nothing usable remains
 * the fallback split (the raw prompt) is returned with {@link #FALLBACK_SCORE} so evolution
 * strongly penalizes it.
 *
 * @author Janusch Rentenatus
 */
public class AskAiDecomposeCalculator implements HarnessCalculator {

    /**
     * Score for the parts fallback (model named no quests, generic "part N" titles are used):
     * far below the CHECK/hit threshold of 50 so a run that had to fall back is clearly marked.
     */
    public static final int FALLBACK_SCORE = 10;

    private final Kind kind;
    private final Modelclass modelclass;
    private final ModelProvider provider;
    private final String systemPrompt;
    private final String subloop;
    private final java.util.Set<String> connectors;

    public AskAiDecomposeCalculator(Kind kind, Modelclass modelclass, ModelProvider provider, String systemPrompt) {
        this(kind, modelclass, provider, systemPrompt, null, null);
    }

    public AskAiDecomposeCalculator(Kind kind, Modelclass modelclass, ModelProvider provider,
                                    String systemPrompt, String subloop) {
        this(kind, modelclass, provider, systemPrompt, subloop, null);
    }

    public AskAiDecomposeCalculator(Kind kind, Modelclass modelclass, ModelProvider provider,
                                    String systemPrompt, String subloop, String connectors) {
        this.kind = kind;
        this.modelclass = modelclass;
        this.provider = provider;
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new de.jare.harness.HarnessException(de.jare.harness.HarnessException.Type.PARAMETER,
                    "askai.decompose needs the config param \"systemPrompt\" (prompts live only in the config JSON)",
                    "systemPrompt");
        }
        this.systemPrompt = systemPrompt;
        this.subloop = subloop;
        this.connectors = new java.util.HashSet<>();
        if (connectors != null) {
            for (String c : connectors.split(",")) {
                String t = c.trim().toLowerCase();
                if (!t.isEmpty()) {
                    this.connectors.add(t);
                }
            }
        }
    }

    // Instances are built by AskAiDecomposeBuilder (Builder pattern): the factory fills the
    // builder with the config params via CalculatorBuilder and calls build(ctx).

    @Override
    public Kind kind() {
        return kind;
    }

    /**
     * The model call - extracted as an overridable seam: tests subclass and return a canned
     * answer, so no live Ollama is needed. Input assembly (segments) and result processing
     * stay separate steps around this call.
     */
    protected AiAnswers.Parsed callModel(CalcContext ctx, List<String> segments, String emphasisPrompt) {
        return AiAnswers.callAndParse(ctx, provider, modelclass, segments, emphasisPrompt);
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
        System.out.println("[DIAG." + de.jare.harness.Diag.runNo() + "] decompose task=\"" + ctx.task().getPrompt() + "\" countHint=" + count);
        // NOTE: the split decision is NOT based on the small scoring model's count (too
        // unreliable) - the strong genAI below returns the atomic quest list and only a
        // list with >1 entries steers a LoopSplit. The count stays a prompt hint ({data.count}).
        // count > 1: decompose. The system prompt template is formatted against the runtime data
        // cache - "{data.count}" becomes the count written by scoring.countQuests.
        String sys = PromptFormatter.format(systemPrompt, ctx.workCache());
        List<String> segments = new ArrayList<>();
        segments.add("SYSTEM:askai.decompose\n" + sys);
        segments.addAll(de.jare.harness.prompts.PromptFormatter.userPromptOnly(ctx));   // only the AUFTRAG segment - the model must not echo history/memory segment headers as quests
        AiAnswers.Parsed parsed = callModel(ctx, segments, null);
        var root = parsed.root();

        List<String> quests = parseQuests(root);
        boolean wellFormed = quests != null;
        if (quests == null) {
            quests = new ArrayList<>();
        }
        quests = dropConnectorOnly(quests);
        if (!wellFormed) {
            throw new HarnessException(HarnessException.Type.SCHEMA_VIOLATION,
                    "decompose answer carries no quests: " + AiAnswers.abbrev(parsed.rawAnswer()));
        }
        if (quests.size() <= 1 && count != null && count > 1) {
            // disagreement: the scoring counted >1 quests (e.g. typo-joined "unf" instead of
            // "und") but the model returned one - one emphasis retry forced to the counted N
            String retrySys = sys + " The scoring counted " + count
                    + " quests in this text. You MUST return exactly " + count + " atomic entries.";
            List<String> retrySegments = new ArrayList<>();
            retrySegments.add("SYSTEM:askai.decompose\n" + retrySys);
            retrySegments.addAll(de.jare.harness.prompts.PromptFormatter.userPromptOnly(ctx));
            parsed = callModel(ctx, retrySegments, null);
            root = parsed.root();
            List<String> retryQuests = parseQuests(root);
            quests = retryQuests == null ? new ArrayList<>() : dropConnectorOnly(retryQuests);
        }
        if (quests.size() <= 1) {
            if (count != null && count > 1) {
                // the scoring counted N>1 quests but the model could not name them (e.g.
                // typo-joined "unf") - split into N generic parts so the split still fires,
                // but the score is strongly penalized (FALLBACK_SCORE, far below the
                // CHECK/hit threshold of 50): a fallback split is a weak result
                return CalcResult.of(kind, "split-fallback")
                        .status(Status.DONE)
                        .score(FALLBACK_SCORE)
                        .message("fallback: model named no quests - generic part titles, score penalized to " + FALLBACK_SCORE)
                        .put("fallback", "parts")
                        .put("count", String.valueOf(count))
                        .put("subloop", subloop == null ? de.jare.harness.model.Loop.SUB : subloop)
                        .action(new None())
                        .loopAction(new LoopSplit(List.of(), subloop, count));
            }
            // the strong model sees only ONE atomic quest - nothing to split (no error)
            return CalcResult.of(kind, "nosplit").status(Status.INVALID)
                    .message("decompose returned " + quests.size() + " quest(s), nothing to split");
        }

        // the result steers via the LoopSplit loop action: the driver creates the N subtasks and
        // starts one subloop (subloop param, default "sub") per subtask — not a fresh "main" —
        // then aggregates the consumption deltas (§15.6). The domain action stays None so the
        // legacy Split action is not executed a second time.
        return CalcResult.of(kind, "split")
                .status(AiAnswers.parseStatus(AiAnswers.text(root, "status", "DONE")))
                .put("quests", String.join("\n", quests))
                .put("count", String.valueOf(count))
                .put("subloop", subloop == null ? de.jare.harness.model.Loop.SUB : subloop)
                .put("rawAnswer", AiAnswers.abbrev(parsed.rawAnswer()))
                .action(new None())
                .loopAction(new LoopSplit(quests, subloop));
    }

    /**
     * Reads {@code payload.quests} from a parsed model answer. Returns {@code null} when the
     * answer structure is malformed (no payload.quests array) - distinct from an empty list.
     */
    private static List<String> parseQuests(de.jare.jsoncasted.lang.JsonNode root) {
        var payload = root.asObjectValues().get("payload");
        if (payload == null || payload.asObjectValues().get("quests") == null
                || !payload.asObjectValues().get("quests").isArray()) {
            return null;
        }
        List<String> quests = new ArrayList<>();
        for (de.jare.jsoncasted.lang.JsonNode q : payload.asObjectValues().get("quests").asArray()) {
            if (!q.asText().isBlank()) {
                quests.add(q.asText());
            }
        }
        return quests;
    }

    /**
     * Defensive filter: drops quest entries that consist only of joining connectors
     * (config param {@code "connectors"}, e.g. a stray "unf" the model echoed as its own
     * quest). The system prompt already forbids this - this is the backstop so a connector
     * never leaks into a sub-prompt / the chat trace.
     */
    private List<String> dropConnectorOnly(List<String> quests) {
        if (connectors.isEmpty()) {
            return quests;
        }
        List<String> out = new ArrayList<>();
        for (String q : quests) {
            String[] tokens = q.toLowerCase().split("[^a-zäöüß]+");
            boolean onlyConnectors = true;
            for (String t : tokens) {
                if (!t.isEmpty() && !connectors.contains(t)) {
                    onlyConnectors = false;
                    break;
                }
            }
            if (!onlyConnectors) {
                out.add(q);
            }
        }
        return out;
    }
}
