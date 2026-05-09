/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;

public class MoveNodeCommand extends AbstractEditCommand {

    private final EditCommandEntry.MovementEntry[] oldEntries;
    private final EditCommandEntry.MovementEntry[] newEntries;

    /**
     * Creates a command to move a single node to a new parent and index.
     */
    public MoveNodeCommand(EditNode node, long newParentId, int newIndex) {
        super(CommandType.MOVE_NODE);
        if (node == null) throw new IllegalArgumentException("Node cannot be null");
        EditNode parent = node.getParent();
        long oldParentId = parent != null ? parent.getEditId() : -1;
        int oldIndex = parent != null ? parent.getChildIndex(node) : -1;
        this.oldEntries = new EditCommandEntry.MovementEntry[] {
            new EditCommandEntry.MovementEntry(oldParentId, oldIndex, null)
        };
        this.newEntries = new EditCommandEntry.MovementEntry[] {
            new EditCommandEntry.MovementEntry(newParentId, newIndex, null)
        };
        setDescription("Move node: " + node.getName());
    }

    /**
     * Creates a command to move a single node to a new index within the same parent.
     */
    public MoveNodeCommand(EditNode node, int newIndex) {
        this(node, node.getParent() != null ? node.getParent().getEditId() : -1, newIndex);
    }

    /**
     * Creates a command to move multiple nodes to the same target parent at starting index.
     * Nodes will be placed sequentially starting at newIndex, preserving their order.
     *
     * @param nodes the nodes to move
     * @param newParentId the target parent ID for all nodes
     * @param newIndex the starting index at the target parent
     */
    public MoveNodeCommand(EditNode[] nodes, long newParentId, int newIndex) {
        super(CommandType.MOVE_NODE);
        if (nodes == null) {
            throw new IllegalArgumentException("Nodes cannot be null");
        }
        if (nodes.length == 0) {
            throw new IllegalArgumentException("Nodes cannot be empty");
        }
        
        this.oldEntries = new EditCommandEntry.MovementEntry[nodes.length];
        this.newEntries = new EditCommandEntry.MovementEntry[nodes.length];
        
        for (int i = 0; i < nodes.length; i++) {
            EditNode node = nodes[i];
            if (node == null) throw new IllegalArgumentException("Node cannot be null");
            
            EditNode parent = node.getParent();
            long oldParentId = parent != null ? parent.getEditId() : -1;
            int oldIndex = parent != null ? parent.getChildIndex(node) : -1;
            this.oldEntries[i] = new EditCommandEntry.MovementEntry(oldParentId, oldIndex, null);
            this.newEntries[i] = new EditCommandEntry.MovementEntry(newParentId, newIndex + i, null);
        }
        
        if (nodes.length == 1) {
            setDescription("Move node: " + nodes[0].getName());
        } else {
            setDescription("Move " + nodes.length + " nodes");
        }
    }

    /**
     * Creates a move command from entries arrays.
     *
     * @param oldEntries array of old entries (source positions)
     * @param newEntries array of new entries (target positions)
     */
    public MoveNodeCommand(EditCommandEntry.MovementEntry[] oldEntries, EditCommandEntry.MovementEntry[] newEntries) {
        super(CommandType.MOVE_NODE);
        if (oldEntries == null || newEntries == null) {
            throw new IllegalArgumentException("Entries cannot be null");
        }
        if (oldEntries.length != newEntries.length) {
            throw new IllegalArgumentException("Arrays must have the same length");
        }
        if (oldEntries.length == 0) {
            throw new IllegalArgumentException("Arrays cannot be empty");
        }
        this.oldEntries = oldEntries;
        this.newEntries = newEntries;
        if (oldEntries.length == 1) {
            setDescription("Move node");
        } else {
            setDescription("Move " + oldEntries.length + " nodes");
        }
    }

    @Override
    public void execute(EditTree tree) {
        // For move, we need to find the nodes by their positions
        // This is more complex with entries, so we iterate through old entries
        // to find nodes and move them to new positions
        for (int i = 0; i < oldEntries.length; i++) {
            EditCommandEntry.MovementEntry oldEntry = oldEntries[i];
            EditCommandEntry.MovementEntry newEntry = newEntries[i];
            
            EditNode parent = tree.findNodeById(oldEntry.parentEditId);
            if (parent != null && oldEntry.index >= 0 && oldEntry.index < parent.getChildCount()) {
                EditNode node = parent.getChildAt(oldEntry.index);
                if (node != null) {
                    tree.moveNode(node.getEditId(), newEntry.parentEditId, newEntry.index);
                }
            }
        }
    }

    @Override
    public void undo(EditTree tree) {
        // Move nodes back in reverse order to maintain correct indices
        for (int i = oldEntries.length - 1; i >= 0; i--) {
            EditCommandEntry.MovementEntry oldEntry = oldEntries[i];
            EditCommandEntry.MovementEntry newEntry = newEntries[i];
            
            EditNode newParent = tree.findNodeById(newEntry.parentEditId);
            if (newParent != null && newEntry.index >= 0 && newEntry.index < newParent.getChildCount()) {
                EditNode node = newParent.getChildAt(newEntry.index);
                if (node != null) {
                    tree.moveNode(node.getEditId(), oldEntry.parentEditId, oldEntry.index);
                }
            }
        }
    }

    public EditCommandEntry.MovementEntry[] getOldEntries() { return oldEntries; }
    public EditCommandEntry.MovementEntry[] getNewEntries() { return newEntries; }
    
    public EditCommandEntry.MovementEntry getOldEntry() { return oldEntries[0]; }
    public EditCommandEntry.MovementEntry getNewEntry() { return newEntries[0]; }
    public long getOldParentId() { return oldEntries[0].parentEditId; }
    public int getOldIndex() { return oldEntries[0].index; }
    public long getNewParentId() { return newEntries[0].parentEditId; }
    public int getNewIndex() { return newEntries[0].index; }
}
