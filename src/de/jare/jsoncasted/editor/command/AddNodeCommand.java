/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;

public class AddNodeCommand extends AbstractEditCommand {

    private final EditCommandEntry.MovementEntry[] entries;

    /**
     * Creates a command to add a single node.
     */
    public AddNodeCommand(long parentId, EditNode node) {
        this(parentId, node, -1);
    }

    /**
     * Creates a command to add a single node at a specific index.
     */
    public AddNodeCommand(long parentId, EditNode node, int index) {
        super(CommandType.ADD_NODE);
        if (node == null) throw new IllegalArgumentException("Node cannot be null");
        this.entries = new EditCommandEntry.MovementEntry[] {
            new EditCommandEntry.MovementEntry(parentId, index, node)
        };
        setDescription("Add node: " + node.getName());
    }

    /**
     * Creates a command to add multiple nodes from entries.
     *
     * @param entries array of entries to add
     */
    public AddNodeCommand(EditCommandEntry.MovementEntry[] entries) {
        super(CommandType.ADD_NODE);
        if (entries == null || entries.length == 0) {
            throw new IllegalArgumentException("Entries cannot be null or empty");
        }
        this.entries = entries;
        if (entries.length == 1) {
            setDescription("Add node: " + entries[0].snapshot.getName());
        } else {
            setDescription("Add " + entries.length + " nodes");
        }
    }

    @Override
    public void execute(EditTree tree) {
        // Add nodes in forward order to maintain correct indices
        for (EditCommandEntry.MovementEntry entry : entries) {
            tree.addNode(entry.parentEditId, entry.snapshot, entry.index);
        }
    }

    @Override
    public void undo(EditTree tree) {
        // Remove nodes in reverse order to maintain correct indices during undo
        for (int i = entries.length - 1; i >= 0; i--) {
            EditNode existingNode = tree.findNodeById(entries[i].snapshot.getEditId());
            if (existingNode != null) {
                tree.removeNode(existingNode.getEditId());
            }
        }
    }

    public EditCommandEntry.MovementEntry[] getEntries() { return entries; }
    
    public long getParentId() { return entries[0].parentEditId; }
    public EditNode getNode() { return entries[0].snapshot; }
    public int getIndex() { return entries[0].index; }
    public long getEditId() { return entries[0].snapshot != null ? entries[0].snapshot.getEditId() : -1; }
}
