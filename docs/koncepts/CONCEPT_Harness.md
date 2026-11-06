# CONCEPT_Harness — Der Harness: Orchestrierung kleiner lokaler Modelle (Ollama)

**Status:** Konzept (Diskussion abgeschlossen, noch keine Implementierung)
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

## 2. Kalkulatoren: ein Interface, neun Zwecke

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

**Modell-Binding pro Kalkulator:** Jedes Kalkulator-Exemplar kann sein eigenes Modell binden — nicht hartkodiert,
sondern in der Harness-Konfig (`"working": {"impl":"ollama","model":"qwen2.5:3b"}`,
`"abort": {"impl":"deterministic"}`). Ollama-Endpunkt konfigurierbar (Default `http://localhost:11434`).
**Deterministische Implementierung bleibt immer als Fallback** erhalten — auch für Ollama-Kalkulatoren bei
JSON-Schema-Verstoß (Parse-Fail → Fallback, Event im Log).

## 3. Task-Baum als Single Source of Truth

Die Daten des Harness-Fensters werden **als Baum gespeichert** und via jsonCasted persistiert
(`harness.json`). Jeder Task-Knoten: `id, taskType, title, status, score, children, calculatorLog[]`.
Das Harness-Fenster zeigt den Baum live (Status-Farben, Score-Badges); Klick auf einen Knoten zeigt dessen
Kalkulator-Protokoll. Pro Aufruf wird im Knoten gespeichert: `kind, attempt, segmentRefs[], promptHash, result`.

Pipeline pro Task (deterministischer Treiber): CHECK → COMPLETE/SPLIT → WORK → EVALUATE → CHECK
(Retry mit Prompt-Supplement | Subtasks) → SUMMARIZE → CLOSE. ABORT wird in jedem Durchlauf konsultiert.

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
- **Anti-Vergessen-Mechanismus:** HISTORY ist nie Roh-Historie, sondern vorkondensierte Zusammenfassungen aus dem `calculatorLog` des Tasks (max. 3 Segmente, vom SUMMARIZE der Vorrunde erzeugt). Dazu ein kurzer **Sticky-Facts**-Block (max. ~150 Tokens: Projektpfad, Sprache, Schema) in jedem Prompt.
- Keine Konversationshistorie über Aufrufe hinweg — jeder Ollama-Call ist zustandslos.

Der **RelevanceCalculator** (`kind() == RELEVANCE`) Adressiert die Liste über **Task-Typ × Bearbeitungs-Schleife (attempt) × Modell-Version**:
`relevance(taskType, attempt, kind, modelVersion) → [segmente]`. Default-Implementierung **deterministisch**
(Registry/Tabelle im Repo — billig, reproduzierbar, versionierbar); Ollama-Implementierung optional, deren
Vorschlag der Treiber gegen Budgets + erlaubte Refs validiert und hart beschneidet.

## 5. Zwei Ebenen: Harness-Modell (Blaupause) vs. Harness-Instanz (Lauf)

- **Harness-Modell** = deklarative Blaupause: Calculator-Slots mit Kind, Prompt-Kategorien + Budgets,
  Task-Typen, Loop-Regeln, Modell-Bindings, Abbruch-Grenzen, Evolutions-Punkte (`evolvable: true`).
  Die Modell-Datei (`harness-model.json`) ist eine **JsonItemDefinition-beschriebene JSON** — parsebar,
  validierbar, im WJJ-Tree editierbar wie jedes Bundle.
- **Harness-Instanz** = das laufende Ding: Task-Baum + Aufruflog + Statistiken, verweist per Pfad auf genau
  eine Modell-Version. „Instanz + Modell-Verweis" ist damit eine stinknormale WJJ-Modellierung.

## 6. Aufruflog: Kennzahlen statt Texte

Pro Kalkulator-Aufruf landet nur das strukturierte Kennzahlen-Set im Log — **nie die Prompt-Texte**
(die bleiben als Hash + Segment-IDs referenzierbar):

```
{ aufrufId, taskId, taskType, kind, slotId, modelVersion, attempt,
  dauerMs, tokensIn, tokensOut, score, status, decision,
  parent1: {aufrufId, score, status}   // redundant (schnelle Aggregation)
  parent2, parent3: aufrufId           // verlinkt (tiefe Kette im Log)
}
```

Statistiken kummulieren pro Slot über die Instanz: Ø-Score, Ø-Tokens, Ø-Dauer, Status-Anteile, gruppiert nach Task-Typ × Kind.

## 7. Der Harness lebt: Swap, Evolution, Stammbaum

1. **On-the-fly-Swap:** Weicht ein Slot auffällig schlecht ab (konfigurierte Schwelle, z. B. Ø-Score < 50 über die letzten N Aufrufe bei gleichem Task-Typ), biegt die Instanz den Slot **zur Laufzeit** auf eine andere Implementierung um — ohne Neustart, als Event im Log.
2. **Evolutions-Schritt:** An als `evolvable` markierten Task-Punkten dürfen kurzfristig **3 alternative Implementierungen** desselben Slots parallel laufen. Nach einem Messfenster (z. B. 20 Aufrufe) werden zwei verworfen, sobald eine Alternative die Zuverlässigkeitsschwelle signifikant schlägt (z. B. Ø-Score +15 und FAIL-Rate halbiert). Verlierer landen mit ihren Stats ins Archiv.
3. **Stammbaum:** Jede Modell-Änderung (Swap, Evolution, manuelles Editieren) erzeugt eine neue Modell-Version mit Parent-Verweis — persistiert projekt-lokal in `<projekt>/.wood/harness/lineage.json` (versionId, parentId, created, changeDescription, statsSnapshot, slots). Rollback = Instanz auf ältere Version zeigen; Weiterschreiben = Kind-Version vom aktuellen Stand.

## 8. Kopplung lokal ↔ global: Messungen koppeln, nie Harnesses

Kein Harness-Merge nötig — Kopplungseinheit ist das **Metric-Event**:

- Jede lokale Evolution hängt ein anonymisiertes Event an einen globalen, append-only Log
  (`~/.wood/harness/metrics.jsonl`): `{ ts, projectIdHash, taskType, kind, slotSignature, model, score, status, tokens, dauerMs }`.
- **Lokale Sicht** = Filter `projectId` (konsistent zur lokalen Lineage); **globale Sicht** = alle Events. Zwei Projektionen derselben Daten — kein Abgleich, kein Konflikt.
- Global → lokal fließt nur lesend: **Priors** („für taskType X, Kind Y liegt der globale Ø-Score bei Z über N Aufrufe") speisen Zuverlässigkeitsschwelle und Kandidatenwahl der 3 Evolutions-Alternativen. Entscheidungen bleiben lokal.
- **Lessons Learned** wandern kontrolliert global: bei signifikantem Vorsprung über das globale Prior wird die siegreiche Slot-Konfig als Lesson nach `~/.wood/harness/lessons.json` exportiert; andere Projekte importieren opt-in.

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
  impl/deterministic/      // regelbasiert (Default + Fallback)
  impl/ollama/             // Ollama-Client, JSON-strict, ein Modell pro Instanz
```

**Plugin-Landschaft (Ausblick, NICHT in diesem Konzept):** WJJ soll langfristig eine Plugin-Plattform werden
(weitere Editoren wie ein LaTeX-Editor mit Plaintext/Parse-Tree/gerenderter Ansicht ladbar). Der Harness ist
plugin-fähig designed, in dieser Ausbaustufe aber fest im WJJ verankert — die spätere Herauslösung soll nur
ein Interface drumherum erfordern, keine Umbauten.

## 11. Offene Punkte (bewusst noch nicht entschieden)

1. Tool-Katalog des WORK-Kalkulators (`HarnessTools` mit Permission-Check) — welche Tools, welche Genehmigung?
2. Konkrete Abbruch-Defaults (max. Iterationen, Tiefe, Retries ohne Score-Anstieg).
3. Herkunft der 3 Evolutions-Alternativen: feste Kandidatenliste, per Ollama vorgeschlagen, oder Mutationen?
4. UI-Details des Harness-Fensters ( eigener View im WJJ? Andock-Panel? )
