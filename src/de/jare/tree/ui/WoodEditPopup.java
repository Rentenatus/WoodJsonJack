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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

public class WoodEditPopup extends JPopupMenu {

    public WoodEditPopup(MasterControl master) {
        JMenuItem addNodeItem = new JMenuItem("Node hinzufügen");
        JMenuItem deleteNodeItem = new JMenuItem("Node löschen");
        JMenuItem renameNodeItem = new JMenuItem("Node umbenennen");

        addNodeItem.addActionListener(e -> master.fireCommand(EDIT_ADD_NODE, this));
        deleteNodeItem.addActionListener(e -> master.fireCommand(EDIT_DELETE_NODE, this));
        renameNodeItem.addActionListener(e -> master.fireCommand(EDIT_RENAME_NODE, this));

        add(addNodeItem);
        add(deleteNodeItem);
        add(renameNodeItem);

        JMenuItem copyItem = new JMenuItem("Copy");
        JMenuItem cutItem = new JMenuItem("Cut");
        JMenuItem pasteItem = new JMenuItem("Paste");

        copyItem.addActionListener(e -> master.fireCommand(EDIT_COPY, master.getClipboardTree()));
        cutItem.addActionListener(e -> master.fireCommand(EDIT_CUT, master.getClipboardTree()));
        pasteItem.addActionListener(e -> master.fireCommand(EDIT_PASTE, master.getClipboardTree()));

        addSeparator();
        add(copyItem);
        add(cutItem);
        add(pasteItem);

        master.addSelectionListener(8, new TreeFocusListener() {
            @Override
            public void onNodeSelected(Object node, Object trigger, boolean rootSelected) {
                boolean enableCutDelete = !rootSelected && node != null;
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
                // optional: Menü bei Editorwechsel anpassen
            }
        });
    }

    /**
     * Hilfsmethode, um das Popup an einem JTree zu registrieren.
     *
     * @param tree
     * @param popup
     */
    public static void installOn(JTree tree, WoodEditPopup popup) {

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handlePopupTrigger(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handlePopupTrigger(e);
            }

            private void handlePopupTrigger(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int x = e.getX();
                int y = e.getY();
                JTree tree = (JTree) e.getSource();
                TreePath path = tree.getPathForLocation(x, y);
                if (path == null) {
                    return;
                }

                TreePath[] selectedPaths = tree.getSelectionPaths();
                boolean alreadySelected = false;
                if (selectedPaths != null) {
                    for (TreePath p : selectedPaths) {
                        if (p.equals(path)) {
                            alreadySelected = true;
                            break;
                        }
                    }
                }

                // Nur wenn der angeklickte Knoten noch NICHT selektiert ist,
                // machen wir eine Einzelauswahl – sonst bleibt die Multi-Selection erhalten.
                if (!alreadySelected) {
                    tree.setSelectionPath(path);
                }

                // Popup anzeigen 
                popup.show(tree, x, y);
            }
        });
    }

}
