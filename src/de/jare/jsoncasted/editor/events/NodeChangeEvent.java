/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.events;

import de.jare.jsoncasted.editor.core.EditNode;

/**
 * Event fired when a node in the tree is added, removed, modified, or moved.
 */
public class NodeChangeEvent implements EditEvent {

    /**
     * Type of change that occurred.
     */
    public enum ChangeType {
        /** A new node was added to the tree */
        ADDED,
        /** A node was removed from the tree */
        REMOVED,
        /** A node's value or name was modified */
        MODIFIED,
        /** A node was moved to a different position or parent */
        MOVED
    }

    private final Object source;
    private final long timestamp;
    private final ChangeType changeType;
    private final EditNode node;
    private final EditNode oldParent;
    private final EditNode newParent;
    private final int oldIndex;
    private final int newIndex;
    private final String description;

    /**
     * Creates a new node change event.
     * 
     * @param source the source of this event
     * @param changeType the type of change
     * @param node the affected node
     * @param oldParent the previous parent (null for added nodes)
     * @param newParent the new parent (null for removed nodes)
     * @param oldIndex the previous index
     * @param newIndex the new index
     */
    public NodeChangeEvent(
            Object source,
            ChangeType changeType,
            EditNode node,
            EditNode oldParent,
            EditNode newParent,
            int oldIndex,
            int newIndex) {
        this(source, changeType, node, oldParent, newParent, oldIndex, newIndex, null);
    }

    /**
     * Creates a new node change event with a custom description.
     * 
     * @param source the source of this event
     * @param changeType the type of change
     * @param node the affected node
     * @param oldParent the previous parent
     * @param newParent the new parent
     * @param oldIndex the previous index
     * @param newIndex the new index
     * @param description custom description
     */
    public NodeChangeEvent(
            Object source,
            ChangeType changeType,
            EditNode node,
            EditNode oldParent,
            EditNode newParent,
            int oldIndex,
            int newIndex,
            String description) {
        this.source = source;
        this.timestamp = System.currentTimeMillis();
        this.changeType = changeType;
        this.node = node;
        this.oldParent = oldParent;
        this.newParent = newParent;
        this.oldIndex = oldIndex;
        this.newIndex = newIndex;
        this.description = description;
    }

    @Override
    public Object getSource() {
        return source;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String getDescription() {
        if (description != null) {
            return description;
        }
        return buildDescription();
    }

    /**
     * Builds a default description based on the change type.
     * 
     * @return the generated description
     */
    private String buildDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Node ").append(changeType).append(" : ");
        sb.append("node=").append(node != null ? node.getName() : "null");
        
        switch (changeType) {
            case ADDED:
                sb.append(", newParent=").append(newParent != null ? newParent.getName() : "null");
                sb.append(", newIndex=").append(newIndex);
                break;
            case REMOVED:
                sb.append(", oldParent=").append(oldParent != null ? oldParent.getName() : "null");
                sb.append(", oldIndex=").append(oldIndex);
                break;
            case MODIFIED:
                sb.append(", oldValue=").append(node != null ? node.getValue() : "null");
                break;
            case MOVED:
                sb.append(", oldParent=").append(oldParent != null ? oldParent.getName() : "null");
                sb.append(", oldIndex=").append(oldIndex);
                sb.append(", newParent=").append(newParent != null ? newParent.getName() : "null");
                sb.append(", newIndex=").append(newIndex);
                break;
        }
        return sb.toString();
    }

    /**
     * Returns the type of change.
     * 
     * @return the change type
     */
    public ChangeType getChangeType() {
        return changeType;
    }

    /**
     * Returns the affected node.
     * 
     * @return the node, may be null
     */
    public EditNode getNode() {
        return node;
    }

    /**
     * Returns the previous parent before the change.
     * For ADDED events, this is typically null.
     * For MOVED events, this is the old parent.
     * 
     * @return the old parent, may be null
     */
    public EditNode getOldParent() {
        return oldParent;
    }

    /**
     * Returns the new parent after the change.
     * For REMOVED events, this is typically null.
     * For MOVED events, this is the new parent.
     * 
     * @return the new parent, may be null
     */
    public EditNode getNewParent() {
        return newParent;
    }

    /**
     * Returns the index in the old parent before the change.
     * 
     * @return the old index, or -1 if not applicable
     */
    public int getOldIndex() {
        return oldIndex;
    }

    /**
     * Returns the index in the new parent after the change.
     * 
     * @return the new index, or -1 if not applicable
     */
    public int getNewIndex() {
        return newIndex;
    }

    @Override
    public String toString() {
        return "NodeChangeEvent[" + changeType + ", node=" + 
               (node != null ? node.getId() : "null") + "]";
    }
}
