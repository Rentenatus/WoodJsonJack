/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.control.commands.WoodCommandAddNodes;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.jsoncasted.editor.clipboard.ClipboardManager;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/**
 * Clipboard tree component that displays and manages copied/cut nodes.
 * Supports both the legacy TreeModel-based system (DefaultMutableTreeNode)
 * and the new EditTree-based system (EditNode).
 */
public class WoodClipboardTree extends JTree {

    // Legacy fields for TreeModel-based clipboard
    private DefaultMutableTreeNode[] clipboardNodes;
    private WoodEditTree sourceTree;
    private boolean cut;
    
    // New fields for EditTree-based clipboard
    private ClipboardManager clipboardManager;
    private EditTree editTree;

    public WoodClipboardTree() {
        super(new DefaultMutableTreeNode("Clipboard"));
        this.cut = false;
        setEditable(false);
        setRootVisible(true);
        setShowsRootHandles(true);
        
        // Initialize ClipboardManager for EditTree support
        this.clipboardManager = new ClipboardManager();
    }

    // ========================================================================
    // Legacy methods for TreeModel-based clipboard (backward compatible)
    // ========================================================================

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
        
        // Also clear the ClipboardManager
        if (clipboardManager != null) {
            clipboardManager.clearActiveStash();
        }
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
        if (userObject instanceof EditNode originalData) {
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

    // ========================================================================
    // New methods for EditTree-based clipboard
    // ========================================================================

    /**
     * Sets the EditTree for this clipboard.
     * 
     * @param editTree the EditTree to use for EditNode-based operations
     */
    public void setEditTree(EditTree editTree) {
        this.editTree = editTree;
    }

    /**
     * Gets the EditTree associated with this clipboard.
     * 
     * @return the EditTree, or null if not set
     */
    public EditTree getEditTree() {
        return editTree;
    }

    /**
     * Gets the ClipboardManager for EditNode-based operations.
     * 
     * @return the ClipboardManager
     */
    public ClipboardManager getClipboardManager() {
        return clipboardManager;
    }

    /**
     * Sets a custom ClipboardManager.
     * 
     * @param clipboardManager the ClipboardManager to use
     */
    public void setClipboardManager(ClipboardManager clipboardManager) {
        this.clipboardManager = clipboardManager;
    }

    /**
     * Copies EditNode selection to the clipboard using ClipboardManager.
     * 
     * @param tree the source EditTree
     * @param nodeIds the IDs of the nodes to copy
     * @param cut whether this is a cut operation
     */
    public void copyEditSelection(EditTree tree, long[] nodeIds, boolean cut) {
        if (clipboardManager != null) {
            if (cut) {
                clipboardManager.cutToActiveStash(tree, nodeIds);
            } else {
                clipboardManager.copyToActiveStash(tree, nodeIds);
            }
            this.cut = cut;
            this.editTree = tree;
            updateClipboardDisplay();
        }
    }

    /**
     * Copies EditNode array to the clipboard using ClipboardManager.
     * 
     * @param tree the source EditTree
     * @param nodes the EditNodes to copy
     * @param cut whether this is a cut operation
     */
    public void copyEditSelection(EditTree tree, EditNode[] nodes, boolean cut) {
        if (clipboardManager != null) {
            if (cut) {
                clipboardManager.cutToActiveStash(tree, 
                    java.util.Arrays.stream(nodes).mapToLong(EditNode::getEditId).toArray());
            } else {
                clipboardManager.copyToActiveStash(tree, nodes);
            }
            this.cut = cut;
            this.editTree = tree;
            updateClipboardDisplay();
        }
    }

    /**
     * Pastes from clipboard to EditTree using ClipboardManager.
     * 
     * @param tree the target EditTree
     * @param parentId the ID of the parent node
     * @param index the insertion index, or -1 to append
     * @return the IDs of the pasted nodes
     */
    public long[] pasteEditClipboard(EditTree tree, long parentId, int index) {
        if (clipboardManager != null) {
            long[] pastedIds = clipboardManager.pasteFromActiveStash(tree, parentId, index);
            this.cut = false;
            updateClipboardDisplay();
            return pastedIds;
        }
        this.cut = false;
        return new long[0];
    }

    /**
     * Pastes from clipboard to EditTree using ClipboardManager.
     * 
     * @param tree the target EditTree
     * @param parentId the ID of the parent node
     * @return the IDs of the pasted nodes
     */
    public long[] pasteEditClipboard(EditTree tree, long parentId) {
        return pasteEditClipboard(tree, parentId, -1);
    }

    /**
     * Checks if paste is possible to the target EditNode.
     * 
     * @param tree the target EditTree
     * @param targetData the target EditNode
     * @return true if paste is possible
     */
    public boolean canPasteToEdit(EditTree tree, EditNode targetData) {
        if (clipboardManager == null || targetData == null) {
            return false;
        }
        EditNode[] clipboardContent = clipboardManager.getActiveStashContent();
        if (clipboardContent == null || clipboardContent.length == 0) {
            return false;
        }
        for (EditNode clipNode : clipboardContent) {
            if (clipNode != null && !targetData.canBeChildOf(clipNode)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Clears the EditTree-based clipboard.
     */
    public void clearEditClipboard() {
        if (clipboardManager != null) {
            clipboardManager.clearActiveStash();
        }
        updateClipboardDisplay();
    }

    /**
     * Returns whether the EditTree-based clipboard is empty.
     * 
     * @return true if clipboard is empty
     */
    public boolean isEditClipboardEmpty() {
        return clipboardManager == null || clipboardManager.isActiveStashEmpty();
    }

    /**
     * Returns the content of the EditTree-based clipboard.
     * 
     * @return the EditNodes in the clipboard
     */
    public EditNode[] getEditClipboardContent() {
        if (clipboardManager != null) {
            return clipboardManager.getActiveStashContent();
        }
        return new EditNode[0];
    }

    /**
     * Updates the clipboard display to show current content.
     */
    private void updateClipboardDisplay() {
        if (clipboardManager != null && !clipboardManager.isActiveStashEmpty()) {
            EditNode[] nodes = clipboardManager.getActiveStashContent();
            if (nodes != null && nodes.length > 0) {
                // Convert EditNodes to DefaultMutableTreeNode for display
                DefaultMutableTreeNode[] displayNodes = new DefaultMutableTreeNode[nodes.length];
                for (int i = 0; i < nodes.length; i++) {
                    displayNodes[i] = convertEditNodeToTreeNode(nodes[i]);
                }
                showClipboardContent(displayNodes);
            } else {
                showClipboardContent(null);
            }
        } else if (clipboardNodes != null) {
            showClipboardContent(clipboardNodes);
        } else {
            showClipboardContent(null);
        }
    }

    /**
     * Converts an EditNode to a DefaultMutableTreeNode for display.
     * 
     * @param editNode the EditNode to convert
     * @return the DefaultMutableTreeNode
     */
    private DefaultMutableTreeNode convertEditNodeToTreeNode(EditNode editNode) {
        if (editNode == null) {
            return null;
        }
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(editNode.deepCopy(false));
        for (int i = 0; i < editNode.getChildCount(); i++) {
            EditNode child = editNode.getChildAt(i);
            DefaultMutableTreeNode childNode = convertEditNodeToTreeNode(child);
            if (childNode != null) {
                node.add(childNode);
            }
        }
        return node;
    }

}
