/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import static de.jare.tree.control.listeners.ContentListener.EDIT_ADD_NODE;
import static de.jare.tree.control.listeners.ContentListener.EDIT_COPY;
import static de.jare.tree.control.listeners.ContentListener.EDIT_CUT;
import static de.jare.tree.control.listeners.ContentListener.EDIT_DELETE_NODE;
import static de.jare.tree.control.listeners.ContentListener.EDIT_PASTE;
import static de.jare.tree.control.listeners.ContentListener.EDIT_RENAME_NODE;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.tree.control.JackMasterControl;
import de.jare.tree.control.JackUndoManager;

public class JackEditPopup extends JPopupMenu {

    private final JMenuItem pasteItem;
    private final JMenuItem deleteNodeItem;
    private final JMenuItem cutItem;
    private final JackMasterControl master;
    private Object lastSelectedNode;

    public JackEditPopup(JackMasterControl master) {
        this.master = master;
        JMenuItem addNodeItem = new JMenuItem("Node hinzufügen");
        deleteNodeItem = new JMenuItem("Node löschen");
        JMenuItem renameNodeItem = new JMenuItem("Node umbenennen");

        addNodeItem.addActionListener(e -> master.fireContentCommand(EDIT_ADD_NODE, this));
        deleteNodeItem.addActionListener(e -> master.fireContentCommand(EDIT_DELETE_NODE, this));
        renameNodeItem.addActionListener(e -> master.fireContentCommand(EDIT_RENAME_NODE, this));

        add(addNodeItem);
        add(deleteNodeItem);
        add(renameNodeItem);

        JMenuItem copyItem = new JMenuItem("Copy");
        cutItem = new JMenuItem("Cut");
        pasteItem = new JMenuItem("Paste");

        copyItem.addActionListener(e -> master.fireContentCommand(EDIT_COPY, this));
        cutItem.addActionListener(e -> master.fireContentCommand(EDIT_CUT, this));
        pasteItem.addActionListener(e -> master.fireContentCommand(EDIT_PASTE, this));

        addSeparator();
        add(copyItem);
        add(cutItem);
        add(pasteItem);

        master.addSelectionListener(8, new TreeFocusListener() {
            @Override
            public void onNodeSelected(Object node, Object trigger, boolean rootSelected) {
                lastSelectedNode = node;
                boolean enableCutDelete = !rootSelected && node != null;
                deleteNodeItem.setEnabled(enableCutDelete);
                cutItem.setEnabled(enableCutDelete);

                updatePasteEnabled();
            }

            @Override
            public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
                // optional: Menü bei Editorwechsel anpassen
            }
        });

        master.getClipboardManager().addClipboardChangeListener(9,
                stashName -> updatePasteEnabled());
    }

    private void updatePasteEnabled() {
        boolean canPaste = false;
        if (lastSelectedNode instanceof DefaultMutableTreeNode dmtn) {
            Object uo = dmtn.getUserObject();
            if (uo instanceof EditNode targetData) {
                canPaste = master.getClipboardManager().canPasteTo(targetData);
            }
        }
        pasteItem.setEnabled(canPaste);
    }

    /**
     * Hilfsmethode, um das Popup an einem JTree zu registrieren.
     *
     * @param tree
     * @param popup
     */
    public static void installOn(JTree tree, JackEditPopup popup) {

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                handlePopupTrigger(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                handlePopupTrigger(event);
            }

            private void handlePopupTrigger(MouseEvent event) {
                if (!event.isPopupTrigger()) {
                    return;
                }
                int x = event.getX();
                int y = event.getY();
                JTree tree = (JTree) event.getSource();
                TreePath path = tree.getPathForLocation(x, y);
                if (path == null) {
                    return;
                }

                TreePath[] selectedPaths = tree.getSelectionPaths();
                boolean alreadySelected = false;
                if (selectedPaths != null) {
                    for (TreePath selPath : selectedPaths) {
                        if (selPath.equals(path)) {
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
