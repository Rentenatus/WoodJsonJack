# DESIGN — Named Calculators, Factory, Builder (WoodHarness)

Status: decided + implemented 2026-11-08, `src_jh/de/jare/harness/`, suite 238/238 green.

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
| Name | Class | Builder | Params |
|---|---|---|---|
| `scoring.countQuests` | `main.calculator.ScoringCountQuests` | `ScoringCountQuestsBuilder` | `model`, `systemPrompt` |
| `askai.decompose` | `main.calculator.AskAiDecomposeBuilder` → `AiCalculator` | `AskAiDecomposeBuilder` | `model` |
| `printig` | `impl.deterministic.CalculatorPrintQuests` | `PrintQuestsBuilder` | — |

## Adding a new named calculator
1. Calculator class in the right package.
2. Its builder implementing `CalculatorBuilder` (typed setters for its params).
3. One line in `DefaultCalculatorFactory.registerNamedDefaults`: `registerNamed("my.calc", MyBuilder::new)`.
4. Config: `{"calculator": "my.calc", "params": {…}}`.

## Related
- Runtime memory model (MemoryQuest/WorkOrder/WorkCache/WorkResponse): `DESIGN_Quest_WorkOrder.md`
- Recipe vs memory: `model/Quest` + `HarnessInstance` = recipe; `memory/*` = per-run memory.
