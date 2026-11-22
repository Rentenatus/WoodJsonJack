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
6. ~~Freie Loop-Konfiguration~~ **erledigt** (CONCEPT §5 Loop-Regel + §15.6, Spec in
   `docs/working/DESIGN_Loops.md`, Commits `c05f360`, `3a2d13e`, `28cdf1a`, `8786625`):
   `loops[]` (benannte Loops) + LoopItem `{id, kind, loop?, trigger}` (START/EXECUTE/CATCH/CLOSE) in der
   Modell-JSON inkl. Codec-Validierung; LoopAction als Flow-Kanal neben HarnessAction
   (Continue/Restart/Break/SubLoop/ThrowError in `main.actions`); ErrorSignal DOMAIN/TECHNICAL, kein
   Throwable über die Loop-Grenze (Fangnetz in `runNode` + `run`); Engine führt Loops nur aus
   (`runLoop`, START einmalig, CLOSE immer im finally, CATCH nur bei DOMAIN, TECHNICAL unwindet);
   Guards (RAG_STORE minScore, SUMMARIZE no-children) sitzen in den Kalkulatoren; Asset + Service-Default
   tragen `loops.main`. Tests: `LoopConfigCodecNGTest`, `LoopActionNGTest`, `LoopEngineNGTest`.

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

Entschieden (engl. Design-Docs: `docs/working/DESIGN_Quest_WorkOrder.md` und
`docs/working/DESIGN_Calculator_Factory_Builder.md`): `HarnessInstance` = **Rezept**;
die **MemoryQuest** (Package `de.jare.harness.memory`, pro Lauf) umschließt einen **WorkOrder**
(Memory der Berechnung: id, `userPrompt`-Feld, Taskbaum, Call-Log, Status), einen **WorkCache**
(Zwischenergebnisse) und eine **WorkResponse** (lebende Antwort an die UI). Benannte Kalkulatoren
(`"calculator": "scoring.countQuests", "params": {…}}`) werden über Registry + `CalculatorBuilder`
uniform aus den Config-Params gebaut — die Fabrik kennt keine Kalkulator-Details.

## 7. Trennung WoodHarness vom Editor (committet in `74a4ce4`)

- **Quellen nach `src_jh/`** (Package `de.jare.harness` bleibt); `src_jh` importiert **nichts** aus WJJ-`src` und **kein Swing/AWT** — allein kompilierbar gegen jsonCasted/jsonpost (verifiziert, 0 Fehler).
- **Neue SPI in `src_jh/de/jare/harness/spi/`:** `HarnessHost` (hostedApiKey, `harnessModelJson(File)` als JsonEdit-Handover, openInEditor, askUser, addRunListener, `wireInto(engine)`) und `HarnessView` (bind/setVisible, ohne Swing-Typen).
- **`HarnessService`** nimmt `HarnessHost` injiziert (WoodSettings-Zugriff eliminiert); `loadModel()` nimmt zuerst das vom Host übergebene JsonNode.
- **Swing-UI host-seitig:** `ui/` → `src/de/jare/tree/ui/harness/` (`HarnessController` als WJJ-Host-Impl, `HarnessViewPanel implements HarnessView`); `JackMainMenu` angepasst.
- **Tests** nach `test_jh/` verschoben (20 Klassen), UI-Test-Imports aktualisiert.
- **Verifiziert:** `src_jh` standalone grün, `src`+`src_jh` grün, **Suite 233/233 grün**. Noch nicht committet.
- **Ziel erreicht:** TUI ohne Swing möglich — zweiter Host implementiert nur `HarnessHost` + `HarnessView`.

## 8. Reroll-Semantik invertiert (ProjectView §7.1, uncommittet)

Umgesetzt in `git_jsonCasted` (`BundleReroller.plan(..., rerollIds, rerollSynonyms)`),
`git_WoodJsonJack` (`RerollIdsDialog`: zwei Checkboxen): **Synonym-Reroll ist Default (an)**,
**ID-Reroll nur als Ausnahme (aus)**. Begründung: IDs sind bundle-scoped — Kopien einer Datei
dürfen dieselben Nummern behalten (starker Hinweis für den Compare); Synonyme sind Wiring-Namen
im gemeinsamen Namensraum und müssen pro Baum frisch sein. 5-arg-`plan` = Default
(`rerollIds=false, rerollSynonyms=true`); interne Links werden nur umgeschrieben, wenn sich etwas
ändert; dangling-Validierung bleibt in jeder Schalterstellung. Tests: `BundleRerollerNGTest` 8/8,
`RerollCommandsNGTest` 1/1 grün. Konzept: `CONCEPT_ProjectView.md` §7.1 + Entscheidung 8.

## 9. Umgesetzt (nicht committet): Task-Baum als EditTree im Harness

Entschieden in `CONCEPT_Harness.md` §3/§10/§15 (Diskussion ViewNode vs. EditNode-Ableitung vs. JsonNode-Speicher):

1. **Build:** src_jh gegen jsonCasted_edit kompilieren lassen (Sourcepath um `D:\git_jsonCasted_edit\src` erweitern; jsonCasted_edit ist Swing-frei — verifiziert 0 AWT/Swing-Imports).
2. **TaskNode → TaskData (Facade mit Back-Reference, §3/§15 Konzept):** `TaskData` hält den belegten `EditNodeObject`; Getter/Setter (status, score, attempt, close, logCall, appendPromptSupplement, rewriteAsSummary, addSubtask) lesen/schreiben durch in die Captured Attributes (`getAttributes()`, primitive Werte) und feuern das Change-Event — kein manuelles Attach. Kein parent/children/depth mehr in TaskData; children/parent navigieren über den EditNode + Registry. Umbenennen nach `TaskData` (Name klärt die zwei Node-Ebenen: fachlicher Task vs. EditNode der Anzeige).
3. **WorkOrder/MemoryQuest halten eine EditTree** als Task-Baum plus eine Registry `IdentityHashMap<EditNode, TaskData>` (Node→TaskData). Codec (`HarnessInstanceCodec.taskToJson/taskFromJson`) entfällt für den Baum — Persistierung läuft über die JsonNode-Wurzel der EditTree; Attributes serialisieren als primitive Werte.
4. **Sync-Brücke:** Harness mutiert EditTree über deren notify-API (`TypeParserListener`, `notifyChildAdded/Removed/…`); WJJ hängt sich als Listener ein und ruft `JackTreeModel.rebuildFromDomain()`/`structureChanged` — DefaultMutableTreeNode bleibt dünne Shell um dieselben EditNode-Instanzen (kein Kopieren). EDT-Marshalling via `SwingUtilities.invokeLater` im Host-Listener.
5. **Read-only-Schutz:** Harness-Bäume sind für die UI nicht editierbar — `readOnly`-Flag an der EditTree (jede `CommandAvailability` verweigert) oder Harness-Panel ohne Edit-Commands; einziger Mutationspfad = Harness-API.
6. **Verifikations-/Testplan:** Engine-Tests (TaskData-Payload statt TaskNode), Codec-Test, Sync-Test (Harness-Mutation → Listener-Event), Read-only-Test (Command verweigert auf Harness-Baum); danach volle WJJ-Suite.

**Verworfene Alternativen (festgehalten):** TaskNode extends EditNode (sealed permits), ViewNode als 5. permit in jsonCasted_edit (src_jh dürfte ihn nicht nutzen), ViewNode als eigener Baum in jsonCasted (Editor müsste konvertieren), TaskNode als JsonNode-Facade (Zwei-Welten-Problem bleibt für die Anzeige bestehen).

**Veraltet — überholt durch §10.11:** die hier beschriebene EditTree-Kopplung (`TaskTree`, Facade auf `EditNodeObject`) wurde verworfen; umgesetzt wurde §10.11 (TaskData als plain POJO, JSON als Persistenzform). §9 bleibt als verworfene Alternative dokumentiert.

## 10. Projektgedächtnis + Chat-Befehle (Konzept §17 entschieden, noch nicht umgesetzt)

Entschieden (siehe `CONCEPT_Harness.md` §17):

1. **ProjectMemory** in `de.jare.harness.memory.project` (o. ä.): Datei `<projekt>/.wood/harness/memory.json`, atomar via `AtomicFileWrite`, geladen/geschrieben von `HarnessService` (projectDir ist bereits bekannt — kein neues Host-API). Einträge `{id, ts, kind: CHAT|NUGGET, role?, text, source?, ttlMs?, uses, hits}` mit TTL-Verfall + `/clear`/`/forget`.
2. **Nugget-Feedback/Scoring:** `uses`/`hits` pro Eintrag = konstruierte erste Ableitung der Kostenfunktion (§9-Analogie) — erlaubt das Scoren **jedes** Kalkulators (auch Nugget-Produzenten im Slot), über dieselbe Metric-Pipeline wie §7/§8. Nugget-Extraktor-Kalkulatoren kommen später als eigene Slots in der Loop (§15.6).
3. **Eigener `de.jare.harness.main.chat.ChatCommandService`:** `dispatch(input)` prüft `/`-Prefix vor jedem Run (`/clear`, `/forget <id>`, `/memory`, `/help`); UI bleibt dumm, Popup-Menü der KI-Pane ruft nur Service-Befehle.

**Umsetzungsplan (schrittweise, testen, dann commit auf Ansage):**

1. `ProjectMemory` + Codec (load/save `memory.json`, TTL-Prune, uses/hits-Pflege) + `ProjectMemoryNGTest`.
2. `HarnessService`: Memory vor dem Run laden, nach dem Run saven; `CalcContext`-Zugriff für Kalkulatoren; CHAT-Einträge als HISTORY-Segment einspeisen.
3. `ChatCommandService` (Befehle + Dispatch, Run-Pfad unverändert) + `ChatCommandServiceNGTest`; Anbindung `HarnessController.runAskAi` → Service statt direkt `runTask`.
4. Feedback-Hook: `uses` inkrementieren, wenn ein Nugget in einen Prompt übernommen wird; `hits` bei Run-Ende mit score ≥ Schwelle; Ausleitung in `MetricsStore` pro `source`-Kalkulator.
5. UI: Popup-Menü (`JPopupMenu`) in `KIAssistantPanel`, ruft `ChatCommandService` (host-seitig verdrahtet).

## 10.1 Umgesetzt: DECISION-Typ + /decide (committet)

`ProjectMemory.Kind.DECISION` (TTL 90 Tage, `addDecision(text, source)`), Einspeisung als
SYSTEM-Segment (`memory:decision-<id>`) in jeden Folge-Run, `/decide <text>` Befehl im
`ChatCommandService` (legt DECISION mit source `user:/decide` an), Popup-Eintrag „Record decision"
legt `/decide ` in die Eingabe. Tests: ProjectMemoryNGTest + ChatCommandServiceNGTest erweitert.

## 10.2 Umgesetzt: Split startet Subloops statt neuer Main-Loops (uncommittet)

`LoopSplit` trägt jetzt die Ziel-Loop-ID (`loopId`, default `"sub"`, null/blank normalisiert auf `"sub"`).
`HarnessEngine.handleSplit` startet pro Subtask `process(child, loopId)` — also einen Subloop — statt
`process(child)` (frische main-Loop). `Loop.SUB = "sub"` + `HarnessModel.defaultSubLoop()` als Fallback,
wenn die JSON zwar `loops[]` hat, aber keinen `"sub"`-Eintrag. `harness-model.json` enthält `"sub"` als
exaktes Duplikat von `"main"`. Optionaler Config-Param `subloop` am `askai.decompose`-Slot (Builder
`AskAiDecomposeBuilder.withSubloop`). WorkCache trägt zusätzlich `subloopId`. Tests: DecomposeSplitNGTest
(neu `testLoopSplitCarriesSubloopId`, Engine-Test mit expliziten main/sub-Loops und SUMMARIZE-Marker in
main — Kinder laufen sub, main-only Items feuern nicht). Harness-Suite 140/140, WJJ gesamt 248/248 grün.

## 10.3 Umgesetzt: GUARD + SKILL am START der main loop (uncommittet)

`Kind` um `GUARD` und `SKILL` erweitert (jetzt 14). In `harness-model.json` hat nur der `"main"`-Loop
am START `{ "id": "guard", "kind": "GUARD" }` (ersetzt das bisherige `abort`) und zusätzlich
`{ "id": "skill", "kind": "SKILL" }`; der `"sub"`-Loop behält `abort` am START. Neue Quests GUARD
(deterministischer Slot) und SKILL (benannter Kalkulator `skill.collect`, registriert in
`DefaultCalculatorFactory`). Deterministische GUARD-Logik in `DeterministicCalculators.guard`:
blanker Prompt oder (konfigurierte, aber alle unavailable) Modelclasses → FAILED + `LoopThrowError`
DOMAIN (CATCH feuert); sonst DONE mit `modelsAvailable`. `skill.collect`
(`SkillCollectCalculator`/`SkillCollectBuilder`) sammelt die SkillRegistry (`ctx.skills()`) in den
WorkCache (`skills.count`/`skills.ids`/`skills.descriptions`). `HarnessModel.defaultMainLoop()` baut
GUARD+SKILL-START programmatisch, `defaultSubLoop()` behält ABORT. Tests: neuer `GuardSkillNGTest`
(5 Tests, inkl. Engine-Lauf: GUARD/SKILL feuern genau 1× in main, Split-Kinder laufen sub mit ABORT),
`LoopConfigCodecNGTest`/`LoopEngineNGTest`/`HarnessEngineNGTest`/`HarnessServiceNGTest`/`HarnessMetricsNGTest`
angepasst. Harness-Suite 145/145, WJJ gesamt 248/248 grün.

## 10.4 Umgesetzt: Kind als Registry-Klasse + kinddefs[] im Root; HarnessService gekapselt (uncommittet)

`Kind` ist jetzt eine `final class` mit String-Registry: 14 eingebaute Konstanten
(`public static final Kind`), `Kind.valueOf` registriert Custom-Literale on the fly,
`Kind.values()/names()/isBuiltin()` vorhanden. `harness-model.json` trägt `"kinddefs": []` im Root;
Codec + Definition-Bean/Converter lesen/schreiben `kinddefs[]` und validieren jedes Kind-Literal in
quests/loops gegen built-ins ∪ kinddefs (Typo-Schutz bleibt). `DeterministicCalculators`: switch →
Map, Custom-Kinds ohne built-in impl → generischer Fallback `done`. `HarnessModel`: kinddefs-Liste +
`isKnownKind` + copy. Service-Kapselung: `setWoodHome`, `getProjectDir`, `modelFile()`,
`instanceFile()`, `getProjectMemory()` jetzt package-private (nur noch same-package Tests/Intern);
public bleiben Konstruktoren, `runTask`, `loadModel`, `getLastMemoryQuest`, `defaultModel`,
`memoryFile()` (braucht `main.chat.ChatCommandService`) und die Datei-Konstanten. P.S. `subloop: "sub"`
explizit im askai.decompose-Slot. Tests: 3 neue kinddef-Tests in LoopConfigCodecNGTest, EnumMap →
HashMap in LoopEngineNGTest/GuardSkillNGTest. Harness-Suite 148/148, WJJ gesamt 248/248 grün.

## 10.5 Umgesetzt: Loop-Struktur + Parameterfehler messen, Prior-Inheritance bei Mutation (uncommittet)

- `CallLogEntry` trägt jetzt `loopId`/`loopItemId`/`trigger`/`paramError` (JSON-Roundtrip inklusive); die Engine
  reicht den Loop-Kontext aus `runItem`/`handleThrow` in `execute(...)` durch.
- `MetricsStore`: neue Kategorie `PARAM_ERROR`; `recordCall` aggregiert jeden Call zusätzlich unter
  `loop:<loopId>/<itemId>@<trigger>` und zählt Param-Fehler unter `slotId` UND `slotId@paramKey`.
- `HarnessException.Type.PARAMETER` + `paramKey`; `ErrorSignal` mappt es auf DOMAIN; `CalcContext.temperature()`
  wirft bei invalider Zahl PARAMETER; Builder strikt via `CalculatorBuilder.rejectUnknown` (generische Params wie
  `temperature` bleiben erlaubt).
- `cloneWithInheritance(mutated, priorWeight)`: mutierter Knoten erbt alte EMA als Prior (Count skaliert),
  `cloneWithReset` = priorWeight 0 (altes Verhalten). `structureScore(node, depth, wFail, wParam)` als
  berechnete Sicht auf die Buckets.
- Tests: +4 in HarnessMetricsNGTest (Loop-Keys, Param-Count, Prior-Inheritance, structureScore), +2 in
  LoopEngineNGTest (Loop-Kontext im CallLog + End-to-End PARAM_ERROR), +2 in HarnessCodecNGTest (temperature
  PARAMETER, strikte Builder). Harness-Suite 155/155, WJJ gesamt 253/253 grün.

## 10.6 Konzept ergänzt: PARAM_ERROR als Mutations-Maß (committet mit Doku)

CONCEPT_Harness.md §7 Punkt 4: Häufige Parameterfehler (`PARAM_ERROR` pro `slotId@paramKey` bzw. `loop:…`-Keys)
sind ein eigenes Schwellen-Kriterium der Feedback-Schleife — betroffener Slot/Loop-Item wird Mutations-Kandidat;
`structureScore` (§6) fasst SCORE + FAIL_RATE + PARAM_ERROR zur Struktur-Bewertung zusammen.

## 10.7 Umgesetzt: skill.match + RAG pro Embedding-Modell (uncommittet)

- Neuer benannter Kalkulator `skill.match` (`SkillMatchCalculator`/`SkillMatchBuilder`): Params `filter`
  (optionaler Namensfilter, kommasepariert=ODER), `minSimilarity` (Cosinus-Akzeptanz, default RAG-Default),
  `topK`, `embedModel` (rag-Modelclass by name, Fallback purpose "rag"; invalide Zahlen → PARAMETER).
  Importiert die Registry-Skills idempotent in den gewählten Store, matcht den User-Prompt gegen alle
  SKILL-Chunks (docType-Filter vor topK via neuem `RagStore.retrieve(query, docType, topK, minScore)`),
  alle Treffer in den WorkCache (`skills.matched`, `skills.matched.count`, `skill.match.<id>`,
  `skills.matched.detail`, `skills.matched.model`).
- Modellauswahl: `OllamaEmbeddingProvider` (POST /api/embed) + `DefaultCalculatorFactory.embeddingProviderFor(mc)`
  + `CalculatorCreationContext.embeddingProviderFor`; ohne Ollama-Provider → hashEmbedding-Fallback.
- **Zwei rag Modelle = zwei DBs:** `HarnessInstance` hält jetzt `Map<modelName, RagStore>`; Persistenz pro
  Modell als `.wood/harness/rag/<model>.json` (Load/Save in `HarnessService.runTask`).
- Config: SKILL-Quest als Puzzle `[skill.collect, skill.match {embedModel: nomic-embed-code, minSimilarity: 0.35}]`.
- Tests: +3 GuardSkillNGTest (Name-Filter + WorkCache-Treffer, Akzeptanzschwelle, zwei Stores), +1
  HarnessRagNGTest (docType vor topK), +1 HarnessServiceNGTest (RAG-Persistenz). Harness-Suite 159/159,
  WJJ gesamt 254/254 grün (HarnessViewNGTest flaky wie bekannt).

## 10.8 Umgesetzt: zweistufiger GUARD (guard.check + guard.escalate) + Sandbox-Tuning des Systemprompts

- `guard.check` (GuardCheckCalculator/Builder, scoring/phi4-mini): klassifiziert den User-Prompt als
  SAFE/SUSPICIOUS/BLOCKED, striktes JSON + Emphasis-Retry; Verdict in WorkCache (`guard.verdict` etc.);
  BLOCKED → `LoopThrowError(DOMAIN)` → CATCH feuert, Run stoppt. `guard.escalate` (evaluation/granite3.3:8b)
  läuft nur bei SUSPICIOUS, liest das erste Verdict aus dem WorkCache, finalisiert als `guard.finalVerdict`.
- Sandbox-Reasoning (C:/tmp/harnessprobe/GuardSandbox, live gegen ollama phi4-mini, temp 0, 6 böse + 6
  harmlose Prompts): 8 Kandidaten-Systemprompts getestet; Gewinner „H" mit 12/12 → in
  `harness-model.json` (guard.check systemPrompt) und als `DEFAULT_SYSTEM_PROMPT` im Kalkulator.
- Config: GUARD-Quest = Puzzle `[guard.check, guard.escalate]`. Tests: GuardVerdictNGTest (6 Tests inkl.
  Engine-Level: BLOCKED stoppt den Run, CATCH feuert, WORK wird nie erreicht). Harness-Suite 165/165,
  WJJ gesamt 254/254 grün.

## 10.9 Code-Review Harness: Redundanzen geschnitten, toter Code entfernt, RAG-Store-Bug gefixt

Review-Fragen: Linientreue zum Konzept, redundanter/ungenutzter Code, Schnittstellen-Qualität.
Schichtung (§10) verifiziert: src_jh hat 0 Swing/AWT- und 0 de.jare.tree-Imports.

**Redundanz geschnitten (Commits 549ff43, 3205cc5, 9e143c6):**
- Neu `impl/AiAnswers`: eine Stelle für Provider-Call (Availability + temperature + MODEL_UNAVAILABLE),
  strict-JSON-Parse (SCHEMA_VIOLATION), tolerantes Status-Parsing, JsonNode-Text/Score-Accessors, abbrev
  und den Emphasis-Retry (`callAndParse`). Ersetzt die 5 kopierten Blöcke in AiCalculator,
  ScoringCountQuestsCalculator, AskAiDecomposeCalculator, GuardCheckCalculator, GuardEscalateCalculator.
- Neu `io/JsonNodes` (child/text/longOf/doubleOf): ersetzt die 4 kopierten Helfer-Sets in CallLogEntry,
  HarnessModelCodec, HarnessInstanceCodec, RagStore; AiAnswers.text delegiert dorthin.
- Neu `impl/ollama/JsonEsc` (esc/quote): ersetzt esc/quote in OllamaProvider + OllamaEmbeddingProvider.

**Toter Code entfernt (9e143c6):** `OllamaProvider.extractResponse`/`jsonString` (nie aufgerufen),
`HarnessInstance.rag`-Feld (nur geschrieben, nie gelesen — Stores leben nur in der `rags`-Map).

**Bug gefixt (d8d1ac3):** `engine.activeRag()` löst den Store der rag-Modelclass auf (Fallback hash);
`ctx.rag()` und `buildSegments` benutzen ihn jetzt — vorher schrieb `skill.match` in den nomic-Store,
das Prompt-Retrieval las aber den Hash-Store (Skill-Treffer kamen nie im Prompt an). Retrieval in
`buildSegments` ist best-effort (leerer Store wird geskippt, Embedder-Ausfall wird geloggt, nicht fatal);
RAG_STORE meldet Embedder-Ausfall als `store-failed` (Seitenkanal, kein Run-Fehler); der deterministische
Fallback kann nicht mehr selbst werfen. `DefaultCalculatorFactory.registerEmbeddingProvider` erlaubt
Hosts/Tests eigene Embedder pro Modellname. Neuer Test `testActiveRagStoreFollowsRagModelclass`.

**Unbenutzter Code — tot vs. Chance (Entscheidung):**
- TOT (entfernt): siehe oben.
- CHANCE (behalten, bewusst): `WorkCache.previousResult` (Schnellzugriff für Deltas, §15.6);
  `MetricsStore.structureScore` (wird als nächstes in `EvolutionRunner` eingehängt); `Kind.RAG_RETRIEVE`
  + `DeterministicCalculators.ragRetrieve` (Loop-Item später; Retrieval läuft heute inline in
  `buildSegments`); `HarnessTools.exposeScopes/allowed` (Exposure-Budget §13, wird als nächstes in
  `AiCalculator.toolLoop` verdrahtet); `LoopItem.loop` (Subloop-Ref, in der Engine verdrahtet, in der
  Konfig noch ungenutzt).

**Noch offen nach dem Review:** TEMP-`[CALC >>]/[CALC <<]`-Printlns in der Engine (warten auf
Debug-Ende); Legacy-`splitInto` prozessiert Kinder noch mit "main" statt "sub" (nur über Legacy-
decision erreichbar); `HarnessEngine.RAG_STORE_MIN_SCORE` als Engine-Konstante (soll als Slot-Param in
den Kalkulator); `HarnessViewNGTest` flaky (~1/3, EDT-Timing, isoliert grün); KI-Pane zeigt die
WorkResponse-Antwort noch nicht an.

Harness-Suite 166/166, WJJ gesamt 255/255 grün.

## 10.10 Umgesetzt: Provider "system" als deklarierter eingebauter Fallback

- `Modelclass.PROVIDER_SYSTEM` + `isSystem()` (case-insensitiv, "System" == "system").
- `DefaultCalculatorFactory.createAtomic`: ai-Slot auf einer system-Modelclass → deterministischer
  Kalkulator des Kind (kein Provider-Call, kein Netzwerk) — dasselbe Verhalten wie der
  Catch-Mapping-Fallback, aber deklarierbar in der Konfig.
- `embeddingProviderFor(system rag)` → `EmbeddingProvider.hashEmbedding(<declared model name>)`;
  der Store keyed darunter und persistiert als `.wood/harness/rag/hash-embedding.json`.
- `HarnessEngine.activeRag()`: echtes rag-Modell (ollama/hosted) schlägt immer den system-Fallback;
  erst wenn nur system deklariert ist, greift es.
- Asset `assets/harness/harness-model.json` + `HarnessService.defaultModel()` deklarieren
  `{ "provider": "system", "model": "hash-embedding", "purpose": "rag", "size": 0, "costFactor": 0.0, "toolCapable": false }`.
- Konzept ergänzt: CONCEPT_Harness.md §2 (Provider-Liste um `system`) und §11 (deklarierter Fallback).
- Tests: +1 HarnessCodecNGTest (ai-auf-system → deterministisch, Codec-Roundtrip, Case-Insensitiv),
  +2 HarnessRagPipelineNGTest (activeRag bevorzugt echtes Modell / fällt auf system zurück),
  Asset-Count 11→12 + system-Assertion in HarnessServiceNGTest. Harness-Suite 169/169, WJJ gesamt 258/258 grün.

## 10.11 Umgesetzt: Task-Baum als plain POJO (EditTree-Kopplung entfernt) + Datenbewegung als calls[]

- **Entscheidungsrevision:** `TaskTree`/EditTree ist raus. `TaskData` ist ein plain POJO
  (Felder + `children` + `calculatorLog` + `calls`), JSON (`toJson/fromJson`) ist direkt die
  Persistenzform — der Codec delegiert an `TaskData.toJson`, kein Hand-Bau, kein EditNode→JSON.
  `src_jh` hängt damit nicht mehr von jsonCasted_edit ab (ViewNode/EditTree-Bridge obsolet).
- **Datenbewegung im Baum (§3):** Jeder atomare Kalkulator-Schritt hängt als `CalcCall` an das
  `calls[]` des Task: `input` (Prompts), `payload` (CalcResult als Daten, z. B. `skills.matched.*`
  von skill.match), `loopAction` (type/flow + `LoopAction.detail()` — LoopSplit trägt die
  Sub-Prompts, LoopThrowError das ErrorSignal, LoopSubLoop die loopId). Hook: `HarnessEngine.runNode`
  nach jedem atomaren `calculate()` → `task.appendCalcCall(...)`.
- **Events:** Mutationen feuern `Consumer<TaskData>` direkt auf dem Task; Subtask-Events bubblen
  mit Origin zur Root; `HarnessInstance.addChangeListener` reicht sie an die UI durch
  (`HarnessViewPanel`, `WoodWindow.openHarnessTreeTab` bauen ihren JTree direkt aus TaskData).
- `LoopAction` hat neues `default JsonNode detail()` (null = nur type/flow); Overrides in
  `main.actions` (LoopSplit/LoopSubLoop/LoopThrowError) — erweiterbar wie die Kalkulatoren.
- Legacy-Load: `id`→`taskId`, `children`→`tasks`, string-CalculatorLog→Array (beides Varianten
  getestet). `WorkOrder` hält nur noch den `TaskData`-Root, `MemoryQuest` erstellt ihn direkt.
- Tests: TaskTreeNGTest → TaskDataNGTest (POJO, Event-Bubbling, CalcCall-Projektion,
  Codec-Roundtrip inkl. calls, Legacy-Load), DecomposeSplitNGTest mit Engine-Level-Assertion
  auf `getCalls()`. wjj_testng.xml aktualisiert. Harness-Suite 170/170, WJJ gesamt 258/258 grün.

## 10.12 Umgesetzt: Legacy-Aktionen auf LoopAction migriert — ein Split-Pfad (splitInto entfernt)

- `HarnessAction` hat zwei neue Default-Methoden: `loopAction()` (null → Fallback über `control()`)
  und `control()` — Ableitung polymorph, kein Type-String-Switch mehr in `CalcResult`.
- Alle Built-ins in `main/actions` deklarieren ihren Flow: `None`→Continue, `Retry`/`ReasoningSubtask`→Restart,
  `Abort`→LoopThrowError(DOMAIN) (CATCH feuert statt stillem Break), `Split`→`LoopSplit(prompts, "sub", parts)`.
- `LoopSplit` trägt jetzt optional `parts` (Fallback für generierte Part-Titles); `LoopActions.fromDecision`
  hat Payload-Overload: legacy decision `"split"` + payload `quests`/`parts` → derselbe `LoopSplit`.
- `HarnessDriver.splitInto` und die Engine-Implementierung sind **entfernt** — `handleSplit` ist der
  einzige Split-Pfad (subloop pro Subtask, Verbrauchsvererbung, Deltas) und macht jetzt auch
  `rewriteAsSummary()` + parent-SUMMARIZE nach den Subloops (Legacy-Semantik bewahrt).
- Tests: LoopActionNGTest (Split/Abort-Mapping, legacy split-decision mit/ohne payload),
  HarnessEngineNGTest.testSplitRewritesTaskAsSummaryAndClosesChildren unverändert grün,
  DecomposeSplitNGTest: SUMMARIZE feuert genau 1× am Parent nach den Subloops.
  Harness-Suite 171/171, WJJ gesamt 258/258 grün.

## 10.13 Umgesetzt: Prompts nur noch in der Config-JSON + structureScore in der Evolution + Embed-API-Fix

- **Prompt-Regel durchgesetzt:** `DEFAULT_SYSTEM_PROMPT`/`DEFAULT_EMPHASIS_PROMPT` aus allen 4
  KI-Kalkulatoren entfernt; fehlt der Config-Param `systemPrompt`, wirft der Kalkulator einen
  PARAMETER-Fehler (PARAM_ERROR-Metrik) — kein hartkodierter Fallback mehr. `emphasisPrompt` ist
  optional (null = kein Retry). `HarnessService.defaultModel()` lädt das gebundelte Asset
  `assets/harness/harness-model.json` (fehlende Kinds werden deterministisch ergänzt, ohne Prompts).
- **Codec/Engine:** `promptBudgets.maxTokensTotal` ist Modell-Feld, keine Kategorie — Codec und
  Bean-Converter routen es auf `HarnessModel.setMaxTokensTotal`, Engine skippt Nicht-Kategorie-Keys.
- **Bugfix OllamaEmbeddingProvider:** `/api/embed` antwortet `{"embeddings":[[...]]}` — der Provider
  parste das Legacy-Feld `embedding` und warf SCHEMA_VIOLATION. Jetzt `embeddings[0]` (Legacy-Feld
  als Fallback). Betrifft skill.match/RAG gegen live Ollama.
- **Evolution (§7.2 Selection-Regel):** `EvolutionRunner` nutzt jetzt beides — harte Gates
  (Ø-Score +15 UND FAIL-Rate halbiert vs. base) entscheiden WER gewinnt; unter den Gate-Passern
  rangiert die `structureScore` (Gewichte wFail=wParam=1.0), bei < MIN_SAMPLES=3 Samples der rohe
  Ø-Score (dünne Daten = structureScore-Rauschen). `CandidateResult` trägt structureScore.
  Test: Kandidat mit höherem Ø-Score (100) aber PARAM_ERROR verliert gegen sauberen Kandidaten (95).
- Tests angepasst (systemPrompt als Param in Codec/Guard/Decompose-Tests). Harness-Suite 172/172,
  WJJ gesamt 259/259 grün (Service-Tests gegen live Ollama im Vollsuite gelegentlich flaky,
  isoliert und im Rerun grün).

## 10.14 Umgesetzt: TaskData-Mutationen nur noch als Commands + Command-History (committet `0aeb4f2`, Konzept §18)

- **Neu:** `TaskView` (Read-Only-Fassade; `ctx.task()` liefert nur das), `TaskCommand`/`TaskCommands`
  (10 Commands als Daten im Package `de.jare.harness`), `CommandHistory` (memory-Package:
  Dispatcher + History, Entry mit Origin-Tripel `loopId/loopItemId/trigger` + `invertible`, default true).
- **TaskData:** `implements TaskView`; alle 9 Mutatoren package-private und feuern nicht mehr selbst —
  jedes Command feuert genau einmal. `SplitInto` ist das Composite (rewriteAsSummary + N×addSubtask,
  1 Entry, 1 Event, Kinder am Command abholbar). `CalcContext` hat den Kanal `ctx.commands()`;
  `abort`/`compact`/`close`/`appendCalcCall` laufen darüber. Engine: alle ~21 Mutationsstellen auf
  `applyCmd(...)`; History hängt an der MemoryQuest (pro User-Call, RAM only — NICHT persistiert,
  `calls[]` deckt das bereits ab).
- **Config:** `HarnessModel.addLoop` prüft doppelte LoopItem-IDs pro Loop → PARAM_ERROR (§18.5;
  gleiche ID in anderem Loop ist ok, das Tripel macht sie eindeutig).
- **Kein Undo** (§18.3): Commands sind Daten, `invertible` startet true; Demotion später per Pull-Scan
  gegen die aktuelle Loop-Config. Read-Seite der History (Anzeige/Demoting/Undo) ist bewusst offen.
- **Cleanup:** TEMP `[CALC >>]/[CALC <<]`-Printlns entfernt, ungenutzte TaskData-Imports gestrichen.
- Tests: `TaskDataNGTest` umgestellt (1 Event pro Command, SplitInto-Composite-Test), Duplikat-Test in
  `LoopConfigCodecNGTest`. Harness-Suite 174 Tests, WJJ gesamt **260/260 grün** (HarnessServiceNGTest
  gegen live Ollama gelegentlich flaky — Baseline zeigt identisches Flaken, kein Regression).

## 10.15 Umgesetzt: KI-Pane zeigt Loop-Actions grau + Antwort in Normalfarbe (committet `e251179` Nachfolge)

- `KIAssistantPanel.appendAction(text)` — hängt eine Action-Zeile als HTML-Span in **Grau (60% Label.foreground + 40% Label.background**, pro Kanal geblendet, theme-abhängig) an; `appendAnswer(text)` — die finale Antwort in normaler Farbe.
- `HarnessController`: `wireInto` hängt einen Engine-Listener ein, der jede `notify`-Zeile (Split/CATCH/Limits/…) live per EDT als graue Action-Zeile an den `actionLineHandler` reicht; nach Run-Ende (`SwingWorker.done`) landet der `WorkResponse`-Text der MemoryQuest über den `answerHandler` in Normalfarbe in der Pane.
- `JackMainMenu` verdrahtet beide Handler auf die KI-Pane. WJJ 260/260 grün.

## 10.16 Umgestellt: Orator statt UI-Referenzen im Controller (committet, Nachfolge `66af887`)

- `CommandHistory.addListener(Consumer<Entry>)` — feuert pro `apply` mit dem neuen Entry; `WorkResponse.addListener` — feuert pro `update`. `HarnessService.addCommandListener/addResponseListener` reicht Registrierungen an jede neue MemoryQuest durch.
- `HarnessController` hält **keine** UI-Handler mehr (`setActionLineHandler`/`setAnswerHandler` entfernt, Engine-Listener in `wireInto` raus); nur noch `addCommandListener/addResponseListener`-Pass-through.
- `JackMainMenu` abonniert direkt: CommandHistory-Entry → `KIAssistantPanel.appendCommandEntry` (graue Action-Zeile nur bei echten Flow-Actions: non-CONTINUE LoopAction oder SplitInto), WorkResponse → `appendResponse` (Antwort in Normalfarbe bei terminalem Status). EDT-Marshalling im Menü. WJJ 261/261 grün.
