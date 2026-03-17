/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.data.JsonTreeNodeData;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

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

    public void pasteClipboard(WoodEditTree trigger, TreePath path) {
        if (clipboardNodes == null || clipboardNodes.length == 0) {
            return;
        }

        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) path.getLastPathComponent();
        DefaultTreeModel model = (DefaultTreeModel) trigger.getModel();

        int index = parent.getChildCount();
        DefaultMutableTreeNode lastCopy = null;

        for (DefaultMutableTreeNode node : clipboardNodes) {
            DefaultMutableTreeNode copy = deepCopy(node, !cut);
            model.insertNodeInto(copy, parent, index++);
            lastCopy = copy;
        }
        this.cut = false;

        if (lastCopy != null) {
            TreePath newPath = new TreePath(lastCopy.getPath());
            trigger.setSelectionPath(newPath);
            trigger.scrollPathToVisible(newPath);
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

    public boolean canPasteTo(JsonTreeNodeData targetData) {

        if (targetData == null || clipboardNodes == null || clipboardNodes.length == 0) {
            return false;
        }
        for (DefaultMutableTreeNode candidate : clipboardNodes) {
            Object clipUo = candidate.getUserObject();
            if (!(clipUo instanceof JsonTreeNodeData clipData)) {
                return false;
            }
            if (!clipData.canBeChildOf(targetData)) {
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
        if (userObject instanceof JsonTreeNodeData originalData) {
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
