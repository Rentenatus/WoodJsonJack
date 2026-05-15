/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

/**
 * Represents the editable tree structure for JSON data. Maintains a hierarchy
 * of EditNode instances and provides fast lookup by ID.
 */
public class EditTree {

    private final EditNode root;

    public EditTree(EditNode root) {
        if (root == null) {
            throw new IllegalArgumentException("Root node cannot be null");
        }
        this.root = root;
    }

    public EditNode getRoot() {
        return root;
    }

    public EditNode findNodeById(long id) {
        EditNode root = getRoot();
        if (root == null) {
            return null;
        }

        java.util.ArrayDeque<EditNode> stack = new java.util.ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            EditNode node = stack.pop();

            if (node.getEditId() == id) {
                return node;
            }

            for (int i = node.getChildCount() - 1; i >= 0; i--) {
                EditNode child = node.getChildAt(i);
                if (child != null) {
                    stack.push(child);
                }
            }
        }

        return null;
    }

    public boolean containsNode(long id) {
        return findNodeById(id) != null;
    }

    public boolean addNode(long parentId, EditNode newNode) {
        return addNode(parentId, newNode, -1);
    }

    public boolean addNode(long parentId, EditNode newNode, int index) {
        if (newNode == null) {
            throw new IllegalArgumentException("New node cannot be null");
        }

        EditNode parent = findNodeById(parentId);
        if (parent == null) {
            throw new IllegalStateException("Parent node with ID " + parentId + " not found");
        }

        if (index >= 0) {
            parent.addChild(newNode, index);
        } else {
            parent.addChild(newNode);
        }

        return true;
    }

    /**
     * Adds multiple nodes to the tree efficiently. Nodes are added in the order
     * they appear in the array.
     *
     * @param parentIds the parent IDs for each node
     * @param newNodes the nodes to add
     * @param indices the indices for each node (-1 for append)
     * @return true if all nodes were added successfully
     */
    public boolean addNodes(long[] parentIds, EditNode[] newNodes, int[] indices) {
        if (newNodes == null || parentIds == null || indices == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        if (newNodes.length != parentIds.length || newNodes.length != indices.length) {
            throw new IllegalArgumentException("Arrays must have the same length");
        }

        for (int i = 0; i < newNodes.length; i++) {
            addNode(parentIds[i], newNodes[i], indices[i]);
        }
        return true;
    }

    public EditNode removeNode(long nodeId) {
        EditNode node = findNodeById(nodeId);
        if (node == null) {
            return null;
        }

        if (node == root) {
            throw new IllegalStateException("Cannot remove root node");
        }

        EditNode parent = node.getParent();
        if (parent == null) {
            throw new IllegalStateException("Node has no parent");
        }

        boolean removed = parent.removeChild(node);

        return removed ? node : null;
    }

    /**
     * Removes multiple nodes from the tree efficiently. Nodes are removed in
     * reverse order to maintain correct indices.
     *
     * @param nodeIds the IDs of the nodes to remove
     */
    public void removeNodes(long[] nodeIds) {
        // Remove in reverse order to maintain correct indices
        for (int i = nodeIds.length - 1; i >= 0; i--) {
            removeNode(nodeIds[i]);
        }
    }

    public boolean moveNode(long nodeId, long newParentId, int newIndex) {
        EditNode node = findNodeById(nodeId);
        EditNode newParent = findNodeById(newParentId);

        if (node == null) {
            throw new IllegalStateException("Node with ID " + nodeId + " not found");
        }
        if (newParent == null) {
            throw new IllegalStateException("New parent with ID " + newParentId + " not found");
        }
        if (node == newParent) {
            throw new IllegalStateException("Cannot move node to be its own parent");
        }

        EditNode oldParent = node.getParent();
        if (oldParent != null) {
            oldParent.removeChild(node);
        }

        if (newIndex >= 0) {
            newParent.addChild(node, newIndex);
        } else {
            newParent.addChild(node);
        }

        return true;
    }

    /**
     * Moves multiple nodes efficiently. Nodes are moved in the order they
     * appear in the array.
     *
     * @param nodeIds the IDs of the nodes to move
     * @param newParentIds the new parent IDs for each node
     * @param newIndices the new indices for each node (-1 for append)
     */
    public void moveNodes(long[] nodeIds, long[] newParentIds, int[] newIndices) {
        for (int i = 0; i < nodeIds.length; i++) {
            moveNode(nodeIds[i], newParentIds[i], newIndices[i]);
        }
    }

    public int getNodeCount() {
        EditNode root = getRoot();
        if (root == null) {
            return 0;
        }

        int count = 0;
        java.util.ArrayDeque<EditNode> stack = new java.util.ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            EditNode node = stack.pop();
            count++;

            for (int i = node.getChildCount() - 1; i >= 0; i--) {
                EditNode child = node.getChildAt(i);
                if (child != null) {
                    stack.push(child);
                }
            }
        }

        return count;
    }

    public void clear() {
        while (root.getChildCount() > 0) {
            EditNode child = root.getChildAt(0);
            root.removeChild(child);
        }
    }

    private void buildReachableSet(EditNode node, java.util.Set<Long> reachable) {
        reachable.add(node.getEditId());
        for (int i = 0; i < node.getChildCount(); i++) {
            buildReachableSet(node.getChildAt(i), reachable);
        }
    }

    @Override
    public String toString() {
        return "EditTree[root=" + root + ", nodes=" + getNodeCount() + "]";
    }
}
