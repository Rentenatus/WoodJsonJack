/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;

public class SetValueCommand extends AbstractEditCommand {

    private final EditCommandEntry.ValueEntry[] entries;

    /**
     * Creates a command to set the value of a single node.
     */
    public SetValueCommand(EditNode node, String newValue) {
        super(CommandType.SET_VALUE);
        if (node == null) throw new IllegalArgumentException("Node cannot be null");
        String oldValue = node.getEditText();
        this.entries = new EditCommandEntry.ValueEntry[] {
            new EditCommandEntry.ValueEntry(node.getEditId(), oldValue, newValue)
        };
        setDescription("Set value: " + (oldValue != null ? oldValue : "null") + " -> " + (newValue != null ? newValue : "null"));
    }

    /**
     * Creates a command to set values for multiple nodes.
     *
     * @param nodes the nodes to update
     * @param newValues the new values for each node
     */
    public SetValueCommand(EditNode[] nodes, String[] newValues) {
        super(CommandType.SET_VALUE);
        if (nodes == null || newValues == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        if (nodes.length != newValues.length) {
            throw new IllegalArgumentException("Arrays must have the same length");
        }
        if (nodes.length == 0) {
            throw new IllegalArgumentException("Arrays cannot be empty");
        }
        
        this.entries = new EditCommandEntry.ValueEntry[nodes.length];
        
        for (int i = 0; i < nodes.length; i++) {
            EditNode node = nodes[i];
            if (node == null) throw new IllegalArgumentException("Node cannot be null");
            String oldValue = node.getEditText();
            this.entries[i] = new EditCommandEntry.ValueEntry(
                node.getEditId(), 
                oldValue,
                newValues[i]
            );
        }
        
        if (nodes.length == 1) {
            setDescription("Set value: " + (entries[0].oldValue != null ? entries[0].oldValue : "null") + " -> " + (entries[0].newValue != null ? entries[0].newValue : "null"));
        } else {
            setDescription("Set values for " + nodes.length + " nodes");
        }
    }

    /**
     * Creates a command from value entries array.
     *
     * @param entries array of value entries
     */
    public SetValueCommand(EditCommandEntry.ValueEntry[] entries) {
        super(CommandType.SET_VALUE);
        if (entries == null || entries.length == 0) {
            throw new IllegalArgumentException("Entries cannot be null or empty");
        }
        this.entries = entries;
        if (entries.length == 1) {
            setDescription("Set value: " + (entries[0].oldValue != null ? entries[0].oldValue : "null") + " -> " + (entries[0].newValue != null ? entries[0].newValue : "null"));
        } else {
            setDescription("Set values for " + entries.length + " nodes");
        }
    }

    @Override
    public void execute(EditTree tree) {
        for (EditCommandEntry.ValueEntry entry : entries) {
            EditNode node = tree.findNodeById(entry.nodeId);
            if (node != null) {
                // Handle null by converting to empty string (EditNodeObject converts "" to null internally)
                String newValue = entry.newValue;
                node.setEditText(newValue != null ? newValue : "");
            }
        }
    }

    @Override
    public void undo(EditTree tree) {
        for (EditCommandEntry.ValueEntry entry : entries) {
            EditNode node = tree.findNodeById(entry.nodeId);
            if (node != null) {
                // Handle null by converting to empty string (EditNodeObject converts "" to null internally)
                String oldValue = entry.oldValue;
                node.setEditText(oldValue != null ? oldValue : "");
            }
        }
    }

    public EditCommandEntry.ValueEntry[] getEntries() { return entries; }
    
    public long[] getNodeIds() {
        long[] ids = new long[entries.length];
        for (int i = 0; i < entries.length; i++) {
            ids[i] = entries[i].nodeId;
        }
        return ids;
    }
    public String[] getOldValues() {
        String[] values = new String[entries.length];
        for (int i = 0; i < entries.length; i++) {
            values[i] = entries[i].oldValue;
        }
        return values;
    }
    public String[] getNewValues() {
        String[] values = new String[entries.length];
        for (int i = 0; i < entries.length; i++) {
            values[i] = entries[i].newValue;
        }
        return values;
    }
    
    public long getNodeId() { return entries[0].nodeId; }
    public String getOldValue() { return entries[0].oldValue; }
    public String getNewValue() { return entries[0].newValue; }
}
