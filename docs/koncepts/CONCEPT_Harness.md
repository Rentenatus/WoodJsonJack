# CONCEPT_Harness — Der Harness: Orchestrierung kleiner lokaler Modelle (Ollama)

**Status:** Konzept beschlossen, Implementierung läuft auf Branch `feature/harness-impl`
(Package `de.jare.harness` im WJJ: Engine, Modell + Codecs, Prompts, Metriken, Tooling,
RAG, Skills, Mindset, Lineage/Evolution, Feedback-Loop, Provider ollama+hosted, HarnessViewPanel;
Test-Suite WJJ 221/221 grün — Fortschritt siehe `docs/working/PROGRESS_Harness.md`)
**Autor:** Konzept aus der Session-Diskussion
**Geltungsbereich:** WoodJsonJack (WJJ), Package `de.jare.harness`

---

## 1. Motivation und Ausgangsproblem

Ziel: Ein kleiner „Harness" (Arbeitsgeschirr) im Editor, der Aufgaben strukturiert abarbeitet und dabei
**lokale Ollama-Modelle** nutzt. Diese Modelle sind etwa 10 % (oder weniger) so stark wie die gehosteten
Mistral-Modelle. Das zentrale Problem: **Kleine Modelle vergessen ihre Anweisungen**, sobald der Kontext
lang wird. Daraus folgt die Kernentscheidung:

> **Die KI orchestriert nie.** Der Ablauf ist deterministischer Java-Code. Das Modell beantwortet pro
> Aufruf genau **eine** gekapselte Frage und gibt **strukturiertes JSON** zurück. Der Zustand lebt nie im
> Prompt, sondern immer im Task-Baum.

## 2. Kalkulatoren: ein Interface, zwölf Zwecke

Alle Kalkulatoren implementieren **ein globales Interface** (kein pro Zweck — der Treiber behandelt alle
gleich, `CalcResult` serialisiert polymorph über `kind()`):

```java
interface HarnessCalculator {
    Kind kind();                        // fester Zweck als Diskriminator
    CalcResult calculate(CalcContext ctx);
}
```

| Kind | Aufgabe |
|---|---|
| `RELEVANCE` | Legt die Prompt-Liste für einen Aufruf fest (siehe §4) |
| `SPLIT` | Ist der Task aufspaltbar? Ja → Subtasks anlegen, Task zur Zusammenfassungsaufgabe umschreiben |
| `COMPLETE` | Ist der Task vollständig? Nein → Reasoning-Subtask anlegen |
| `WORK` | Führt den Task aus — deterministisch (Tool-Calls) **oder** Ollama-Aufruf |
| `EVALUATE` | Bewertet das Ergebnis, setzt Status + Score (0–100) am Task |
| `CHECK` | Je Status: doch Subtasks nötig? Oder Retry mit verbesserter Aufgabenstellung (Prompt-Supplement am Task) |
| `ABORT` | Endlos-Schutz: max. Iterationen/Tiefe/Retries, Score-Stagnation, Token-Budget — läuft bei jedem Loop mit |
| `SUMMARIZE` | Fühlt Kinder-Ergebnisse zur Eltern-Antwort zusammen (Eltern-Score = deterministische Aggregation, LLM scored nur Blätter) |
| `CLOSE` | Finalisiert den Task, rollt Status nach oben, schreibt Artefakt |
| `RAG_STORE` | Chunk in die Projekt-RAG schreiben (nur bei EVALUATE ≥ Schwelle) — Details §11 |
| `RAG_RETRIEVE` | Relevante Chunks suchen → DATA-Segment der Prompt-Liste — Details §11 |
| `RAG_PRUNE` | Veraltete RAG-Chunks entfernen (Dedup, Supersede, Score, Budget) — Details §11 |

**Modell-Binding pro Kalkulator:** Jedes Kalkulator-Exemplar bindet über **`modelclass`** (z. B.
`"work": {"impl":"ai","modelclass":"edit"}`, `"abort": {"impl":"deterministic"}`) — der Wert ist der
**purpose** (z. B. `"edit"`), nicht der Modellname; die Auflösung `purpose → [{provider, model, size,
costFactor, toolCapable}]` steht nur in der Top-Level-Liste `modelclasses[]` des Modells (§5), nie im
Slot. Mehrere Modelclasses können denselben purpose tragen — bei Mehrfachbelegung wählt der Kalkulator
anhand von `size` (B, ganzzahlig).
Provider `ollama` defaultet auf `http://localhost:11434`.
**Deterministische Implementierung bleibt immer als Fallback** erhalten — auch für AI-Kalkulatoren bei
JSON-Schema-Verstoß (Parse-Fail → Fallback, Event im Log).
**Provider & Nebenläufigkeit:** `provider` in der Modelclass ist `ollama` (lokal) **oder `hosted`**
(z. B. Mistral-API). Nebenläufigkeit ist denkbar, **aber nicht innerhalb von Ollama** (ein lokales Modell
wird sequenziell bedient); erlaubt ist aber, **mehrere Modelle für dieselbe Quest parallel** zu fahren —
z. B. ein Ollama- und ein gehostetes Modell als zwei Kandidaten derselben Aufgabe (Evolutions-Kandidaten
können lokale und gehostete Provider mischen).
**Provider-Ausfall:** Ein ausgefallener Provider wird **gemerkt und markiert** (`available: false` +
Zeitstempel der letzten Störung an der Modelclass / in den Metriken); betroffene Calls fallen auf den
deterministischen Fallback oder einen anderen Provider zurück. **Kostenlimits in Euro sind nicht vorgesehen.**

## 3. Task-Baum als Single Source of Truth

Die Daten des Harness-Fensters werden **als Baum gespeichert** und via jsonCasted persistiert
(`<projekt>/.wood/harness.json`, §5). Jeder Task-Knoten: `id, taskType, title, status, score, children, calculatorLog[]`.
Das Harness-Fenster zeigt den Baum live (Status-Farben, Score-Badges); Klick auf einen Knoten zeigt dessen
Kalkulator-Protokoll. Pro Aufruf wird im Knoten gespeichert: `kind, attempt, segmentRefs[], promptHash, result`.

Pipeline pro Task (deterministischer Treiber): CHECK → COMPLETE/SPLIT → WORK → EVALUATE → CHECK
(Retry mit Prompt-Supplement | Subtasks) → SUMMARIZE → CLOSE. ABORT wird in jedem Durchlauf konsultiert.

**Entry Points (beides):** (1) **Tool-Aufruf** — direkt aus dem Prompt-Fenster; (2) **Selektion im Editor**
— nur indirekt: Aus der Selektion wird ein Prompt konstruiert und im Prompt-Fenster fortgesetzt.

## 4. Prompt-Trennung: Liste statt Einzelprompt, streng reglementiert

Ein einzelner Prompt reicht nicht — jeder Aufruf bekommt eine **geordnete Liste von Segmenten**:

```java
record PromptSegment(Category cat, String ref, String text, int maxTokens) {}
enum Category { SYSTEM, SKILL, HISTORY, DATA, AUFTRAG }   // Reihenfolge fix
```

Regeln (vom Treiber hart durchgesetzt, nicht verhandelbar):

- Reihenfolge fix: SYSTEM → SKILL → HISTORY → DATA → AUFTRAG.
- Budgets pro Kategorie und gesamt (z. B. max. 2 SYSTEM, 1 SKILL, 3 HISTORY, 2 DATA, 1 AUFTRAG; harte Token-Deckel). Wer sprengt, fliegt deterministisch raus.
- Quelle pro Segment: `ref` (dateibasiert, versioniert, z. B. `prompts/system/orchestrator.v3.md`) oder inline (nur für generierte Inhalte).
- **Anti-Vergessen-Mechanismus:** HISTORY ist nie Roh-Historie, sondern vorkondensierte Zusammenfassungen aus dem `calculatorLog` des Tasks (max. 3 Segmente — **gemeinsames Budget**, reserviert für die beiden Produzenten: SUMMARIZE der Vorrunde und die `compact`-Methode bei `ANSWER_VERBOSE`, §13; sie konkurrieren um dieselben 3 Segmente). Dazu ein kurzer **Sticky-Facts**-Block (max. ~150 Tokens: Projektpfad, Sprache, Schema) in jedem Prompt.
- Keine Konversationshistorie über Aufrufe hinweg — jeder Ollama-Call ist zustandslos.

Der **RelevanceCalculator** (`kind() == RELEVANCE`) Adressiert die Liste über **Task-Typ × Bearbeitungs-Schleife (attempt) × Modell-Version**:
`relevance(taskType, attempt, kind, modelVersion) → [segmente]`. Default-Implementierung **deterministisch**
(Registry/Tabelle im Repo — billig, reproduzierbar, versionierbar); Ollama-Implementierung optional, deren
Vorschlag der Treiber gegen Budgets + erlaubte Refs validiert und hart beschneidet.

## 5. Zwei Ebenen: Harness-Modell (Blaupause) vs. Harness-Instanz (Lauf)

- **Harness-Modell** = deklarative Blaupause: Quests mit Kind + Slots (ein Calculator je Slot, mit
  `mutation`-Label), Prompt-Kategorien + Budgets, Task-Typen, Loop-Regeln, Modell-Bindings,
  Abbruch-Grenzen, Evolutions-Punkte (`evolvable: true` an der Quest).
  Die Modell-Datei (`harness-model.json`) ist eine **JsonItemDefinition-beschriebene JSON** — parsebar,
  validierbar, im WJJ-Tree editierbar wie jedes Bundle.
- **Harness-Instanz** = das laufende Ding: Task-Baum + Aufruflog + Statistiken, verweist per Pfad auf genau
  eine Modell-Version. Die Instanz-Datei lebt **lokal im Projekt unter `<projekt>/.wood/harness.json`**
  (lokale Mutationen + lokale Verwendung); der **trainierte Master** liegt analog im Benutzerverzeichnis
  (`~/.wood/harness/`, siehe Lessons §8). „Instanz + Modell-Verweis" ist damit eine stinknormale
  WJJ-Modellierung.
- **Kein explizites Registrieren** des Harness in `.wood/project.json` — die Eindeutigkeit garantieren
  die `.wood/`-Pfade selbst (`harness.json`, `harness/`, `rag/`, `skills/`).
- **Persistierung atomar:** alle Harness-Dateien (`harness.json`, `lineage.json`, globale
  `metrics.json`, RAG) werden per **write-temp-rename** geschrieben (tempäre Datei + atomares Umbenennen).

### Modellstruktur: Beispiel-JSON und Baumansicht

```json
{
  "name": "default-harness",
  "version": "1.0.0",
  "taskTypes": ["RESEARCH", "CODE_EDIT", "TEST_RUN", "SUMMARIZE"],
  "promptBudgets": { "SYSTEM": 2, "SKILL": 1, "HISTORY": 3, "DATA": 2, "AUFTRAG": 1, "maxTokensTotal": 4000 },
  "abortLimits": { "maxIterations": 50, "maxDepth": 5, "maxRetries": 3, "maxToolCalls": 10 },  // finale Defaults, entschieden in §15
  "modelclasses": [
    { "provider": "ollama", "model": "qwen3.8:27b",  "purpose": "investigation", "size": 27, "costFactor": 1.0, "toolCapable": true },
    { "provider": "ollama", "model": "devstral-small-2:24b", "purpose": "edit",   "size": 24, "costFactor": 2.0, "toolCapable": true },
    { "provider": "ollama", "model": "glm-4.7-flash:latest", "purpose": "plan",   "size": 30, "costFactor": 1.0, "toolCapable": true },
    { "provider": "ollama", "model": "granite3.3:8b", "purpose": "evaluation",     "size": 8,  "costFactor": 0.5, "toolCapable": false },
    { "provider": "ollama", "model": "manutic/nomic-embed-code:latest", "purpose": "rag", "size": 0, "costFactor": 0.2, "toolCapable": false }
  ],
  "quests": [
    {
      "kind": "WORK",
      "evolvable": true,
      "slots": [
        { "id": "work.base", "mutation": null,
          "calculator": { "type": "atomic", "impl": "ai", "modelclass": "edit" } },
        { "id": "work.m1", "mutation": "Mutation-Resolve-01",
          "calculator": { "type": "puzzle", "strategy": "sequence",
            "children": [
              { "type": "atomic", "impl": "ai", "modelclass": "investigation" },
              { "type": "atomic", "impl": "deterministic", "tool": "fs.read" }
            ] } },
        { "id": "work.m2", "mutation": "Mutation-Resolve-02",
          "calculator": { "type": "atomic", "impl": "ai", "modelclass": "plan" } },
        { "id": "work.m3", "mutation": "Mutation-Resolve-03",
          "calculator": { "type": "atomic", "impl": "deterministic" } }
      ]
    },
    {
      "kind": "ABORT",
      "slots": [
        { "id": "abort", "mutation": null,
          "calculator": { "type": "atomic", "impl": "deterministic" } }
      ]
    }
  ]
}
```

```
HarnessModel  (harness-model.json, JsonItemDefinition)
├── taskTypes[], promptBudgets{}, abortLimits{}
├── modelclasses[]   ← **Liste** (kein JsonMap-Key mehr): value = { provider, model, purpose, size, costFactor, toolCapable }
│     `purpose` ist die Rolle (z. B. "investigation") und **nicht eindeutig** — mehrere Modelclasses
│     dürfen denselben Zweck tragen; Slots adressieren den Zweck, Kalkulatoren wählen bei Mehrfach-
│     belegung über `size` (Parameter in B, ganzzahlig, 0 = unbekannt; aus "27b" im Namen parsebar).
│     Modellnamen nur hier, nie in Slots; inkl. purpose "rag" für die Projekt-RAG, §11
└── quests[]
    └── Quest  (kind, evolvable)
        └── slots[]
            └── Slot  (id, mutation — default null/unbelegt; benennt die Evolutions-Variante)
                └── calculator: HarnessCalculator
                    ├── "atomic"  → impl: deterministic | ai (+ modelclass: <klasse aus modelclasses>)
                    └── "puzzle"  → HarnessCalculatorPuzzle
                        └── children: HarnessCalculator[]   ← rekursiv: jeder Eintrag wieder atomar ODER Puzzle
```

**Rekursionsregel (binding):** Das Element `HarnessCalculator` ist entweder **atomar** (`impl` + optional
`modelclass`/`tool`) oder ein **`HarnessCalculatorPuzzle`**, das eine Liste von `HarnessCalculator` trägt —
diese können selbst wieder komponiert (Puzzle) oder atomar sein. Der Treiber liest den Baum rekursiv;
Blätter sind die eigentlichen Aufrufe, Puzzle-Knoten nur Orchestrierung (z. B. `strategy: sequence`).
**Quest/Slot-Regel (binding):** `kind` und `evolvable` sitzen an der **Quest**, nicht am Slot; der Slot
trägt nur `id`, `mutation` (default unbelegt) und den `calculator`. Die drei `Mutation-Resolve-*`-Slots
im Beispiel sind die drei parallelen Kandidaten eines Evolutions-Schritts (§7) an einer `evolvable` Quest.
Damit ist §13 (Composite Calculators) im Modell selbst abbildbar: ein Slot kann einen ganzen
Kalkulator-Teilbaum tragen.

## 6. Aufruflog: Kennzahlen statt Texte

Pro Kalkulator-Aufruf landet nur das strukturierte Kennzahlen-Set im Log — **nie die Prompt-Texte**
(die bleiben als Hash + Segment-IDs referenzierbar):

```
{ aufrufId, taskId, taskType, kind, slotId, modelVersion, attempt,
  dauerMs, tokensIn, tokensOut, score, status, decision,
  parent1: {aufrufId, score, status},  // redundant (schnelle Aggregation ohne Join)
  parent2: aufrufId                    // Link genügt: parent3+ sind über den parent2-Eintrag
                                       // (dessen redundantes parent1) per Walk beliebig tief erreichbar
}
```

parent3 und tiefer werden **nicht** gespeichert — die Information liegt über die Kette bereits vor.

**ID-Vergabe:** `taskId`/`aufrufId` kommen aus einem **internen Zähler im WJJ**, persistiert als
`nextId` in `<projekt>/.wood/harness.json`. Nach einem WJJ-Restart wird der **persistierte Counter geladen**
(nicht per Scan die Max-ID gesucht — Log/Archiv kann gekürzt sein); defensiv gilt
`nextId = max(persistierter nextId, höchste gefundene ID in Baum + Log)`.

Statistiken kummulieren pro Slot über die Instanz: Ø-Score, Ø-Tokens, Ø-Dauer, Status-Anteile, gruppiert nach Task-Typ × Kind.
Der Roh-Log bleibt lokal; **global** landet nur die aggregierte Kennzahlenliste (M × 3, mit Fade) — siehe §8.

## 7. Der Harness lebt: Swap, Evolution, Stammbaum

1. **Swap nur zwischen Läufen (nie mitten im Lauf):** Laufende Tasks rechnen die alte Version zu Ende.
   Es gibt eine **Feedback-Schleife nach dem Lauf** (nach CLOSE des Root-Tasks): Erst wenn die Statistik
   bestimmte Schwellen überschreitet (z. B. Ø-Score < 50 über die letzten N Aufrufe bei gleichem Task-Typ),
   wird ein Swap vorgeschlagen und als **Kind-Version im Stammbaum** erzeugt — wirksam ab dem nächsten Lauf
   (das alte Modell wird dann entladen). Während des Laufs bleibt die Modell-Version frozen.
2. **Evolutions-Schritt:** An als `evolvable` markierten Task-Punkten dürfen kurzfristig **3 alternative Implementierungen** desselben Slots parallel laufen. Nach einem Messfenster (z. B. 20 Aufrufe) werden zwei verworfen, sobald eine Alternative die Zuverlässigkeitsschwelle signifikant schlägt (z. B. Ø-Score +15 und FAIL-Rate halbiert). Verlierer landen mit ihren Stats ins Archiv.
   **Herkunft der 3 Kandidaten — alles drei, gewichtet:**
   - **Hauptquelle: feste Kandidatenliste** (im Modell/Repo versioniert) — am meisten Kandidaten.
   - **Selten: zwei zufällige Mutationen** — fallen sie schlechter aus, verfallen sie einfach (ins Archiv).
   - **Per Ollama vorgeschlagen** (z. B. 3. Vorschlag: `{in-prompt learning | guardrails | in drei Aufrufe je Kategorie splitten}`) — höhere Komplexität, braucht eine eigene Logik-Welt; optional, nie ohne Validierung durch den Treiber.
3. **Stammbaum:** Jede Modell-Änderung (Swap, Evolution, manuelles Editieren) erzeugt eine neue Modell-Version mit Parent-Verweis — persistiert projekt-lokal in `<projekt>/.wood/harness/lineage.json` (versionId, parentId, created, changeDescription, statsSnapshot, slots). Rollback = Instanz auf ältere Version zeigen; Weiterschreiben = Kind-Version vom aktuellen Stand.

## 8. Kopplung lokal ↔ global: aggregierte Kennzahlen, nie Harnesses, keine Event-Sammlung

Kein Harness-Merge nötig — Kopplungseinheit ist die **aggregierte Kennzahl**, nicht das Event. Der globale
Store sammelt **keine Einträge** (kein append-only Log, keine nachträgliche Generations-Filterung):

- Global liegt `~/.wood/harness/metrics.json`: pro Harness-Knoten (Signatur = Modell-Version + Slot-Signatur
  + projectIdHash) **genau eine vererbbare Kennzahlenliste der Größe M × 3** — M Metrik-Kategorien
  (z. B. score, tokensOut, dauerMs, failRate, retryCount), je an **3 Tiefen** der Aufruf-Kette
  (parent1/2/3 aus §6). Pro Bucket genau eine Metrikaussage.
- **Update-Regel pro lokalem Aufruf:** count += 1, sum/max/min akkumulieren, und **Fade**:
  `ema = 0.9 * ema_alt + 0.1 * wert_neu`. Es wird nur aufaddiert/maximiert/minimiert/gefded — nie gespeichert,
  welche Generation den Wert produzierte.
- **Lokale Sicht** = die Kennzahlenliste des eigenen Knotens (konsistent zur lokalen Lineage);
  **globale Sicht** = Aggregation über alle Knoten. Kein Abgleich, kein Konflikt.
- Global → lokal fließt nur lesend: **Priors** (Ø-Score, EMA etc. aus der globalen Liste) speisen
  Zuverlässigkeitsschwelle und Kandidatenwahl der 3 Evolutions-Alternativen. Entscheidungen bleiben lokal.
- **Vererbung beim Klonen (Evolution):** Das Kind übernimmt die Kennzahlenliste des Eltern-Knotens komplett,
  setzt aber die Buckets der **mutierten Quest/Slot-Stellen** zurück (unmeasured) — Baseline bleibt die
  ungemutete Historie.
- **Lessons Learned** wandern kontrolliert global: bei signifikantem Vorsprung über das globale Prior wird
  die siegreiche **Slot-Konfig** (nicht Prompts, nicht Task-Daten) als Lesson nach `~/.wood/harness/lessons.json`
  exportiert; andere Projekte importieren opt-in.

## 9. ML-Analogie (bewusst eng geführt)

- **Globaler Lesson-Harness = Pretraining/Trainig** (allgemeine Priors aus vielen Projekten).
- **Projekt-Harness = Fine-tuning** (kleine Diffs am Slot-Config, gemessen an Projekttasks).
- Generationen speichern nur **Diffs** (LoRA-Prinzip; Interface und Engine bleiben frozen).
- Messfenster + Zuverlässigkeitsschwelle = **Early Stopping**; Evolutions-Selektion (3 Kandidaten, messen, 2 verwerfen) ersetzt den Gradienten (statistisch/evolutionär, kein Backprop).
- **Catastrophic Forgetting** = Overfitting an Projekt-Eigenheiten; Guard: globale Priors laufen mit, Rollback via Stammbaum.
- **Holdout-Tasks** im Projekt: nie zur Kandidatenwahl, nur zur finalen Bewertung einer Generation.
- **Explizit ausgeschlossen:** Die Ollama-Modelle selbst werden NICHT fine-tuned.

## 10. Package-Layout (Java, fest im WJJ)

```
de.jare.harness/
  HarnessCalculator.java, CalcContext.java, CalcResult.java, Kind.java
  TaskNode.java            // jsonCasted-persistiert
  HarnessModel.java        // JsonItemDefinition-Blaupause
  HarnessInstance.java, HarnessEngine.java   // Engine = deterministischer Treiber
  CallLog.java, Metrics.java, HarnessLineage.java, EvolutionSlot.java
  prompts/                 // versionierte Prompt-Segment-Ressourcen
  rag/                     // §11: RagStore, RagIndex (Embeddings), RagChunk (docType SKILL/…)
  skills/                  // §12: SkillRegistry (global ~/.wood/skills/skill + lokal), SkillImport
  tools/                   // §13: HarnessTool, HarnessTools-Facade, FsTools/GitTools/ProcessTools/SearchTools
  signals/                 // §13: Signal-Vokabel + on-Mapping (TOOL_RESULT, TOOL_ERROR, …, compact)
  impl/deterministic/      // regelbasiert (Default + Fallback)
  impl/ollama/             // Ollama-Client, JSON-strict
  impl/hosted/             // gehostete Provider (z. B. Mistral-API) — gleiche Schnittstelle wie ollama
```

**Plugin-Landschaft (Ausblick, NICHT in diesem Konzept):** WJJ soll langfristig eine Plugin-Plattform werden
(weitere Editoren wie ein LaTeX-Editor mit Plaintext/Parse-Tree/gerenderter Ansicht ladbar). Der Harness ist
plugin-fähig designed, in dieser Ausbaustufe aber fest im WJJ verankert — die spätere Herauslösung soll nur
ein Interface drumherum erfordern, keine Umbauten.

## 11. Projekt-RAG (projekt-lokal, nie global)

Jedes Projekt bekommt eine eigene RAG-Datenbank unter `<projekt>/.wood/rag/` (Chunks + Embedding-Index).
**Ausnahmslos projekt-lokal — nie global.** Der globale Metric-Log bleibt kennzahlen-only; RAG-Texte
wandern niemals in die globale Ebene (vgl. §8).

- **Einbettung:** kleines lokales Ollama-Embedding-Modell (Modelclass `rag`, z. B. `nomic-embed-text`) — alles lokal, kein externer Dienst.
- **Nur ein RAG-Modell gleichzeitig:** Es ist immer nur **eine** `rag`-Modelclass aktiv. **Modellwechsel = Reset:** Beim Wechsel des Embedding-Modells wird die RAG geleert und neu aufgebaut — Skills etc. werden neu gelesen, die RAG-Historie stirbt. Ein Wechsel ist **nur möglich, wenn keine Prompt-Bearbeitung läuft** (kein aktiver Harness-Lauf / Prompt-Fenster in Arbeit); bei laufender Bearbeitung wird der Wechsel verweigert.
- **Tabellenspalten der Embedding-RAG (Chunk-Tabelle):**

| Spalte | Inhalt |
|---|---|
| `id` / `contentHash` | eindeutige ID + Hash des Inhalts (Dedup, siehe `RAG_PRUNE` unten) |
| `docType` | Dokumenttyp, z. B. `SKILL`, `RESULT`, `FILE` (§12) |
| `document` | der Inhalt selbst (Text des Chunks) |
| `meta` | `{taskId, aufrufId, taskType, kind, score, ts, scope, source}` (bei SKILL: `skillId, descIndex, scope, source`) |
| `embedding` | Vektor der aktiven `rag`-Modelclass |
| **RAG-Defaults** | globale Default-Spalten/Konfig pro RAG: `topK`, `minScore` (Schwelle für STORE und Retrieval), `maxChunks` (Größenbudget), `ttl` |
- **Drei RAG-Kalkulatoren** (neue Kinds, Interface unverändert):

| Kind | Aufgabe |
|---|---|
| `RAG_STORE` | Chunk ablegen: Inhalt + Metadaten `{taskId, aufrufId, taskType, kind, docType, score, ts, contentHash}` |
| `RAG_RETRIEVE` | Relevante Chunks suchen — **nur über Vektor-Ähnlichkeit** (Embedding-Cosine), top-k mit hartem Budget → landet als DATA-Segment in der Prompt-Liste des RelevanceCalculators |
| `RAG_PRUNE` | Veraltetes entfernen: contentHash-Dedup, Supersede-Kette (neuerer Chunk zum selben taskId macht ältere obsolet), Score-Pruning, Größenbudget; läuft z. B. nach jedem CLOSE |

- **Schreiben nur bei Erfolg:** RAG_STORE schreibt nur, wenn EVALUATE `score ≥ Schwelle` meldet (konfigurierbar), sonst landet das Ergebnis nur im Log.
- **Vertrauen:** Retrieval gewichtet `score × similarity`; EVALUATE kann „Quelle war RAG-Chunk mit Score X" mitbewerten.

## 12. Skills: global lagerbar, projekt-lokal importiert/approved, Description als RAG-Chunks

- **Ablage:** Skills können **global** liegen und werden **gezielt oder pro Projekt** importiert/approved;
  das Projekt kann aber auch eigene Skills haben. Konkrete Pfade mit Typ-Segment (der User hat volle
  Kontrolle auf Dateiebene — Skills sind normale Dateien/Ordner, direkt anleg-/editier-/löschbar):
  - global: `~/.wood/skills/skill/`
  - projekt-lokal: `<projekt>/.wood/skills/skill/`

  Der Typ-Ordner `skill` ist bewusst nur der erste unter `.skills/` — weitere Typen (z. B. andere
  Doc-Arten) können später daneben liegen, ohne die Struktur zu ändern.
  **Sichtbar im linken Projektbaum:** Der Partition-Typ `skill` mountet beide Ordner im Sidebar-Tree
  (lokal editierbar, global readonly zum Browsen/Importieren) — siehe `CONCEPT_ProjectView.md` §2/§3.
- **Import = RAG-Eintrag:** Wird ein Skill importiert bzw. lokal approved, wird er in die **Projekt-RAG** geschrieben mit `docType = SKILL`.
- **N Descriptions = N RAG-Chunks:** Anders als übliche Harnesses muss der Skill seine Description nicht im Fenster halten — jede Description wird als **eigener RAG-Chunk** abgelegt (Metadaten: `{skillId, docType: SKILL, descIndex, scope, source}`). Beispiel: Desc 1 beschreibt, dass der Skill greift, wenn der User **sucht/analysiert** (scope `search`), Desc 2, wenn der User **Änderungen vornehmen** will (scope `edit`). Getrennte Scopes, getrenntes Matching gegen den User-Prompt — aber **ein Skill**.
- **Matching:** RAG_RETRIEVE matcht das AUFTRAG-Segment (User-Prompt) gegen die SKILL-Chunks *pro Scope*; der RelevanceCalculator baut daraus das SKILL-Segment der Prompt-Liste — als Verweis auf die Skill-ID, nicht als Volltext (der Volltext liegt in der RAG).

## 13. Tooling & Composite Calculators (WORK-Zugriff)

Der WORK-Kalkulator muss auf Tooling zugreifen können. Grundsätzliche Festlegung: Das WJJ-Tooling ist
**standardmäßig ein MCP-Server — im Harness binden wir es als direkte Java-SST (in-process) an**, kein
MCP-Hop. Über dasselbe Interface können später aber auch MCP-backed Tools eingehängt werden.

### Tool-Katalog mit Scopes

```java
interface HarnessTool {
    String name();          // z. B. "fs.read", "git.commit"
    String scope();         // Domäne: fs | git | process | search | editor
    JsonSchema args();      // hartes Schema
    ToolResult run(ToolArgs a);
}
```

Standardaktionen: `fs.*` (read/write/list/delete — sandboxed auf das Projekt), `process.*` (Tests/Build
laufen lassen), `git.*` (status/diff/log/add/commit), `search.*` (grep/semantische Suche), `editor.*`
(open im Tab).

### Berechtigungsebene für Agenten

- **Alle lesenden Tools** dürfen im Modus **`automode`** laufen (nie genehmigungspflichtig).
- **Schreibende Tools** sind nach **Kategorie** aufgeteilt:
  `editor` (lokal im Editor), `disk-in` (Festplatte innerhalb des Projekts), `disk-out` (Festplatte
  außerhalb des Projekts), `external` (externe Dienste/Netz), `system` (Systemebene).
- Pro Kategorie gibt es eine **Einstellung: `nein` | `fragen` | `automode`** (Projekt-Konfig).
  Bei `fragen` erscheint eine **UI-Genehmigungsabfrage** (welches Tool, welche Kategorie, welcher Task).
- Jedes Tool trägt weiterhin ein Risk-Level (`read` / `write` / `destructive`); `destructive` (z. B.
  `git.commit`, Löschen außerhalb des Projekts) ist immer mindestens `fragen`, auch wenn die Kategorie
  auf `automode` steht.

### Das „zu viele Tools"-Problem: Composite Calculators + Exposure-Budget

Wie der Executor aus dem Tooling die richtigen Tools herauszieht, ist gelöst über **zwei Mechanismen**:

1. **Composite Calculators:** Jeder Kalkulator darf selbst als **Teilbaum von Komposit-Kalkulatoren**
   auftreten — Kalkulatoren bilden also selbst einen Baum (wie die Tasks). Tools sind nach Domäne in
   Komposit-Kalkulatoren gruppiert (`FsTools`, `GitTools`, `ProcessTools`, `SearchTools`); ein Komposit
   exponiert nur die kleine, feste Sub-Schnittstelle seiner Kinder. Der Treiber behandelt ein Komposit
   wie eine Task-Zerlegung: Kinder werden als Sub-Kalkulatoren ausgeführt.
2. **Exposure-Budget:** Pro WORK-Aufruf werden höchstens **K Tools (z. B. 5)** als JSON-Schemas in den
   Prompt serialisiert — ausgewählt nach Task-Typ × Scope (gleiches Adressierungsmuster wie Skills/RAG,
   siehe §12), vom Treiber hart gedeckelt. Das kleine Modell sieht nie den ganzen Katalog, nur die
   passenden Tools.

`CalcContext` trägt damit eine **Facade** (`HarnessTools`), nicht den Roh-Katalog: `tools.allowed(taskType)`
liefert die bounded Auswahl, `tools.call(name, args)` führt mit Permission-Check aus.

### Tool-Call-Loop, Signale und compact (AI-Kalkulatoren)

- **Tool-Fähigkeit deklariert die Modelclass:** `investigation`, `edit` (und `plan`) tragen
  `toolCapable: true` im `modelclasses`-Mapping; `evaluation`/`rag` typischerweise nicht. Der Treiber
  weiß daraus, ob das Exposure-Budget gilt.
- **Bounded Tool-Loop im AI-Kalkulator:** Ein Kalkulator mit `impl: "ai"` fährt eine ReAct-Schleife —
  Modell nennt Tool aus dem exponierten Katalog (max. K), Treiber führt mit Permission-Check aus,
  Tool-Ergebnis kommt als nächster Prompt-Bestandteil zurück. Gedeckelt durch neues Abort-Limit
  `maxToolCalls` (neben maxIterations/maxDepth/maxRetries).
- **Signale als modellierte Methoden:** Jeder `HarnessCalculator` im Modell kann ein **`on`-Mapping**
  tragen: `Signal → Child-Calculator`. Feste Signal-Vokabel des Treibers: `TOOL_RESULT`, `TOOL_ERROR`,
  `ANSWER_RECEIVED`, `ANSWER_VERBOSE`, `RETRY`. Beispiel: `TOOL_ERROR → CHECK` (Retry), oder eine eigene
  ReAct-Methode — konfigurierbar pro Slot, nicht hartkodiert.
- **`compact`-Methode:** Bei `ANSWER_VERBOSE` („brauchbar aber überschwänglich") kann optional eine
  `compact`-Methode am Kalkulator die bisherigen Calls + Antworten nachträglich über eigenen Prompt
  verdichten. Das Ergebnis landet als HISTORY-Segment mit Markierung **`[compacted]`** im nächsten
  Versuch — hart gedeckelt durch das HISTORY-Budget (max. 3 Segmente, §4).
- **Catch-Kalkulatoren (analog catch-Block):** Jeder `HarnessCalculator` im Modell kann optional ein
  **`catch`-Mapping** tragen: Fehlertyp → Catch-Kalkulator. Fehlertypen (Treiber-Vokabel):
  `MODEL_UNAVAILABLE` (Provider-Ausfall, §2), `SCHEMA_VIOLATION` (JSON-Parse-Fail), `TOOL_ERROR`,
  `TIMEOUT`, `LIMIT`, `ABORTED`. Der Treiber fängt den Fehler des Blatts, loggt ihn und ruft den
  Catch-Kalkulator (z. B. Fallback auf deterministischen Slot, Retry mit anderem Provider). Die globale
  Endlos-Schutz-Quest (`ABORT`) bleibt davon unberührt und läuft zusätzlich in jedem Loop.
- **`LIMIT`-Ausnahme:** Ein Kalkulator kann im Ausnahmefall **bis zu 2 extra Zähler genehmigen**
  (z. B. 2 zusätzliche Tool-Calls über `maxToolCalls` hinaus), wenn er es **begründet** (Begründung landet
  im Log); alternativ ruft er **`compact` + Abbruch** auf (Antwort verdichten, dann kontrolliert stoppen).

## 14. Harness-API — die Operationen, die Kalkulatoren am Treiber aufrufen können

Alles läuft über `CalcContext ctx` (Facades, nie Roh-Zugriff). Direkt in §2–§13 genannt oder dort
abgeleitet; mit `*` markiert: bisher nicht explizit erwähnt, aber benötigt.

```java
ctx.task        // TaskNode des aktuellen Aufrufs
  .getId() .getTaskType() .getStatus() .getScore() .getAttempt()
  .getCalculatorLog()                 // eigene History (HISTORY-Quelle, §4)
  .addSubtask(taskType, title)        // SPLIT / COMPLETE (Reasoning-Subtask)
  .rewriteAsSummary()                 // SPLIT: Task wird Zusammenfassungsaufgabe
  .setStatus(status) .setScore(0..100)// EVALUATE
  .appendPromptSupplement(text)       // CHECK: Retry mit verbesserter Aufgabenstellung
  .close(artifact)                    // CLOSE: finalisieren + Status nach oben rollen
  .parent1() .parent2()               // Aufruf-Kette (§6)

ctx.prompts     // Prompt-Liste (§4)
  .relevance(taskType, attempt, kind, modelVersion)  → List<PromptSegment>   // RELEVANCE
  .current()                          // die eigene, bereits assemblierte Liste

ctx.tools       // HarnessTools-Facade (§13)
  .allowed(taskType)                  // bounded Auswahl (max. K, Exposure-Budget)
  .call(name, args)                   // Permission-Check + Ausführung → ToolResult
  .requestApproval(tool, category, reason) → boolean   // * UI-Genehmigung bei Einstellung "fragen"

ctx.signals     // Signal-Vokabel (§13)
  .emit(TOOL_RESULT | TOOL_ERROR | ANSWER_RECEIVED | ANSWER_VERBOSE | RETRY)  // → on-Mapping

ctx.catch       // Catch-Kalkulatoren (§13)
  .handle(MODEL_UNAVAILABLE | SCHEMA_VIOLATION | TOOL_ERROR | TIMEOUT | LIMIT | ABORTED)
  .approveExtraCounters(n ≤ 2, justification) → boolean   // * LIMIT-Ausnahme, Begründung ins Log
  .compact()                          // * ruft compact-Calculator, Ergebnis als [compacted]-HISTORY
  .abort(reason)                      // * kontrollierten Abbruch einleiten

ctx.limits      // ABORT-Zähler (§2, §5 abortLimits)
  .getCounter(iterations|depth|retries|toolCalls) .remaining(name) .isAborted()

ctx.rag         // Projekt-RAG (§11)
  .store(docType, document, meta)     // RAG_STORE (nur bei score ≥ minScore)
  .retrieve(embedding, topK)          // RAG_RETRIEVE — nur Vektor-Ähnlichkeit
  .prune()                            // RAG_PRUNE

ctx.skills      // Skills (§12)
  .importSkill(source, scope)         // * Import/Approve → SKILL-Chunks in die RAG
  .skillChunks(skillId, scope)        // * Skill-Descriptions als Chunks lesen

ctx.mindset     // Mindset der AI (§16) — global, kategorisiert
  .get(category)                      // * Mindset einer Kategorie lesen (z. B. "analysis", "answer")
  .set(category, text)               // * AI legt User-Vorliebe ab; max. 1024 Chars, nur Warnung bei Überschreitung

ctx.metrics     // Kennzahlen (§6, §8)
  .logCall(metrics)                   // lokaler Roh-Log + globales Aggregat (count/sum/max/min/ema, M×3)
  .stats(slotSignature, depth)       // lokale Sicht
  .globalPriors(slotSignature)        // * globale Sicht, read-only

ctx.lineage     // Stammbaum / Evolution (§7) — nur nach Lauf-Ende (Feedback-Schleife)
  .proposeSwap(slotId, newImpl, reason)   // Kind-Version erzeugen, wirksam ab nächstem Lauf *
  .requestEvolution(questId, candidates[3])  // 3 parallele Kandidaten an evolvable Quest *
  .cloneWithStats(resetSlots[])       // Kind erbt Kennzahlen, mutierte Buckets reset *
  .rollbackTo(versionId)              // * Rollback auf ältere Version

ctx.model       // Modelclass (§2, §5)
  .modelclass()                       // { provider, model, costFactor, toolCapable, available }
  .isProviderAvailable()              // * Provider-Ausfall merken/marks (§2)

ctx.ids         // interne Zähler (§6)
  .nextTaskId() .nextAufrufId()       // persistierter nextId

ctx.ui          // * neu: Harness-Fenster
  .notify(text) .focusTask(taskId) .askUser(question, options) → answer   // Genehmigung/Info
```

## 15. Entschiedene Punkte (früher offen)

1. **Abbruch-Defaults — entschieden:** `maxIterations: 50`, `maxDepth: 5`, `maxRetries: 3`,
   `maxToolCalls: 10`. Diese finalen Defaults stehen im gebündelten Modell
   (`assets/harness/harness-model.json`) und sind die Code-Defaults in `HarnessModel`;
   pro Projekt überschreibbar via `abortLimits` im eigenen `harness-model.json`.
   (Die Werte im §5-Beispiel waren illustrativ und sind jetzt genau diese finalen Defaults.)
2. **UI des Harness-Fensters — entschieden:** eigener Dialog `HarnessViewPanel` (Live-Taskbaum
   mit Status-Farben/Score-Badges, Detail-Pane mit Kalkulator-Protokoll pro Knoten), geöffnet
   über das Harness-Menü in `JackMainMenu` („Show Harness Window“; „Run Task…“ startet einen
   Lauf gegen das aktuelle Projekt). Kein eigenes Andock-Panel.

## 16. Mindset of the AI (nur global, analog zu Skills)

- **Ablage nur global:** `~/.wood/mindset/` (§12-Pattern analog zu `.skills/skill/`) —
  **nie projekt-lokal, nie in die Projekt-RAG**. Der User hat volle Kontrolle auf Dateiebene.
- **Inhalt:** der Mindset der AI, darin legt die AI auch die **Vorlieben des Users** ab.
- **Kategorien:** verschiedene Kategorien, die **verschiedene Kalkulatoren lesen** können — adressiert
  über `ctx.mindset.get(category)` (§14) bzw. als Segment in der Prompt-Liste (RelevanceCalculator
  kann eine Mindset-Kategorie als SKILL/SYSTEM-Segment ziehen). Beispiele:
  - Kategorie `analysis`: „Starte die Analyse in drei Rollen: technisch, kreativ, wirtschaftlich"
  - Kategorie `answer`: „Antworte wie ein Mathematiker" (**wird zuerst getestet**) / „wie Captain Hook"
    / „Peter Pan" / „Jack Sparrow" …
- **Schreibrecht:** die AI selbst legt Vorlieben via `ctx.mindset.set(category, text)` ab.
- **Limit:** max. **1024 Chars** pro Eintrag — bei Überschreitung nur eine **Warnung**, kein Abbruch.
