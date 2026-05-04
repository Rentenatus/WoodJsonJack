/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the editable tree structure for JSON data.
 * Maintains a hierarchy of EditNode instances and provides fast lookup by ID.
 */
public class EditTree {

    private final EditNode root;
    private final Map<Long, EditNode> nodeRegistry;

    public EditTree(EditNode root) {
        if (root == null) {
            throw new IllegalArgumentException("Root node cannot be null");
        }
        this.root = root;
        this.nodeRegistry = new HashMap<>();
        registerNode(root);
    }

    public EditNode getRoot() {
        return root;
    }

    public EditNode findNodeById(long id) {
        return nodeRegistry.get(id);
    }

    public boolean containsNode(long id) {
        return nodeRegistry.containsKey(id);
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

        registerNode(newNode);
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
        if (removed) {
            unregisterNode(node);
        }

        return node;
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

    public int getNodeCount() {
        return nodeRegistry.size();
    }

    public void clear() {
        while (root.getChildCount() > 0) {
            EditNode child = root.getChildAt(0);
            root.removeChild(child);
            unregisterNode(child);
        }
    }

    private void registerNode(EditNode node) {
        nodeRegistry.put(node.getEditId(), node);
        for (int i = 0; i < node.getChildCount(); i++) {
            registerNode(node.getChildAt(i));
        }
    }

    private void unregisterNode(EditNode node) {
        nodeRegistry.remove(node.getEditId());
        for (int i = 0; i < node.getChildCount(); i++) {
            unregisterNode(node.getChildAt(i));
        }
    }

    public boolean validate() {
        if (!nodeRegistry.containsKey(root.getEditId())) {
            return false;
        }

        java.util.Set<Long> reachable = new java.util.HashSet<>();
        buildReachableSet(root, reachable);

        if (reachable.size() != nodeRegistry.size()) {
            return false;
        }

        for (EditNode node : nodeRegistry.values()) {
            EditNode parent = node.getParent();
            if (parent != null) {
                if (!nodeRegistry.containsKey(parent.getEditId())) {
                    return false;
                }
                if (parent.getChildIndex(node) == -1) {
                    return false;
                }
            } else if (node != root) {
                return false;
            }
        }

        return true;
    }

    private void buildReachableSet(EditNode node, java.util.Set<Long> reachable) {
        reachable.add(node.getEditId());
        for (int i = 0; i < node.getChildCount(); i++) {
            buildReachableSet(node.getChildAt(i), reachable);
        }
    }

    @Override
    public String toString() {
        return "EditTree[root=" + root + ", nodes=" + nodeRegistry.size() + "]";
    }
}
