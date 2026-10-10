# Konzept: Wood-Links im Editor-Core und Fields-Pane

**Status**: Phase A umgesetzt (jsonCasted_edit, Branch `feature/woodLinks`, Commits 4b8b899–89554b7), Phase B umgesetzt (WoodJsonJack, Branch `feature/fieldsPane`, Commit 6c12e7b). Offen: Auswahl-Semantik (Kapitel 6), Merge nach develop.
**Betroffene Projekte**: `jsonCasted_edit` (Phase A, Editor-Core), `WoodJsonJack` (Phase B, Swing-UI)
**Abhängigkeiten**: `JsonTreeConverter`, `EditTreeWriter`, `EditTree`, `EditNodeObject`, `EditLinkingSet`, `JsonModelDescriptor`, `JsonTerms`
**Vorbedingung**: Phase A vor Phase B — die Combobox des Fields-Pane filtert Objekt-Instanzen, die der Editor heute noch verliert.

---

## Inhaltsverzeichnis

1. [Ausgangslage](#1-ausgangslage)
2. [Zielbild](#2-zielbild)
3. [Phase A: Wood-Links in der Editor-Core](#3-phase-a-wood-links-in-der-editor-core)
4. [Phase B: Fields-Pane](#4-phase-b-fields-pane)
5. [Selektions- und Fokus-Semantik](#5-selektions-und-fokus-semantik)
6. [Offene Punkte / bewusst vertagt](#6-offene-punkte--bewusst-vertagt)
7. [Aufwand und Reihenfolge](#7-aufwand-und-reihenfolge)
8. [Risiken](#8-risiken)

---

## 1. Ausgangslage

### 1.1 Link-System in jsonCasted (funktioniert)

Ein Objekt kann in der Deserialisierung N-mal verlinkt sein. Writer und Parser lösen das über
Metadaten-Member, die in `JsonTerms` definiert sind:

```
"_woodObjectId": "123456"        // Term: TERM_WOOD_OBJECT_ID — Identität des Originals
"_woodLink": "save::123456"     // Term: TERM_WOOD_LINK     — Referenz auf eine woodId
```

Der `WoodIdFinder` baut daraus eine `LinkingSet` (objectIdMap, linkMap, unconnected-Set),
`WoodResolution`/`WoodProxyResolver` lösen die Referenzen beim Casten auf. Der Writer
emittiert die Terme wieder. Ein Roundtrip über `JsonParser` → `JsonObjectWriter` ist verlustfrei.

Dazu kommt der **Systemknoten** `JsonTerms.TERM_WOOD_DEFINITIONS` (`_woodDefinitions`):
Objekte können außerhalb der Baumstruktur in der Ressourcen-/Definition-Sektion abgelegt
werden. Das existiert vollständig — der Parser scannt die Knoten
(`RootParser` → `JsonWoodProviderTinker` → `JsonResource.getDefinitionNodes()`),
und der Writer sammelt über `DefinitionsContext`/`DefinitionsContextObjectRecord`
Objekte, die referenziert statt inline serialisiert werden. Ein Objekt muss also
nicht im Baum stehen, um als Link-Ziel zu existieren.

### 1.2 Editor-Core jsonCasted_edit (unfertig, verlustbehaftet)

Der Ist-Zustand bei der Analyse:

| Baustein | Zustand |
|---|---|
| `EditLinkingSet` | existiert, ist das 1:1-Analogon zur `LinkingSet` (objectIdMap/linkMap über `EditNodeObject`) — **tot**: `registerObject`/`registerLink` haben null Aufrufstellen |
| `EditTree` | hält ein `EditLinkingSet` über `getLinkingSet()`/`setLinkingSet()` — wird nie befüllt |
| `JsonTreeConverter` | **verwirft** `_woodObjectId` und `_woodLink` beim Import (`isMetadataKey` filtert beide; nur `_class` wird als `castName` gerettet) |
| `EditTreeWriter` | schreibt `_woodModel`, aber keine Wood-Terme — weder woodId noch Link |
| `EditNodeObject` | kennt `castName` als Metadaten-Seitenkanal-Muster, aber keine `woodObjectId`, kein `linkTarget` |

**Folge**: Ein N-fach verlinktes Objekt erscheint im Editor als das Original plus N leere
`{}`-Knoten. Speichert man (`JackMainActions.saveActiveFile` → `EditTreeWriter.toJsonFile`),
sind die Links endgültig verloren. Das ist ein heute existierender Datenverlust-Bug,
unabhängig vom Fields-Pane.

Wichtig für die Architektur: `JsonTreeConverter` baut den Baum, **bevor** das `EditTree`
existiert (Kommentar im `EditTree`-Konstruktor). Die Link-Registrierung kann daher nicht
während der Konvertierung ins EditTree schreiben — der bereits existierende
`setLinkingSet`-Setter ist genau der vorgesehene Übergabepunkt dafür.

---

## 2. Zielbild

1. Der Editor kennt die Identität von Objekten: `EditNodeObject` trägt seine woodId und
   Referenz-Knoten tragen ihr Link-Ziel. Import und Export sind verlustfrei, Undo/Redo
   und Clipboard erhalten die Linkstruktur.
2. Das Fields-Pane zeigt zum selektierten Objekt eine Sicht auf das Modell: Kopfzeile mit
   Json-Typ, eine Zeile pro Feld (Kindknoten). Bei Objekt-Feldern bietet eine Combobox nur
   die passenden Objekt-Instanzen aus der bereits geparsten JSON an — bei Interfacetypen
   die Menge der implementierenden Objekttypen, was nur eine Zusatzprüfung des Filters ist.

---

## 3. Phase A: Wood-Links in der Editor-Core

### A1 — `EditNodeObject` erhält woodId und Link-Ziel

Symmetrisch zum bewährten `castName`-Muster (Metadaten als Feld, nicht als Kindknoten):

```java
// EditNodeObject (neu)
private String woodObjectId;   // Identität des Originals (aus _woodObjectId), null = unverlinkt
private String linkTarget;     // "provider::id" (aus _woodLink), null = kein Referenz-Knoten

public String getWoodObjectId();
public void setWoodObjectId(String woodObjectId);   // meldet sich beim EditTree-LinkingSet an/ab
public String getLinkTarget();
public void setLinkTarget(String linkTarget);
public boolean isLinkReference();                   // linkTarget != null
```

Begleitende Arbeiten:

- `getAttributes()`: zusätzliche Einträge `"|woodId"` und `"|linkTarget"` — der
  Prefix `|` kennzeichnet im Attributes-Pane bereits read-only Anzeigezeilen
  (wie `"|cast name"`). Damit sind beide Werte ohne UI-Arbeit sichtbar.
- `setAttributes(...)`: symmetrisch dazu (damit `SetValueCommand`-artige Pfade greifen).
- `deepCopy(...)`: `woodObjectId` und `linkTarget` mitkopieren. **Diskussionspunkt**:
  Eine Kopie des Originals behält zunächst dieselbe Identität — zwei Knoten mit
  derselben woodId sind danach unterscheidbar, aber noch nicht eindeutig. Variante:
  `regenerateEditId == true` erzeugt auch eine neue woodObjectId. Empfehlung für
  Phase A: Identität mitkopieren und den Konfliktfall über A5 als WARNING markieren;
  das echte Verhalten gehört zur Auswahl-Semantik (Kapitel 6).
- `computeHash()` und Range-Logik unverändert — die Felder sind reine Metadaten
  und beeinflussen kein Parsing.
- Ein Referenz-Knoten (`isLinkReference()`) hat per Definition keine Kindknoten;
  `canBeParentOf*()` gibt dafür `false` zurück, damit keine Felder unter eine
  Referenz gehängt werden können.

**Erweiterung `EditLinkingSet`: Reverse-Index.** Zusätzlich zu den vorhandenen
objectIdMap/linkMap pflegt das Set einen Reverse-Index:

```java
// EditLinkingSet (neu)
private final Map<String, List<EditNodeObject>> referencesByTarget; // woodId -> Referenz-Stubs

public List<EditNodeObject> getReferencesTo(String woodObjectId);   // nie null, ggf. leer
```

Pflege automatisch in `registerLink`/`removeLink`/`removeObject` (Letzteres
entfernt auch den Eintrag des Ziels samt Referenzliste — Objekt endgültig weg,
Zeiger mit). Damit wird der UI-Fan-out der Symlink-Anzeige (Kapitel 6.4) vom
Filtern über die ganze linkMap zu einem einzigen Map-Lookups; dieselbe Methode
dient dem Validator (A5, "Luft-Objekt ohne Referenzen") und dem Export (A3,
"referenziert ja/nein"). Die Schleife über Referenzen bleibt reine Anzeige —
Domäne, Commands und Undo bleiben Einzeltreffer.

### A2 — Import: `JsonTreeConverter` registriert Links

`convertJsonNodeToEditNode` behandelt die beiden Terme wie heute `TERM_CLASS`:

```
TERM_WOOD_OBJECT_ID -> node.setWoodObjectId(wert), linkingSet.registerObject(id, node)
TERM_WOOD_LINK      -> node.setLinkTarget(wert),    linkingSet.registerLink(link, node)
```

Da der Konverter vor dem `EditTree` läuft, baut er selbst ein `EditLinkingSet` und
übergibt es nach dem Bau des Baums an das `EditTree` (bereits vorhandener Setter):

```java
// neue Signatur (Vorwärts-kompatibel überladbar)
public static EditNodeAbstract importFromJsonNode(JsonNode jsonNode, String rootName,
        EditTimes weightMonitor, EditLinkingSet linkingSet) throws JsonParseException
```

`EditTree` übernimmt das Set im Konstruktor bzw. direkt danach per `setLinkingSet`.
Der `providerName` des Sets kommt aus der obersten `_woodProviders`-Angabe bzw. dem
Dateikontext; Fallback: leeres Set (unverlinkte Dateien verhalten sich wie heute).

Randfälle:

- `_woodLink`-Ziel existiert nicht in derselben Datei (Cross-Provider-Referenz):
  trotzdem registrieren; Bewertung übernimmt A5.
- Link-Knoten, die vor ihrem Original im JSON stehen: Reihenfolge egal, da die
  Maps erst nach dem kompletten Baum ausgewertet werden.

**Systemknoten-Import**: Objekte unter `_woodDefinitions` sind per Definition keine
Baumknoten. Sie werden als `EditNodeObject` erzeugt (vollständig, mit allen Feldern
und `woodObjectId`), im `EditLinkingSet` registriert — aber **nicht** als Kind in
den sichtbaren Baum eingehängt. Das LinkingSet ist damit nicht nur Register,
sondern Halteraum: ein solches Objekt existiert editorseitig außerhalb des Baums
und bleibt über seine woodId referenzierbar.

### A3 — Export: `EditTreeWriter` emittiert die Terme

Analog zu `writeWoodModel`:

- `writeObject(EditNodeObject obj, ...)`: hat `obj.getWoodObjectId()`, wird als
  **erstes** Member `"_woodObjectId": "..."` geschrieben (Reihenfolge an der
  Erwartung des Parsers/`WoodIdFinder` ausrichten — vor Implementierung prüfen,
  wie der Writer in jsonCasted die Position hält).
- Referenz-Knoten (`isLinkReference()`): nur `"_woodLink": "..."` schreiben,
  keine Felder — das ist die serialisierte Form einer Referenz. Der Knoten hat
  ohnehin keine Kinder (siehe A1).
- **Systemknoten-Export**: Objekte, die das `EditLinkingSet` hält, aber nicht im
  Baum hängen (nach A4 gelöscht, oder aus `_woodDefinitions` importiert), und die
  noch referenziert werden (`!getReferencesTo(woodId).isEmpty()`), schreibt der
  Writer in die `_woodDefinitions`-Sektion
  der Ressource — nicht als Baumknoten. Referenzierte, aber im Baum nicht mehr
  vorhandene Objekte gehen so nicht verloren. Nicht mehr referenzierte Luft-Objekte
  werden beim Export weggelassen (ansonsten müllt sich die Datei mit Zombies zu).

### A4 — Commands, Undo/Redo, Clipboard

**Löschen ist ein Baum-Vorgang, kein Identitäts-Vorgang.** Löscht ein Anwender ein
Original mit `woodObjectId`, wird der Knoten nur aus der Baumstruktur entfernt —
das `EditLinkingSet` hält weiter eine starke Referenz auf das `EditNodeObject`
(mit komplettem Unterbaum, der GC kann nichts einsammeln). Das Objekt hängt damit
"in der Luft": aus der Sicht des Baums weg, aus der Sicht aller `_woodLink`-Referenzen
weiterhin vorhanden und intakt. Wir müssen beim Editieren keine Referenzen zerstören.

Damit das funktioniert:

- `EditNodeAbstract.removeChild`/`sayOnRemoved`-Ketten dürfen die entfernten
  Knoten **nicht** entdaten (Prüfung der `onChildObjectDataRemoved`-Hooks: sie
  sind für den Abriß von Objekt-Daten gedacht, nicht fürs Verschieben ins
  LinkingSet). Ggf. entfernt `DeleteNodeCommand` für woodId-Originalknoten den
  Kind-Verweis ohne Abriß-Callbacks.
- `DeleteNodeCommand` für ein Luft-Objekt endgültig (zweiter Löschen auf das
  im LinkingSet gehaltene Objekt): erst dann `removeObject` aus dem LinkingSet.
  Dafür braucht es später eine UI-Sicht auf den Halteraum (siehe Kapitel 6).
- `AddNodeCommand`/`PasteFromStash`: neu eingefügte Knoten mit woodId oder
  linkTarget registrieren (`registerObject`/`registerLink`).
- Undo/Redo: Delete aus dem Baum macht das Objekt zum Luft-Objekt, Undo hängt es
  wieder ein — Registrierung bleibt durchgehend im LinkingSet, nur die
  Baumzugehörigkeit wird zurückgerollt. Das ist unkritisch, weil das LinkingSet
  der stabile Halter ist und der Baum nur eine Sicht.
- `CopyToStashCommand`: Referenz-Knoten kopieren ist unproblematisch (Ziel zeigt
  weiter auf dasselbe Original); Original-Kopie siehe A1.

### A5 — Validierung: `DanglingLinkValidator`

Neuer Contributor in `de.jare.jsoncasted.editor.core.validation`, angebunden über die
bestehende `ValidatorRegistry`/`EditValidationRunner`-Infrastruktur, Vorbild
`EditNodeRequiredFieldsValidator`:

- Referenz-Knoten, dessen `linkTarget` **weder im Baum noch im LinkingSet**
  auflöst → **ERROR** (wirklich abgerissen). Ein Ziel, das nur als Luft-Objekt im
  LinkingSet hängt, ist dagegen **ok** — genau dafür ist der Halteraum da.
- Zwei Knoten mit derselben woodObjectId (nach Copy eines Originals) → **WARNING**
  mit beiden Pfaden.
- Luft-Objekt im LinkingSet, auf das keine Referenz mehr zeigt
  (`getReferencesTo(woodId).isEmpty()`) → **INFO/Hinweis**
  (Kandidat fürs Aufräumen; der Export lässt es ohnehin weg).

### A6 — Tests (jsonCasted_edit, `test/de/jare/jsoncasted/editor/`)

1. **Roundtrip**: JSON mit einem Original und N=3 `_woodLink`-Referenzen →
   Import → Export → byte-genauer Vergleich der Wood-Terme (Original-Id bleibt,
   alle Links bleiben).
2. **Konverter**: `_woodObjectId`/`_woodLink` landen als Felder auf dem richtigen
   `EditNodeObject`, LinkingSet enthält beide Maps korrekt; Reverse-Index
   konsistent zu linkMap (jede registrierte Referenz erscheint unter ihrem Ziel,
   `removeObject` räumt den Eintrag mit).
3. **Commands**: Original aus dem Baum löschen, während N Referenzen existieren →
   Referenzen bleiben intakt, Objekt hängt im LinkingSet, Export schreibt es nach
   `_woodDefinitions`; Undo hängt es wieder in den Baum.
4. **deepCopy**: woodObjectId/linkTarget werden transportiert.
5. **Systemknoten**: JSON mit Objekt in `_woodDefinitions` → Import registriert es
   im LinkingSet ohne Baumposition → Export schreibt es zurück in die Sektion.

---

## 4. Phase B: Fields-Pane

### B1 — `JsonJackFieldTableModel` (neu, `de.jare.tree.ui`)

Direktes Analogon zu `JsonJackAttrTableModel`, implementiert `TreeFocusListener`,
registriert über `jackmaster.addSelectionListener(...)`:

```
onNodeSelected(node)
  -> EditNode bestimmen (userObject des DefaultMutableTreeNode)
  -> ist es EditNodeProperty (Feld): Parent-Objekt anzeigen (EditNodeAbstract.getParent()),
     ausgewähltes Feld wird Fokus-Zeile
  -> ist es EditNodeObject: dieses Objekt anzeigen
  -> Zeilen: [Kopf "jsonType" = getJsonType()?.getTypeName() oder jsonType-Attribut]
             [pro Kindknoten: name = child.getName(),
                             value = child.getValue() (primitiv)
                                    oder Objektname + Typ (Objekt-Kind)]
```

Fokus-Zeile wird über `getEditId()` des Kindknotens gesucht (nicht über den Namen —
bei Arrays/`EditNodePropertyArr` sind Namen mehrdeutig), dann
`setSelectionInterval(row, row)` + `scrollRectToVisible(...)`.

### B2 — Combobox-Befüllung, dreistufig nach Parsbarkeit

| Zustand des Feldes | Quelle | Combobox-Inhalt |
|---|---|---|
| sauber geparst (`EditNodeProperty.getJsonField() != null`), Feldtyp Objekt | `JsonFieldDescriptor.getTypeName()` + `getCollectionType()` | alle Objekt-Instanzen aus der geparsten JSON, deren Typ passt |
| Interfacetyp als Feldtyp | `JsonTypeDescriptor.getImplementors()`/`containsSuper()` | alle Instanzen implementierender Typen — Zusatzprüfung desselben Filters |
| nicht geparst, Node-Typ primitiv | `EditNodeProperty.getType()` (`JsonNodeType`) | primitive Alternativen (STRING, NUMBER, LONG, BOOLEAN, NULL) |
| nicht geparst, Node-Typ OBJECT | — | leer/deaktiviert |

Filterbasis für die Objekt-Instanzen: ein Walk über den aktiven `EditTree` über alle
`EditNodeObject` mit aufgelöstem `getJsonType()` — **plus** die Einträge der objectIdMap
des LinkingSets (Phase A): ein aus dem Baum gelöschtes, aber noch referenziertes
Objekt bleibt damit wählbar, genau wie ein Objekt aus `_woodDefinitions`. Instanzen
mit woodId werden bevorzugt angezeigt, da nur sie echte Link-Ziele sind.
Im Combobox wählt man ausschließlich zwischen Objekten.

### B3 — Combobox-Cell-Editor (neu, wiederverwendbar)

`JsonJackAttrTableModel` hat die Modell-Seite (`isComboBoxCell`, `getComboBoxValues`)
schon — nur ist im `WoodWindow` kein Cell-Editor dafür installiert; die Methoden sind
derzeit tot. Neuer Editor (~50 Zeilen):

```java
public class JsonJackComboBoxCellEditor extends AbstractCellEditor implements TableCellEditor {
    // getTableCellEditorComponent: delegiert an model.isComboBoxCell(row, col),
    // baut JComboBox(model.getComboBoxValues(row)), sonst DefaultTextField
}
```

`installComboBoxEditor` im `WoodWindow` für beide Tabellen — rüstet damit auch den
Attributes-Pane nach.

### B4 — Asynchroner Refresh bei Typauflösung

Die Feld-Description trifft asynchron ein: `setJsonField` feuert
`EditTree.notifyTypeDescriptorChanged` und `propagateElementTypeToChildren`. Das
Fields-Model muss darauf reagieren und die sichtbaren Zeilen aktualisieren, sonst
stehen leere Comboboxen, obwohl das Modell die Antwort längst kennt. Derselbe
Refresh-Hook ist ein latenter Bugfix für das `JsonJackAttrTableModel` (refresht
bisher nur auf `onNodeSelected`).

### B5 — Einbindung in `WoodWindow`

- Konstante `TAB_FIELDS = "Fields"`; `bottomTabs.addTab(TAB_FIELDS, createFieldsPanel())`
  neben `TAB_ATTRIBUTES`.
- `createFieldsPanel()` analog `createAttributesPanel()`: Model bauen,
  `jackmaster.addSelectionListener(fieldsModel)`, JTable mit `EditStatusCellRenderer`,
  Combobox-Editor aus B3.
- Model als **Feld** in `WoodWindow` halten: der Orator des Master-Controls hält
  Listener nur schwach referenziert, der GC deregistriert sie sonst still
  (gleiche Falle wie Commit 9de659e — dort für Parse-Listener gefixt).
- Rückkopplungsschutz: die Tabelle ist reiner Listener. Soll später ein
  Zeilen-Klick den Baum steuern, ist der `trigger`-Parameter am Orator die
  Loop-Protection — bereits vorhanden, nicht neu bauen.

---

## 5. Selektions- und Fokus-Semantik

- Klick auf ein Objekt im Baum → Fields-Pane zeigt dieses Objekt (jsonType + alle Felder).
- Klick auf ein Feld (Property) im Baum → Fields-Pane zeigt das **Parent-Objekt**,
  die Zeile des geklickten Feldes erhält den Fokus (Selection + Scroll).
- Der Feld-Fokus bleibt auch beim Wechsel zwischen Attributes/Fields-Tab erhalten
  (Model behält seinen Zustand, Tab-Wechsel baut die Tabelle nicht neu).

---

## 6. Offene Punkte / bewusst vertagt

1. **Auswahl-Semantik der Combobox** (der "später reden"-Punkt): Auswahl eines Objekts
   setzt das Feld auf eine **Link-Referenz** (Kindknoten wird Referenz-Knoten mit
   `linkTarget`, Export schreibt `_woodLink`) statt auf eine Kopie. Braucht ein
   eigenes Command (`SetFieldLinkCommand`), damit Undo greift. Klärungsfragen:
   Verweisen auf Objekte mit oder ohne woodId? Wird beim Setzen automatisch eine
   woodId ans Original vergeben?
2. **woodId-Generierung**: Woher kommen Ids für neu angelegte Objekte? Kandidat ist
   der bestehende `IdGenerator`; ob woodIds editor-lokal oder provider-global sein
   müssen, klärt die Auswahl-Semantik.
3. **UI-Sicht auf den Halteraum**: Luft-Objekte im LinkingSet sind unsichtbar, solange
   kein Knoten auf sie zeigt. Für das endgültige Löschen und Aufräumen braucht es
   eine Sicht (z. B. Sektion im Fields-Pane oder eigener Baumabschnitt). Design offen.
4. **Renderer**: Darstellung von Referenz-Knoten im Baum (Icon/Tooltip "ref: name").
   Assets in `assets/icons/` erweitern. Zwei Varianten mit unterschiedlichem
   UI-Aufwand:
   - **Stub-Anzeige** (Standard im Konzept): Der Swing-Baum ist eine zweite
     Materialisierung des EditNode-Baums (`JackTreeModel.buildSubtree`, userObject
     = EditNode); Updates laufen positionslokal über
     `JackEditTree` → `findNodeByIdAndRange` → `buildSubtreeStructureChanged`
     (`nodeStructureChanged`). Da jeder Referenz-Stub ein eigenes EditNodeObject
     mit eigenem editId an eigener Position ist, bleibt die 1:1-Positionsmapping
     erhalten — ein Edit am Original informiert nur die Original-Position, keine
     Fan-out-Mechanik nötig.
   - **Symlink-Anzeige** (dereferenziert beim Zeichnen über `findLinkTarget`):
     Dann hängen N UI-Positionen von einer fremden Position ab, die
     positionslokalen Events erreichen sie nicht. Fan-out nötig: nach Edit am
     Original `linkingSet.getReferencesTo(woodId)` (Reverse-Index, A1 — ein
     Lookup statt Filterung), je Referenz
     `findNodeById(ref.getEditId())` + `nodeChanged(uiNode)`. Nur-Lesen —
     expandierbare dereferenzierte Kinder wären wieder Kopie-Synchronisation.
5. **Multi-Tab**: `EditLinkingSet` ist pro `EditTree` — Cross-Tab-Verweise sind
   bewusst out of scope; Validator meldet sie als ERROR.
6. **Enum-Felder**: `JsonTypeDescriptor.getPermittedValues()` liefert fertige
   Auswahllisten — gratis ausbaufähig, sobald Auswahl-Semantik steht.
7. **Modellsicht ohne Instanz**: Modell-Felder des Typs ohne Kindknoten als
   "fehlende" Zeilen anzeigen. Tür steht offen, kein Bestandteil dieses Konzepts.

---

## 7. Aufwand und Reihenfolge

| Phase | Inhalt | Schätzung |
|---|---|---|
| A1–A3 | woodId/linkTarget, Import, Export | 1–2 Tage |
| A4–A5 | Commands/Undo, Validator | 1 Tag |
| A6 | Tests | 0,5–1 Tag |
| B1–B5 | Fields-Pane inkl. Editor + Refresh-Hook | 1 Tag |

Reihenfolge zwingend A vor B: ohne Link-Trägerfähigkeit kann die Combobox nur
Stufe 2/3 (primitive Fallbacks) liefern und der Editor verliert weiterhin Links.
Innerhalb Phase A ist A2+A3 der kritische Pfad (schließen den Datenverlust-Bug),
A4/A5 sind Härtung.

---

## 8. Risiken

- **Signaturänderung `importFromJsonNode`**: Aufrufer sind `EditTree` und
  `JackMainActions` — überschaubar, aber Breaking Change innerhalb des Ökosystems.
  Absicherung über überladene Methode mit Default-LinkingSet.
- **Parser-Erwartung an Member-Reihenfolge**: Vor A3 klären, wo `_woodObjectId`
  in der Ausgabe stehen muss, damit der Roundtrip garantiert (Test A6/1 deckt es ab).
- **Undo-Konsistenz**: Registrierungs-Hooks in Commands müssen in beiden Richtungen
  (execute/undo) sauber arbeiten — sonst Maps-Drift. Test A6/3 deckt es ab.
- **Entfernungs-Callbacks**: `removeChild`/`sayOnRemoved`/`onChildObjectDataRemoved`
  sind auf Abriß ausgelegt. Beim Lösen eines woodId-Originals aus dem Baum dürfen
  sie die Nutzdaten nicht anfassen, sonst verliert das Luft-Objekt im LinkingSet
  seinen Inhalt. Test A6/3 deckt es ab.
- **Export-Müll**: Luft-Objekte, auf die keine Referenz zeigt, müssen beim Export
  weggelassen werden — sonst sammelt die Datei Zombies aus jedem Löschen+rückgängig.
- **Kopie-Konflikt**: zwei Knoten mit gleicher woodId nach Copy — bewusst erst
  WARNING (A5), Endgültigkeit mit der Auswahl-Semantik.

---

## 9. Umsetzungsnotizen (Abweichungen vom Konzept)

Beim Umsetzen haben sich vier Punkte gegenüber dem ursprünglichen Text vereinfacht
oder verschoben:

1. **woodObjectId = objektId.** `EditNodeObject` trägt bereits ein ungenutztes
   `objektId`-Feld, und `EditLinkingSet` registriert schon danach — statt eines
   Zweitfelds wird genau dieses befüllt und angezeigt (Attributzeile
   `objektId` existiert im Attributes-Pane schon).
2. **`_woodDefinitions` bleibt sichtbar.** Der Konverter hängt Definitions-
   Objekte als normalen Array-Knoten in den Baum (heutiges Verhalten), statt
   sie unsichtbar im LinkingSet zu halten. "Luft-Objekt" ist damit nur, was
   zur Laufzeit aus dem Baum gelöscht wurde; der Export schreibt genau diese
   nach `_woodDefinitions` zurück, der Re-Import zeigt sie wieder als Baum-
   Knoten. Rundtrip-stabil und ehrlich.
3. **Zombie-Regel verschärft.** Definitions-Einträge werden nur für Objekte
   geschrieben, auf die auch Stubs zeigen, die selbst noch im exportierten
   Baum hängen — abgerissene Stubs im Reverse-Index halten kein Ziel mehr am
   Leben (`EditTreeWriter.isReferencedFromTree`).
4. **Undo-Rebinding über addChild.** Die Commands fügen Snapshot-Kopien ein,
   nicht die Originale. Die Wartung von objectIdMap und Reverse-Index hängt
   deshalb zentral an `EditTree.addChild`/`removeChild`: jede eingefügte Kopie
   registriert sich neu und überschreibt damit die Identity-Map-Einträge; das
   Undo einer Löschung bindet die Identität so an die wieder eingefügte Kopie.
   Gelöschte Stubs verlassen den Reverse-Index, Identitäten bleiben
   (Halteraum).

Zusätzlich gilt im Validator-Umfeld: der `EditValidationRunner` läuft nur mit
einem non-null Deskriptor — WoodLink-Diagnosen erscheinen also nur bei
geladenem Modell (Verhalten aller Validatoren, nicht spezifisch für Wood-Links).

Die Tests der Phase A liegen in jsonCasted_edit
(`EditLinkingSetNGTest`, `WoodLinkImportNGTest`, `WoodLinkRoundTripNGTest`,
`WoodLinkCommandNGTest`, `WoodLinkValidatorNGTest` — Suite 139 Tests, 0 Failures),
die der Phase B in WoodJsonJack (`JsonJackFieldTableModelNGTest`, Suite 75
Tests, 0 Failures).

## 10. editString: Anzeige-Templates als Annotation (nachgetragen)

editString ist quer durch alle vier Schichten umgesetzt (Detailkonzept:
`annotationen-konzept.md`, Entscheidung 12, jsonCasted):

- **Modell (jsonCasted):** `JsonAnnotation.EDIT_STRING` ("editString"),
  deklariert am `descriptType` der Selbstbeschreibung — damit kann jeder
  Typ-Eintrag einer Description-Datei `@editString` tragen.
  `JsonTypeDescriptor.editString` ist Laufzeit-Speicher; die Adoption läuft
  ueber `JsonModelDescriptor.adoptEditStrings(raw)` aus dem ROHEN JsonNode-Baum
  (der Builder ist @-blind, Entscheidung 3 des Annotations-Konzepts).
- **Validator (jsonCasted_edit):** `EditStringAnnotationValidator`
  (`editstring.singleElement`) — `@editString` muss ein ein-elementiges
  String-Array sein; ein Inline-String zaehlt als sein ein Element.
- **Editor (jsonCasted_edit):** `EditStringEvaluator` — `${param}` greift auf
  die Kind-Properties zu (Spezialnamen `woodId`, `jsonType`), Filter `upper`,
  `lower`, `trim`, `substr:a:b`, `number:pattern`, `default:x`, Escape `$${`.
  `displayText()` Fallback-Kette: Template → name-Feld → woodId → Knotenname.
  Die Adoption haengt im `JsonTreeConverter.loadDescrAndConvertRessourceToEditTree`
  (ein Rohparse bedient Cast und Adoption).
- **UI (WoodJsonJack):** `JsonJackFieldTableModel` zeigt Kandidaten-Labels
  (Combobox, Collection-Dialog: text-Spalte) und die Value-Spalte
  objektwertiger Felder ueber `EditStringEvaluator.displayText`.

Der Baum-Renderer (Hauptbaum-Labels) zeigt weiter den strukturellen Namen —
editString greift zunaechst nur in Pane, Comboboxen und Dialogen.
