/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.control.JackMasterControl;
import static de.jare.tree.control.listeners.ContentListener.EDIT_ADD_NODE;
import static de.jare.tree.control.listeners.ContentListener.EDIT_COPY;
import static de.jare.tree.control.listeners.ContentListener.EDIT_CUT;
import static de.jare.tree.control.listeners.ContentListener.EDIT_DELETE_NODE;
import static de.jare.tree.control.listeners.ContentListener.EDIT_PASTE;
import static de.jare.tree.control.listeners.ContentListener.EDIT_PASTE_UNDERNEATH;
import static de.jare.tree.control.listeners.ContentListener.EDIT_RENAME_NODE;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import java.awt.event.KeyEvent;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import de.jare.jsoncasted.editor.core.EditNode;

public class JackMainMenu extends JMenuBar {

    private final WoodWindow woodWindow;
    private final JackMasterControl master;
    private final JMenuItem pasteItem;
    private final JMenuItem pasteUnderneathItem;
    private final JMenuItem deleteNodeItem;
    private final JMenuItem cutItem;
    private Object lastSelectedNode;

    public JackMainMenu(WoodWindow mainFrame, JackMasterControl master) {
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
        cutItem = new JMenuItem("Cut");
        pasteItem = new JMenuItem("paste into this node");
        pasteUnderneathItem = new JMenuItem("Paste underneath this node.");

        copyItem.addActionListener(e -> master.fireContentCommand(EDIT_COPY, this));
        cutItem.addActionListener(e -> master.fireContentCommand(EDIT_CUT, this));
        pasteItem.addActionListener(e -> master.fireContentCommand(EDIT_PASTE, this));
        pasteUnderneathItem.addActionListener(e -> master.fireContentCommand(EDIT_PASTE_UNDERNEATH, this));

        projectMenu.addSeparator();
        projectMenu.add(copyItem);
        projectMenu.add(cutItem);
        projectMenu.add(pasteItem);
        projectMenu.add(pasteUnderneathItem);

        // Edit-Menü
        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic(KeyEvent.VK_E);

        JMenuItem addNodeItem = new JMenuItem("Node hinzufügen");
        deleteNodeItem = new JMenuItem("Node löschen");
        JMenuItem renameNodeItem = new JMenuItem("Node umbenennen");

        addNodeItem.addActionListener(e -> master.fireContentCommand(EDIT_ADD_NODE, this));
        deleteNodeItem.addActionListener(e -> master.fireContentCommand(EDIT_DELETE_NODE, this));
        renameNodeItem.addActionListener(e -> master.fireContentCommand(EDIT_RENAME_NODE, this));

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
                lastSelectedNode = node;
                boolean enableCutDelete = !rootSelected && node instanceof DefaultMutableTreeNode;
                deleteNodeItem.setEnabled(enableCutDelete);
                cutItem.setEnabled(enableCutDelete);

                updatePasteEnabled();
            }

            @Override
            public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
                // hier könntest du bei Editorwechsel ggf. alles disablen,
                // wenn kein aktiver JSON-Editor offen ist
            }
        });

        master.getClipboardManager().addClipboardChangeListener(9,
                stashName -> updatePasteEnabled());

    }

    private void updatePasteEnabled() {
        boolean canPaste = false;
        boolean canPasteUnderneath = false;
        if (lastSelectedNode instanceof DefaultMutableTreeNode dmtn) {
            Object uo = dmtn.getUserObject();
            if (uo instanceof EditNode targetData) {
                canPaste = master.getClipboardManager().canPasteTo(targetData);
            }
            // For paste underneath, check if parent exists and can accept paste
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) dmtn.getParent();
            if (parent != null) {
                Object parentUo = parent.getUserObject();
                if (parentUo instanceof EditNode parentData) {
                    canPasteUnderneath = master.getClipboardManager().canPasteTo(parentData);
                }
            }
        }
        pasteItem.setEnabled(canPaste);
        pasteUnderneathItem.setEnabled(canPasteUnderneath);
    }

    private void openPreferences() {
        woodWindow.openPreferences();
    }

}
