/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.control.commands.WoodCommandAddNodes;
import de.jare.tree.control.listeners.TreeFocusComponent;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;

public class WoodClipboardTree extends JTree {

    private DefaultMutableTreeNode[] clipboardNodes;
    private WoodEditTree sourceTree;
    private boolean cut;

    public WoodClipboardTree() {
        super(new DefaultMutableTreeNode("Clipboard"));
        this.cut = false;
        setEditable(false);
        setRootVisible(true);
        setShowsRootHandles(true);
    }

    public DefaultMutableTreeNode[] getClipboardNodes() {
        return clipboardNodes;
    }

    public WoodEditTree getSourceTree() {
        return sourceTree;
    }

    public void clearClipboard() {
        clipboardNodes = null;
        sourceTree = null;
        showClipboardContent(null);
    }

    public void copySelection(WoodEditTree trigger, TreePath[] paths, boolean cut) {
        if (paths == null || paths.length == 0) {
            clipboardNodes = null;
            return;
        }
        sourceTree = trigger;
        this.cut = cut;
        clipboardNodes = deepCopies(paths, !cut);
        showClipboardContent(clipboardNodes);
    }

    public void pasteClipboard(TreeFocusComponent trigger, TreePath path) {
        if (clipboardNodes == null || clipboardNodes.length == 0) {
            return;
        }

        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) path.getLastPathComponent();
        DefaultTreeModel model = (DefaultTreeModel) trigger.getModel();

        int index = parent.getChildCount();

        // Arrays fuer Undo-Command
        DefaultMutableTreeNode[] added = new DefaultMutableTreeNode[clipboardNodes.length];
        DefaultMutableTreeNode[] parents = new DefaultMutableTreeNode[clipboardNodes.length];

        DefaultMutableTreeNode lastCopy = null;

        for (int i = 0; i < clipboardNodes.length; i++) {
            DefaultMutableTreeNode node = clipboardNodes[i];
            DefaultMutableTreeNode copy = deepCopy(node, !cut);
            model.insertNodeInto(copy, parent, index++);
            added[i] = copy;
            parents[i] = parent;
            lastCopy = copy;
        }
        this.cut = false;

        // Undo-Command registrieren (Paste = AddNodes)
        if (trigger.getMaster() != null) {
            trigger.getMaster().getUndoManager().pushCommand(
                    new WoodCommandAddNodes(added, parents, null)
            );
        }

        if (lastCopy != null) {
            TreePath newPath = new TreePath(lastCopy.getPath());
            trigger.getTree().setSelectionPath(newPath);
            trigger.getTree().scrollPathToVisible(newPath);
        }
    }

    /**
     * Zeigt den aktuellen Clipboard-Inhalt als Kopie im Tree an.
     *
     * @param nodes
     */
    public void showClipboardContent(DefaultMutableTreeNode[] nodes) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) getModel().getRoot();
        root.removeAllChildren();
        if (nodes != null) {
            for (DefaultMutableTreeNode n : nodes) {
                root.add(deepCopy(n, false));
            }
        }
        ((DefaultTreeModel) getModel()).reload();
        if (getRowCount() > 0) {
            expandRow(0);
        }
    }

    public boolean canPasteTo(EditNode targetData) {

        if (targetData == null || clipboardNodes == null || clipboardNodes.length == 0) {
            return false;
        }
        for (DefaultMutableTreeNode candidate : clipboardNodes) {
            Object clipUo = candidate.getUserObject();
            if (!(clipUo instanceof EditNode clipData)) {
                return false;
            }
            if (!targetData.canBeChildOf(clipData)) {
                return false;
            }
        }
        return true;
    }

    private DefaultMutableTreeNode[] deepCopies(TreePath[] paths, boolean regenerateEditId) {
        DefaultMutableTreeNode copies[] = new DefaultMutableTreeNode[paths.length];
        for (int i = 0; i < paths.length; i++) {
            final DefaultMutableTreeNode original = (DefaultMutableTreeNode) paths[i].getLastPathComponent();
            copies[i] = deepCopy(original, regenerateEditId);
        }
        return copies;
    }

    private DefaultMutableTreeNode deepCopy(DefaultMutableTreeNode original, boolean regenerateEditId) {
        Object userObject = original.getUserObject();
        if (userObject instanceof EditNodeAbstract originalData) {
            userObject = originalData.deepCopy(regenerateEditId);
        } else {
            userObject = String.valueOf(userObject);
        }
        DefaultMutableTreeNode copy = new DefaultMutableTreeNode(userObject);
        for (int i = 0; i < original.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) original.getChildAt(i);
            copy.add(deepCopy(child, regenerateEditId));
        }
        return copy;
    }

}
