/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.events;

import de.jare.jsoncasted.editor.core.EditNode;

/**
 * Event fired when the selection in the editor changes.
 */
public class SelectionEvent implements EditEvent {

    private final Object source;
    private final long timestamp;
    private final EditNode selectedNode;
    private final EditNode previousNode;
    private final boolean isMultiSelection;

    /**
     * Creates a new selection event.
     * 
     * @param source the source of this event
     * @param selectedNode the newly selected node
     * @param previousNode the previously selected node
     */
    public SelectionEvent(Object source, EditNode selectedNode, EditNode previousNode) {
        this(source, selectedNode, previousNode, false);
    }

    /**
     * Creates a new selection event with multi-selection flag.
     * 
     * @param source the source of this event
     * @param selectedNode the newly selected node
     * @param previousNode the previously selected node
     * @param isMultiSelection true if this is a multi-selection event
     */
    public SelectionEvent(
            Object source,
            EditNode selectedNode,
            EditNode previousNode,
            boolean isMultiSelection) {
        this.source = source;
        this.timestamp = System.currentTimeMillis();
        this.selectedNode = selectedNode;
        this.previousNode = previousNode;
        this.isMultiSelection = isMultiSelection;
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
        StringBuilder sb = new StringBuilder();
        sb.append("Selection changed: ");
        sb.append("selected=").append(selectedNode != null ? selectedNode.getName() : "null");
        sb.append(", previous=").append(previousNode != null ? previousNode.getName() : "null");
        if (isMultiSelection) {
            sb.append(", multi-selection=true");
        }
        return sb.toString();
    }

    /**
     * Returns the newly selected node.
     * 
     * @return the selected node, may be null if selection was cleared
     */
    public EditNode getSelectedNode() {
        return selectedNode;
    }

    /**
     * Returns the previously selected node.
     * 
     * @return the previous node, may be null
     */
    public EditNode getPreviousNode() {
        return previousNode;
    }

    /**
     * Returns whether this is a multi-selection event.
     * 
     * @return true if multiple nodes are selected
     */
    public boolean isMultiSelection() {
        return isMultiSelection;
    }

    /**
     * Returns whether the selection is empty (no node selected).
     * 
     * @return true if no node is selected
     */
    public boolean isSelectionEmpty() {
        return selectedNode == null;
    }

    @Override
    public String toString() {
        return "SelectionEvent[selected=" + 
               (selectedNode != null ? selectedNode.getId() : "null") + 
               ", previous=" + (previousNode != null ? previousNode.getId() : "null") + "]";
    }
}
