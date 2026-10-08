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
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.JsonWriteException;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import static de.jare.jsoncasted.lang.JsonTerms.THIS_SYNONYM;
import de.jare.tree.control.JackMasterControl;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import de.jare.tree.control.model.JackTreeModel;
import de.jare.tree.settings.SettingsService;
import de.jare.tree.settings.WoodSettings;
import de.jare.tree.settings.theme.LafCatalog;
import de.jare.tree.settings.theme.ThemeSuite;
import de.jare.tree.ui.settings.PreferencesDialog;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

public class WoodWindow extends JFrame {

    // Bottom tab constants
    private static final String TAB_ATTRIBUTES = "Attributes";
    private static final String TAB_JACK_UNDO = "Jack Undo";
    private static final String TAB_KI_ASSISTANT = "AI Assistant";
    private static final String TAB_JACK_CLIPBOARD = "Jack Clipboard";
    private static final String TAB_SEARCH_RESULT = "Search result";
    private static final String TAB_FAST_LOG = "Fast Log";

    private final JackMasterControl jackmaster;
    private final JTabbedPane centerTabs;
    private final JackEditTreeContainer editorTree1;
    private final JackEditTreeContainer editorTree2;
    private final SettingsService settingsService;
    private final WoodSettings settings;
    private final ThemeSuite themeSuite;
    private final List<JackEditTreeContainer> editorTrees = new ArrayList<>();
    private final Map<JackEditTreeContainer, File> editorFiles = new HashMap<>();
    private PreferencesDialog preferencesDialog;
    private final List<Consumer<Boolean>> darkModeListeners = new ArrayList<>();
    private JackClipboardPanel jackClipboardPanel;
    private JackUndoPanel jackPanel;
    private SearchResultPanel searchResultPanel;
    private JackFastLogPanel fastLogPanel;
    private final TreeFocusListener treeFocusListener;

    public WoodWindow() {
        settingsService = new SettingsService();
        WoodSettings loaded = settingsService.loadWoodSettings(false);
        WoodSettings.INSTANCE.adopt(loaded);
        settings = WoodSettings.INSTANCE;
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

        // create the Jack clipboard panel
        jackClipboardPanel = new JackClipboardPanel(jackmaster.getClipboardManager(), editorTree2.getLeftTree());

        // upper toolbar above the editor tabs
        JPanel upperToolbar = new JackUpperToolbar(jackmaster);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(upperToolbar, BorderLayout.NORTH);
        centerPanel.add(centerTabs, BorderLayout.CENTER);

        horizontalSplit.setRightComponent(centerPanel);

        // tab change controls the active editor and pauses timers of inactive tabs
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
        bottomTabs.addTab(TAB_FAST_LOG, createFastLogPanel());
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

        // Register parse problems listener to display problems in the Search result tab
        jackmaster.addParseProblemsListener(searchResultPanel);

        // Switch to Search result tab when parse problems are published
        jackmaster.addParseProblemsListener((source, nodes) -> {
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

        // stop all parsers and timers when the window closes
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

        // edit status color at WARNING/ERROR for the text columns (Name, Value, Type)
        attributesTable.setDefaultRenderer(String.class, new EditStatusCellRenderer());

        installAttrDetailButtonColumn();

        JPanel borderedPanel = new JPanel(new BorderLayout());
        borderedPanel.add(new JScrollPane(attributesTable), BorderLayout.CENTER);
        return borderedPanel;
    }

    /**
     * Installs the "^" column (column 3) of the attribute table: a field
     * rendered as a button that opens the detail dialog on click when the
     * attribute value is neither null nor empty.
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
     * Opens the attribute detail dialog for the given row.
     *
     * @param modelRow the model row index
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
     * Builds the path of a node by concatenating the names upwards from
     * the node to the roots (format: "a > b > c").
     *
     * @param node the node
     * @return the path, or an empty string when the node is null
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
     * Renderer for the text columns of the attribute table. Overlays the
     * foreground color with the edit status color of the selected node when
     * it has status ERROR or WARNING – analogous to the
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
                                    .getColor(WoodSettings.INSTANCE.getColorPrefix()
                                            + "fore." + status.getLiteral()))
                            : null;
            if (statusColor != null) {
                c.setForeground(statusColor);
            }
            return c;
        }
    }

    /**
     * Renderer that shows a JButton in the "^" column. The button is only
     * enabled when the attribute value is neither null nor empty.
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
        prompt.setText("AI prompt here...");
        JButton askBtn = new JButton("Ask AI");
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

    private JPanel createFastLogPanel() {
        fastLogPanel = new JackFastLogPanel(jackmaster);
        return fastLogPanel;
    }

    public void openPreferences() {
        if (preferencesDialog == null) {
            preferencesDialog = new PreferencesDialog(this, settings, themeSuite, settingsService);
        }

        preferencesDialog.setVisible(true);
        preferencesDialog.toFront();
    }

    /**
     * Registers a listener that is immediately notified about the current dark mode state and
     * on every change, so toggle items stay in sync with the preferences.
     *
     * @param listener receives true when the dark mode is active
     */
    public void addDarkModeListener(Consumer<Boolean> listener) {
        darkModeListeners.add(listener);
        listener.accept(settings.isDarkMode());
    }

    public boolean isDarkMode() {
        return settings.isDarkMode();
    }

    /**
     * Switches the color mode: installs the look and feel of the target mode, refreshes all
     * windows and persists the switch, so it survives a restart.
     *
     * @param darkMode true for dark, false for light
     */
    public void setDarkMode(boolean darkMode) {
        if (settings.isDarkMode() == darkMode) {
            return;
        }
        settings.setDarkMode(darkMode);
        LafCatalog.apply(settings);
        persistSettings();
        for (Consumer<Boolean> listener : darkModeListeners) {
            listener.accept(darkMode);
        }
    }

    /**
     * Persists the wood settings, e.g. after a mode or theme switch.
     */
    public void persistSettings() {
        try {
            settingsService.saveWoodSettings(settings);
        } catch (IOException | JsonParseException | JsonWriteException ex) {
            Logger.getGlobal().log(Level.SEVERE, "Could not save wood settings: ", ex);
        }
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
                THIS_SYNONYM,
                THIS_SYNONYM
        );
        // set the loaded EditTree in the left tree
        JackTreeModel model = new JackTreeModel(tree);
        newContainer.getLeftTree().getTree().setModel(model);
        newContainer.getLeftTree().refreshResourceInfo();
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
        editorFiles.put(treeContainer, file);
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
     * Returns the editor container of the currently active editor tab, or { null} when no loaded editor
     * is active. The fixed empty start editors are not part of the loaded editor list.
     *
     * @return the active JackEditTreeContainer, or { null}
     */
    public JackEditTreeContainer getActiveContainer() {
        final Object active = jackmaster.getActiveEditor();
        if (!(active instanceof JackEditTree)) {
            return null;
        }
        for (JackEditTreeContainer container : editorTrees) {
            if (container.getLeftTree() == active || container.getRightTree() == active) {
                return container;
            }
        }
        return null;
    }

    /**
     * Returns the edit tree of the loaded file of the currently active editor tab. The file content lives in the
     * left tree of its container, regardless of which side has the focus.
     *
     * @return the edit tree of the active editor tab, or { null}
     */
    public EditTree getActiveEditTree() {
        final JackEditTreeContainer container = getActiveContainer();
        if (container == null || container.getLeftTree() == null || container.getLeftTree().getModel() == null) {
            return null;
        }
        return container.getLeftTree().getModel().getEditTree();
    }

    /**
     * Returns the file of the currently active editor tab, or { null} when the tree was not loaded from a
     * file and has not been saved yet.
     *
     * @return the file of the active editor tab, or { null}
     */
    public File getActiveFile() {
        final JackEditTreeContainer container = getActiveContainer();
        return container == null ? null : editorFiles.get(container);
    }

    /**
     * Sets the file of the currently active editor tab, e.g. after a save-as.
     *
     * @param file the file to remember for the active editor tab
     */
    public void setActiveFile(File file) {
        final JackEditTreeContainer container = getActiveContainer();
        if (container != null) {
            editorFiles.put(container, file);
        }
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
                "Error",
                JOptionPane.ERROR_MESSAGE);
    }

}
