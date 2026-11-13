# DESIGN — Quest, WorkOrder, WorkCache, WorkResponse (WoodHarness runtime model)

Status: decided 2026-11-08, implementation follows in `src_jh/de/jare/harness/`.

## Roles (do not confuse)

| Term | Class | Role |
|---|---|---|
| Recipe | `HarnessInstance` + `model/Quest` | the blueprint side: model version, quests/slots, budgets, limits — persisted, copied into lineage, **no runtime state** |
| MemoryQuest | `memory/MemoryQuest` | the **memory-side quest instance** of one run: references the recipe `Quest` and wraps WorkOrder + WorkCache + WorkResponse |
| WorkOrder (Bearbeitungsauftrag) | `memory/WorkOrder` | the **memory of the current calculation**: id (slot id, e.g. `askai.base`), quest kind, `userPrompt` field, task tree, call log, status |
| WorkCache (BearbeitungsCache) | `memory/WorkCache` | intermediate state of the current run: retrieved RAG chunks, tool outputs, partial results |
| WorkResponse (Bearbeitungsantwort) | `memory/WorkResponse` | the current, still-changing **answer** of the run — live view for the UI, as opposed to the final persisted result |

## Flow at AskAi

1. UI AskAi action (`onThinking`, Enter or "Ask AI") calls the harness with the input text.
2. The harness creates a new **MemoryQuest** in memory: it creates the **WorkOrder** AND the root task (TaskData on the EditTree) itself — `id = "askai.base"`, `userPrompt` = the raw input. The UI only passes the prompt text, it knows no tree logic.
3. The MemoryQuest (recipe: kind `SPLIT`, slot `askai.base`) also carries `memory/WorkCache` + `memory/WorkResponse`.
4. Run: `scoring.countQuests` (phi4-mini) counts the quests in the prompt → `askai.decompose` (nemotron3:33b) generates the N atomic user prompts → `printig` writes them to the output. Intermediate results go to the WorkCache, the live answer to the WorkResponse.
5. The UI attaches to the Quest's WorkResponse via the host SPI (`HarnessHost.addRunListener` / `HarnessView`).

## Glossary (German → English)

- Bearbeitungsauftrag = **WorkOrder**
- BearbeitungsCache = **WorkCache**
- Bearbeitungsantwort = **WorkResponse**
- Rezept = **recipe** (`HarnessInstance` + `model/Quest`)
- Memory-Quest-Instanz = **MemoryQuest** (runtime, owns WorkOrder/WorkCache/WorkResponse)

## Layering: runtime truth vs. persisted projection (CONCEPT_Harness.md §3)

This memory package IS the runtime truth of a run: the engine logic (call stack, status, call
log) runs here independently of any tree. `WorkOrder.getTaskTree()` / `getRoot()` expose a
`TaskData`/EditTree that is only the PERSISTED PROJECTION (trace) of that runtime state:
structure (parent/children) + an OVERWRITABLE "current" cell (status/score/attempt —
progress-bar semantics) + an APPEND-ONLY call log (`calculatorLog[]`). The bridge from runtime
to projection is the write-through setter path of the `TaskData` facade (fires the EditTree
change events); `HarnessEngine` writes `instance.logCall` (runtime) and `task.logCall`
(projection).
