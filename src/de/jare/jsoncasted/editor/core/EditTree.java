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

    private final EditNodeAbstract root;
    private final EditTime weightMonitor = new EditTime();

    public EditTree(String rootText) {
        this(new EditNodeObject(String.valueOf(rootText)));
    }

    EditTree(EditNodeAbstract root) {
        this.root = root;
    }

    public EditNodeAbstract getRoot() {
        return root;
    }

    public EditNodeAbstract findNodeById(long id) {
        if (root == null) {
            return null;
        }

        java.util.ArrayDeque<EditNodeAbstract> stack = new java.util.ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            EditNodeAbstract node = stack.pop();

            if (node.getEditId() == id) {
                return node;
            }

            for (int i = node.getChildCount() - 1; i >= 0; i--) {
                EditNodeAbstract child = (EditNodeAbstract) node.getChildAt(i);
                if (child != null) {
                    stack.push(child);
                }
            }
        }
        return null;
    }

    public boolean hasNodeStarting(EditNodeAbstract start, EditNodeAbstract search) {
        if (start == null) {
            return false;
        }

        java.util.ArrayDeque<EditNodeAbstract> stack = new java.util.ArrayDeque<>();
        stack.push(start);

        while (!stack.isEmpty()) {
            EditNodeAbstract node = stack.pop();

            if (node == search) {
                return true;
            }

            for (int i = node.getChildCount() - 1; i >= 0; i--) {
                EditNodeAbstract child = (EditNodeAbstract) node.getChildAt(i);
                if (child != null) {
                    stack.push(child);
                }
            }
        }
        return false;
    }

    public boolean containsNode(long id) {
        return findNodeById(id) != null;
    }

    public boolean addNode(long parentId, EditNodeAbstract newNode) {
        return addNode(parentId, newNode, -1);
    }

    public boolean addNode(long parentId, EditNodeAbstract newNode, int index) {
        if (newNode == null) {
            throw new IllegalArgumentException("New node cannot be null");
        }

        EditNodeAbstract parent = findNodeById(parentId);
        if (parent == null) {
            throw new IllegalStateException("Parent node with ID " + parentId + " not found.");
        }

        if (newNode.isOrHasParent(root)) {
            throw new IllegalArgumentException("The parent node must be a node from the tree.");
        }
        checkCycles(newNode, parent);

        if (index >= 0) {
            parent.addChild(newNode, index, weightMonitor);
        } else {
            parent.addChild(newNode, weightMonitor);
        }

        return true;
    }

    public EditNodeAbstract addNewChild(EditNodeAbstract parentNode, String nodeText) {
        checkParentProps(parentNode);
        return parentNode.addNewChild(nodeText, weightMonitor);
    }

    public EditNodeAbstract addNewChild(EditNodeAbstract parentNode, String nodeText, int index) {
        checkParentProps(parentNode);
        return parentNode.addNewChild(nodeText, index, weightMonitor);
    }

    private void checkParentProps(EditNodeAbstract parentNode) throws IllegalArgumentException {
        if (parentNode == null) {
            throw new IllegalArgumentException("Parent node cannot be null.");
        }
        checkParentMembership(parentNode);
    }

    private void checkNewAndParentProps(EditNodeAbstract newNode, EditNodeAbstract parentNode) throws IllegalArgumentException {
        if (newNode == null || parentNode == null) {
            throw new IllegalArgumentException("New and parent nodes cannot be null.");
        }
        checkParentMembership(parentNode);
        checkCycles(newNode, parentNode);
        if (!newNode.canBeChildOf(parentNode)) {
            throw new IllegalArgumentException("This new node cannot become a child of the specified parent.");
        }
    }

    private void checkParentMembership(EditNodeAbstract parentNode) throws IllegalArgumentException {
        if (!parentNode.isOrHasParent(root)) {
            throw new IllegalArgumentException("The parent node must be a node from the tree");
        }
    }

    private void checkCycles(EditNodeAbstract newNode, EditNodeAbstract parentNode) throws IllegalArgumentException {
        if (hasNodeStarting(newNode, parentNode)) {
            throw new IllegalArgumentException("A parent node must not be cyclically contained within a child node.");
        }
    }

    public void addChild(EditNodeAbstract parentNode, EditNodeAbstract newNode) {
        checkNewAndParentProps(newNode, parentNode);

        parentNode.addChild(newNode, weightMonitor);
    }

    public void addChild(EditNodeAbstract parentNode, EditNodeAbstract newNode, int index) {
        checkNewAndParentProps(newNode, parentNode);

        parentNode.addChild(newNode, index, weightMonitor);
    }

    public boolean removeChild(EditNodeAbstract parentNode, EditNodeAbstract child) {
        checkParentProps(parentNode);
        return parentNode.removeChild(child);
    }

    public boolean removeNode(EditNodeAbstract child) {
        if (child == null) {
            throw new IllegalArgumentException("Child node cannot be null");
        }
        EditNodeAbstract parentNode = child.getParent();
        if (parentNode == null) {
            return false;
        }
        checkParentMembership(parentNode);
        return parentNode.removeChild(child);
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
    public boolean addNodes(long[] parentIds, EditNodeAbstract[] newNodes, int[] indices) {
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
        EditNodeAbstract node = findNodeById(nodeId);
        if (node == null) {
            return null;
        }

        if (node == root) {
            throw new IllegalStateException("Cannot remove root node");
        }

        EditNodeAbstract parent = node.getParent();
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

    public int getNodeCount() {
        if (root == null) {
            return 0;
        }

        int count = 0;
        java.util.ArrayDeque<EditNodeAbstract> stack = new java.util.ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            EditNodeAbstract node = stack.pop();
            count++;

            for (int i = node.getChildCount() - 1; i >= 0; i--) {
                EditNodeAbstract child = (EditNodeAbstract) node.getChildAt(i);
                if (child != null) {
                    stack.push(child);
                }
            }
        }

        return count;
    }

    public void clear() {
        while (root.getChildCount() > 0) {
            EditNodeAbstract child = (EditNodeAbstract) root.getChildAt(0);
            root.removeChild(child);
        }
    }

    public void buildReachableSet(EditNode node, java.util.Set<Long> reachable) {
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
