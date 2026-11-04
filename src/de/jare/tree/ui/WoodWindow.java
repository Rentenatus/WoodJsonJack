/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditProject;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.JsonWriteException;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import static de.jare.jsoncasted.lang.JsonTerms.THIS_SYNONYM;
import de.jare.tree.control.JackMasterControl;
import de.jare.tree.control.listeners.ParseProblemsListener;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import de.jare.tree.control.model.JackTreeModel;
import de.jare.tree.settings.SettingsService;
import de.jare.tree.settings.WoodSettings;
import de.jare.tree.settings.theme.LafCatalog;
import de.jare.tree.settings.theme.ThemeSuite;
import de.jare.tree.ui.settings.PreferencesDialog;
import java.awt.*;
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

public class WoodWindow extends JFrame {

    // Bottom tab constants
    private static final String TAB_ATTRIBUTES = "Attributes";
    private static final String TAB_FIELDS = "Object";
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
    // the loaded tabs' scroll panes and base titles - the closable headers carry the titles, the OUTDATED
    // markers rewrite them (concept doc/JSON_PROJECT.md phase 4)
    private final Map<JackEditTreeContainer, JScrollPane> containerPanes = new HashMap<>();
    private final Map<JackEditTreeContainer, String> containerTitles = new HashMap<>();
    private PreferencesDialog preferencesDialog;
    private final List<Consumer<Boolean>> darkModeListeners = new ArrayList<>();
    private JackClipboardPanel jackClipboardPanel;
    private JackUndoPanel jackPanel;
    private SearchResultPanel searchResultPanel;
    private JackFastLogPanel fastLogPanel;
    // The project session of the editor (concept doc/JSON_PROJECT.md): null when no project session is open
    private EditProject editProject;
    private final TreeFocusListener treeFocusListener;
    // The orator of the master control holds its listeners via weak references
    // only: the field keeps the listener reachable, otherwise the garbage
    // collector silently deregisters it.
    private final ParseProblemsListener parseProblemsListener;

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

        centerTabs.addTab("Tree Editor 1", wrapEditor(editorTree1));
        centerTabs.addTab("Tree Editor 2", wrapEditor(editorTree2));

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
            refreshOutdatedMarkers();
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
        bottomTabs.addTab(TAB_ATTRIBUTES, new JsonJackAttributesPane(jackmaster, this));
        bottomTabs.addTab(TAB_FIELDS, new JsonJackObjectPane(jackmaster, this));
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
        parseProblemsListener = (source, nodes) -> {
            bottomTabs.setSelectedIndex(bottomTabs.indexOfTab(TAB_SEARCH_RESULT));
        };
        jackmaster.addParseProblemsListener(parseProblemsListener);

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

        // the file-currency axis surfaces on focus: external changes show as (outdated) in the tab titles
        addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowGainedFocus(java.awt.event.WindowEvent e) {
                refreshOutdatedMarkers();
            }
        });
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
        addEditorTab(file, tree, null, false);
    }

    /**
     * Opens a project session in the editor (concept doc/JSON_PROJECT.md, phase 4): the main file becomes the
     * editable tab identity, every sub resource of the parse project opens as a read only view on the SHARED
     * EditTree (visibility axis: promoted FOREGROUND, read only first - decision 3 of the plan). A previously
     * open project session closes; its tabs stay (their trees live on) unless a new project replaces them.
     *
     * @param mainFile the project's main resource file
     * @param project the session clamp with the shared trees
     */
    public void openProject(File mainFile, EditProject project) throws JsonParseException {
        if (editProject != null) {
            editProject.close();
        }
        this.editProject = project;

        addEditorTab(mainFile, project.getMainTree(), null, false);
        for (final String synonym : project.getSortedSynonyms()) {
            final EditTree tree = project.findBySynonym(synonym);
            if (tree == null || tree == project.getMainTree()) {
                continue;
            }
            final File treeFile = resourceFileOf(tree);
            addEditorTab(treeFile, tree, "[" + synonym + "]", true);
        }
    }

    /**
     * Returns the open project session, or null when no project was opened.
     *
     * @return the edit project of the session, or null
     */
    public EditProject getEditProject() {
        return editProject;
    }

    /**
     * Closes the open project session (releases the shared trees and the parse project). The editor tabs keep
     * their views - the session ends, the windows do what their user wants with them.
     */
    public void closeProject() {
        if (editProject != null) {
            editProject.close();
            editProject = null;
        }
    }

    private static File resourceFileOf(EditTree tree) {
        if (tree == null || tree.getBaseSnapshot() == null || !tree.getBaseSnapshot().hasResourceFile()) {
            return null;
        }
        return new File(tree.getBaseSnapshot().getResourceFile());
    }

    /**
     * Builds the closable tab header: the title label (the OUTDATED marker updates it) and the close button.
     * Only loaded editor tabs carry it - the two fixed start tabs stay as they are.
     */
    private java.awt.Component createTabHeader(JLabel titleLabel, JackEditTreeContainer container) {
        final JPanel header = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        header.setOpaque(false);
        header.add(titleLabel);
        final JButton close = new JButton("\u2715");
        close.setFont(close.getFont().deriveFont(9f));
        close.setMargin(new java.awt.Insets(0, 4, 0, 0));
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setFocusable(false);
        close.setToolTipText("Close this tab (a tree with unsaved changes asks first)");
        close.addActionListener(e -> closeEditorTab(container));
        header.add(close);
        return header;
    }

    /**
     * Closes one loaded editor tab (close guard, concept doc/JSON_PROJECT.md phase 4): a tree with unsaved
     * changes asks before it goes; session trees of an open project are NOT closed on the EditTree level -
     * the session owns them, the tab only loses its view (promote it again any time). Plain file tabs stop
     * their parsers, exactly like the window close does.
     *
     * @param container the tab's editor container
     */
    public void closeEditorTab(JackEditTreeContainer container) {
        final EditTree tree = container.getLeftTree() != null && container.getLeftTree().getModel() != null
                ? container.getLeftTree().getModel().getEditTree() : null;
        if (tree != null && tree.isEdited()) {
            final int answer = JOptionPane.showConfirmDialog(this,
                    "This tab carries unsaved changes.\nClose it anyway (the changes are lost)?",
                    "Close tab", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }
        final JScrollPane pane = containerPanes.remove(container);
        if (pane != null) {
            centerTabs.remove(pane);
        }
        editorTrees.remove(container);
        editorFiles.remove(container);
        containerTitles.remove(container);

        container.getLeftTree().stopParseRefreshTimer();
        container.getRightTree().stopParseRefreshTimer();
        if (tree != null && !isSessionTree(tree) && tree.isParserRunning()) {
            tree.stopParserService();
            tree.close();
        }
    }

    /**
     * Tells whether the tree belongs to the open project session - session trees stay alive after their tab
     * closes (the session owns them, they come back via promote).
     */
    private boolean isSessionTree(EditTree tree) {
        if (editProject == null || tree == null) {
            return false;
        }
        if (editProject.getMainTree() == tree) {
            return true;
        }
        try {
            for (final String synonym : editProject.getSortedSynonyms()) {
                if (tree == editProject.findBySynonym(synonym)) {
                    return true;
                }
            }
        } catch (JsonParseException ex) {
            // an open session has no cycle (it opened); safety net, never expected
        }
        return false;
    }

    /**
     * Promotes a project resource to its own tab again (visibility axis, concept doc/JSON_PROJECT.md): a
     * closed session resource is BACKGROUND - this opens (or selects) its read only view.
     *
     * @param synonym the session synonym of the resource
     */
    public void showProjectResource(String synonym) {
        final EditProject project = getEditProject();
        if (project == null) {
            return;
        }
        final EditTree tree = project.findBySynonym(synonym);
        if (tree == null) {
            return;
        }
        final JackEditTreeContainer open = findContainerOf(tree);
        if (open != null) {
            final JScrollPane pane = containerPanes.get(open);
            if (pane != null) {
                centerTabs.setSelectedComponent(pane);
            }
            return;
        }
        addEditorTab(resourceFileOf(tree), tree, "[" + synonym + "]", true);
    }

    private JackEditTreeContainer findContainerOf(EditTree tree) {
        for (final JackEditTreeContainer container : editorTrees) {
            final EditTree candidate = container.getLeftTree() != null
                    && container.getLeftTree().getModel() != null
                            ? container.getLeftTree().getModel().getEditTree() : null;
            if (candidate == tree) {
                return container;
            }
        }
        return null;
    }

    /**
     * Refreshes the OUTDATED markers of all loaded tabs (concept file-currency axis made visible): a tree
     * whose origin file changed externally gets the marker in its tab title until the tab re-imports or
     * saves. Called on tab switches, window focus and after saves.
     */
    public void refreshOutdatedMarkers() {
        for (final Map.Entry<JackEditTreeContainer, String> entry : containerTitles.entrySet()) {
            final JackEditTreeContainer container = entry.getKey();
            final EditTree tree = container.getLeftTree() != null
                    && container.getLeftTree().getModel() != null
                            ? container.getLeftTree().getModel().getEditTree() : null;
            final String title = tree != null && tree.isOutdated()
                    ? entry.getValue() + "  (outdated)" : entry.getValue();
            final JScrollPane pane = containerPanes.get(container);
            if (pane == null) {
                continue;
            }
            final int index = centerTabs.indexOfComponent(pane);
            if (index >= 0) {
                // the header component carries the title label - find it and update its text
                final java.awt.Component header = centerTabs.getTabComponentAt(index);
                if (header instanceof JPanel panel) {
                    for (int i = 0; i < panel.getComponentCount(); i++) {
                        if (panel.getComponent(i) instanceof JLabel label) {
                            if (!title.equals(label.getText())) {
                                label.setText(title);
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Adds a new editor tab with the loaded JSON content, optionally read only and with a title hint (the
     * project sub resources open as read only views, concept doc/JSON_PROJECT.md visibility axis).
     *
     * @param file the JSON file that was loaded
     * @param tree the Tree with the loaded content
     * @param titleHint extra title text after the file name, or null for none
     * @param readonly true when the tab must not edit its tree
     */
    public void addEditorTab(File file, EditTree tree, String titleHint, boolean readonly) {
        JackEditTreeContainer newContainer = new JackEditTreeContainer(
                jackmaster,
                THIS_SYNONYM,
                THIS_SYNONYM
        );
        // set the loaded EditTree in the left tree
        JackTreeModel model = new JackTreeModel(tree);
        newContainer.getLeftTree().getTree().setModel(model);
        newContainer.getLeftTree().refreshResourceInfo();
        if (readonly) {
            newContainer.getLeftTree().setReadonly(true);
            newContainer.getRightTree().setReadonly(true);
        }
        // newContainer.getLeftTree().getModel().rebuildFromDomain();

        addEditorTab(file, newContainer, titleHint);
    }

    /**
     * Wraps an editor container in its tab scroll pane. The scroll bar that scrolls the whole content (banner
     * included) is deliberately thin - about 60% of the LaF default: the trees carry their own scroll panes, the
     * outer one is only the fallback when the window is smaller than the container.
     *
     * @param container the editor container, not null.
     * @return the wrapping scroll pane with the slim scroll bars.
     */
    private JScrollPane wrapEditor(JackEditTreeContainer container) {
        final JScrollPane scrollPane = new JScrollPane(container);
        scrollPane.getVerticalScrollBar().setPreferredSize(
                new java.awt.Dimension(EDITOR_SCROLLBAR_WIDTH, 0));
        scrollPane.getHorizontalScrollBar().setPreferredSize(
                new java.awt.Dimension(0, EDITOR_SCROLLBAR_WIDTH));
        return scrollPane;
    }

    /** The slim scroll bar width of an editor tab (about 60% of the LaF default). */
    private static final int EDITOR_SCROLLBAR_WIDTH = 9;

    /**
     * Adds a new editor tab with the loaded JSON content.
     *
     * @param file the JSON file that was loaded
     * @param treeContainer the JackEditTreeContainer with the loaded content
     */
    private void addEditorTab(File file, JackEditTreeContainer treeContainer) {
        addEditorTab(file, treeContainer, null);
    }

    /**
     * Adds a new editor tab with the loaded JSON content.
     *
     * @param file the JSON file that was loaded
     * @param treeContainer the JackEditTreeContainer with the loaded content
     * @param titleHint extra title text after the file name, or null for none
     */
    private void addEditorTab(File file, JackEditTreeContainer treeContainer, String titleHint) {
        editorTrees.add(treeContainer);
        editorFiles.put(treeContainer, file);
        String tabTitle = file != null ? file.getName() : "New Editor";
        if (titleHint != null && !titleHint.isBlank()) {
            tabTitle = tabTitle + "  " + titleHint;
        }
        JScrollPane scrollPane = wrapEditor(treeContainer);
        centerTabs.addTab(tabTitle, scrollPane);
        centerTabs.setSelectedComponent(scrollPane);

        // the closable header (the tab title lives here - the OUTDATED marker updates this label too)
        containerPanes.put(treeContainer, scrollPane);
        containerTitles.put(treeContainer, tabTitle);
        final JLabel titleLabel = new JLabel(tabTitle);
        centerTabs.setTabComponentAt(centerTabs.getTabCount() - 1,
                createTabHeader(titleLabel, treeContainer));

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
        // the project session ends with the window - the clamp releases the shared trees
        closeProject();
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
