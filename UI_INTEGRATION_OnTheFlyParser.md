# UI-Integration: On-the-Fly Type Parser

**Zielgruppe**: UI-Entwickler, die den On-the-Fly Type Parser in die Benutzeroberfläche integrieren
**Status**: Fertig zur Implementation
**Abhängigkeiten**: `EditTree`, `TypeParserService`, `ParseState`, `EditStatus`

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
