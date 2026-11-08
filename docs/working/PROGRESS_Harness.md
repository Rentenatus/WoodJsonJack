# PROGRESS_Harness — Fortschrittsdokument zur Harness-Implementierung

**Stand:** 2026-11-06, Branch `feature/harness-impl` @ `a7cbfc7` + Working Tree (Trennung, uncommittet)
**Produktname:** WoodHarness — Architektur `jsonCasted → jsonCasted_edit → WoodHarness (src_jh/) → WoodJsonJack (src/)`
**Konzept:** `docs/koncepts/CONCEPT_Harness.md` (16 Abschnitte)
**Verifikation:** frischer Build (javac, WJJ + jsonCasted + jsonCasted_edit + ndimcol) und
TestNG-Suite `test_assets/wjj_testng.xml` von Repo-Root aus — **233/233 bestanden, 0 Failures**
(Headless-Lauf am 2026-11-06; die beiden Asset-Tests brauchen das Repo-Root als Working-Dir)

---

## 1. Branch-Lage

| Branch | Stand | Inhalt |
|---|---|---|
| `feature/harness-impl` (aktuell) | 68 Commits, Working Tree sauber | komplette Implementierung `de.jare.harness` (53 Klassen), 20 Testklassen; `develop` ist vollständig enthalten (merge-base = develop-Tip) |
| `feature/projectview` | eigene Linie | Konzept-Ausbau von `CONCEPT_Harness.md` (Quest/Slot-Modell, Modelclasses als JsonMap, M×3-Metriken, RAG, Skills, Tooling, Mindset) + ProjectView-Phasen (Sidebar, Bundles, Reroll-ID) |
| `develop` | 1 Commit vor `main` | nur JsonBundle-Rename in der UI; `main` = Merge von `develop` |

**Noch offen:** `feature/harness-impl` ist nicht nach `main` gemerged — dort fehlen alle 62 Harness-Commits.

## 2. Umsetzungsstand gegen das Konzept

| Konzept | Stand | Code |
|---|---|---|
| §1 Kernentscheidung (deterministischer Treiber, eine Frage pro Call) | ✅ umgesetzt | `HarnessEngine` (Pipeline ABORT → SPLIT → COMPLETE → WORK → EVALUATE → CHECK → SUMMARIZE → CLOSE), `HarnessCalculator`/`CalcResult` |
| §2 Zwölf Kalkulator-Kinds, ein Interface | ✅ alle 12 in `Kind.java` | `RELEVANCE, SPLIT, COMPLETE, WORK, EVALUATE, CHECK, ABORT, SUMMARIZE, CLOSE, RAG_STORE, RAG_RETRIEVE, RAG_PRUNE` |
| §2 Modelclass-Binding + Provider ollama/hosted + Ausfall-Markierung | ✅ | `model/Modelclass`, `impl/ollama/OllamaProvider`, `impl/hosted/HostedProvider` — beide über jsonCasteds `PostJsonClient` (typed chat), Outage an der Modelclass; Stub-Server-Tests |
| §3 Task-Baum als Single Source of Truth | ✅ | `TaskNode`, `HarnessInstance`, Persistierung `<projekt>/.wood/harness.json` per write-temp-rename (`io/AtomicFileWrite`) |
| §4 Prompt-Segment-Liste mit Budgets + HISTORY-Anti-Vergessen | ✅ | `prompts/PromptSegment`, `PromptAssembler`, `RelevanceRegistry`; `buildSegments` leitet vorkondensierte HISTORY-Segmente (max. 3, gemeinsames Budget mit compact) aus dem calculatorLog ab |
| §5 Modell (JsonItemDefinition) vs. Instanz | ✅ | `io/def/JsonHarnessModelDefinition` + Beans + `HarnessModelBeanConverter`; `harness-model.json` parst/validiert/editiert wie jedes Bundle; gebundeltes Asset `assets/harness/harness-model.json` |
| §6 Metrik-only Call-Log, parent-Kette, persistierter nextId | ✅ | `CallLogEntry`, `metrics/MetricsStore` (M×3-Buckets), `CalcContext.nextTaskId/nextAufrufId` |
| §7 Swap/Evolution/Stammbaum (nie mitten im Lauf) | ✅ | `HarnessLineage`, `EvolutionRunner` (3 Kandidaten, +15 Ø-Score & halbierte Fail-Rate), Feedback-Loop nach CLOSE des Root-Tasks; `.wood/harness/lineage.json` persistiert über Läufe |
| §8 Globaler Metrik-Store (aggregiert, nie Events) | ✅ inkl. globaler Ablage | `metrics/MetricsStore`, `Lessons`, `GlobalWood` — `~/.wood/harness/metrics.json` + `lessons.json` werden pro Lauf geladen/akkumuliert/atomar zurückgeschrieben; Lesson-Export bei Ø-Score-Vorsprung ≥ 10 über das globale Prior; Priors read-only via `ctx.metrics.globalPriors()` |
| §11 Projekt-RAG inkl. Modellwechsel-Reset | ✅ inkl. Pipeline | `rag/RagStore`, `RagChunk`, `EmbeddingProvider`; RAG_STORE nach EVALUATE ≥ 50, RAG_RETRIEVE als DATA-Segmente, RAG_PRUNE nach CLOSE; `RagStore.load` vergleicht `ragModel` mit dem aktiven Embedder — Mismatch leert die RAG (Reset), verweigert aber bei aktiver Prompt-Bearbeitung (`setPromptEditActive`) |
| §12 Skills (Import → SKILL-RAG-Chunks, Matching pro Scope → SKILL-Segment als Verweis) | ✅ inkl. Prompt-Liste | `skills/Skill`, `SkillRegistry`; `HarnessEngine.buildSegments` matcht AUFTRAG gegen SKILL-Chunks und trägt nur `skill:<id>`-Verweis als SKILL-Segment (Volltext bleibt in der RAG, landet nicht als DATA) |
| §13 Tooling, Permission-Levels, ReAct-Loop, Signale, Catch | ✅ voller Katalog | `tools/`: `FsTools` (read/list/write/delete), `ProcessTools.run` (system, timeout, cwd=Projekt), `GitTools` (status/diff/log read, add write, commit destructive), `SearchTools.grep` (read, sandboxed), `EditorTools.open` (editor-Kategorie, UI-Handler via `HarnessEngine.setEditorOpenHandler`); alle 12 in der Engine registriert; bounded Tool-Loop gegen `maxToolCalls`; `signals/Signal` + on-Mapping; Catch-Mapping; LIMIT-Ausnahme; `[compacted]` |
| §14 Harness-API über `CalcContext` | ✅ vollständig | `task(), prompts()/relevance(), tools()/callTool(), rag(), metrics()/globalPriors(), skills(), lineage(), mindset(), modelclass()/isProviderAvailable(), limits(), emit(), approveExtraCounters(), compact(), abort(), focusTask(), askUser(), notify()` |
| §16 Mindset (global, kategorisiert, 1024-Chars-Limit, als Prompt-Segment) | ✅ inkl. `~/.wood/mindset/` + Prompt | `mindset/Mindset` + `GlobalWood` — eine Datei pro Kategorie unter `~/.wood/mindset/`, Load/Save um den Lauf in `HarnessService`, Facade `ctx.mindset()`; `buildSegments` zieht die Kategorie (`analysis` für RELEVANCE/EVALUATE/CHECK, sonst `answer`) als SYSTEM-Segment `mindset:<cat>` |
| UI Harness-Fenster + beide Entry Points (§3) | ✅ | `ui/HarnessViewPanel` (Live-Taskbaum, Status-Farben, Score-Badges, Detail mit Kalkulator-Protokoll), `ui/HarnessController`, Harness-Menü in `JackMainMenu` (Run Task…, **Run Task from Selection…** — Selektion wird zum Prompt, §3 Entry Point 2, Show Harness Window) |
| AI-Antwort-Vertrag (strict JSON) | ✅ | `impl/AiCalculator` — Parse-Fail → `SCHEMA_VIOLATION`, Provider down → `MODEL_UNAVAILABLE` |

## 3. Teststand

- Suite `test_assets/wjj_testng.xml`: **233 Tests, 233 grün** (frisch verifiziert, siehe Kopf).
- 20 Harness-Testklassen (`test/de/jare/harness/`), inkl. Stub-Server-Tests für beide Provider
  (Ollama `/api/chat` + `/api/tags`, Hosted Chat-Completions mit `Authorization: Bearer`),
  Tool-Loop, RAG-Pipeline, Evolution, Feedback, Signale/Catch, AI-Answer, Codecs, Definition, View, Controller,
  globale Ebene (`GlobalWood`-Pfade, globaler Metrik-Record, Service schreibt `~/.wood/harness/*` + `~/.wood/mindset/`).

## 4. Offene Punkte / nächste Schritte

1. **Merge:** `feature/harness-impl` nach `main` bringen (62 Commits fehlen dort; `develop` ist bereits enthalten).
2. ~~Konzept-Statuszeile~~ **erledigt:** `CONCEPT_Harness.md` verweist auf dieses Dokument (Commit `d96c49b`).
3. ~~§15 finale Defaults~~ **erledigt:** Abbruch-Defaults festgezurrt auf `maxIterations 50 / maxDepth 5 / maxRetries 3 / maxToolCalls 10` (Code-Defaults in `HarnessModel` = gebündeltes Asset = `HarnessService.defaultModel()`, Guard-Test in `HarnessServiceNGTest`); UI entschieden als Dialog `HarnessViewPanel` + Harness-Menü. §15 im Konzept umgeschrieben von „offen“ auf „entschieden“. Verbleibend offen im Konzept: keine.
4. ~~Globale Ebene `~/.wood/`~~ **erledigt:** `GlobalWood` verdrahtet `~/.wood/harness/metrics.json` + `lessons.json` und `~/.wood/mindset/` (Load vor Lauf, atomares Save nach Lauf, Lesson-Export bei signifikantem Vorsprung, Priors read-only via `ctx.metrics.globalPriors()`, Mindset via `ctx.mindset()`). Test-Override über System Property `de.jare.harness.woodHome` bzw. `HarnessService.setWoodHome()`.
5. ~~SKILL-/Mindset-Segmente in der Prompt-Liste~~ **erledigt:** `HarnessEngine.buildSegments` (nutzt von `execute` und `onSignal`) matcht AUFTRAG gegen SKILL-Chunks pro Scope → SKILL-Segment als `skill:<id>`-Verweis (nie Volltext, nie als DATA), und zieht die Mindset-Kategorie als `SYSTEM:mindset:<cat>`-Segment (§12/§16). Test in `HarnessSkillsMindsetNGTest`.

## 5. Review 2026-11-06 (Code vs. Konzept, Branch geprüft)

Branch sauber, Suite frisch **226/226 grün**. Umgesetzt und konform: alle 12 Kinds, Engine-Pipeline + harte Limits, Signale/Catch, Tool-Loop, Call-Log mit parent-Kette + persistiertem nextId, atomare Writes, Lineage/Evolution/Feedback, Provider-Outage an der Modelclass, strict-JSON-Antwortvertrag, globale `~/.wood`-Ebene, SKILL/Mindset-Segmente, §15 entschieden, UI.

**Lücken gegen das Konzept (Review-Findings) — Stand nach Umsetzung:**

1. ~~**§13 Tool-Katalog unvollständig**~~ **erledigt** (`fed8412`): voller Katalog — `fs.delete`, `process.run`, `git.status/diff/log/add/commit`, `search.grep`, `editor.open`; 12 Tools in der Engine registriert; 4 Tests (Katalog/Risiko/Kategorien, destructive-in-automode verweigert, grep-Sandbox, editor-Handler).
2. ~~**§4 HISTORY-Segmente fehlten**~~ **erledigt** (`1149da9`): `buildSegments` leitet vorkondensierte HISTORY-Segmente aus dem calculatorLog ab (max. 3, assembler-enforced); 1 Test.
3. ~~**§11 RAG-Modellwechsel-Reset fehlte**~~ **erledigt** (`2b8bc10`): `RagStore.load` vergleicht `ragModel` — Mismatch leert die RAG, verweigert aber bei aktiver Prompt-Bearbeitung (`setPromptEditActive`); 1 Test (keep/reset/refused).
4. ~~**§14 Facaden unvollständig**~~ **erledigt** (`6b5c84c`): `ctx.skills/lineage/ui.focusTask/askUser/prompts.relevance/modelclass/isProviderAvailable` + Skill-Registry-Loading aus global+lokal in `HarnessService`; 1 Facaden-Test. (`parent1()/parent2()` bleiben am `CallLogEntry` — die Kette ist dort persistiert, §6; als Task-Methoden nicht nötig.)
5. ~~**§3 Entry Point (2) fehlte**~~ **erledigt** (`a7cbfc7`): Harness-Menü „Run Task from Selection…“ — Selektion wird zum Prompt (`HarnessController.runTaskFromSelection`).
6. **§12 Skill-Partition im Projektbaum** — weiterhin offen, aber **Cross-Branch**: Sidebar-Mount der Skill-Ordner ist ProjectView-Feature (`feature/projectview`), nicht Teil dieses Branches.

**Nächste Schritte:** Merge nach `main` (68 Commits); Skill-Partition im Sidebar-Tree auf dem ProjectView-Branch.

## 6. Runtime-Modell: Quest umschließt WorkOrder + WorkCache + WorkResponse

Entschieden (engl. Design-Doc: `docs/working/DESIGN_Quest_WorkOrder.md`): `HarnessInstance` = **Rezept**;
die **Quest** (z. B. `AskAi`/SPLIT, slot `askai.base`) umschließt pro Lauf einen **WorkOrder** (Memory der
Berechnung: id, `userPrompt`-Feld, Taskbaum, Call-Log, Status), einen **WorkCache** (Zwischenergebnisse:
RAG-Chunks, Tool-Outputs) und eine **WorkResponse** (lebende, sich ändernde Antwort an die UI).

## 7. Trennung WoodHarness vom Editor (committet in `74a4ce4`)

- **Quellen nach `src_jh/`** (Package `de.jare.harness` bleibt); `src_jh` importiert **nichts** aus WJJ-`src` und **kein Swing/AWT** — allein kompilierbar gegen jsonCasted/jsonpost (verifiziert, 0 Fehler).
- **Neue SPI in `src_jh/de/jare/harness/spi/`:** `HarnessHost` (hostedApiKey, `harnessModelJson(File)` als JsonEdit-Handover, openInEditor, askUser, addRunListener, `wireInto(engine)`) und `HarnessView` (bind/setVisible, ohne Swing-Typen).
- **`HarnessService`** nimmt `HarnessHost` injiziert (WoodSettings-Zugriff eliminiert); `loadModel()` nimmt zuerst das vom Host übergebene JsonNode.
- **Swing-UI host-seitig:** `ui/` → `src/de/jare/tree/ui/harness/` (`HarnessController` als WJJ-Host-Impl, `HarnessViewPanel implements HarnessView`); `JackMainMenu` angepasst.
- **Tests** nach `test_jh/` verschoben (20 Klassen), UI-Test-Imports aktualisiert.
- **Verifiziert:** `src_jh` standalone grün, `src`+`src_jh` grün, **Suite 233/233 grün**. Noch nicht committet.
- **Ziel erreicht:** TUI ohne Swing möglich — zweiter Host implementiert nur `HarnessHost` + `HarnessView`.
