/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.tree.control.model;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

/**
 *
 * @author Jansuch Rentenatus
 */
public class JackTreeModel extends DefaultTreeModel {

    private final EditTree editTree;

    public JackTreeModel(String rootName) {
        this(new EditTree("{" + rootName + "}"));
    }

    public JackTreeModel(EditTree editTree) {
        super(buildRoot(editTree));
        this.editTree = editTree;
    }

    public EditTree getEditTree() {
        return editTree;
    }

    /**
     * Returns the JsonModelDescriptor from the underlying EditTree.
     *
     * @return the model descriptor, or {@code null} if not set
     */
    public de.jare.jsoncasted.model.descriptor.JsonModelDescriptor getJsonModelDescriptor() {
        if (editTree == null) {
            return null;
        }
        return editTree.getJsonModelDescriptor();
    }

    public DefaultMutableTreeNode findNodeByIdAndRange(long id, long left, long times) {
        return findNodeByIdAndRange(id, left, times, (DefaultMutableTreeNode) getRoot(), true);
    }

    /**
     * The tree cell editor returns the typed text; the rename itself ran as a command
     * through the editor (see JsonJackTreeCellEditor). The default implementation of
     * DefaultTreeModel would plant that String as the user object of the swing node -
     * the node keeps rendering, but every id-based walk skips or crashes on it until
     * the next full rebuild (ClassCastException in the fallback of findNodeById). The
     * swing node keeps its EditNode payload instead; the repaint fires as usual.
     *
     * @param path the path of the edited node
     * @param newValue the editor value, ignored here
     */
    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
        if (path != null && path.getLastPathComponent() instanceof DefaultMutableTreeNode dmtn
                && dmtn.getUserObject() instanceof EditNode) {
            nodeChanged(dmtn);
        }
    }

    public DefaultMutableTreeNode findNodeByIdAndRange(long id, long left, long times, boolean fallback) {
        return findNodeByIdAndRange(id, left, times, (DefaultMutableTreeNode) getRoot(), fallback);
    }

    public DefaultMutableTreeNode findNodeByIdAndRange(long id, long left, long times, DefaultMutableTreeNode DMStart, boolean fallback) {
        DefaultMutableTreeNode ret = findNodeByIdAndRange(id, left, times, DMStart);
        if (ret == null && fallback) {
            return findNodeById(id, DMStart);
        }
        return ret;
    }

    private DefaultMutableTreeNode findNodeByIdAndRange(long id, long left, long times, DefaultMutableTreeNode DMStart) {
        if (DMStart == null) {
            return null;
        }
        if (!(DMStart.getUserObject() instanceof EditNodeAbstract startNode)) {
            return null;
        }
        // Check if current node matches the id
        if (startNode.getEditId() == id) {
            return DMStart;
        }

        // Iterate through all children
        for (int i = 0; i < DMStart.getChildCount(); i++) {
            DefaultMutableTreeNode DMChild = (DefaultMutableTreeNode) DMStart.getChildAt(i);
            if (!(DMChild.getUserObject() instanceof EditNodeAbstract child)) {
                continue;
            }
            if (child == null) {
                continue;
            }

            // If child matches the id, return it
            if (child.getEditId() == id) {
                return DMChild;
            }

            // Check if timesRange is greater than times - use old method
            if (child.getTimesRange() > times) {
                DefaultMutableTreeNode DMResult = findNodeById(id, DMChild);
                if (DMResult != null) {
                    return DMResult;
                }
                continue;
            }

            // Check if left is within the child's range (inclusive)
            if (left >= child.getLeftRange() && left <= child.getRightRange()) {
                DefaultMutableTreeNode DMResult = findNodeByIdAndRange(id, left, times, DMChild);
                if (DMResult != null) {
                    return DMResult;
                }
            }

        }
        return null;
    }

    public DefaultMutableTreeNode findNodeById(long id) {
        return findNodeById(id, (DefaultMutableTreeNode) getRoot());
    }

    public DefaultMutableTreeNode findNodeById(long id, DefaultMutableTreeNode DMStart) {
        if (DMStart == null) {
            return null;
        }

        java.util.ArrayDeque<DefaultMutableTreeNode> stack = new java.util.ArrayDeque<>();
        stack.push(DMStart);

        while (!stack.isEmpty()) {
            DefaultMutableTreeNode node = stack.pop();

            // foreign payloads (a planted String, for example) are no edit nodes: skip
            // them instead of crashing the walk - they carry no id to match anyway
            if (!(node.getUserObject() instanceof EditNode edNode)) {
                continue;
            }

            if (edNode.getEditId() == id) {
                return node;
            }

            for (int i = node.getChildCount() - 1; i >= 0; i--) {
                TreeNode child = node.getChildAt(i);
                if (child != null) {
                    stack.push((DefaultMutableTreeNode) child);
                }
            }
        }

        return null;
    }

    public void buildSubtreeStructureChanged(DefaultMutableTreeNode mutableTreeNode, EditNode editNode) {
        buildSubtree(mutableTreeNode, editNode);
        nodeStructureChanged(mutableTreeNode);
    }

    /**
     * Refreshes the rendering of every ancestor of the given node, up to the root: the label of
     * an edited node feeds the labels of its ancestors (an array property, for example, shows
     * the live summary of its children), and JTree recomputes the row bounds only for nodes it
     * hears about - a repainted ancestor follows the new content with the right width. The node
     * itself and its subtree are not touched. The root carries no parent, so DefaultTreeModel
     * fires no change event for it; no object label aggregates children, there is nothing to
     * refresh.
     *
     * @param editNode the node whose ancestors are refreshed, may be {@code null}
     */
    public void refreshAncestorRendering(EditNode editNode) {
        EditNode parent = editNode == null ? null : editNode.getParent();
        while (parent != null) {
            DefaultMutableTreeNode swingNode = findNodeByIdAndRange(
                    parent.getEditId(), parent.getLeftRange(), parent.getTimesRange());
            if (swingNode != null) {
                nodeChanged(swingNode);
            }
            parent = parent.getParent();
        }
    }

    private DefaultMutableTreeNode buildSubtree(DefaultMutableTreeNode rootNode, EditNode editNode) {
        rootNode.setUserObject(editNode);

        int childCount = getChildCountSafe(editNode);
        for (int i = 0; i < childCount; i++) {
            EditNode childEditNode = getChildSafe(editNode, i);
            if (childEditNode != null) {
                DefaultMutableTreeNode newChildSwingNode = buildSubtree(childEditNode);
                rootNode.add(newChildSwingNode);
            }
        }
        return rootNode;
    }

    private static DefaultMutableTreeNode buildRoot(EditTree editTree) {
        return buildSubtree(editTree.getRoot());
    }

    public static DefaultMutableTreeNode buildSubtree(EditNode editNode) {
        DefaultMutableTreeNode swingNode = new DefaultMutableTreeNode(editNode);

        int childCount = getChildCountSafe(editNode);
        for (int i = 0; i < childCount; i++) {
            EditNode child = getChildSafe(editNode, i);
            if (child != null) {
                swingNode.add(buildSubtree(child));
            }
        }
        return swingNode;
    }

    public void rebuildFromDomain() {
        DefaultMutableTreeNode newRoot = buildSubtree(editTree.getRoot());
        setRoot(newRoot);
        reload();
    }

    public int resolveChildIndex(EditNode parent, EditNode child) {
        int count = getChildCountSafe(parent);
        for (int i = 0; i < count; i++) {
            EditNode current = getChildSafe(parent, i);
            if (current == child) {
                return i;
            }
        }
        return -1;
    }

    private static int getChildCountSafe(EditNode node) {
        try {
            return node.getChildCount();
        } catch (Exception ex) {
            return 0;
        }
    }

    private static EditNode getChildSafe(EditNode node, int index) {
        try {
            return node.getChildAt(index);
        } catch (Exception ex) {
            return null;
        }
    }

}
