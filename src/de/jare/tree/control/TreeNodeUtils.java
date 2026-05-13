/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;

/**
 * Utility class for tree node operations.
 * Supports both TreeModel-based (DefaultMutableTreeNode) and EditTree-based (EditNode) operations.
 * Replaces the WoodUtils interface with concrete utility methods.
 */
public final class TreeNodeUtils {

    private TreeNodeUtils() {
        // Utility class - prevent instantiation
    }

    // ========================================================================
    // TreeModel-based utilities (legacy support)
    // ========================================================================

    /**
     * Finds a node by its edit ID in a TreeModel.
     *
     * @param model the TreeModel to search
     * @param id the edit ID to find
     * @return the DefaultMutableTreeNode with the matching edit ID, or null if not found
     */
    public static DefaultMutableTreeNode findNodeByEditId(TreeModel model, long id) {
        Object root = model.getRoot();
        if (!(root instanceof DefaultMutableTreeNode dmtn)) {
            return null;
        }
        return findNodeByEditId(dmtn, id);
    }

    /**
     * Finds a node by its edit ID in a DefaultMutableTreeNode subtree.
     *
     * @param node the root node to search
     * @param id the edit ID to find
     * @return the DefaultMutableTreeNode with the matching edit ID, or null if not found
     */
    public static DefaultMutableTreeNode findNodeByEditId(DefaultMutableTreeNode node, long id) {
        Object uo = node.getUserObject();
        if (uo instanceof EditNode data && data.getEditId() == id) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            DefaultMutableTreeNode found = findNodeByEditId(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Creates a deep copy of a DefaultMutableTreeNode and its subtree.
     * EditNode user objects are also deep-copied with regenerateEditId=false.
     *
     * @param original the node to copy
     * @return a deep copy of the node
     */
    public static DefaultMutableTreeNode deepCopy(DefaultMutableTreeNode original) {
        return deepCopy(original, false);
    }

    /**
     * Creates a deep copy of a DefaultMutableTreeNode and its subtree.
     *
     * @param original the node to copy
     * @param regenerateEditId whether EditNode edit IDs should be regenerated
     * @return a deep copy of the node
     */
    public static DefaultMutableTreeNode deepCopy(DefaultMutableTreeNode original, boolean regenerateEditId) {
        Object uo = original.getUserObject();
        if (uo instanceof EditNode data) {
            uo = data.deepCopy(regenerateEditId);
        } else {
            uo = String.valueOf(uo);
        }
        DefaultMutableTreeNode copy = new DefaultMutableTreeNode(uo);
        for (int i = 0; i < original.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) original.getChildAt(i);
            copy.add(deepCopy(child, regenerateEditId));
        }
        return copy;
    }

    // ========================================================================
    // EditTree-based utilities (new editor package support)
    // ========================================================================

    /**
     * Finds a node by its edit ID in an EditTree.
     *
     * @param tree the EditTree to search
     * @param id the edit ID to find
     * @return the EditNode with the matching ID, or null if not found
     */
    public static EditNode findEditNodeById(EditTree tree, long id) {
        if (tree == null) {
            return null;
        }
        return tree.findNodeById(id);
    }

    /**
     * Finds a node by its edit ID, trying both TreeModel and EditTree.
     * This is a bridge method for code that needs to work with both systems.
     *
     * @param model the TreeModel (may be null)
     * @param editTree the EditTree (may be null)
     * @param id the edit ID to find
     * @return the found node as Object (either DefaultMutableTreeNode or EditNode), or null
     */
    public static Object findNodeByEditId(TreeModel model, EditTree editTree, long id) {
        if (editTree != null) {
            EditNode editNode = findEditNodeById(editTree, id);
            if (editNode != null) {
                return editNode;
            }
        }
        if (model != null) {
            return findNodeByEditId(model, id);
        }
        return null;
    }

    // ========================================================================
    // Conversion utilities
    // ========================================================================

    /**
     * Converts an EditNode to a DefaultMutableTreeNode.
     *
     * @param editNode the EditNode to convert
     * @return a DefaultMutableTreeNode wrapping the EditNode
     */
    public static DefaultMutableTreeNode toTreeNode(EditNode editNode) {
        if (editNode == null) {
            return null;
        }
        return new DefaultMutableTreeNode(editNode);
    }

    /**
     * Converts an EditNode to a DefaultMutableTreeNode with a deep copy.
     *
     * @param editNode the EditNode to convert
     * @param regenerateEditId whether to regenerate edit IDs
     * @return a DefaultMutableTreeNode wrapping a deep copy of the EditNode
     */
    public static DefaultMutableTreeNode toTreeNode(EditNode editNode, boolean regenerateEditId) {
        if (editNode == null) {
            return null;
        }
        return new DefaultMutableTreeNode(editNode.deepCopy(regenerateEditId));
    }

    /**
     * Extracts the EditNode from a DefaultMutableTreeNode.
     *
     * @param treeNode the tree node
     * @return the EditNode, or null if the user object is not an EditNode
     */
    public static EditNode toEditNode(DefaultMutableTreeNode treeNode) {
        if (treeNode == null) {
            return null;
        }
        Object uo = treeNode.getUserObject();
        return uo instanceof EditNode ? (EditNode) uo : null;
    }

}
