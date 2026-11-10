# DESIGN_Loops — Freie Loop-Konfiguration des Harness (Umsetzungsspec + Plan)

Beschlossen in `CONCEPT_Harness.md` §5 (Loop-Regel) und §15.6. Dieses Dokument ist die
Ausführungs-Spec: Datenmodell, Lifecycle-Semantik, Fehlerkanäle und der schrittweise
Umsetzungsplan. **Stand: umgesetzt** (Commits `c05f360` Modell+Codec, `3a2d13e` LoopAction/ErrorSignal,
`28cdf1a` Engine-`runLoop`, `8786625` Asset-Migration; Tests `LoopConfigCodecNGTest`,
`LoopActionNGTest`, `LoopEngineNGTest`).

## 1. Ziel

Die Main-Loop des `HarnessEngine` ist heute hartkodiert (`ABORT → SPLIT → COMPLETE → WORK →
EVALUATE → RAG_STORE → CHECK → SUMMARIZE → CLOSE`, bounded durch `abortLimits`). Sie soll als
**benannte Loops** in `assets/harness/harness-model.json` konfigurierbar werden, inklusive
verschachtelter SubLoops. Trennung der Verantwortlichkeiten:

- **Loop (Konfig/Daten)** = „nur Ausführung nach Vorschrift“: wann feuert welches Item (Trigger),
  in welcher Reihenfolge, welcher Loop wird wann aufgerufen.
- **Kalkulator** = alle fachlichen Entscheidungen: Guards (count==null, score-Schwellen,
  hasChildren), Ergebnisse in den WorkCache, fachliche `HarnessAction` (Split/Retry/…), und die
  **LoopAction** (Flow-Steuerung).

## 2. Datenmodell (JSON)

```json
"loops": [
  { "id": "main", "items": [
    { "id": "abort", "kind": "ABORT",     "trigger": "START"   },
    { "id": "split", "kind": "SPLIT",     "trigger": "EXECUTE" },
    { "id": "work",  "kind": "WORK",      "trigger": "EXECUTE" },
    { "id": "eval",  "kind": "EVALUATE",  "trigger": "EXECUTE" },
    { "id": "rag",   "kind": "RAG_STORE", "trigger": "EXECUTE" },
    { "id": "check", "kind": "CHECK",     "trigger": "EXECUTE" },
    { "id": "sub",   "kind": "WORK", "loop": "resolve", "trigger": "EXECUTE" },
    { "id": "sum",   "kind": "SUMMARIZE", "trigger": "EXECUTE" },
    { "id": "err",   "kind": "ABORT",     "trigger": "CATCH"   },
    { "id": "close", "kind": "CLOSE",     "trigger": "CLOSE"   }
  ]},
  { "id": "resolve", "items": [ ... ] }
]
```

**LoopItem — fester Attributsatz:**

| Attribut | Pflicht | Bedeutung |
|---|---|---|
| `id` | ja | Log-/Debug-Label (calculatorLog, Harness Tree); bei gleichem `kind` mehrfach im Loop nötig |
| `kind` | ja | Quest-Kind, dessen Slots ausgeführt werden |
| `loop` | nein | Ref auf anderen Loop-ID → SubLoop-Aufruf statt Quest-Ausführung |
| `trigger` | nein (default `EXECUTE`) | `START` \| `EXECUTE` \| `CATCH` \| `CLOSE` |

**Validierung beim Laden (Codec, fail-fast):** unbekannter `kind` (nicht in `quests[]` und nicht
in `Kind`), unbekannte `loop`-Ref, doppelte Loop-`id`, doppelte Item-`id` innerhalb eines Loops,
unbekannter `trigger` → Ladefehler mit klarer Meldung. Zirkuläre SubLoop-Referenzen sind
erlaubt, aber durch `maxDepth` begrenzt.

**Backward-Compat:** fehlt `loops[]` komplett, baut der Codec den Legacy-default `"main"` mit der
bisher hartkodierten Reihenfolge (START: ABORT; EXECUTE: SPLIT, COMPLETE, WORK, EVALUATE,
RAG_STORE, CHECK, SUMMARIZE; CLOSE: CLOSE).

## 3. Lifecycle-Semantik (eiserne Regeln)

`runLoop(task, loopId)`:

1. **START**-Items feuern genau einmal beim Eintritt in diesen Loop (Reihenfolge wie in der Liste).
2. Iterationsschleife: `limits.countIteration()`; `limits.isAborted(depth)` → harter Stopp.
   Pro Iteration feuern die **EXECUTE**-Items in Listenreihenfolge.
3. Nach jedem Item wird die **LoopAction** des Kalkulators ausgewertet (s. §4).
4. **CATCH**-Items feuern nur bei DOMAIN-`ErrorSignal` (s. §5); ein Throw *innerhalb* von CATCH
   entrollt eine Loop-Ebene höher (kein erneutes CATCH im selben Loop).
5. **CLOSE**-Items feuern genau einmal beim Verlassen des Loops — im `finally`, also auch nach
   Break, CATCH und technischem Fehler (Cleanup garantiert).
6. `maxIterations` zählt EXECUTE-Iterationen **global über alle verschachtelten Loops**;
   `maxDepth` begrenzt die SubLoop-Verschachtelung (neben der Task-Tiefe).

## 4. LoopAction — der Flow-Kanal (neben HarnessAction)

Zwei Kanäle im `CalcResult`, nie vermischt:

- `action` (`HarnessAction`, §15.5) — fachliche Aktion am Task (Split legt Kinder an, Retry, Abort).
- `loopAction` (`LoopAction`, default `Continue`) — Wohin geht der Loop-Flow.

| LoopAction | Wirkung |
|---|---|
| `Continue` | nächstes Item (default, auch bei `None`-Action) |
| `Restart` | aktueller Loop ab erstem EXECUTE-Item neu; START feuert **nicht** erneut |
| `Break` | aktuellen Loop verlassen; CLOSE feuert; Rückgabe an den aufrufenden Loop (nächstes Item) |
| `SubLoop(loopId)` | benannten Loop auf den Call-Stack pushen und `runLoop(task, loopId)` rekursiv; Depth-Check gegen `maxDepth` |
| `ThrowError(errorSignal)` | DOMAIN → CATCH-Items des aktuellen Loops; TECHNICAL → CATCH überspringen, Quest FAILED, Entrollen bis oben |

Platzierung: Interface `LoopAction` in `de.jare.harness` (neben `HarnessAction`), Repräsentanten
in `de.jare.harness.main.actions` (`LoopContinue`, `LoopRestart`, `LoopBreak`, `LoopSubLoop`,
`LoopThrowError`). Das alte `HarnessAction.Control`-Enum wird in `LoopAction` überführt
(`Continue/Restart/Break` entsprechen `CONTINUE/LOOP/STOP`); `Actions.fromDecision` mappt den
Legacy-Decision-String auf **beide** Kanäle (action + loopAction), sodass bestehende Kalkulatoren
weiterlaufen.

## 5. Fehlerkanäle: ErrorSignal als Daten, nie Throwable im Loop

- **`ErrorSignal`** (neu, `de.jare.harness`): `{ kind: DOMAIN | TECHNICAL, reason: String, source: calculatorName }`.
  Trägt die `LoopAction.ThrowError`.
- **DOMAIN** (fachlich): Kalkulator meldet aktiv Fehler — count==null, Provider-JSON nicht
  parsebar (SCHEMA_VIOLATION), Quest fachlich nicht lösbar. → CATCH feuert, Quest kann gefixt
  oder sauber aborted werden.
- **TECHNICAL** (Störung/Bug): Provider nicht erreichbar (MODEL_UNAVAILABLE), NPE, Codec-Fehler,
  verletzte Invariante. → CATCH wird **übersprungen**, CLOSE läuft (finally), Quest hart FAILED,
  Grund ins calculatorLog, kein weiterer Loop.
- **Eiserne Regel: kein `Throwable` überschreitet die Loop-Grenze.** Fangnetz 1: Kalkulator-Aufruf
  in `HarnessEngine.runNode` — jedes `Throwable` wird in `ErrorSignal(TECHNICAL)` verpackt und als
  LoopAction `ThrowError` durchgereicht. Fangnetz 2 (äußerst): `HarnessEngine.run` — garantiert
  sauberes FAILED auch für Code außerhalb des Loops (z. B. Anlegen der MemoryQuest).
- **`HarnessException`** (existiert, §13) bleibt das interne Vokabular: ihre `Type`-Werte
  (MODEL_UNAVAILABLE, SCHEMA_VIOLATION, TOOL_ERROR, TIMEOUT, LIMIT, ABORTED) werden beim Verpacken
  auf DOMAIN/TECHNICAL gemappt: SCHEMA_VIOLATION, LIMIT, ABORTED → DOMAIN ( fachlich behandelbar);
  MODEL_UNAVAILABLE, TIMEOUT, TOOL_ERROR, alles Unbekannte → TECHNICAL.
- Provider-Schichtunterscheidung: „Ollama nicht erreichbar“ = TECHNICAL; „Ollama antwortet mit
  Müll-JSON“ = DOMAIN.

## 6. Umsetzungsplan (Schritte, jeweils kompilieren + Tests grün, kein Commit bis Freigabe)

1. **Modell + Codec**
   - `model/Loop.java`, `model/LoopItem.java`, `Trigger`-Enum (`de.jare.harness.model`).
   - `io/def/HarnessModelBeans.java`: `LoopBean`, `LoopItemBean` (id, kind, loop, trigger).
   - `io/def/JsonHarnessModelDefinition.java`: `loops` ergänzen.
   - `io/HarnessModelCodec.java`: parsen + validieren (s. §2); default `"main"` bei fehlendem `loops`.
   - `model/HarnessModel.java`: `Map<String, Loop> getLoops()`, `getLoop(id)`, `defaultMainLoop()`.
   - Test: `LoopConfigCodecNGTest` — Round-Trip, Defaults, alle Validierungsfehler.
2. **Flow-Typen**
   - `de.jare.harness/LoopAction.java` (Interface, trägt `Control`-Semantik), `ErrorSignal.java`.
   - `main/actions/LoopContinue|LoopRestart|LoopBreak|LoopSubLoop|LoopThrowError.java`.
   - `CalcResult`: zusätzliches Feld `loopAction` (default `LoopContinue`); `HarnessAction.Control`
     deprecated/remappt — `Actions.fromDecision` füllt beide Kanäle.
   - Test: `LoopActionNGTest` — Factory-Mapping, Defaults.
3. **Engine**
   - `HarnessEngine.processLoop` → `runLoop(task, loopId)` gemäß §3 (START einmal, EXECUTE-Schleife,
     CATCH bei DOMAIN, CLOSE im finally, SubLoop rekursiv mit Depth-Check).
   - Fangnetz 1 in `runNode` (Throwable → TECHNICAL-ErrorSignal), Fangnetz 2 in `run`.
   - Guards aus der Engine entfernen: `RAG_STORE_MIN_SCORE` und hasChildren-Check wandern in die
     RAG_STORE-/SUMMARIZE-Kalkulatoren (Schwellen als Slot-Params, z. B. `minScore`).
   - Test: `LoopEngineNGTest` mit Fake-Kalkulatoren — START feuert genau einmal (auch bei Restart),
     EXECUTE-Reihenfolge, Restart, Break mit CLOSE, SubLoop-Verschachtelung + maxDepth, CATCH bei
     DOMAIN, TECHNICAL umgeht CATCH und setzt FAILED, CLOSE feuert in allen Exit-Pfaden.
4. **Asset-Migration**
   - `assets/harness/harness-model.json`: `loops[]` mit `"main"` eintragen (Legacy-Reihenfolge,
     s. §2 Backward-Compat), inkl. CATCH-Item auf ABORT.
   - `HarnessServiceNGTest` anpassen/erweitern (Guard: default model enthält loops.main).
5. **Doku**
   - PROGRESS_Harness.md §4: dieser Plan als offener Punkt verlinken; nach Umsetzung Haken setzen.

## 7. Offene Punkte / Risiken

- **Guard-Konvention:** Jeder Kalkulator muss vor dem teuren Provider-Call abbrechen
  (idempotent guard-fähig, nie ungeprüft `Restart` liefern) — dokumentieren, Tests mitzählen.
- **Kein konfigurierbarer Wiedereinstieg** bei Restart (immer Index 0 der EXECUTE-Gruppe) —
  heute kein Bedarf, bei Bedarf später als LoopAction-Variante.
- **TEMP-Debug-Printlns** (`[CALC >>]/[CALC <<]`) werden nach dem Live-Test entfernt.
