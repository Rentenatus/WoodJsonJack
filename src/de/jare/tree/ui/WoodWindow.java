/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.tree.control.JackMasterControl;
import de.jare.tree.control.model.JackTreeModel;
import de.jare.tree.settings.SettingsService;
import de.jare.tree.settings.WoodSettings;
import de.jare.tree.settings.theme.ThemeSuite;
import de.jare.tree.ui.settings.PreferencesDialog;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

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

        // Tab-Wechsel steuert aktiven Editor
        centerTabs.addChangeListener(e -> {
            int idx = centerTabs.getSelectedIndex();
            if (idx >= 0 && idx < editorTrees.size()) {
                JackEditTreeContainer container = editorTrees.get(idx);
                jackmaster.setActiveEditor(container.getLeftTree(), this);
            }
        });
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
    }

    private JTable attributesTable;
    private JsonJackAttrTableModel attributesModel;

    private JPanel createAttributesPanel() {
        attributesModel = new JsonJackAttrTableModel();
        jackmaster.addSelectionListener(attributesModel);
        attributesTable = new JTable(attributesModel);
        attributesTable.setFillsViewportHeight(true);
        attributesTable.getTableHeader().setVisible(true);

        JPanel borderedPanel = new JPanel(new BorderLayout());
        borderedPanel.add(new JScrollPane(attributesTable), BorderLayout.CENTER);
        return borderedPanel;
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

}
