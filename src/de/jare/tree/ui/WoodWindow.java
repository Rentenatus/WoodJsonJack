/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.control.MasterControl;
import java.awt.*;
import javax.swing.*;

public class WoodWindow extends JFrame {

    private final MasterControl master;
    private final JTabbedPane centerTabs;
    private final WoodEditTree editorTree1;
    private final WoodEditTree editorTree2;

    public WoodWindow() {
        master = new MasterControl();

        setTitle("Wood Json Studio");
        setSize(1200, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        WoodMainMenu bar = new WoodMainMenu(this, master);
        setJMenuBar(bar);

        JSplitPane horizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        // Left: project tree
        WoodProjektTree projectTree = new WoodProjektTree("Project", "Node1", "Node2", "Folder");
        projectTree.setPreferredSize(new Dimension(250, 0));
        horizontalSplit.setLeftComponent(new JScrollPane(projectTree));

        // Center: editor tabs + upper toolbar
        centerTabs = new JTabbedPane();

        editorTree1 = new WoodEditTree(master, "Root1", "Scene1", "Character1", "Scene2", "Character2", "Scene3", "Character3");
        editorTree2 = new WoodEditTree(master, "Root2", "Scene4", "Character4");

        centerTabs.addTab("Tree Editor 1", new JScrollPane(editorTree1));
        centerTabs.addTab("Tree Editor 2", new JScrollPane(editorTree2));

        // obere Toolbar ueber den Editor-Tabs
        // obere Toolbar �ber den Editor-Tabs
        JPanel upperToolbar = new WoodUpperToolbar(master);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(upperToolbar, BorderLayout.NORTH);
        centerPanel.add(centerTabs, BorderLayout.CENTER);

        horizontalSplit.setRightComponent(centerPanel);

        // Tab-Wechsel steuert aktiven Editor
        centerTabs.addChangeListener(e -> {
            int idx = centerTabs.getSelectedIndex();
            JTree editor = switch (idx) {
                case 0 ->
                    editorTree1;
                case 1 ->
                    editorTree2;
                default ->
                    null;
            };
            master.setActiveEditor(editor, this);
        });
        // initial
        master.setActiveEditor(editorTree1, master);

        WoodEditPopup popup = new WoodEditPopup(master);
        WoodEditPopup.installOn(editorTree1, popup);
        WoodEditPopup.installOn(editorTree2, popup);

        // Bottom: tabs + bottom toolbar
        JTabbedPane bottomTabs = new JTabbedPane();
        bottomTabs.addTab("Properties", createPropertiesPanel());
        bottomTabs.addTab("Clipboard", createClipboardPanel());
        bottomTabs.addTab("Undo", createUndoPanel());
        bottomTabs.addTab("KI Assistant", createKIAssistant());

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
        master.addSelectionListener(propertyModel);
        master.setClipboardTree(clipboardTree);

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

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(propertyTable), BorderLayout.CENTER);
        return panel;
    }

    private WoodUndoPanel panel;

    private JPanel createUndoPanel() {
        panel = new WoodUndoPanel(master);
        return panel;
    }

    private WoodClipboardTree clipboardTree;

    private JPanel createClipboardPanel() {
        clipboardTree = new WoodClipboardTree();
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(clipboardTree), BorderLayout.CENTER);
        return panel;
    }

    public WoodClipboardTree getClipboardTree() {
        return clipboardTree;
    }

    private JPanel createKIAssistant() {
        JPanel panel = new JPanel(new BorderLayout());
        JTextArea prompt = new JTextArea(5, 20);
        prompt.setText("KI-Prompt hier...");
        JButton askBtn = new JButton("KI fragen");
        panel.add(new JScrollPane(prompt), BorderLayout.CENTER);
        panel.add(askBtn, BorderLayout.SOUTH);
        return panel;
    }

}
