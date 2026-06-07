/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.control.JackMasterControl;
import de.jare.tree.control.MasterControl;
// import de.jare.tree.control.MasterControl;
import de.jare.tree.settings.SettingsService;
import de.jare.tree.settings.WoodSettings;
import de.jare.tree.settings.theme.ThemeSuite;
import de.jare.tree.ui.settings.PreferencesDialog;
import java.awt.*;
import javax.swing.*;

public class WoodWindow extends JFrame {

    private final JackMasterControl jackmaster;
    private final JTabbedPane centerTabs;
    private final JackEditTreeContainer editorTree1;
    private final JackEditTreeContainer editorTree2;
    private final SettingsService settingsService;
    private final WoodSettings settings;
    private final ThemeSuite themeSuite;
    private PreferencesDialog preferencesDialog;
    private JackClipboardPanel jackClipboardPanel;

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

        centerTabs.addTab("Tree Editor 1", new JScrollPane(editorTree1));
        centerTabs.addTab("Tree Editor 2", new JScrollPane(editorTree2));

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
            switch (idx) {
                case 0:
                    JackEditTree editor = editorTree1.getLeftTree();
                    jackmaster.setActiveEditor(editor, this);
                    break;
                case 1:
                    JackEditTree editor2 = editorTree2.getLeftTree();
                    jackmaster.setActiveEditor(editor2, this);
                    break;
                default:
                    break;
            };
        });
        // initial
        jackmaster.addSelectionListener(propertyModel);
        jackmaster.setActiveEditor(editorTree1.getLeftTree(), this);

        JackWoodEditPopup jackPopup = new JackWoodEditPopup(jackmaster);

        JackWoodEditPopup.installOn(editorTree1.getLeftTree().getTree(), jackPopup);
        JackWoodEditPopup.installOn(editorTree1.getRightTree().getTree(), jackPopup);

        JackWoodEditPopup.installOn(editorTree2.getLeftTree().getTree(), jackPopup);
        JackWoodEditPopup.installOn(editorTree2.getRightTree().getTree(), jackPopup);

        // Bottom: tabs + bottom toolbar
        JTabbedPane bottomTabs = new JTabbedPane();
        bottomTabs.addTab("Properties", createPropertiesPanel());
        bottomTabs.addTab("Jack Undo", createJackUndoPanel());
        bottomTabs.addTab("KI Assistant", createKIAssistant());
        bottomTabs.addTab("Jack Clipboard", createJackClipboardPanel());

        JPanel bottomToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnApply = new JButton("Apply");
        JCheckBox cbAutoApply = new JCheckBox("Auto apply");
        // TODO: ActionListener hinzuf?gen
        bottomToolbar.add(btnApply);
        bottomToolbar.add(cbAutoApply);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(bottomToolbar, BorderLayout.NORTH);
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

    private JTable propertyTable;
    private PropertyTableModel propertyModel;

    private JPanel createPropertiesPanel() {
        propertyModel = new PropertyTableModel();
        propertyTable = new JTable(propertyModel);
        propertyTable.setFillsViewportHeight(true);
        propertyTable.getTableHeader().setVisible(false); // keine ?berschrift anzeigen

        JPanel borderedPanel = new JPanel(new BorderLayout());
        borderedPanel.add(new JScrollPane(propertyTable), BorderLayout.CENTER);
        return borderedPanel;
    }

    private JackUndoPanel jackPanel;

    private JPanel createJackUndoPanel() {
        jackPanel = new JackUndoPanel(jackmaster);
        return jackPanel;
    }

    private WoodClipboardTree clipboardTree;

    public WoodClipboardTree getClipboardTree() {
        return clipboardTree;
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

    public void openPreferences() {
        if (preferencesDialog == null) {
            preferencesDialog = new PreferencesDialog(this, settings, themeSuite);
        }

        preferencesDialog.setVisible(true);
        preferencesDialog.toFront();
    }
}
