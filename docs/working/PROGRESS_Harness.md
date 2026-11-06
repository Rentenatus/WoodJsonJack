# PROGRESS_Harness — Fortschrittsdokument zur Harness-Implementierung

**Stand:** 2026-11-06, Branch `feature/harness-impl` @ `b53a19f`
**Konzept:** `docs/koncepts/CONCEPT_Harness.md` (16 Abschnitte)
**Verifikation:** frischer Build (javac, WJJ + jsonCasted + jsonCasted_edit + ndimcol) und
TestNG-Suite `test_assets/wjj_testng.xml` von Repo-Root aus — **224/224 bestanden, 0 Failures**
(Headless-Lauf am 2026-11-06; die beiden Asset-Tests brauchen das Repo-Root als Working-Dir)

---

## 1. Branch-Lage

| Branch | Commits vor `main` | Inhalt |
|---|---|---|
| `feature/harness-impl` (aktuell) | 58 | komplette Implementierung `de.jare.harness` (48 Klassen), 20 Testklassen, 114 Dateien geändert (+13.592) |
| `feature/projectview` | viele (eigene Linie) | Konzept-Ausbau von `CONCEPT_Harness.md` (Quest/Slot-Modell, Modelclasses als JsonMap, M×3-Metriken, RAG, Skills, Tooling, Mindset) + ProjectView-Phasen (Sidebar, Bundles, Reroll-ID) |
| `develop` | 1 | nur JsonBundle-Rename in der UI; `main` = Merge von `develop` |

**Noch offen:** `feature/harness-impl` ist nicht nach `main`/`develop` gemerged — `main` fehlen alle 58 Harness-Commits.

## 2. Umsetzungsstand gegen das Konzept

| Konzept | Stand | Code |
|---|---|---|
| §1 Kernentscheidung (deterministischer Treiber, eine Frage pro Call) | ✅ umgesetzt | `HarnessEngine` (Pipeline ABORT → SPLIT → COMPLETE → WORK → EVALUATE → CHECK → SUMMARIZE → CLOSE), `HarnessCalculator`/`CalcResult` |
| §2 Zwölf Kalkulator-Kinds, ein Interface | ✅ alle 12 in `Kind.java` | `RELEVANCE, SPLIT, COMPLETE, WORK, EVALUATE, CHECK, ABORT, SUMMARIZE, CLOSE, RAG_STORE, RAG_RETRIEVE, RAG_PRUNE` |
| §2 Modelclass-Binding + Provider ollama/hosted + Ausfall-Markierung | ✅ | `model/Modelclass`, `impl/ollama/OllamaProvider`, `impl/hosted/HostedProvider` — beide über jsonCasteds `PostJsonClient` (typed chat), Outage an der Modelclass; Stub-Server-Tests |
| §3 Task-Baum als Single Source of Truth | ✅ | `TaskNode`, `HarnessInstance`, Persistierung `<projekt>/.wood/harness.json` per write-temp-rename (`io/AtomicFileWrite`) |
| §4 Prompt-Segment-Liste mit Budgets | ✅ | `prompts/PromptSegment`, `PromptAssembler`, `RelevanceRegistry` |
| §5 Modell (JsonItemDefinition) vs. Instanz | ✅ | `io/def/JsonHarnessModelDefinition` + Beans + `HarnessModelBeanConverter`; `harness-model.json` parst/validiert/editiert wie jedes Bundle; gebundeltes Asset `assets/harness/harness-model.json` |
| §6 Metrik-only Call-Log, parent-Kette, persistierter nextId | ✅ | `CallLogEntry`, `metrics/MetricsStore` (M×3-Buckets), `CalcContext.nextTaskId/nextAufrufId` |
| §7 Swap/Evolution/Stammbaum (nie mitten im Lauf) | ✅ | `HarnessLineage`, `EvolutionRunner` (3 Kandidaten, +15 Ø-Score & halbierte Fail-Rate), Feedback-Loop nach CLOSE des Root-Tasks; `.wood/harness/lineage.json` persistiert über Läufe |
| §8 Globaler Metrik-Store (aggregiert, nie Events) | ✅ inkl. globaler Ablage | `metrics/MetricsStore`, `Lessons`, `GlobalWood` — `~/.wood/harness/metrics.json` + `lessons.json` werden pro Lauf geladen/akkumuliert/atomar zurückgeschrieben; Lesson-Export bei Ø-Score-Vorsprung ≥ 10 über das globale Prior; Priors read-only via `ctx.metrics.globalPriors()` |
| §11 Projekt-RAG | ✅ inkl. Pipeline | `rag/RagStore`, `RagChunk`, `EmbeddingProvider`; RAG_STORE nach EVALUATE ≥ 50, RAG_RETRIEVE als DATA-Segmente, RAG_PRUNE nach CLOSE |
| §12 Skills | ✅ | `skills/Skill`, `SkillRegistry` |
| §13 Tooling, Permission-Levels, ReAct-Loop, Signale, Catch | ✅ | `tools/HarnessTool`, `HarnessTools`, `FsTools`, `PermissionPolicy`; bounded Tool-Loop gegen `maxToolCalls`; `signals/Signal` + on-Mapping; Catch-Mapping inkl. `MODEL_UNAVAILABLE`/`SCHEMA_VIOLATION`; LIMIT-Ausnahme (max. 2 Extra-Zähler mit Begründung); `[compacted]`-HISTORY |
| §14 Harness-API über `CalcContext` | ✅ Facade liegt vor | `task(), prompts(), tools()/callTool(), rag(), metrics(), emit(), approveExtraCounters(), compact(), abort(), limits(), notify()` … |
| §16 Mindset (global, kategorisiert, 1024-Chars-Limit) | ✅ inkl. `~/.wood/mindset/` | `mindset/Mindset` + `GlobalWood` — eine Datei pro Kategorie unter `~/.wood/mindset/`, Load/Save um den Lauf in `HarnessService`, Facade `ctx.mindset()` |
| UI Harness-Fenster | ✅ | `ui/HarnessViewPanel` (Live-Taskbaum, Status-Farben, Score-Badges, Detail mit Kalkulator-Protokoll), `ui/HarnessController`, Harness-Menü in `JackMainMenu` (Run Task…, Show Harness Window) |
| AI-Antwort-Vertrag (strict JSON) | ✅ | `impl/AiCalculator` — Parse-Fail → `SCHEMA_VIOLATION`, Provider down → `MODEL_UNAVAILABLE` |

## 3. Teststand

- Suite `test_assets/wjj_testng.xml`: **224 Tests, 224 grün** (frisch verifiziert, siehe Kopf).
- 20 Harness-Testklassen (`test/de/jare/harness/`), inkl. Stub-Server-Tests für beide Provider
  (Ollama `/api/chat` + `/api/tags`, Hosted Chat-Completions mit `Authorization: Bearer`),
  Tool-Loop, RAG-Pipeline, Evolution, Feedback, Signale/Catch, AI-Answer, Codecs, Definition, View, Controller,
  globale Ebene (`GlobalWood`-Pfade, globaler Metrik-Record, Service schreibt `~/.wood/harness/*` + `~/.wood/mindset/`).

## 4. Offene Punkte / nächste Schritte

1. **Merge:** `feature/harness-impl` nach `develop`/`main` bringen (alle Harness-Commits fehlen dort).
2. ~~Konzept-Statuszeile~~ **erledigt:** `CONCEPT_Harness.md` steht auf „221/221 grün“ mit Verweis auf dieses Dokument (Commit `d96c49b`).
3. **§15 des Konzepts** ist noch als „offen“ markiert: finale Abbruch-Defaults (maxIterations/maxDepth/maxRetries/maxToolCalls) und UI-Details — die View existiert inzwischen als `HarnessViewPanel`, die Default-Werte sollten final festgezurrt und ins Konzept übernommen werden.
4. ~~Globale Ebene `~/.wood/`~~ **erledigt:** `GlobalWood` verdrahtet `~/.wood/harness/metrics.json` + `lessons.json` und `~/.wood/mindset/` (Load vor Lauf, atomares Save nach Lauf, Lesson-Export bei signifikantem Vorsprung, Priors read-only via `ctx.metrics.globalPriors()`, Mindset via `ctx.mindset()`). Test-Override über System Property `de.jare.harness.woodHome` bzw. `HarnessService.setWoodHome()`.
5. Skills-Import in die Projekt-RAG ist umgesetzt (`SkillRegistry.importSkill`, `docType=SKILL`, N Descriptions = N Chunks ✅) — **fehlt noch:** SKILL- und Mindset-Segmente in der Prompt-Liste (`prompts/`-Paket erzeugt noch keine `SKILL`-Kategorie; RelevanceCalculator zieht Skill-Chunks/Mindset noch nicht, §12/§16).
