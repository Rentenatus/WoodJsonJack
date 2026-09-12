# UI-Integration: On-the-Fly Type Parser

**Zielgruppe**: UI-Entwickler, die den On-the-Fly Type Parser in die Benutzeroberfläche integrieren
**Status**: Plan erstellt — bereit zur schrittweisen Implementation
**Abhängigkeiten**: `EditTree`, `TypeParserService`, `ParseState`, `EditStatus`, `JsonModelDescriptor`
**Core-Bibliothek**: `D:\git_jsonCasted_edit` (Editor-Core) und `D:\git_jsonCasted` (Model/Parser)
**Ziel-Projekt**: `D:\git_WoodJsonJack` (Swing-UI "WoodJsonJack")

---

## Inhaltsverzeichnis

1. [Einleitung](#1-einleitung)
2. [Grundlagen: Was der Parser bietet](#2-grundlagen-was-der-parser-bietet)
3. [UI-Integration: API-Referenz](#3-ui-integration-api-referenz)
4. [Verwendungsszenarien](#4-verwendungsszenarien)
5. [Best Practices](#5-best-practices)
6. [Fehlerbehandlung](#6-fehlerbehandlung)
7. [Beispiel-Implementierung](#7-beispiel-implementierung)
8. [Anhang: ParseState und EditStatus](#8-anhang-parsestate-und-editstatus)
9. [API-Korrekturen gegenüber der ursprünglichen Dokumentation](#9-api-korrekturen-gegenüber-der-ursprünglichen-dokumentation)
10. [Integrationsplan für WoodJsonJack](#10-integrationsplan-für-woodjsonjack)
11. [Phasen-Status](#11-phasen-status)

---

## 1. Einleitung

Der **On-the-Fly Type Parser** ermöglicht automatisches, inkrementelles Typ-Parsen für `EditTree`-Strukturen während des Editierens. Diese Anleitung erklärt, wie eine UI diese Funktionalität nutzen kann, um:

- Typ-Informationen automatisch anzuzeigen
- Parse-Fortschritt zu visualisieren
- Fehler und Warnungen dem Benutzer zu kommunizieren
- Die UI responsiv zu halten

---

## 2. Grundlagen: Was der Parser bietet

### 2.1 Kernkomponenten

| Komponente | Verantwortung |
|-----------|--------------|
| `TypeParserService` | Hintergrund-Service, der Knoten parst |
| `ParseState` | Zustand des Parsens pro Knoten (NONE, EDITED, PENDING, DONE) |
| `EditStatus` | Validierungsstatus (STATELESS, OKAY, WARNING, ERROR) |
| `EditTree` | Hält Parser-Service, Queue und Listener |

### 2.2 Automatische Funktionen

Der Parser **läuft automatisch** wenn:
- Ein `JsonModelDescriptor` via `setJsonModelDescriptor()` gesetzt wird
- Ein Knoten verändert wird (Name, Wert, Kinder)
- Ein `TypeParserService` manuell gesetzt wird

Der Parser **aktualisiert automatisch**:
- `ParseState` pro Knoten
- `EditStatus` und `EditMessage` basierend auf Parser-Ergebnissen
- Parent-Typen via Feldnamen-Inferenz (wenn eindeutig)

---

## 3. UI-Integration: API-Referenz

### 3.1 EditTree Methoden (für UI)

#### Lebenszyklus

```java
// Automatically started when model is set
editTree.setJsonModelDescriptor(modelDescriptor);

// Manual start/stop
editTree.startParserService();
editTree.stopParserService();

// Full re-parse
editTree.triggerFullReparse();

// Cleanup
editTree.close();

// Check if parser is running
boolean isRunning = editTree.isParserRunning();
```

#### Parser-Service Zugriff

```java
// Get the parser service
TypeParserService service = editTree.getParserService();

// Manual parse request
if (service != null) {
    service.requestParse(node);  // Parse single node
    service.requestFullParse();   // Parse entire tree
}
```

#### Queue-Informationen (für Debug/UI)

```java
// Get queue statistics
int queueSize = service.getQueuedTaskCount();
int activeThreads = service.getActiveThreadCount();
```

### 3.2 Knoten-Zustände abfragen

#### ParseState (Parsen-Fortschritt)

```java
EditNode node = ...;
ParseState state = node.getParseState();

switch (state) {
    case NONE:      // Noch nie geparst
        break;
    case EDITED:   // Geändert, wartet auf Parsen
        break;
    case PENDING:  // In der Queue, wird bald geparst
        break;
    case DONE:     // Erfolgreich geparst
        break;
}
```

#### EditStatus (Validierung)

```java
EditStatus status = node.getEditStatus();
String message = node.getEditMessage();

switch (status) {
    case STATELESS: // Kein Status
        break;
    case OKAY:      // Typ erfolgreich zugewiesen
        break;
    case WARNING:   // Typ gefunden, aber mit Warnung (z.B. mehrdeutig)
        break;
    case ERROR:     // Fehler bei Typzuordnung
        break;
}
```

### 3.3 Typ-Informationen abfragen

#### Für EditNodeObject (Objekt-Knoten)

```java
if (node instanceof EditNodeObject) {
    EditNodeObject objNode = (EditNodeObject) node;
    JsonTypeDescriptor type = objNode.getJsonType();
    
    if (type != null) {
        String typeName = type.getTypeName();
        List<JsonFieldDescriptor> fields = type.getFields();
        // UI kann Typ-Info anzeigen
    }
}
```

#### Für EditNodeProperty (Eigenschafts-Knoten)

```java
if (node instanceof EditNodeProperty) {
    EditNodeProperty propNode = (EditNodeProperty) node;
    JsonFieldDescriptor field = propNode.getJsonField();
    
    if (field != null) {
        String fieldName = field.getFieldName();
        String fieldType = field.getTypeName();
        boolean required = field.isRequired();
        // UI kann Feld-Info anzeigen
    }
}
```

---

## 4. Verwendungsszenarien

### 4.1 Szenario: Typ-Informationen in der Baumansicht anzeigen

**Ziel**: Zeige Typ-Icon oder -Name neben jedem Knoten in der Baumansicht an.

**Implementation**:

```java
public class TreeViewRenderer {
    
    public Icon getNodeIcon(EditNode node) {
        if (node instanceof EditNodeObject) {
            EditNodeObject objNode = (EditNodeObject) node;
            JsonTypeDescriptor type = objNode.getJsonType();
            
            if (type != null) {
                return getTypeIcon(type.getTypeName());
            }
        }
        return getDefaultIcon();
    }
    
    public String getNodeTooltip(EditNode node) {
        StringBuilder tooltip = new StringBuilder();
        tooltip.append("Name: ").append(node.getName()).append("\n");
        
        if (node instanceof EditNodeObject) {
            EditNodeObject objNode = (EditNodeObject) node;
            JsonTypeDescriptor type = objNode.getJsonType();
            if (type != null) {
                tooltip.append("Type: ").append(type.getTypeName()).append("\n");
            }
        }
        
        // ParseState anzeigen
        if (node instanceof EditNodeAbstract) {
            ParseState state = ((EditNodeAbstract) node).getParseState();
            tooltip.append("Parse: ").append(state.toString()).append("\n");
        }
        
        // EditStatus anzeigen
        EditStatus status = node.getEditStatus();
        String message = node.getEditMessage();
        if (status != EditStatus.OKAY && message != null) {
            tooltip.append("Status: ").append(status).append(" - ").append(message);
        }
        
        return tooltip.toString();
    }
}
```

### 4.2 Szenario: Parse-Fortschritt visualisieren

**Ziel**: Zeige dem Benutzer, welche Knoten aktuell geparst werden.

**Implementation**:

```java
public class ParseProgressUI {
    private final EditTree editTree;
    private final Timer progressTimer;
    
    public ParseProgressUI(EditTree editTree) {
        this.editTree = editTree;
        
        // Timer für regelmäßige Updates
        progressTimer = new Timer(200, e -> updateProgressDisplay());
        progressTimer.start();
    }
    
    private void updateProgressDisplay() {
        TypeParserService service = editTree.getParserService();
        if (service == null || !service.isRunning()) {
            progressLabel.setText("Parser: Inaktiv");
            return;
        }
        
        int queueSize = service.getQueuedTaskCount();
        int activeThreads = service.getActiveThreadCount();
        
        progressLabel.setText(String.format(
            "Parser: %d Threads, %d in Queue",
            activeThreads, queueSize
        ));
    }
    
    public void shutdown() {
        progressTimer.stop();
    }
}
```

### 4.3 Szenario: Fehler und Warnungen anzeigen

**Ziel**: Zeige Fehler- und Warning-Meldungen in der UI an.

**Implementation**:

```java
public class ValidationDecorator {
    
    public Color getNodeColor(EditNode node) {
        EditStatus status = node.getEditStatus();
        
        switch (status) {
            case ERROR:
                return Color.RED;
            case WARNING:
                return Color.ORANGE;
            case OKAY:
                return Color.GREEN;
            default:
                return Color.BLACK;
        }
    }
    
    public String getStatusMessage(EditNode node) {
        String message = node.getEditMessage();
        if (message == null || message.isEmpty()) {
            return null;
        }
        return message;
    }
    
    // Für Baumansicht: Markiere Knoten mit Fehlern
    public void decorateTreeNode(TreeNode uiNode, EditNode editNode) {
        EditStatus status = editNode.getEditStatus();
        
        switch (status) {
            case ERROR:
                uiNode.setIcon(errorIcon);
                uiNode.setForeground(Color.RED);
                break;
            case WARNING:
                uiNode.setIcon(warningIcon);
                uiNode.setForeground(Color.ORANGE);
                break;
            case OKAY:
                uiNode.setIcon(okayIcon);
                uiNode.setForeground(Color.GREEN);
                break;
            default:
                uiNode.setIcon(defaultIcon);
                uiNode.setForeground(Color.BLACK);
        }
        
        // Tooltip mit ParseState und EditMessage
        String tooltip = buildTooltip(editNode);
        uiNode.setToolTipText(tooltip);
    }
    
    private String buildTooltip(EditNode node) {
        StringBuilder sb = new StringBuilder();
        
        if (node instanceof EditNodeAbstract) {
            EditNodeAbstract absNode = (EditNodeAbstract) node;
            sb.append("Parse: ").append(absNode.getParseState().getName());
        }
        
        sb.append(" | Status: ").append(node.getEditStatus().getName());
        
        String message = node.getEditMessage();
        if (message != null && !message.isEmpty()) {
            sb.append(" | ").append(message);
        }
        
        return sb.toString();
    }
}
```

### 4.4 Szenario: EditTree mit Modell laden

**Ziel**: Lade ein JSON-Dokument und ein Modell, dann starte das Parsen.

**Implementation**:

```java
public class DocumentLoader {
    private EditTree editTree;
    
    public void loadDocument(File jsonFile, JsonModelDescriptor model) {
        // 1. JSON laden und EditTree erstellen
        String jsonContent = readJsonFile(jsonFile);
        editTree = JsonParser.parseToEditTree(jsonContent);
        
        // 2. Modell setzen (startet Parser automatisch)
        editTree.setJsonModelDescriptor(model);
        
        // 3. Warten, bis Root geparst ist (optional)
        waitForRootParse();
    }
    
    private void waitForRootParse() {
        EditNodeAbstract root = editTree.getRoot();
        
        // Maximal 5 Sekunden warten
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 5000) {
            if (root.getParseState() == ParseState.DONE) {
                return; // Fertig
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Timeout - trotzdem weitermachen
        System.out.println("Warning: Root parse timed out");
    }
    
    public void unloadDocument() {
        if (editTree != null) {
            editTree.stopParserService();
            editTree.close();
            editTree = null;
        }
    }
}
```

### 4.5 Szenario: Knoten bearbeiten (Name/Wert ändern)

**Ziel**: Wenn der Benutzer einen Knoten umbenennt oder seinen Wert ändert, wird das Parsen automatisch ausgelöst.

**Implementation**:

```java
public class NodeEditor {
    private final EditTree editTree;
    
    public void renameNode(EditNode node, String newName) {
        // Die setName()-Methode löst automatisch das Parsen aus
        // (via Listener in EditNodeAbstract/EditNodeObject/EditNodeProperty)
        node.setName(newName);
        
        // Optional: Warten auf Parse-Ergebnis und UI aktualisieren
        scheduleUIUpdate(node);
    }
    
    public void setNodeValue(EditNode node, String newValue) {
        if (node instanceof EditNodeProperty) {
            EditNodeProperty propNode = (EditNodeProperty) node;
            // setValue() löst automatisch das Parsen aus
            propNode.setValue(newValue);
            scheduleUIUpdate(node);
        }
    }
    
    private void scheduleUIUpdate(EditNode node) {
        // UI-Update nach kurzer Verzögerung planen
        // (gibt dem Parser Zeit, die Änderungen zu verarbeiten)
        SwingUtilities.invokeLater(() -> {
            // Warte 200ms auf Parse-Ergebnis
            new Timer(200, e -> updateNodeDisplay(node)).setRepeats(false).start();
        });
    }
    
    private void updateNodeDisplay(EditNode node) {
        // UI aktualisieren: Icon, Farbe, Tooltip
        treeView.updateNode(node);
    }
}
```

### 4.6 Szenario: Manuelles Reparsing erzwingen

**Ziel**: Ermögliche dem Benutzer, das Parsen manuell zu triggern (z.B. nach Modell-Änderungen).

**Implementation**:

```java
public class ManualParseAction extends AbstractAction {
    private final EditTree editTree;
    
    public ManualParseAction(EditTree editTree) {
        super("Re-parse Types");
        this.editTree = editTree;
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        // Full re-parse auslösen
        editTree.triggerFullReparse();
        
        // Benutzer informieren
        JOptionPane.showMessageDialog(
            null,
            "Re-parsing started. Check status for progress.",
            "Reparsing",
            JOptionPane.INFORMATION_MESSAGE
        );
    }
}
```

---

## 5. Best Practices

### 5.1 Thread-Safety

✅ **DO**:
- UI-Updates immer in SwingUtilities.invokeLater() ausführen
- ParseState und EditStatus nur lesen (sie werden vom Parser-Thread geschrieben)
- Die Parse-Queue nie direkt modifizieren (nur über EditTree-Methoden)

❌ **DON'T**:
- Parser-Methoden direkt aus dem UI-Thread aufrufen (blockiert die UI)
- ParseState oder EditStatus direkt setzen (wird vom Parser verwaltet)
- Annahme treffen, dass Parsen sofort abgeschlossen ist

### 5.2 Performance

✅ **DO**:
- Hash-Vergleich nutzen, um unnötiges Re-Rendering zu vermeiden
- ParseState.NONE und ParseState.DONE gleich behandeln (beide = aktuell)
- UI-Updates batchen (z.B. alle 200ms statt bei jeder Änderung)

❌ **DON'T**:
- Bei jeder kleinen Änderung die gesamte Baumansicht neu zeichnen
- Auf ParseState.PENDING warten (kann zu UI-Freezes führen)

### 5.3 Benutzererfahrung

✅ **DO**:
- Parse-Fortschritt visualisieren (Queue-Größe, aktive Threads)
- Fehler und Warnungen deutlich hervorheben
- Tooltips mit detaillierten Informationen anbieten
- Automatisches Parsen als Feature kommunizieren

❌ **DON'T**:
- Den Benutzer mit Parser-Fehlern überfluten
- UI blockieren, während geparst wird
- ParseState als primäre Information anzeigen (Benutzer interessiert sich für EditStatus)

### 5.4 Error Handling

✅ **DO**:
- EditStatus.ERROR als rote Markierung anzeigen
- EditMessage in Tooltips oder Statusbar anzeigen
- Mehrdeutige Typen (ambiguous fields) als Warnung anzeigen
- Parse-Fehler (kein Modell) als Information anzeigen

❌ **DON'T**:
- Exceptions aus dem Parser an die UI weiterleiten
- Parse-Fehler als kritische Fehler behandeln (sind normal)

---

## 6. Fehlerbehandlung

### 6.1 Häufige Fehler und Lösungen

| Fehler | Ursache | Lösung |
|--------|---------|--------|
| `EditStatus.ERROR` mit "Field not found" | Feld existiert nicht im Parent-Typ | Modell prüfen, Feldname korrigieren |
| `EditStatus.WARNING` mit "Type not found" | Typ existiert nicht im Modell | Modell prüfen, Typname korrigieren |
| `EditStatus.WARNING` mit "ambiguous" | Feldname in mehreren Typen | Feldname qualifizieren oder Typ manuell setzen |
| `EditStatus.WARNING` mit "No model descriptor" | JsonModelDescriptor nicht gesetzt | Modell vor dem Parsen setzen |
| ParseState bleibt PENDING | Parser nicht gestartet | `startParserService()` aufrufen |

### 6.2 Debug-Informationen

```java
// Debug-Info für einen Knoten
public String getDebugInfo(EditNode node) {
    StringBuilder sb = new StringBuilder();
    
    if (node instanceof EditNodeAbstract) {
        EditNodeAbstract absNode = (EditNodeAbstract) node;
        sb.append("ParseState: ").append(absNode.getParseState().getName());
        sb.append("\n");
        sb.append("LastHash: ").append(absNode.getLastParsedHash());
        sb.append("\n");
    }
    
    sb.append("EditStatus: ").append(node.getEditStatus().getName());
    sb.append("\n");
    sb.append("EditMessage: ").append(node.getEditMessage());
    sb.append("\n");
    
    if (node instanceof EditNodeObject) {
        EditNodeObject objNode = (EditNodeObject) node;
        JsonTypeDescriptor type = objNode.getJsonType();
        sb.append("Type: ").append(type != null ? type.getTypeName() : "null");
        sb.append("\n");
    }
    
    if (node instanceof EditNodeProperty) {
        EditNodeProperty propNode = (EditNodeProperty) node;
        JsonFieldDescriptor field = propNode.getJsonField();
        sb.append("Field: ").append(field != null ? field.getFieldName() : "null");
    }
    
    return sb.toString();
}
```

---

## 7. Beispiel-Implementierung

### 7.1 Komplette UI-Integration (Swing)

```java
public class JsonTreeUI extends JFrame {
    private EditTree editTree;
    private JTree treeView;
    private JLabel statusLabel;
    private ParseProgressUI progressUI;
    
    public JsonTreeUI() {
        setTitle("JSON Editor with On-the-Fly Parsing");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        // TreeView erstellen
        treeView = new JTree();
        treeView.setCellRenderer(new TypedTreeCellRenderer());
        JScrollPane scrollPane = new JScrollPane(treeView);
        add(scrollPane, BorderLayout.CENTER);
        
        // Statusbar
        statusLabel = new JLabel("Ready");
        add(statusLabel, BorderLayout.SOUTH);
        
        // Progress-UI
        progressUI = new ParseProgressUI(editTree);
        
        // Menu
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        fileMenu.add(new JMenuItem(new LoadAction(this)));
        fileMenu.add(new JMenuItem(new SaveAction(this)));
        fileMenu.addSeparator();
        fileMenu.add(new JMenuItem(new ManualParseAction(editTree)));
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);
        
        // Listener für Knoten-Auswahl
        treeView.addTreeSelectionListener(e -> {
            EditNode selectedNode = getSelectedNode();
            updateStatusLabel(selectedNode);
        });
    }
    
    public void loadDocument(File jsonFile, JsonModelDescriptor model) {
        // Altes Dokument schließen
        if (editTree != null) {
            editTree.close();
        }
        
        // Neues Dokument laden
        editTree = JsonParser.parseToEditTree(jsonFile);
        
        // Modell setzen (startet Parser automatisch)
        editTree.setJsonModelDescriptor(model);
        
        // Progress-UI aktualisieren
        progressUI.setEditTree(editTree);
        
        // Baumansicht aktualisieren
        updateTreeView();
        
        // Status aktualisieren
        statusLabel.setText("Document loaded, parsing...");
    }
    
    private void updateTreeView() {
        if (editTree == null) return;
        
        EditNodeAbstract root = editTree.getRoot();
        TreeNode rootTreeNode = createTreeNode(root);
        DefaultTreeModel treeModel = new DefaultTreeModel(rootTreeNode);
        treeView.setModel(treeModel);
    }
    
    private TreeNode createTreeNode(EditNode editNode) {
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(editNode);
        
        for (int i = 0; i < editNode.getChildCount(); i++) {
            EditNode child = editNode.getChildAt(i);
            treeNode.add(createTreeNode(child));
        }
        
        return treeNode;
    }
    
    private void updateStatusLabel(EditNode node) {
        if (node == null) {
            statusLabel.setText("No node selected");
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Node: ").append(node.getName());
        
        if (node instanceof EditNodeAbstract) {
            EditNodeAbstract absNode = (EditNodeAbstract) node;
            sb.append(" | Parse: ").append(absNode.getParseState().getName());
        }
        
        sb.append(" | Status: ").append(node.getEditStatus().getName());
        
        String message = node.getEditMessage();
        if (message != null && !message.isEmpty()) {
            sb.append(" | ").append(message);
        }
        
        statusLabel.setText(sb.toString());
    }
    
    public void unloadDocument() {
        if (editTree != null) {
            editTree.close();
            editTree = null;
        }
        treeView.setModel(null);
        statusLabel.setText("Ready");
    }
    
    @Override
    public void dispose() {
        progressUI.shutdown();
        if (editTree != null) {
            editTree.close();
        }
        super.dispose();
    }
    
    // Cell Renderer mit Typ-Informationen
    private static class TypedTreeCellRenderer extends DefaultTreeCellRenderer {
        private final Icon objectIcon = loadIcon("object.png");
        private final Icon propertyIcon = loadIcon("property.png");
        private final Icon arrayIcon = loadIcon("array.png");
        private final Icon errorIcon = loadIcon("error.png");
        private final Icon warningIcon = loadIcon("warning.png");
        
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            
            super.getTreeCellRendererComponent(tree, value, selected, expanded, 
                    leaf, row, hasFocus);
            
            if (value instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) value;
                Object userObject = treeNode.getUserObject();
                
                if (userObject instanceof EditNode) {
                    EditNode editNode = (EditNode) userObject;
                    decorateNode(this, editNode);
                }
            }
            
            return this;
        }
        
        private void decorateNode(DefaultTreeCellRenderer renderer, EditNode node) {
            // Icon basierend auf Typ
            if (node instanceof EditNodeObject) {
                renderer.setIcon(objectIcon);
            } else if (node instanceof EditNodeProperty) {
                EditNodeProperty prop = (EditNodeProperty) node;
                if (prop.getType() == JsonNodeType.ARRAY) {
                    renderer.setIcon(arrayIcon);
                } else {
                    renderer.setIcon(propertyIcon);
                }
            }
            
            // Farbe basierend auf Status
            EditStatus status = node.getEditStatus();
            switch (status) {
                case ERROR:
                    renderer.setForeground(Color.RED);
                    renderer.setIcon(errorIcon);
                    break;
                case WARNING:
                    renderer.setForeground(Color.ORANGE);
                    renderer.setIcon(warningIcon);
                    break;
                case OKAY:
                    renderer.setForeground(new Color(0, 100, 0));
                    break;
                default:
                    renderer.setForeground(Color.BLACK);
            }
            
            // Tooltip
            StringBuilder tooltip = new StringBuilder();
            tooltip.append("<html>");
            tooltip.append("<b>").append(node.getName()).append("</b><br>");
            
            if (node instanceof EditNodeAbstract) {
                EditNodeAbstract absNode = (EditNodeAbstract) node;
                tooltip.append("Parse: ").append(absNode.getParseState().getName());
                tooltip.append("<br>");
            }
            
            tooltip.append("Status: ").append(status.getName());
            String message = node.getEditMessage();
            if (message != null && !message.isEmpty()) {
                tooltip.append(" - ").append(escapeHtml(message));
            }
            tooltip.append("</html>");
            
            renderer.setToolTipText(tooltip.toString());
        }
        
        private String escapeHtml(String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
```

---

## 8. Anhang: ParseState und EditStatus

### 8.1 ParseState

| Zustand | Bedeutung | UI-Empfehlung |
|---------|-----------|--------------|
| `NONE` | Noch nie geparst | Normal anzeigen |
| `EDITED` | Geändert, wartet auf Parsen | evtl. grauer Hinterlegungsfarbe |
| `PENDING` | In der Queue, wird geparst | evtl. Animations-Indikator |
| `DONE` | Erfolgreich geparst | Normal anzeigen |

### 8.2 EditStatus

| Status | Bedeutung | UI-Empfehlung |
|--------|-----------|--------------|
| `STATELESS` | Kein Status | Normal anzeigen |
| `OKAY` | Typ erfolgreich zugewiesen | Grün anzeigen |
| `WARNING` | Typ mit Warnung (z.B. mehrdeutig) | Orange/ Gelb anzeigen |
| `ERROR` | Fehler bei Typzuordnung | Rot anzeigen |

### 8.3 Typ-Informationen

| Knotentyp | Typ-Information | UI-Anzeige |
|----------|----------------|------------|
| `EditNodeObject` | `JsonTypeDescriptor` | Typname, Felder |
| `EditNodeProperty` | `JsonFieldDescriptor` | Feldname, Feldtyp, required |
| `EditNodePropertyArr` | `JsonFieldDescriptor` | Feldname, Array-Typ |

---

## Zusammenfassung

Die UI-Integration des On-the-Fly Parsers erfordert:

1. **ParseState und EditStatus beobachten** für Fortschritt und Validierung
2. **Typ-Informationen anzeigen** (JsonTypeDescriptor, JsonFieldDescriptor)
3. **Fehler und Warnungen visualisieren** für bessere Benutzererfahrung
4. **Parser-Lebenszyklus managen** (start/stop bei Dokument-Wechsel)
5. **UI-Updates batchen** für Performance

Die Integration ist **einfach**, da der Parser automatisch:
- Startet, wenn ein Modell gesetzt wird
- Knoten parst, wenn sie geändert werden
- Parent-Typen ableitet, wenn Feldnamen eindeutig sind
- Thread-safe arbeitet

Die UI muss nur **Zustände beobachten** und **Informationen anzeigen**. Komplexe Logik ist nicht erforderlich.

---

## 9. API-Korrekturen gegenüber der ursprünglichen Dokumentation

Bei der Analyse der tatsächlichen Implementierung (`D:\git_jsonCasted_edit` und `D:\git_jsonCasted`)
wurden folgende Abweichungen zwischen diesem Dokument und dem Code festgestellt.

### 9.1 `JsonParser.parseToEditTree` existiert nicht

**Dokument** (Abschnitt 4.4, 7.1): verwendet `JsonParser.parseToEditTree(jsonContent)` bzw.
`JsonParser.parseToEditTree(jsonFile)`.

**Tatsächlich**: Weder die Klasse `JsonParser` (als Editor-Einstieg) noch die Methode
`parseToEditTree` existieren im Editor-Core. Der korrekte Einstiegspunkt ist:

```java
// Korrekt: EditTree aus Datei erzeugen
import de.jare.jsoncasted.editor.core.JsonTreeConverter;

EditTree tree = JsonTreeConverter.fromJsonFile(file);
// oder
EditTree tree = JsonTreeConverter.fromJsonString(jsonString, rootName);
```

`JsonTreeConverter` nutzt intern `JsonParserService.parse(file, JsonDebugLevel.SIMPLE)`
aus `de.jare.jsoncasted.io.parserservice` und konvertiert die `JsonResource` in einen
`EditTree`. Die Klasse `JsonParser` existiert zwar in `de.jare.jsoncasted.io`, ist aber
ein generischer Parser, der `WoodResolution`/`JsonItem` zurückgibt — keinen `EditTree`.

### 9.2 `getParseState()` liegt auf `EditNodeAbstract`, nicht auf `EditNode`

**Dokument** (Abschnitt 3.2): ruft `node.getParseState()` auf einer Variable vom Typ `EditNode`
auf.

**Tatsächlich**: `EditNode` ist ein `sealed interface`. `getParseState()` ist dort **nicht**
deklariert — es existiert nur auf `EditNodeAbstract` (der abstrakten Basisklasse).

**Korrekt**:
```java
// Falsch (kompiliert nicht):
EditNode node = ...;
ParseState state = node.getParseState();  // Compile-Fehler

// Korrekt:
if (node instanceof EditNodeAbstract absNode) {
    ParseState state = absNode.getParseState();
}
```

`getEditStatus()` und `getEditMessage()` hingegen sind auf `EditNode` deklariert und
funktionieren auf jedem Knotentyp.

### 9.3 `getName()` liefert Kleinbuchstaben

`ParseState.getName()` liefert `"none"`, `"edited"`, `"pending"`, `"done"` (klein).
`ParseState.toString()` und `getLiteral()` liefern `"NONE"`, `"EDITED"`, `"PENDING"`, `"DONE"`.
Entsprechend für `EditStatus`: `getName()` = `"stateless"`, `"okay"`, `"warning"`, `"error"`;
`toString()` = `"STATELESS"`, `"OKAY"`, `"WARNING"`, `"ERROR"`.

Für Tooltips, die Großschreibung erwarten, `toString()` verwenden oder kapitalisieren.

### 9.4 Kein Push-Event bei Parse-Abschluss

**Dokument** (Abschnitt 2.2): "Der Parser aktualisiert automatisch: ParseState pro Knoten,
EditStatus und EditMessage".

**Tatsächlich**: Der Parser mutiert diese Werte direkt im Hintergrund-Thread. Es gibt
**keinen Callback** bei Parse-Abschluss. Das `TypeParserListener`-Interface feuert bei
Nutzeränderungen (Rename, Wert, Child hinzugefügt/entfernt), um Re-Parse **anzustoßen** —
nicht, wenn Parsen **fertig** ist. Zudem gibt `EditTree` nur **einen** `parserListener`-Slot
frei (`setParserListener`), den der `TypeParserService` selbst belegt.

**Konsequenz**: Die UI muss **Polling** betreiben (Swing `Timer`, ca. 200ms), um
Zustandsänderungen zu bemerken. Das entspricht den Szenarien 4.2 und 4.5, ist aber dort
als optionale Verzögerung beschrieben, nicht als zwingender Mechanismus.

### 9.5 Verifizierte API-Signaturen

Alle folgenden Methoden wurden gegen die Quelle verifiziert und existieren mit
passender Signatur:

| Klasse | Methode | Quelle |
|--------|--------|--------|
| `EditTree` | `setJsonModelDescriptor(JsonModelDescriptor)` | `EditTree.java:771` |
| `EditTree` | `startParserService()` | `EditTree.java:791` |
| `EditTree` | `stopParserService()` | `EditTree.java:803` |
| `EditTree` | `triggerFullReparse()` | `EditTree.java:1050` |
| `EditTree` | `close()` | `EditTree.java:1062` |
| `EditTree` | `isParserRunning()` | `EditTree.java:1079` |
| `EditTree` | `getParserService()` → `TypeParserService` | `EditTree.java:834` |
| `EditTree` | `getRoot()` → `EditNodeAbstract` | `EditTree.java:96` |
| `EditTree` | `getJsonModelDescriptor()` → `JsonModelDescriptor` | `EditTree.java:761` |
| `TypeParserService` | `requestParse(EditNodeAbstract)` | `TypeParserService.java:494` |
| `TypeParserService` | `requestFullParse()` | `TypeParserService.java:502` |
| `TypeParserService` | `getQueuedTaskCount()` → `int` | `TypeParserService.java:538` |
| `TypeParserService` | `getActiveThreadCount()` → `int` | `TypeParserService.java:519` |
| `TypeParserService` | `isRunning()` → `boolean` | `TypeParserService.java:174` |
| `EditNodeAbstract` | `getParseState()` → `ParseState` | `EditNodeAbstract.java:150` |
| `EditNodeAbstract` | `getLastParsedHash()` → `long` | `EditNodeAbstract.java:169` |
| `EditNode` | `getEditStatus()` → `EditStatus` | `EditNode.java:162` |
| `EditNode` | `getEditMessage()` → `String` | `EditNode.java:169` |
| `EditNodeObject` | `getJsonType()` → `JsonTypeDescriptor` | `EditNodeObject.java:182` |
| `EditNodeProperty` | `getJsonField()` → `JsonFieldDescriptor` | `EditNodeProperty.java:176` |
| `JsonTypeDescriptor` | `getTypeName()` → `String` | `JsonTypeDescriptor.java:72` |
| `JsonTypeDescriptor` | `getFields()` → `List<JsonFieldDescriptor>` | `JsonTypeDescriptor.java:169` |
| `JsonFieldDescriptor` | `getFieldName()` → `String` | `JsonFieldDescriptor.java:83` |
| `JsonFieldDescriptor` | `getTypeName()` → `String` | `JsonFieldTypeNote.java:38` (geerbt) |
| `JsonFieldDescriptor` | `isRequired()` → `boolean` | `JsonFieldDescriptor.java:94` |
| `JsonModelDescriptor` | `getType(String)` → `JsonTypeDescriptor` | `JsonModelDescriptor.java:145` |

---

## 10. Integrationsplan für WoodJsonJack

Dieser Plan beschreibt, wie der On-the-Fly Type Parser in die WoodJsonJack-Swing-UI
(`D:\git_WoodJsonJack`) integriert wird. Die Core-Bibliothek liegt in
`D:\git_jsonCasted_edit` (Editor-Core) und `D:\git_jsonCasted` (Model/Parser).

### 10.0 Architektur-Überblick

```
D:\git_jsonCasted          Model- und Parser-Klassen (JsonModel, JsonModelDescriptor, JsonParser)
        │
D:\git_jsonCasted_edit      Editor-Core (EditTree, EditNode, TypeParserService, ParseState, EditStatus)
        │
D:\git_WoodJsonJack         Swing-UI (JackEditTree, JsonJackTreeCellRenderer, JackTreeModel, ...)
```

```
Load       JackMainActions.loadJsonFile
             EditTree tree = JsonTreeConverter.fromJsonFile(file)     ← kein Descriptor gesetzt
             WoodResolution resolution = JsonParser.parse(file, definition, rootClass)
             descriptionTrees geladen (nur als EditTree, ohne Descriptor-Zuweisung)
             LÜCKE A: tree.setJsonModelDescriptor(...) fehlt  → Parser startet nie

Render     JsonJackTreeCellRenderer.getTreeCellRendererComponent
             liest nur getTypeKey(); kein ParseState, kein direktes EditStatus
             LÜCKE B: keine Icons, keine Tooltips, kein EditStatus-Farbe

Refresh    JackEditTree.UndoRedoListenerImpl
             VORHANDEN: handleRebuildNode / handleUpdatedNode (per editId+Range)
             LÜCKE C: kein Timer, der ParseState/EditStatus-Änderungen abholt

Lifecycle  WoodWindow / JackEditTree
             LÜCKE D: kein stopParserService()/close() bei Tab-Wechsel/Schließen
```

### Phase 0 — Descriptor-Beschaffung (gelöst, vor Phase 1)

**Ziel**: Eine `JsonModelDescriptor`-Instanz pro geladenem Dokument beschaffen.

**Erkenntnis aus Analyse**: `JsonTreeConverter.fromJsonFile(file)` erzeugt einen `EditTree`
**ohne** `JsonModelDescriptor`. Der Descriptor ist aktuell `null`. Auch WoodJsonJack ruft
nie `editTree.setJsonModelDescriptor(...)` auf. Somit startet der Parser bisher nie.

**Lösung**: Description-Dateien sind serialisierte `JsonModelDescriptor`s. Die Klasse
`JsonDescriptorDefinition` (in `de.jare.jsoncasted.model.descriptor.def`) ist eine
`JsonItemDefinition`, die genau dieses Format beschreibt — sie ist das Meta-Modell, das
definiert, wie ein `JsonModelDescriptor` in JSON serialisiert wird
(`JsonTypeDescriptor`, `JsonFieldDescriptor`, `JsonModelDescriptor` als Reflect-Klassen).

Der Weg: Description-Datei mit `JsonParser.parse(file, JsonDescriptorDefinition.getInstance(), ...)`
parsen, dann `JsonBuilder.buildInstance(model, true, answer)` liefert das
`JsonModelDescriptor`-Objekt. Dieses Pattern ist in WoodJsonJack bereits etabliert:
`SettingsService.java` (L73, L101, L128) nutzt exakt
`JsonBuilder.buildInstance(definition.getModel(), true, item)` für Settings/Themes/Projects.

**Konkrete Implementation** in `loadDescriptionFiles` (JackMainActions L155–186):

```java
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.def.JsonDescriptorDefinition;
import de.jare.jsoncasted.item.builder.JsonBuilder;

// In loadDescriptionFiles, Schleife über descriptionFileMap:
for (Map.Entry<String, String> entry : descriptionFileMap.entrySet()) {
    String modelName = entry.getKey();
    String filePath = entry.getValue();
    File descriptionFile = findDescriptionFile(filePath, originalFile);
    if (descriptionFile != null && descriptionFile.exists()) {
        // 1. Als EditTree laden (bestehend, für Baumansicht)
        EditTree descriptionTree = JsonTreeConverter.fromJsonFile(descriptionFile);
        descriptionTrees.put(modelName, descriptionTree);

        // 2. NEU: Als JsonModelDescriptor parsen (für On-the-Fly Parser)
        JsonDescriptorDefinition descDef = JsonDescriptorDefinition.getInstance();
        WoodResolution descResolution = JsonParser.parse(
                descriptionFile, descDef, descDef.getRootClass());
        if (descResolution != null && descResolution.getAnswer() != null) {
            JsonModelDescriptor descriptor = (JsonModelDescriptor) JsonBuilder.buildInstance(
                    descDef.getModel(), true, descResolution.getAnswer());
            // Descriptor für dieses Modell cachen (siehe Phase 1)
            descriptors.put(modelName, descriptor);
        }
    }
}
```

**Rückgabewert erweitern**: `loadDescriptionFiles` derzeit `Map<String, EditTree>`.
Entweder zusätzlich `Map<String, JsonModelDescriptor>` zurückgeben, oder die Methode
umbauen auf eine Struktur, die beides hält (z.B. ein `DescriptionBundle`-Record mit
`EditTree` und `JsonModelDescriptor` pro Modell).

**Welcher Descriptor für den Hauptbaum?**: Der Descriptor, dessen `modelName` zum
Hauptdokument passt. Falls mehrere Modelle referenziert werden, alle als Repo-Descriptoren
in den Haupt-Descriptor aufnehmen (`descriptor.addRepoDescriptor(synonym, repoDesc)`)
oder den passenden auswählen. Die Zuordnung läuft über `descriptionFileMap`
(Modellname → Dateipfad).

**Status**: Gelöst. Kein Blocker mehr. `JsonDescriptorDefinition` ist genau die
`JsonItemDefinition` für Description-Dateien.

**Eingriffspunkt**: `JackMainActions.loadDescriptionFiles` (L155–186).

### Phase 1 — Descriptor setzen, Parser starten (LÜCKE A)

**Ziel**: `JsonModelDescriptor` auf den `EditTree` setzen, damit der `TypeParserService`
startet und Typen auflöst.

**Eingriffspunkte**:
- `JackMainActions.loadJsonFile` (L48–76): nach `JsonTreeConverter.fromJsonFile(file)`
  und nach Descriptor-Beschaffung aus Phase 0.
- `WoodWindow.addEditorTab` (L232–244): falls der Descriptor erst nach dem Tab-Aufbau
  verfügbar ist.

**Änderung**:
```java
// In JackMainActions.loadJsonFile, nach Schritt 3 (loadDescriptionFiles):
// descriptors ist die Map aus Phase 0 (Modellname → JsonModelDescriptor)
JsonModelDescriptor descriptor = descriptors.get(modelName);  // passendes Modell
if (descriptor != null) {
    tree.setJsonModelDescriptor(descriptor);
}
```

**Verhalten beim Setzen** (`EditTree.java:771–784`):
1. Speichert `jsonModelDescriptor`.
2. Ruft `assignTypesFromModel()` — weist allen Knoten initial Typen zu.
3. Ruft `startParserService()` — startet den Hintergrund-Parser.
4. Markiert alle Knoten `ParseState.EDITED` und fügt Root zur Parse-Queue hinzu.
5. Parser arbeitet Queue ab, setzt pro Knoten `ParseState.PENDING` → `DONE` und
   `EditStatus`/`EditMessage`.

**Descriptor cachen**: im `JackTreeModel` (bereits über `getJsonModelDescriptor()`
als passthrough vorhanden) und ggf. in `WoodWindow` für spätere Reparse-Aktionen.
`JsonJackAttrTableModel` (L31, L193) pullt den Descriptor bereits über
`editor.getModel().getJsonModelDescriptor()` — sobald er auf dem `EditTree` gesetzt ist,
funktioniert diese existing-Verkettung automatisch.

**Mehrere Description-Modelle**: Falls mehrere Description-Dateien existieren, pro Modell
einen Descriptor bauen und den zum Hauptdokument passenden setzen. Die Zuordnung läuft
über `descriptionFileMap` (Modellname → Dateipfad, JackMainActions L162–177).

**Abhängigkeit**: Phase 0 muss vorher gelöst sein.

### Phase 2 — Renderer: ParseState & EditStatus sichtbar machen (LÜCKE B)

**Ziel**: Der Baum zeigt Parse-Fortschritt und Validierungsstatus farblich und per Tooltip an.

**Eingriffspunkt**: `JsonJackTreeCellRenderer.getTreeCellRendererComponent` (L38–84).

**Ist-Zustand**: Zwei-Label-Panel (`editLabel` = Name, `infoLabel` = Wert). Farbe via
`WoodSettings.INSTANCE.getShownTheme().getColor("light." + data.getTypeKey())`. Keine Icons,
keine Tooltips. `EditStatus` wird nicht direkt gelesen — nur `getTypeKey()` (object/property/array).

**Vorhandene Infrastruktur**: `AScheme` (settings/theme/AScheme.java L17–27) definiert
bereits Color-Keys pro `EditStatus`:
```
light.fore.OKAY    light.fore.WARNING    light.fore.ERROR
dark.fore.OKAY     dark.fore.WARNING     dark.fore.ERROR
```
Diese können direkt genutzt werden.

#### Phase 2a — EditStatus-Farbe und Tooltip (ohne Icons)

**Änderung in `getTreeCellRendererComponent`**:

```java
if (data != null) {
    editLabel.setText(data.getName());
    String foreKey = "light." + data.getTypeKey();
    editLabel.setForeground(WoodSettings.INSTANCE.getShownTheme().getColor(foreKey));
    infoLabel.setText(data.rightString() + " ");

    // NEU: EditStatus-Farbe (überlagert TypeKey-Farbe bei WARNING/ERROR)
    EditStatus status = data.getEditStatus();
    if (status == EditStatus.ERROR || status == EditStatus.WARNING) {
        String statusKey = "light.fore." + status.getLiteral();
        editLabel.setForeground(WoodSettings.INSTANCE.getShownTheme().getColor(statusKey));
    }

    // NEU: Tooltip
    panel.setToolTipText(buildTooltip(data));
}
```

```java
private String buildTooltip(EditNode data) {
    StringBuilder sb = new StringBuilder("<html>");
    sb.append("<b>").append(escapeHtml(data.getName())).append("</b><br>");
    if (data instanceof EditNodeAbstract absNode) {
        sb.append("Parse: ").append(absNode.getParseState().getName()).append("<br>");
    }
    sb.append("Status: ").append(data.getEditStatus().getName());
    String msg = data.getEditMessage();
    if (msg != null && !msg.isEmpty()) {
        sb.append(" - ").append(escapeHtml(msg));
    }
    // Optional: Typ-Info
    if (data instanceof EditNodeObject objNode && objNode.getJsonType() != null) {
        sb.append("<br>Typ: ").append(escapeHtml(objNode.getJsonType().getTypeName()));
    } else if (data instanceof EditNodeProperty propNode && propNode.getJsonField() != null) {
        sb.append("<br>Feld: ").append(escapeHtml(propNode.getJsonField().getFieldName()));
    }
    sb.append("</html>");
    return sb.toString();
}
```

**Imports hinzufügen**: `EditStatus`, `EditNodeAbstract`, `EditNodeObject`,
`EditNodeProperty`, `ParseState` (alle aus `de.jare.jsoncasted.editor.core`).

**Keine Logikänderung** an `getTypeKey()` — nur additive Ergänzung. Bestehende Farbgebung
bleibt als Fallback (bei `STATELESS`/`OKAY`).

#### Phase 2b — ParseState-Icon (optional, später)

`EDITED`/`PENDING` → kleiner Indikator (z.B. gelber Punkt / Spinner-Symbol);
`DONE`/`NONE` → neutral. Aktuell hat der Renderer keine Icons — Icon-Ressourcen müssen
beschafft und ans Panel gehängt werden. Empfehlung: erst 2a (Farben+Tooltips), dann 2b.

**Abhängigkeit**: Phase 1 (sonst ist `EditStatus` immer `STATELESS` und `ParseState`
immer `NONE`). Phase 3 (sonst bleibt die Anzeige statisch — nur der initiale State wird
gezeichnet, Updates werden nicht sichtbar).

### Phase 3 — Reaktives Refresh per Timer (LÜCKE C)

**Ziel**: Zustandsänderungen des Hintergrund-Parsers werden in der UI sichtbar, ohne
dass der Nutzer manuell aktualisieren muss.

**Begründung**: Da es keinen Push-Event bei Parse-Abschluss gibt (siehe Abschnitt 9.4),
muss die UI pollen.

**Eingriffspunkt**: `JackEditTree` — neuer `javax.swing.Timer` pro sichtbarem
`JackEditTree`.

**Mechanismus**:

1. **Timer**: `javax.swing.Timer` mit ca. 200ms Intervall, läuft im EDT.
2. **Was pollen**: Über `JackTreeModel` alle sichtbaren `EditNodeAbstract` durchlaufen
   und prüfen, ob sich `getParseState()` oder `getEditStatus()` oder `getEditMessage()`
   seit dem letzten Tick geändert hat.
3. **Was updaten**: Die existierenden Helfer `handleRebuildNode`/`handleUpdatedNode`
   in `JackEditTree.UndoRedoListenerImpl` (L296–330) nutzen. `handleUpdatedNode`
   (`JackEditTree.java:315–330`) ruft `model.nodeChanged(swingNode)` auf, was den Renderer
   für diesen Knoten neu triggert → Phase 2 zeigt dann den neuen State.

**Effizienz** (Dokument §5.2):

- **Dirty-Set**: Pro Knoten den zuletzt gesehenen `(parseState, editStatus, editMessage)`
  cachen. Nur veränderte Knoten re-painten. Alternativ: nur Knoten durchlaufen, deren
  `getParseState() != DONE` (sind nach Edit-Pausen wenige). Sobald `DONE` erreicht,
  aus dem Beobachtungsset nehmen.
- **Nicht** bei jedem Tick alle Knoten neu zeichnen — `nodeChanged()` nur für
  tatsächlich veränderte aufrufen.
- **Batching**: Mehrere veränderte Knoten pro Tick sammeln und in einem
  `DefaultTreeModel`-Update block zusammenfassen.

**Thread-Safety**: `ParseState`/`EditStatus` werden vom Parser-Thread geschrieben, vom
EDT nur gelesen — das ist sicher (Dokument §5.1). Gelegentlich veraltete Anzeigen
werden beim nächsten Tick korrigiert — kosmetisch, akzeptabel.

**Tab-Sichtbarkeit**: Timer stoppen, wenn das Dokument-Tab nicht sichtbar ist
(`WoodWindow.centerTabs` `ChangeListener`), um CPU zu sparen.

**Skizze**:

```java
// In JackEditTree, neues Feld:
private Timer parseRefreshTimer;
private final Map<Long, ParseState> lastParseStates = new HashMap<>();  // editId → state

// Im Konstruktor (nach jtree-Setup):
parseRefreshTimer = new Timer(200, e -> refreshParseStates());
parseRefreshTimer.start();

private void refreshParseStates() {
    JackTreeModel model = getModel();
    if (model == null || model.getEditTree() == null) return;
    EditNodeAbstract root = model.getEditTree().getRoot();
    if (root == null) return;
    // Rekursiv prüfen:
    checkNodeChanged(root, model);
}

private void checkNodeChanged(EditNodeAbstract node, JackTreeModel model) {
    Long id = node.getEditId();
    ParseState current = node.getParseState();
    ParseState last = lastParseStates.get(id);
    if (last != current) {
        lastParseStates.put(id, current);
        // Swing-Knoten finden und re-painten:
        DefaultMutableTreeNode swingNode = model.findNodeByIdAndRange(
                node.getEditId(), node.getLeftRange(), node.getTimesRange());
        if (swingNode != null) {
            model.nodeChanged(swingNode);
        }
    }
    for (EditNode child : node.getChildren()) {
        if (child instanceof EditNodeAbstract absChild) {
            checkNodeChanged(absChild, model);
        }
    }
}
```

**Abhängigkeit**: Phase 1 (ohne Parser keine Zustandsänderungen). Phase 2 (ohne Renderer
bleiben Zustandsänderungen unsichtbar). Phase 2 und 3 können parallel/iterativ erfolgen.

### Phase 4 — Lebenszyklus & Controller-Fan-Out (LÜCKE D)

**Ziel**: Parser und Timer beim Schließen/Wechseln von Dokumenten sauber beenden.

**Ist-Zustand**: `WoodWindow` und `JackEditTree` haben keine `close()`/`dispose()`-Methoden.
Tabs werden über `centerTabs` (JTabbedPane) verwaltet. Es gibt kein Tab-Schließen und kein
`WindowListener` für `windowClosing`.

#### Phase 4a — Timer an Lebenszyklus binden

- Timer starten, wenn Tab angelegt wird (`WoodWindow.addEditorTab`).
- Timer stoppen, wenn Tab geschlossen oder versteckt wird.
- `WoodWindow` benötigt einen `ChangeListener` auf `centerTabs`, um Timer zu pausieren,
  wenn Tab nicht sichtbar.

#### Phase 4b — Parser stoppen beim Schließen

- In einer neuen `closeEditorTab`-Methode (oder `dispose`): `editTree.stopParserService()`
  und `editTree.close()` aufrufen (Dokument §3.1).
- Aktuell wird `close()` nirgends gerufen — bei Tab-Schließen oder `windowClosing` nachrüsten.

**Eingriffspunkte**:
- `WoodWindow`: Tab-Schließen-Routine (neu), `WindowListener.windowClosing` (neu).
- `JackEditTree`: `dispose()`-Override (neu), Timer-Feld + `stopTimer()`.

#### Phase 4c — Controller-Fan-Out (optional)

In `JackMasterControl` einen Parser-Event-Kanal analog `UndoRedoListener` vorsehen, falls
später mehrere `JackEditTree`-Instanzen (links/rechts, mehrere Tabs) synchron gepollt/
aktualisiert werden sollen. Für Phase 1–3 nicht zwingend, da jeder `JackEditTree` seinen
eigenen Timer halten kann.

### Phase 5 — Manuelle Reparse-Aktion (optional)

**Ziel**: Benutzer kann volles Re-Parsing manuell auslösen (z.B. nach Modelländerungen).

**Eingriffspunkt**: `JackMainMenu` — neuer Menüeintrag "Re-parse Types".

```java
// In JackMainActions oder als eigene Action:
editTree.triggerFullReparse();
```

Niedrige Priorität, da Edit-Änderungen ohnehin automatisch re-parst werden. Nützlich nur
nach Modelländerungen oder als Debug-Hilfe.

### 10.1 Reihenfolge & Abhängigkeiten

```
Phase 0 (Descriptor-Beschaffung)           ← gelöst (JsonDescriptorDefinition)
   │
   ▼
Phase 1 (setJsonModelDescriptor)           ← Parser startet
   │
   ├──► Phase 2 (Renderer: Status/Tooltip) ← sichtbar
   │
   └──► Phase 3 (Timer-Refresh)            ← nötig, damit Phase 2 live wird
            │
            ▼
        Phase 4 (Lifecycle)                ← aufräumen
            │
            ▼
        Phase 5 (manueller Reparse)        ← optional
```

- Phase 1 ist die harte Voraussetzung für alles.
- Phase 2 und 3 können parallel/iterativ erfolgen; ohne Phase 3 bleibt Phase 2 statisch
  (nur der initiale State wird gezeichnet).
- Phase 4 sollte zeitnah nach Phase 3 folgen, um Timer/Parser nicht leerlaufen zu lassen.

### 10.2 Datei-Übersicht: was wo geändert wird

| Phase | Datei (WoodJsonJack) | Art der Änderung |
|-------|----------------------|-------------------|
| 0 | `ui/JackMainActions.java` | Descriptor-Beschaffung in `loadDescriptionFiles`: `JsonParser.parse` + `JsonBuilder.buildInstance` mit `JsonDescriptorDefinition` (neue Logik, plus neue Imports) |
| 1 | `ui/JackMainActions.java` | `tree.setJsonModelDescriptor(descriptor)` in `loadJsonFile` (1 Zeile + Descriptor-Variable) |
| 2a | `ui/JsonJackTreeCellRenderer.java` | `EditStatus`-Farbe, `buildTooltip`, neue Imports (additiv) |
| 2b | `ui/JsonJackTreeCellRenderer.java` | Icon-Support (neu, optional) |
| 3 | `ui/JackEditTree.java` | Timer-Feld, `refreshParseStates`, `checkNodeChanged` (neu) |
| 4a | `ui/WoodWindow.java` | `ChangeListener` auf `centerTabs`, Timer-Pause (neu) |
| 4b | `ui/WoodWindow.java`, `ui/JackEditTree.java` | `closeEditorTab`, `dispose`, `stopParserService` (neu) |
| 5 | `ui/JackMainMenu.java` | Menüeintrag "Re-parse Types" (neu, optional) |

---

## 11. Phasen-Status

| Phase | Beschreibung | Status | Ausgearbeitet | Blocker |
|-------|-------------|--------|---------------|---------|
| **0** | Descriptor-Beschaffung | **Gelöst** | **Ja** — `JsonDescriptorDefinition` + `JsonBuilder.buildInstance`, konkrete Code-Skizze, Pattern aus `SettingsService` bestätigt | Keiner |
| **1** | Descriptor setzen, Parser starten | Bereit | **Ja** — `setJsonModelDescriptor`, Eingriffspunkt und Verhalten dokumentiert | Keiner (Phase 0 gelöst) |
| **2a** | Renderer: EditStatus-Farbe + Tooltip | Bereit | **Ja** — konkrete Code-Skizze, Imports, AScheme-Keys identifiziert | Phase 1 |
| **2b** | Renderer: ParseState-Icon | Optional | **Nein** — Icon-Ressourcen nicht definiert, nur Ansatz | — |
| **3** | Timer-Refresh | Bereit | **Ja** — konkrete Code-Skizze, Dirty-Set-Strategie, Thread-Safety, Effizienz | Phase 1, 2 |
| **4a** | Timer an Lebenszyklus | Bereit | **Ja** — Eingriffspunkte identifiziert, aber keine Tab-Schließen-Routine vorhanden (muss neu gebaut werden) | — |
| **4b** | Parser stoppen beim Schließen | Bereit | **Ja** — Methoden identifiziert (`stopParserService`, `close`), aber keine `close`/`dispose`-Hooks vorhanden (muss neu gebaut werden) | — |
| **4c** | Controller-Fan-Out | Optional | Teilweise — nur Ansatz, nicht zwingend für Phase 1–3 | — |
| **5** | Manuelle Reparse-Aktion | Optional | **Ja** — Einzeiler `triggerFullReparse`, Menü-Eintrag | — |

### Bewertung pro Phase

**Phase 0 — Ausreichend ausgearbeitet (gelöst)**:
Description-Dateien sind serialisierte `JsonModelDescriptor`s. `JsonDescriptorDefinition`
(in `de.jare.jsoncasted.model.descriptor.def`) ist die `JsonItemDefinition`, die dieses
Format beschreibt. Der Weg: `JsonParser.parse(file, JsonDescriptorDefinition.getInstance(), rootClass)`
→ `JsonBuilder.buildInstance(model, true, answer)` liefert das `JsonModelDescriptor`-Objekt.
Das Pattern `JsonBuilder.buildInstance(definition.getModel(), true, item)` ist in WoodJsonJack
bereits in `SettingsService.java` (L73, L101, L128) etabliert. Konkrete Code-Skizze steht
im Plan. Kein Blocker mehr.

**Phase 1 — Ausreichend ausgearbeitet**:
Eingriffspunkt (`JackMainActions.loadJsonFile` nach `loadDescriptionFiles`), die Zeile
`tree.setJsonModelDescriptor(descriptor)` mit Descriptor aus der Phase-0-Map, und das
Auto-Start-Verhalten sind dokumentiert. Die existing Verkettung `JsonJackAttrTableModel` →
`JackTreeModel.getJsonModelDescriptor()` → `editTree.getJsonModelDescriptor()` funktioniert
automatisch, sobald der Descriptor gesetzt ist.

**Phase 2a — Ausreichend ausgearbeitet**:
Konkrete Code-Skizze für `buildTooltip` und `EditStatus`-Farbe. `AScheme`-Color-Keys
identifiziert (`light.fore.<status>`). `getParseState()`-Cast auf `EditNodeAbstract`
berücksichtigt. `escapeHtml`-Hilfsfunktion skizziert. Additiv zur existing Logik —
kein Breaking Change.

**Phase 2b — Nicht ausreichend ausgearbeitet**:
Nur Ansatz ("Icon bei EDITED/PENDING"). Icon-Ressourcen sind nicht definiert, kein
Mechanismus beschrieben, wie Icons ins Panel eingefügt werden. Als optional markiert —
kann später ergänzt werden.

**Phase 3 — Ausreichend ausgearbeitet**:
Konkrete Code-Skizze mit `Timer`, `lastParseStates`-Map, rekursiver `checkNodeChanged`,
Wiederverwendung von `model.findNodeByIdAndRange` und `model.nodeChanged`. Effizienz-
Strategie (Dirty-Set, nur `!= DONE` beobachten) und Thread-Safety diskutiert. Tab-
Sichtbarkeits-Pause erwähnt.

**Phase 4a — Ausreichend ausgearbeitet**:
Eingriffspunkte identifiziert (`WoodWindow.centerTabs`, `ChangeListener`). Allerdings
existiert bisher keine Tab-Schließen-Routine — diese muss neu gebaut werden. Der Plan
nennt das, aber der Code-Entwurf dafür fehlt.

**Phase 4b — Ausreichend ausgearbeitet**:
Methoden (`stopParserService`, `close`) und Eingriffspunkte (`windowClosing`, `dispose`)
identifiziert. Wie bei 4a: die Hooks existieren noch nicht und müssen neu gebaut werden.
Der Plan nennt das explizit.

**Phase 4c — Teilweise ausgearbeitet**:
Nur Ansatz ("Parser-Event-Kanal analog `UndoRedoListener`"). Als optional markiert —
nicht zwingend für Phase 1–3, da jeder `JackEditTree` seinen eigenen Timer hält.

**Phase 5 — Ausreichend ausgearbeitet**:
Einzeiler `editTree.triggerFullReparse()`, Menü-Eintrag. Trivial, optional.
