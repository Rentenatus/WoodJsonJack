# DESIGN — Named Calculators, Factory, Builder (WoodHarness)

Status: decided + implemented 2026-11-08, `src_jh/de/jare/harness/`; calculator list extended
2026-11-27 (see table), full WJJ suite 253/253 green.

## Problem
A config node may reference a calculator by NAME instead of `impl`:
`"calculator": "scoring.countQuests", "params": { "model": "phi4-mini:latest", "systemPrompt": "…" }`.
The factory must turn name + params into a real Java instance — without knowing the internals
of any single calculator (otherwise it is no longer a factory).

## Architecture

```
config (harness-model.json)                src_jh
─────────────────────────────              ─────────────────────────────────────────────
"calculator": "scoring.countQuests"  ──►  CalculatorRegistry: name → Supplier<CalculatorBuilder>
"params": {…}                          ──►  CalculatorBuilder (per calculator, understands its params)
                                              │ withParam/withParams      (config params)
                                              ▼ build(CalculatorCreationContext)
                                       HarnessCalculator instance
```

- **`CalculatorNode`** (model, recipe side): carries `calculator` (name) + `params` map; survives codec/definition round trips.
- **`CalculatorRegistry`**: maps name → builder supplier; `create(name, ctx, params)` = `newBuilder(name).withParams(params).build(ctx)`.
- **`CalculatorBuilder`** (interface): `withParam(key, value)`, `withParams(map)`, `build(ctx)`. Each calculator has its own builder with typed setters (`withModel`, `withSystemPrompt`, …).
- **`CalculatorCreationContext`** (record: `kind`, `model`, `factory` + helpers `modelclassByName` / `modelclassByPurpose` / `providerFor`): the only context a builder gets besides the params.
- **`DefaultCalculatorFactory`**: registers builder suppliers (`"scoring.countQuests" → ScoringCountQuestsBuilder::new`, …) and in `createAtomic` just does `registry.create(node.getCalculator(), ctx, node.getParams())`; unknown name → fallback to `impl` (deterministic/ai). No per-calculator logic.

## Built-in named calculators (main/calculator + impl/deterministic)
All registered in `DefaultCalculatorFactory.registerNamedDefaults`; AI calculators take their
prompts ONLY from config params (missing `systemPrompt` = PARAMETER error, never a code default).
AI calculators also expose a `protected callModel(...)` seam so tests can run without live Ollama.

| Name | Class | Builder | Params (beyond the generic ones) |
|---|---|---|---|
| `scoring.countQuests` | `main.calculator.ScoringCountQuestsCalculator` | `ScoringCountQuestsBuilder` | `model`, `systemPrompt`, `emphasisPrompt`, `temperature` |
| `askai.decompose` | `main.calculator.AskAiDecomposeCalculator` | `AskAiDecomposeBuilder` | `model`, `systemPrompt`, `emphasisPrompt`, `temperature`, `subloop`, `connectors` (comma list absorbed into the sub-quests; connector-only quests are dropped, fallback split scored `FALLBACK_SCORE=10`) |
| `work.answer` | `main.calculator.WorkAnswerCalculator` | `WorkAnswerBuilder` | `model`, `systemPrompt`, … — answer lands in the payload as `answer` |
| `summarize.compose` | `main.calculator.SummarizeComposeCalculator` | `SummarizeComposeBuilder` | `model`, `systemPrompt`, … — composes a readable answer from leaf answers |
| `complete.check` | `main.calculator.CompleteCheckCalculator` | `CompleteCheckBuilder` | `model`, `systemPrompt`, … — is the user request fully covered |
| `evaluate.score` | `main.calculator.EvaluateScoreCalculator` | `EvaluateScoreBuilder` | `model`, `systemPrompt`, … — one aspect (completeness/correctness/readability) |
| `evaluate.aggregate` | `main.calculator.EvaluateAggregateCalculator` | `EvaluateAggregateBuilder` | min over the EVALUATE calls of the current iteration only (`ctx.iterationCallBase`) |
| `check.verify` | `main.calculator.CheckVerifyCalculator` | `CheckVerifyBuilder` | `model`, `systemPrompt`, … — verifies the final answer against the original request |
| `printig` | `impl.deterministic.CalculatorPrintQuests` | `PrintQuestsBuilder` | — |
| `skill.collect` | `main.calculator.SkillCollectCalculator` | `SkillCollectBuilder` | — |
| `skill.match` | `main.calculator.SkillMatchCalculator` | `SkillMatchBuilder` | `embedModel`, `minSimilarity`, `topK`, `filter` |
| `guard.check` | `main.calculator.GuardCheckCalculator` | `GuardCheckBuilder` | `model`, `systemPrompt`, `emphasisPrompt`, `temperature` |
| `guard.escalate` | `main.calculator.GuardEscalateCalculator` | `GuardEscalateBuilder` | `model`, `systemPrompt`, `emphasisPrompt`, `temperature` |

## Adding a new named calculator
1. Calculator class in the right package.
2. Its builder implementing `CalculatorBuilder` (typed setters for its params).
3. One line in `DefaultCalculatorFactory.registerNamedDefaults`: `registerNamed("my.calc", MyBuilder::new)`.
4. Config: `{"calculator": "my.calc", "params": {…}}`.

## Related
- Runtime memory model (MemoryQuest/WorkOrder/WorkCache/WorkResponse): `DESIGN_Quest_WorkOrder.md`
- Recipe vs memory: `model/Quest` + `HarnessInstance` = recipe; `memory/*` = per-run memory.
