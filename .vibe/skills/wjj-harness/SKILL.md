---
name: wjj-harness
description: Load this skill when working on Wood Harness (src_jh/) in the WoodJsonJack repo — extending the harness model/config (assets/harness/harness-model.json), adding kinds/quests/slots/loops, named calculators + builders, loop actions, or touching the task tree (TaskData, calls[]), the engine loop, or harness persistence. Contains the architecture rules, extension recipes, build/test commands and commit conventions.
---

# Wood Harness (wjj-harness)

Wood Harness is the agent/quest engine of WoodJsonJack, living in `src_jh/de/jare/harness/`
(package root `de.jare.harness`). It is a separate layer: **no imports from WJJ `src/`, no
Swing/AWT, no `jsoncasted.editor`** — a TUI must be able to drive it. Verify with:
`grep -rln "de.jare.tree\|javax.swing\|java.awt\|de.jare.jsoncasted.editor" src_jh --include=*.java` → must be empty.

## Architecture map

```
jsonCasted → jsonCasted_edit → WoodHarness (src_jh/) → WoodJsonJack (src/)
```

- **Recipe (blueprint)**: `model/` — `HarnessModel`, `Quest`, `Slot`, `CalculatorNode`, `Loop`,
  `LoopItem`, `Modelclass`, `Kind`. Configured in `assets/harness/harness-model.json`
  (loaded/saved by `io/HarnessModelCodec`).
- **Runtime truth**: `memory/` — `MemoryQuest` (one run), `WorkOrder` (the calculation's memory),
  `WorkCache` (intermediate results + result channel between calculators of a puzzle sequence:
  `putResult(name, res)` / `getLastResult()` / `getPreviousResult()`), `WorkResponse` (live answer).
  `ProjectMemory` = project-local memory across runs (`.wood/harness/memory.json`, `/cmd`s).
- **Projection (trace, persisted)**: `TaskData` — a plain POJO (NOT an EditNode; JSON via
  `toJson()/fromJson()` is the persistence shape, codec `io/HarnessInstanceCodec` delegates to it,
  persisted at `<projekt>/.wood/harness.json`). Fields incl. `prompt` (the model prompt text —
  defaults to the title; a reasoning subtask keeps the ORIGINAL prompt here and only its label in
  the title) + `tasks[]` children + append-only `calculatorLog[]` + `calls[]` (one `CalcCall` per
  executed atomic calculator: `input` prompts, `payload`, `loopAction` with type/flow/detail — the
  visible data movement; the engine appends automatically in `HarnessEngine.runNode` via
  `task.appendCalcCall(...)`). Mutations go through `TaskCommands` + `CommandHistory` only (§18).
- **Engine**: `HarnessEngine` — runs loops (`runLoop`/`runItem`), `execute()` per kind → quest →
  base slot → `runNode` (puzzle = sequence of children, results threaded through WorkCache),
  catch-mapping to deterministic fallback. Steering is OO via `HarnessAction` (domain) and
  `LoopAction` (flow, §15.6: CONTINUE/RESTART/BREAK/SUBLOOP/SPLIT/THROW).
- **Actions**: interface in `de.jare.harness`, representatives in `main/actions/`
  (`LoopSplit(prompts, loopId)` default subloop `"sub"`, `LoopSubLoop`, `LoopThrowError`, …).
  Loop actions expose carried data via `LoopAction.detail()` (default null → type/flow only).
- **Calculators**: interface `HarnessCalculator`, factory `CalculatorFactory` /
  `DefaultCalculatorFactory`. Named calculators live in `main/calculator/` as pair
  `XxxCalculator` + `XxxBuilder` (builder understands the config `params`, strict
  `rejectUnknown`, generic `temperature`); registered by name in the factory
  (e.g. `scoring.countQuests`, `askai.decompose`, `work.answer`, `summarize.compose`, `complete.check`,
  `evaluate.score`, `evaluate.aggregate`, `check.verify`, `printig`, `skill.collect`, `skill.match`,
  `guard.check`, `guard.escalate`). AI calls/parsing go through `impl/AiAnswers` only.
- **RAG**: one store per embedding model (`HarnessInstance.getRag(embedder)`, persisted
  `.wood/harness/rag/<model>.json`); `engine.activeRag()` resolves the rag-purpose modelclass's
  store. Provider `"system"` = built-in deterministic fallback (hash embedding), declared in the
  asset as `{provider: system, model: hash-embedding, purpose: rag}`.
- **Metrics**: `metrics/MetricsStore` + `CallLogEntry` (loopId/loopItemId/trigger/paramError,
  `structureScore = ema(SCORE) − 100·(wFail·FAIL + wParam·PARAM_ERROR)`).
- **UI**: `src/de/jare/tree/ui/harness/` — `HarnessController` (AskAi entry from the KI pane),
  `HarnessViewPanel` (dialog with live task tree incl. `calls[]` child nodes, status colors,
  protocol detail pane). The harness tree is NOT registered as an editor tab anymore.

## Configuration (`assets/harness/harness-model.json`)

- `modelclasses[]`: `{provider, model, purpose, size, costFactor, toolCapable}` — purpose is the
  routing key (investigation/edit/plan/evaluation/scoring/rag), NOT the model name; several
  modelclasses can share a purpose (size decides on multi-occupancy).
- `kinddefs[]`: free kind literals without recompiling; codec validates every quest/loop-item
  kind against built-ins (14 in `Kind`) + kinddefs.
- `quests[]`: `{kind, evolvable, slots[]}` — slot: `{id, mutation, calculator}` where calculator
  is `atomic` (`impl: ai|deterministic`, optional `calculator: <name>`, `modelclass`, `tool`,
  `params{model, systemPrompt, emphasisPrompt, temperature, subloop, …}`) or `puzzle`
  (`strategy: sequence`, `children[]`).
- `loops[]`: `{id, items[]}` — item `{id, kind, trigger: START|EXECUTE|CATCH|CLOSE, skipAfter: [itemIds]}`.
  `skipAfter` skips this item in the same EXECUTE iteration when one of the listed items steered
  (e.g. main: complete/work/rag_store/summarize carry `"skipAfter": ["split"]`; evaluate/check are
  deliberately NOT skipped). The steered set is fresh per iteration; scores are reset per iteration
  (`ClearScore`), `promptSupplement` persists (retry feedback); a stagnation guard aborts when an
  iteration repeats exactly without score improvement (→ CATCH).
  `main` START = GUARD+SKILL, EXECUTE = split→…→summarize, CATCH = ABORT, CLOSE = close+rag_prune.
  `sub` = same but START = ABORT (split children inherit consumption, no re-guard).
- SPLIT children always run the `sub` loop (`LoopSplit`, never a fresh `main`).
- **Conversation convention**: every `execute()`/loop iteration starts with ONE AUFTRAG segment
  (`conversation` ref) carrying four labeled lines — `AUFTRAG:` (task prompt), `ANTWORT:` (leaf
  answers), `BEWERTUNG:` (evaluate results), `ZUSATZ:` (promptSupplement) — empty when not yet
  present (`PromptFormatter.conversationLines/conversationSegment`). `userPromptOnly` reads ONLY the
  AUFTRAG line; answer-side calculators use `ctx.conversation()`.

## Extension recipes

1. **New kind**: add literal to `kinddefs[]` in the asset (no code change) or a built-in in `Kind`.
   Deterministic fallback: add to `DeterministicCalculators.IMPLS`.
2. **New quest/slot**: add to `quests[]`; reference the kind from a loop item.
3. **New named calculator**: create `main/calculator/FooCalculator` + `FooBuilder`
   (`CalculatorBuilder`, params via `withParam`, `build(CalculatorCreationContext)`), register
   in `DefaultCalculatorFactory` under its name; reference via `"calculator": "foo"` + `params`.
   AI calculators parse answers only via `impl/AiAnswers`. **RULE: prompts live ONLY as params in
   the config JSON (`assets/harness/harness-model.json`) — never as code defaults.** A missing
   `systemPrompt` param is a PARAMETER error (PARAM_ERROR metric), not a hidden hardcoded prompt;
   `emphasisPrompt` is optional. `HarnessService.defaultModel()` loads the bundled asset.
4. **New loop action**: implement `LoopAction` in `main/actions/`, override `detail()` for
   carried data, map its `Flow` in `HarnessEngine.runItem`'s switch and `CalcResult.getLoopAction()`.
5. **New loop**: add to `loops[]`; reference by id (`subloop` param / `LoopSubLoop`).
6. **Persistence**: anything on `TaskData` (incl. `calls[]`) persists automatically via
   `toJson/fromJson`; legacy shapes are normalized in `HarnessInstanceCodec.normalizeLegacyTask`.

## Build & test (Windows, Git Bash)

```bash
cd /d/git_WoodJsonJack
find src src_jh test test_jh D:/git_jsonCasted_edit/src D:/git_jsonCasted/src D:/git_ndimcol/src -name "*.java" > C:/tmp/allsrc.txt
M2=C:/Users/Administrator/.m2/repository
CP="D:/git_WoodJsonJack/extern/formdev/flatlaf/3.7.1/flatlaf-3.7.1.jar;$M2/org/testng/testng/7.10.2/testng-7.10.2.jar;$M2/com/beust/jcommander/1.82/jcommander-1.82.jar;$M2/org/slf4j/slf4j-api/2.0.16/slf4j-api-2.0.16.jar"
javac -nowarn -d out_wjj -cp "$CP" @C:/tmp/allsrc.txt        # argfile MUST use D:/ paths; regenerate after adding files
CLASSES=$(ls test_jh/de/jare/harness/*.java | sed 's|test_jh/||; s|\.java$||; s|/|.|g' | paste -sd,)
java -cp "out_wjj;$CP" org.testng.TestNG -testclass "$CLASSES" -usedefaultlisteners false -verbose 1   # harness suite
java -cp "out_wjj;$CP" org.testng.TestNG test_assets/wjj_testng.xml -usedefaultlisteners false -verbose 1 # full WJJ (last: 253/253)
```

## Conventions

- Commit message heredoc always ends with:
  `Generated by Mistral Vibe.` + `Co-Authored-By: Mistral Vibe <vibe@mistral.ai>`.
- Never commit build outputs (`out*/`, `test-output/`, `.vibe/` session logs, `sandbox/`).
- `touch` changed files before staging (timestamps).
- Tests green before every commit; run the harness suite after src_jh changes.
- Known open items (see `docs/working/PROGRESS_Harness.md` §10.18): re-split on RESTART can still
  create duplicate children (bounded by the stagnation guard, behavior under observation); TEMP
  `[DIAG.x]` prints still in code (`Diag.EDIT_VERSION` is manual — clean before commit); probe
  `.class` files under `test_assets/` are build artifacts — remove before commit.
- Tests must not depend on live Ollama: AI calculators expose a protected `callModel` seam and
  `HarnessService.createProvider(Modelclass)` is overridable; use `test_jh/.../FakeModelProvider`.
- Life test against running Ollama (localhost:11434) before merging harness changes to main.

## Docs (read on demand, paths relative to this skill dir)

- `../../../docs/koncepts/CONCEPT_Harness.md` — the concept (§2 provider, §3 layering/task tree,
  §11 RAG, §15.6 loops, §17 project memory)
- `../../../docs/working/PROGRESS_Harness.md` — progress log incl. open items (§10.x)
- `../../../docs/working/DESIGN_Loops.md`, `DESIGN_Quest_WorkOrder.md`, `DESIGN_Calculator_Factory_Builder.md`
