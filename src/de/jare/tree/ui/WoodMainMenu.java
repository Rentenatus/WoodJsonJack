/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.control.MasterControl;
import static de.jare.tree.control.listeners.ContentListener.EDIT_ADD_NODE;
import static de.jare.tree.control.listeners.ContentListener.EDIT_COPY;
import static de.jare.tree.control.listeners.ContentListener.EDIT_CUT;
import static de.jare.tree.control.listeners.ContentListener.EDIT_DELETE_NODE;
import static de.jare.tree.control.listeners.ContentListener.EDIT_PASTE;
import static de.jare.tree.control.listeners.ContentListener.EDIT_RENAME_NODE;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import de.jare.tree.data.JsonTreeNodeData;
import java.awt.event.KeyEvent;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

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

        JMenu helpMenu = new JMenu("Help");
        JMenuItem preferencesItem = new JMenuItem("Preferences");

        preferencesItem.addActionListener(e -> openPreferences());

        helpMenu.add(preferencesItem);

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
        add(helpMenu);
        add(infoMenu);

        master.addSelectionListener(7, new TreeFocusListener() {
            @Override
            public void onNodeSelected(Object node, Object trigger, boolean rootSelected) {
                boolean enableCutDelete = !rootSelected && node instanceof DefaultMutableTreeNode;
                deleteNodeItem.setEnabled(enableCutDelete);
                cutItem.setEnabled(enableCutDelete);

                boolean canPaste = false;
                if (node instanceof DefaultMutableTreeNode dmtn) {
                    Object uo = dmtn.getUserObject();
                    if (uo instanceof JsonTreeNodeData targetData) {
                        canPaste = master.getClipboardTree().canPasteTo(targetData);
                    }
                }
                pasteItem.setEnabled(canPaste);
            }

            @Override
            public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
                // hier könntest du bei Editorwechsel ggf. alles disablen,
                // wenn kein aktiver JSON-Editor offen ist
            }
        });

    }

    private void openPreferences() {
        woodWindow.openPreferences();
    }

}
