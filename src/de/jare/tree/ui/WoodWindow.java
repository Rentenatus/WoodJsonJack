/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditStatus;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.tree.control.JackMasterControl;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import de.jare.tree.control.model.JackTreeModel;
import de.jare.tree.settings.SettingsService;
import de.jare.tree.settings.WoodSettings;
import de.jare.tree.settings.WoodSettings;
import de.jare.tree.settings.theme.ThemeSuite;
import de.jare.tree.ui.settings.PreferencesDialog;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

public class WoodWindow extends JFrame {

    // Bottom tab constants
    private static final String TAB_ATTRIBUTES = "Attributes";
    private static final String TAB_JACK_UNDO = "Jack Undo";
    private static final String TAB_KI_ASSISTANT = "KI Assistant";
    private static final String TAB_JACK_CLIPBOARD = "Jack Clipboard";
    private static final String TAB_SEARCH_RESULT = "Search result";

    private final JackMasterControl jackmaster;
    private final JTabbedPane centerTabs;
    private final JackEditTreeContainer editorTree1;
    private final JackEditTreeContainer editorTree2;
    private final SettingsService settingsService;
    private final WoodSettings settings;
    private final ThemeSuite themeSuite;
    private final List<JackEditTreeContainer> editorTrees = new ArrayList<>();
    private PreferencesDialog preferencesDialog;
    private JackClipboardPanel jackClipboardPanel;
    private JackUndoPanel jackPanel;
    private SearchResultPanel searchResultPanel;
    private final TreeFocusListener treeFocusListener;

    public WoodWindow() {
        settingsService = new SettingsService();
        settings = settingsService.loadWoodSettings(false);
        themeSuite = settingsService.loadThemeSuite(false);
        settings.useThemeSuite(themeSuite);
        jackmaster = new JackMasterControl();

        setTitle("Wood Json Studio");
        setSize(1200, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JackMainMenu bar = new JackMainMenu(this, jackmaster);
        setJMenuBar(bar);

        JSplitPane horizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        // Left: project tree
        WoodProjektTree projectTree = new WoodProjektTree("Project", "Node1", "Node2", "Folder");
        projectTree.setPreferredSize(new Dimension(250, 0));
        horizontalSplit.setLeftComponent(new JScrollPane(projectTree));

        // Center: editor tabs + upper toolbar
        centerTabs = new JTabbedPane();

        editorTree1 = new JackEditTreeContainer(jackmaster, "Root1", "Scene1", "Character1", "Scene2", "Character2", "Scene3", "Character3");
        editorTree2 = new JackEditTreeContainer(jackmaster, "Root2", "Scene4", "Character4", "Scene5", "Character6", "Scene7");

        editorTrees.add(editorTree1);
        editorTrees.add(editorTree2);

        centerTabs.addTab("Tree Editor 1", new JScrollPane(editorTree1));
        centerTabs.addTab("Tree Editor 2", new JScrollPane(editorTree2));

        editorTree1.setReadonly(true);

        // Erstelle Jack Clipboard Panel
        jackClipboardPanel = new JackClipboardPanel(jackmaster.getClipboardManager(), editorTree2.getLeftTree());

        // obere Toolbar ueber den Editor-Tabs
        JPanel upperToolbar = new JackUpperToolbar(jackmaster);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(upperToolbar, BorderLayout.NORTH);
        centerPanel.add(centerTabs, BorderLayout.CENTER);

        horizontalSplit.setRightComponent(centerPanel);

        // Tab-Wechsel steuert aktiven Editor und pausiert Timer inaktiver Tabs
        centerTabs.addChangeListener(e -> {
            int idx = centerTabs.getSelectedIndex();
            for (int i = 0; i < editorTrees.size(); i++) {
                JackEditTreeContainer container = editorTrees.get(i);
                boolean active = (i == idx);
                container.getLeftTree().setParseTimerActive(active);
                container.getRightTree().setParseTimerActive(active);
            }
            if (idx >= 0 && idx < editorTrees.size()) {
                JackEditTreeContainer container = editorTrees.get(idx);
                jackmaster.setActiveEditor(container.getLeftTree(), this);
            }
        });

        treeFocusListener = new TreeFocusListener() {
            @Override
            public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
                if (editor == null) {
                    return;
                }
                // Find the tab containing this editor
                for (int i = 0; i < editorTrees.size(); i++) {
                    JackEditTreeContainer container = editorTrees.get(i);
                    if (container.getLeftTree() == editor || container.getRightTree() == editor) {
                        centerTabs.setSelectedIndex(i);
                        break;
                    }
                }
            }
        };

        // Editor-Wechsel steuert Tab-Auswahl
        jackmaster.addSelectionListener(7, treeFocusListener);

        // initial
        jackmaster.setActiveEditor(editorTree1.getLeftTree(), this);

        JackEditPopup jackPopup = new JackEditPopup(jackmaster);

        JackEditPopup.installOn(editorTree1.getLeftTree().getTree(), jackPopup);
        JackEditPopup.installOn(editorTree1.getRightTree().getTree(), jackPopup);

        JackEditPopup.installOn(editorTree2.getLeftTree().getTree(), jackPopup);
        JackEditPopup.installOn(editorTree2.getRightTree().getTree(), jackPopup);

        // Bottom: tabs + bottom toolbar
        JTabbedPane bottomTabs = new JTabbedPane();
        bottomTabs.addTab(TAB_ATTRIBUTES, createAttributesPanel());
        bottomTabs.addTab(TAB_JACK_UNDO, createJackUndoPanel());
        bottomTabs.addTab(TAB_KI_ASSISTANT, createKIAssistant());
        bottomTabs.addTab(TAB_JACK_CLIPBOARD, createJackClipboardPanel());
        bottomTabs.addTab(TAB_SEARCH_RESULT, createSearchResultPanel());

        SearchToolbar searchToolbar = new SearchToolbar(jackmaster);
        searchToolbar.setCurrentEditor(editorTree1.getLeftTree());

        // Register search result panel as TreeFocusListener to handle node selection
        jackmaster.addSelectionListener(6, searchResultPanel);

        // Register search listener to display results in Search result tab
        searchToolbar.addSearchListener(searchResultPanel);

        // Register search listener to switch to Search result tab when search is performed
        searchToolbar.addSearchListener((criteria, results) -> {
            bottomTabs.setSelectedIndex(bottomTabs.indexOfTab(TAB_SEARCH_RESULT));
        });

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(searchToolbar, BorderLayout.NORTH);
        bottomPanel.add(bottomTabs, BorderLayout.CENTER);

        verticalSplit.setTopComponent(horizontalSplit);
        verticalSplit.setBottomComponent(bottomPanel);

        horizontalSplit.setDividerLocation(300);
        verticalSplit.setDividerLocation(600);

        add(verticalSplit, BorderLayout.CENTER);

        // Properties an Selection-Orator h?ngen
        setLocationRelativeTo(null);
        setVisible(true);

        // Beim Schließen des Fensters alle Parser und Timer beenden
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                shutdownEditors();
            }
        });
    }

    private JTable attributesTable;
    private JsonJackAttrTableModel attributesModel;

    private JPanel createAttributesPanel() {
        attributesModel = new JsonJackAttrTableModel();
        jackmaster.addSelectionListener(attributesModel);
        attributesTable = new JTable(attributesModel);
        attributesTable.setFillsViewportHeight(true);
        attributesTable.getTableHeader().setVisible(true);

        // EditStatus-Farbe bei WARNING/ERROR fuer die Text-Spalten (Name, Value, Typ)
        attributesTable.setDefaultRenderer(String.class, new EditStatusCellRenderer());

        installAttrDetailButtonColumn();

        JPanel borderedPanel = new JPanel(new BorderLayout());
        borderedPanel.add(new JScrollPane(attributesTable), BorderLayout.CENTER);
        return borderedPanel;
    }

    /**
     * Installiert die "^"-Spalte (Spalte 3) der Attribut-Tabelle: ein als
     * Button gerendertes Feld, das bei Klick den Detail-Dialog oeffnet, sofern
     * der Attribut-Wert weder null noch leer ist.
     */
    private void installAttrDetailButtonColumn() {
        TableColumn buttonCol = attributesTable.getColumnModel().getColumn(3);
        buttonCol.setCellRenderer(new ButtonRenderer());
        buttonCol.setPreferredWidth(36);
        buttonCol.setMaxWidth(36);
        buttonCol.setMinWidth(28);
        buttonCol.setResizable(false);

        attributesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewCol = attributesTable.columnAtPoint(e.getPoint());
                int viewRow = attributesTable.rowAtPoint(e.getPoint());
                if (viewRow < 0 || viewCol < 0) {
                    return;
                }
                if (attributesTable.convertColumnIndexToModel(viewCol) != 3) {
                    return;
                }
                int modelRow = attributesTable.convertRowIndexToModel(viewRow);
                if (!attributesModel.isButtonEnabled(modelRow)) {
                    return;
                }
                openAttrDetailDialog(modelRow);
            }
        });
    }

    /**
     * Oeffnet den Attribut-Detail-Dialog fuer die angegebene Zeile.
     *
     * @param modelRow der Modellzeilenindex
     */
    private void openAttrDetailDialog(int modelRow) {
        JsonJackAttrTableModel.PropertyRow row = attributesModel.getRow(modelRow);
        if (row == null) {
            return;
        }
        EditNode node = attributesModel.getCurrentEditNode();
        String nodeName = (node != null && node.getName() != null) ? node.getName() : "";
        String nodePath = buildNodePath(node);
        String value = row.value() == null ? "" : row.value().toString();
        boolean editable = attributesModel.isAttributeEditable(modelRow);
        JsonJackAttrDetailDialog dialog = new JsonJackAttrDetailDialog(
                this, nodeName, nodePath, row.type(), row.name(), value, editable,
                newValue -> attributesModel.setValueAt(newValue, modelRow, 1));
        dialog.setVisible(true);
    }

    /**
     * Baut den Pfad eines Knotens, indem vom Knoten aus zu den Wurzeln
     * aufsteigend die Namen verkettet werden (Format: "a > b > c").
     *
     * @param node der Knoten
     * @return der Pfad oder ein leerer String, wenn der Knoten null ist
     */
    private String buildNodePath(EditNode node) {
        if (node == null) {
            return "";
        }
        List<String> names = new ArrayList<>();
        EditNode current = node;
        while (current != null) {
            String name = current.getName();
            names.add(name == null ? "" : name);
            current = current.getParent();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = names.size() - 1; i >= 0; i--) {
            if (sb.length() > 0) {
                sb.append(" > ");
            }
            sb.append(names.get(i));
        }
        return sb.toString();
    }

    /**
     * Renderer fuer die Text-Spalten der Attribut-Tabelle. Ueberlagert die
     * Vordergrundfarbe mit der EditStatus-Farbe des ausgewaehlten Knotens, wenn
     * dieser den Status ERROR oder WARNING hat – analog zum
     * JsonJackTreeCellRenderer.
     */
    private static class EditStatusCellRenderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            c.setForeground(table.getForeground());
            if (isSelected || !(table.getModel() instanceof JsonJackAttrTableModel model)) {
                return c;
            }
            int modelRow = table.convertRowIndexToModel(row);
            JsonJackAttrTableModel.PropertyRow attrRow = model.getRow(modelRow);
            if (attrRow == null || !"|edit status".equals(attrRow.name())) {
                return c;
            }
            EditNode node = model.getCurrentEditNode();
            if (node == null) {
                return c;
            }
            EditStatus status = node.getEditStatus();
            Color statusColor = (status == EditStatus.ERROR
                    || status == EditStatus.WARNING
                    || status == EditStatus.OKAY)
                            ? (WoodSettings.INSTANCE.getShownTheme()
                                    .getColor("light.fore." + status.getLiteral()))
                            : null;
            if (statusColor != null) {
                c.setForeground(statusColor);
            }
            return c;
        }
    }

    /**
     * Renderer, der in der "^"-Spalte einen JButton anzeigt. Der Button ist nur
     * aktiviert, wenn der Attribut-Wert nicht null oder leer ist.
     */
    private static class ButtonRenderer extends JButton implements TableCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            setText("^");
            int modelRow = table.convertRowIndexToModel(row);
            if (table.getModel() instanceof JsonJackAttrTableModel model) {
                setEnabled(model.isButtonEnabled(modelRow));
            } else {
                setEnabled(false);
            }
            return this;
        }
    }

    private JPanel createJackUndoPanel() {
        jackPanel = new JackUndoPanel(jackmaster);
        return jackPanel;
    }

    private JPanel createKIAssistant() {
        JPanel borderedPanel = new JPanel(new BorderLayout());
        JTextArea prompt = new JTextArea(5, 20);
        prompt.setText("KI-Prompt hier...");
        JButton askBtn = new JButton("KI fragen");
        borderedPanel.add(new JScrollPane(prompt), BorderLayout.CENTER);
        borderedPanel.add(askBtn, BorderLayout.SOUTH);
        return borderedPanel;
    }

    private JPanel createJackClipboardPanel() {
        return jackClipboardPanel;
    }

    private JPanel createSearchResultPanel() {
        searchResultPanel = new SearchResultPanel(jackmaster);
        return searchResultPanel;
    }

    public void openPreferences() {
        if (preferencesDialog == null) {
            preferencesDialog = new PreferencesDialog(this, settings, themeSuite);
        }

        preferencesDialog.setVisible(true);
        preferencesDialog.toFront();
    }

    /**
     * Adds a new editor tab with the loaded JSON content.
     *
     * @param file the JSON file that was loaded
     * @param tree the Tree with the loaded content
     */
    public void addEditorTab(File file, EditTree tree) {
        JackEditTreeContainer newContainer = new JackEditTreeContainer(
                jackmaster,
                file.getName(),
                file.getName()
        );
        // Setze das geladene EditTree im linken Baum
        JackTreeModel model = new JackTreeModel(tree);
        newContainer.getLeftTree().getTree().setModel(model);
        // newContainer.getLeftTree().getModel().rebuildFromDomain();

        addEditorTab(file, newContainer);
    }

    /**
     * Adds a new editor tab with the loaded JSON content.
     *
     * @param file the JSON file that was loaded
     * @param treeContainer the JackEditTreeContainer with the loaded content
     */
    private void addEditorTab(File file, JackEditTreeContainer treeContainer) {
        editorTrees.add(treeContainer);
        String tabTitle = file != null ? file.getName() : "New Editor";
        JScrollPane scrollPane = new JScrollPane(treeContainer);
        centerTabs.addTab(tabTitle, scrollPane);
        centerTabs.setSelectedComponent(scrollPane);

        // Add popup to the new tree
        JackEditPopup jackPopup = new JackEditPopup(jackmaster);
        JackEditPopup.installOn(treeContainer.getLeftTree().getTree(), jackPopup);
        JackEditPopup.installOn(treeContainer.getRightTree().getTree(), jackPopup);

        // Set initial active editor
        jackmaster.setActiveEditor(treeContainer.getLeftTree(), this);
    }

    /**
     * Stops all parse refresh timers and parser services for all editor tabs.
     * Called when the window is closing.
     */
    private void shutdownEditors() {
        for (JackEditTreeContainer container : editorTrees) {
            container.getLeftTree().stopParseRefreshTimer();
            container.getRightTree().stopParseRefreshTimer();
            EditTree leftTree = container.getLeftTree().getModel().getEditTree();
            if (leftTree != null && leftTree.isParserRunning()) {
                leftTree.stopParserService();
                leftTree.close();
            }
        }
    }

    /**
     * Shows an error dialog with the given message.
     *
     * @param message the error message to display
     */
    public void showErrorDialog(String message) {
        JOptionPane.showMessageDialog(this,
                message,
                "Fehler",
                JOptionPane.ERROR_MESSAGE);
    }

}
