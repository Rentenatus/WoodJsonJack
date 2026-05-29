/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.clipboard;

import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import java.util.Collections;
import java.util.Set;

/**
 * Represents a single clipboard stash that holds an array of EditNode
 * snapshots.
 *
 * <p>
 * In the revised clipboard model, a stash is intentionally neutral: it stores
 * only pasteable subtree snapshots and does not encode whether the content
 * originated from copy or cut, and it does not remember a source tree.</p>
 *
 * <p>
 * Additionally, a stash can optionally store the expansion state of nodes
 * (as a set of edit IDs) to restore the visual state in a clipboard tree.</p>
 */
public class ClipboardStash {

    private final String name;
    private EditNodeAbstract[] nodes;
    private long timestamp;
    private Set<Long> expandedNodeIds;

    /**
     * Creates a new clipboard stash with the given name.
     *
     * @param name the name of this stash
     */
    public ClipboardStash(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Stash name cannot be null or empty");
        }
        this.name = name;
        this.nodes = new EditNodeAbstract[0];
        this.timestamp = System.currentTimeMillis();
        this.expandedNodeIds = null;
    }

    /**
     * Returns the name of this stash.
     *
     * @return the stash name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the nodes stored in this stash.
     *
     * <p>
     * A defensive copy of the array is returned. The contained node objects
     * themselves are not deep-copied here.</p>
     *
     * @return a defensive copy of the stored nodes
     */
    public EditNodeAbstract[] getNodes() {
        return nodes != null ? nodes.clone() : new EditNodeAbstract[0];
    }

    /**
     * Sets the nodes for this stash.
     *
     * <p>
     * A defensive copy of the array is stored. The node instances are assumed
     * to already represent clipboard-safe snapshots.</p>
     *
     * @param nodes the nodes to store
     */
    public void setNodes(EditNodeAbstract[] nodes) {
        if (nodes == null) {
            this.nodes = new EditNodeAbstract[0];
        } else {
            this.nodes = nodes.clone();
        }
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Sets the expansion state for nodes in this stash.
     *
     * @param expandedNodeIds a set of edit IDs for nodes that were expanded, or null
     */
    public void setExpandedNodeIds(Set<Long> expandedNodeIds) {
        this.expandedNodeIds = expandedNodeIds != null 
                ? Collections.unmodifiableSet(expandedNodeIds) 
                : null;
    }

    /**
     * Returns the expansion state for nodes in this stash.
     *
     * @return an unmodifiable set of expanded node IDs, or null if not available
     */
    public Set<Long> getExpandedNodeIds() {
        return expandedNodeIds;
    }

    /**
     * Returns the timestamp when this stash was last updated.
     *
     * @return the timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the number of nodes in this stash.
     *
     * @return the node count
     */
    public int getNodeCount() {
        return nodes != null ? nodes.length : 0;
    }

    /**
     * Clears all nodes from this stash.
     */
    public void clear() {
        this.nodes = new EditNodeAbstract[0];
        this.expandedNodeIds = null;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Returns whether this stash is empty.
     *
     * @return true if the stash contains no nodes
     */
    public boolean isEmpty() {
        return nodes == null || nodes.length == 0;
    }

    /**
     * Creates a deep copy of this stash.
     *
     * @param regenerateEditIds whether copied nodes should regenerate their
     * edit IDs
     * @return a new stash with copied node snapshots and expansion state
     */
    public ClipboardStash deepCopy(boolean regenerateEditIds) {
        ClipboardStash copy = new ClipboardStash(this.name);
        if (this.nodes != null && this.nodes.length > 0) {
            EditNodeAbstract[] copiedNodes = new EditNodeAbstract[this.nodes.length];
            for (int i = 0; i < this.nodes.length; i++) {
                copiedNodes[i] = this.nodes[i] != null
                        ? this.nodes[i].deepCopy(regenerateEditIds)
                        : null;
            }
            copy.setNodes(copiedNodes);
        }
        // Copy expansion state if available
        if (this.expandedNodeIds != null) {
            copy.setExpandedNodeIds(java.util.Collections.unmodifiableSet(this.expandedNodeIds));
        }
        return copy;
    }

    @Override
    public String toString() {
        return "ClipboardStash["
                + "name=" + name
                + ", nodeCount=" + getNodeCount()
                + ", timestamp=" + timestamp
                + "]";
    }
}
