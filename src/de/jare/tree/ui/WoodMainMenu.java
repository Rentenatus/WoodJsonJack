/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.control.MasterControl;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import static de.jare.tree.control.listeners.ContentListener.EDIT_ADD_NODE;
import static de.jare.tree.control.listeners.ContentListener.EDIT_COPY;
import static de.jare.tree.control.listeners.ContentListener.EDIT_CUT;
import static de.jare.tree.control.listeners.ContentListener.EDIT_DELETE_NODE;
import static de.jare.tree.control.listeners.ContentListener.EDIT_PASTE;
import static de.jare.tree.control.listeners.ContentListener.EDIT_RENAME_NODE;
import java.awt.event.KeyEvent;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;

/**
 * Main menu bar for the application.
 * Supports both TreeModel-based and EditTree-based operations with automatic
 * fallback to maintain backward compatibility.
 */
public class WoodMainMenu extends JMenuBar {

    private final WoodWindow woodWindow;
    private final MasterControl master;

    public WoodMainMenu(WoodWindow mainFrame, MasterControl master) {
        this.woodWindow = mainFrame;
        this.master = master;

        // Projekt-Menü
        JMenu projectMenu = new JMenu("Projekt");
        projectMenu.setMnemonic(KeyEvent.VK_P);

        JMenuItem newItem = new JMenuItem("Neu");
        JMenuItem openItem = new JMenuItem("Öffnen...");
        JMenuItem saveItem = new JMenuItem("Speichern");
        JMenuItem saveAsItem = new JMenuItem("Speichern unter...");
        JMenuItem exitItem = new JMenuItem("Beenden");

        exitItem.addActionListener(e -> woodWindow.dispose());

        projectMenu.add(newItem);
        projectMenu.add(openItem);
        projectMenu.add(saveItem);
        projectMenu.add(saveAsItem);
        projectMenu.addSeparator();
        projectMenu.add(exitItem);

        JMenuItem copyItem = new JMenuItem("Copy");
        JMenuItem cutItem = new JMenuItem("Cut");
        JMenuItem pasteItem = new JMenuItem("Paste");

        copyItem.addActionListener(e -> master.fireCommand(EDIT_COPY, master.getClipboardTree()));
        cutItem.addActionListener(e -> master.fireCommand(EDIT_CUT, master.getClipboardTree()));
        pasteItem.addActionListener(e -> master.fireCommand(EDIT_PASTE, master.getClipboardTree()));

        projectMenu.addSeparator();
        projectMenu.add(copyItem);
        projectMenu.add(cutItem);
        projectMenu.add(pasteItem);

        // Edit-Menü
        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic(KeyEvent.VK_E);

        JMenuItem addNodeItem = new JMenuItem("Node hinzufügen");
        JMenuItem deleteNodeItem = new JMenuItem("Node löschen");
        JMenuItem renameNodeItem = new JMenuItem("Node umbenennen");

        addNodeItem.addActionListener(e -> master.fireCommand(EDIT_ADD_NODE, this));
        deleteNodeItem.addActionListener(e -> master.fireCommand(EDIT_DELETE_NODE, this));
        renameNodeItem.addActionListener(e -> master.fireCommand(EDIT_RENAME_NODE, this));

        editMenu.add(addNodeItem);
        editMenu.add(deleteNodeItem);
        editMenu.add(renameNodeItem);

        JMenu optionsMenu = new JMenu("Options");
        JMenuItem preferencesItem = new JMenuItem("Preferences");

        preferencesItem.addActionListener(e -> openPreferences());

        optionsMenu.add(preferencesItem);

        // Info-Menü
        JMenu infoMenu = new JMenu("Info");
        infoMenu.setMnemonic(KeyEvent.VK_I);

        JMenuItem aboutItem = new JMenuItem("Über...");
        aboutItem.addActionListener(e
                -> JOptionPane.showMessageDialog(woodWindow,
                        "Tree Editor\n© 2026",
                        "Über",
                        JOptionPane.INFORMATION_MESSAGE
                )
        );
        infoMenu.add(aboutItem);

        add(projectMenu);
        add(editMenu);
        add(optionsMenu);
        add(infoMenu);

        master.addSelectionListener(7, new TreeFocusListener() {
            @Override
            public void onNodeSelected(Object node, Object trigger, boolean rootSelected) {
                boolean enableCutDelete = !rootSelected && node instanceof DefaultMutableTreeNode;
                deleteNodeItem.setEnabled(enableCutDelete);
                cutItem.setEnabled(enableCutDelete);

                pasteItem.setEnabled(canPasteToCurrent(node));
            }

            @Override
            public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
                // hier könntest du bei Editorwechsel ggf. alles disablen,
                // wenn kein aktiver JSON-Editor offen ist
            }
        });

    }

    /**
     * Checks if paste operation is possible for the current node.
     * Supports both EditTree-based and TreeModel-based clipboard content.
     *
     * @param node the target node
     * @return true if paste is possible
     */
    private boolean canPasteToCurrent(Object node) {
        WoodClipboardTree clipboardTree = master.getClipboardTree();
        if (clipboardTree == null || node == null) {
            return false;
        }

        // Try EditTree-based paste check first
        EditTree editTree = master.getEditTree();
        if (editTree != null && node instanceof EditNode targetEditNode) {
            return clipboardTree.canPasteToEdit(editTree, targetEditNode);
        }

        // Fallback to legacy TreeModel-based paste check
        if (node instanceof DefaultMutableTreeNode dmtn) {
            Object uo = dmtn.getUserObject();
            if (uo instanceof EditNode targetData) {
                return clipboardTree.canPasteTo(targetData);
            }
        }

        return false;
    }

    private void openPreferences() {
        woodWindow.openPreferences();
    }

}
