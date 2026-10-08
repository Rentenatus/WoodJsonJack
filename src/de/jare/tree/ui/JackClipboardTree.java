/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.clipboard.ClipboardManager;
import de.jare.jsoncasted.editor.clipboard.ClipboardStash;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.tree.control.JackMasterControl;
import de.jare.tree.control.listeners.TreeFocusComponent;
import java.util.Set;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/**
 * Clipboard tree implementation for JackEditTree with ClipboardStash
 * support. Shows the content of the current stash and allows
 * switching between stashes.
 */
public class JackClipboardTree extends JTree {

    private final ClipboardManager clipboardManager;
    private JackEditTree sourceTree;
    private String currentStashName;
    private final ClipboardManager.ClipboardChangeListener clipboardChangeListener = this::onClipboardChanged;

    public JackClipboardTree(ClipboardManager clipboardManager) {
        super(new DefaultMutableTreeNode("Clipboard"));
        this.clipboardManager = clipboardManager;
        this.currentStashName = ClipboardManager.CLIPBOARD_STASH_NAME;
        setEditable(false);
        setRootVisible(true);
        setShowsRootHandles(true);

        // register listener for clipboard changes (higher priority for content updates)
        clipboardManager.addClipboardChangeListener(4, clipboardChangeListener);

        // show the clipboard stash by default
        showStashContent(currentStashName);
    }

    public JackClipboardTree(ClipboardManager clipboardManager, JackMasterControl master) {
        this(clipboardManager);
    }

    public void setSourceTree(JackEditTree sourceTree) {
        this.sourceTree = sourceTree;
    }

    public JackEditTree getSourceTree() {
        return sourceTree;
    }

    /**
     * Switches the shown stash and displays its content.
     *
     * @param stashName the name of the stash to show
     */
    public void switchStash(String stashName) {
        if (stashName == null || stashName.equals(currentStashName)) {
            return;
        }
        this.currentStashName = stashName;
        showStashContent(stashName);
    }

    /**
     * Shows the content of the given stash in the tree.
     *
     * @param stashName the name of the stash
     */
    public void showStashContent(String stashName) {
        ClipboardStash stash = clipboardManager.getStash(stashName);
        if (stash == null) {
            return;
        }

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) getModel().getRoot();
        root.removeAllChildren();

        EditNodeAbstract[] nodes = stash.getNodes();
        if (nodes != null && nodes.length > 0) {
            for (EditNodeAbstract node : nodes) {
                if (node != null) {
                    root.add(buildTreeNodeFromEditNode(node));
                }
            }
        }

        ((DefaultTreeModel) getModel()).reload();

        // update the root name
        root.setUserObject("Clipboard - " + stashName);
        ((DefaultTreeModel) getModel()).nodeChanged(root);

        // restore expansion state if available
        Set<Long> expandedNodeIds = stash.getExpandedNodeIds();
        if (expandedNodeIds != null && !expandedNodeIds.isEmpty()) {
            restoreExpandedNodes(expandedNodeIds);
        } else if (getRowCount() > 0) {
            // fallback: first node expanded
            expandRow(0);
        }
    }

    /**
     * Restores the expansion states for the given edit node ids.
     *
     * @param expandedNodeIds set of edit node ids that should be expanded
     */
    private void restoreExpandedNodes(Set<Long> expandedNodeIds) {
        if (expandedNodeIds == null || expandedNodeIds.isEmpty()) {
            return;
        }

        restoreExpandedNodes((DefaultMutableTreeNode) getModel().getRoot(), expandedNodeIds);
    }

    /**
     * Recursive: restores the expansion states for the children of a node.
     */
    private void restoreExpandedNodes(DefaultMutableTreeNode node, Set<Long> expandedNodeIds) {
        if (node == null) {
            return;
        }

        Object uo = node.getUserObject();
        if (uo instanceof EditNodeAbstract editNode) {
            if (expandedNodeIds.contains(editNode.getEditId())) {
                TreePath path = new TreePath(node.getPath());
                expandPath(path);
                // recursively for all children
                for (int i = 0; i < node.getChildCount(); i++) {
                    restoreExpandedNodes((DefaultMutableTreeNode) node.getChildAt(i), expandedNodeIds);
                }
            }
        } else {
            // check all children
            for (int i = 0; i < node.getChildCount(); i++) {
                restoreExpandedNodes((DefaultMutableTreeNode) node.getChildAt(i), expandedNodeIds);
            }
        }
    }

    /**
     * Listener callback for clipboard changes. Called when
     * a stash changes.
     *
     * @param stashName the name of the changed stash, or null for all
     */
    private void onClipboardChanged(String stashName) {
        // update the display when the changed stash is the current one
        // or when all stashes are affected (stashName == null)
        if (stashName == null || stashName.equals(currentStashName)) {
            SwingUtilities.invokeLater(() -> refreshCurrentStash());
        }
    }

    /**
     * Creates a JTree node from an EditNodeAbstract.
     *
     * @param node the EditNodeAbstract
     * @return the created DefaultMutableTreeNode
     */
    private DefaultMutableTreeNode buildTreeNodeFromEditNode(EditNodeAbstract node) {
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);

        for (int i = 0; i < node.getChildCount(); i++) {
            EditNodeAbstract child = node.getChildAt(i);
            if (child != null) {
                treeNode.add(buildTreeNodeFromEditNode(child));
            }
        }

        return treeNode;
    }

    /**
     * Checks whether the nodes of the current stash can be inserted at the
     * target position.
     *
     * @param targetData the target EditNode
     * @return true when insertion is possible
     */
    public boolean canPasteTo(EditNode targetData) {
        if (targetData == null) {
            return false;
        }

        ClipboardStash stash = clipboardManager.getStash(currentStashName);
        if (stash == null || stash.isEmpty()) {
            return false;
        }

        return canPasteTo(targetData, stash.getNodes());
    }

    private boolean canPasteTo(EditNode targetData, EditNodeAbstract[] nodes) {
        if (targetData == null || nodes == null || nodes.length == 0) {
            return false;
        }

        for (EditNodeAbstract candidate : nodes) {
            if (candidate == null) {
                continue;
            }
            if (!targetData.canBeChildOf(candidate)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Refreshes the display of the current stash.
     */
    public void refreshCurrentStash() {
        showStashContent(currentStashName);
    }

    /**
     * Returns the current stash name.
     *
     * @return the name of the current stash
     */
    public String getCurrentStashName() {
        return currentStashName;
    }

    /**
     * Returns the ClipboardManager.
     *
     * @return the ClipboardManager
     */
    public ClipboardManager getClipboardManager() {
        return clipboardManager;
    }

    /**
     * Clears the content of the current stash.
     */
    public void clearCurrentStash() {
        clipboardManager.clearStash(currentStashName);
        showStashContent(currentStashName);
    }

    /**
     * Creates a new stash with the given name.
     *
     * @param name the name of the new stash
     */
    public void createNewStash(String name) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        clipboardManager.createStash(name);
    }

    /**
     * Deletes the stash with the given name.
     *
     * @param name the name of the stash to delete
     */
    public void removeStash(String name) {
        if (name == null || name.equals(currentStashName)) {
            return;
        }
        clipboardManager.removeStash(name);
    }

    /**
     * Returns all available stash names.
     *
     * @return array with all stash names
     */
    public String[] getAllStashNames() {
        return clipboardManager.getStashNames();
    }

    /**
     * Returns the current stash.
     *
     * @return the current ClipboardStash
     */
    public ClipboardStash getCurrentStash() {
        return clipboardManager.getStash(currentStashName);
    }
}
