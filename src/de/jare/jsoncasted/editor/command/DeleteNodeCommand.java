/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;

/**
 * Command that deletes node(s) from the tree. 
 * When executed, the node(s) are removed from their parent(s).
 * When undone, the node(s) are restored from deep copy snapshots.
 * 
 * Note: The snapshots preserve the original node IDs to ensure undo/redo works correctly.
 */
public class DeleteNodeCommand extends AbstractEditCommand {

    private final EditCommandEntry.MovementEntry[] entries;

    /**
     * Creates a new delete node command for a single node.
     * Takes a snapshot of the node to be deleted for undo restoration.
     * The snapshot preserves the original ID (regenerateEditId=false).
     *
     * @param node the node to delete
     */
    public DeleteNodeCommand(EditNode node) {
        super(CommandType.DELETE_NODE);
        if (node == null) throw new IllegalArgumentException("Node cannot be null");
        EditNode parent = node.getParent();
        long parentId = parent != null ? parent.getEditId() : -1;
        int index = parent != null ? parent.getChildIndex(node) : -1;
        // Preserve original ID for undo restoration
        this.entries = new EditCommandEntry.MovementEntry[] {
            new EditCommandEntry.MovementEntry(parentId, index, node.deepCopy(false))
        };
        setDescription("Delete node: " + node.getName());
    }

    /**
     * Creates a new delete node command for multiple nodes.
     * Takes snapshots of all nodes to be deleted for undo restoration.
     *
     * @param nodes the nodes to delete
     */
    public DeleteNodeCommand(EditNode[] nodes) {
        super(CommandType.DELETE_NODE);
        if (nodes == null || nodes.length == 0) {
            throw new IllegalArgumentException("Nodes cannot be null or empty");
        }
        this.entries = new EditCommandEntry.MovementEntry[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            EditNode node = nodes[i];
            if (node == null) throw new IllegalArgumentException("Node cannot be null");
            EditNode parent = node.getParent();
            long parentId = parent != null ? parent.getEditId() : -1;
            int index = parent != null ? parent.getChildIndex(node) : -1;
            this.entries[i] = new EditCommandEntry.MovementEntry(parentId, index, node.deepCopy(false));
        }
        if (nodes.length == 1) {
            setDescription("Delete node: " + nodes[0].getName());
        } else {
            setDescription("Delete " + nodes.length + " nodes");
        }
    }

    /**
     * Creates a delete command from entries array.
     *
     * @param entries array of entries to delete
     */
    public DeleteNodeCommand(EditCommandEntry.MovementEntry[] entries) {
        super(CommandType.DELETE_NODE);
        if (entries == null || entries.length == 0) {
            throw new IllegalArgumentException("Entries cannot be null or empty");
        }
        this.entries = entries;
        if (entries.length == 1) {
            setDescription("Delete node: " + entries[0].snapshot.getName());
        } else {
            setDescription("Delete " + entries.length + " nodes");
        }
    }

    @Override
    public void execute(EditTree tree) {
        // Delete nodes in reverse order to maintain correct indices
        for (int i = entries.length - 1; i >= 0; i--) {
            EditNode node = tree.findNodeById(entries[i].snapshot.getEditId());
            if (node != null) {
                tree.removeNode(node.getEditId());
            }
        }
    }

    @Override
    public void undo(EditTree tree) {
        // Restore nodes in forward order (original order)
        for (EditCommandEntry.MovementEntry entry : entries) {
            EditNode parent = tree.findNodeById(entry.parentEditId);
            if (parent != null) {
                // Restore from snapshot, preserving the original ID
                parent.addChild(entry.snapshot.deepCopy(false), entry.index);
            } else {
                // Fallback: add to root
                tree.addNode(tree.getRoot().getEditId(), entry.snapshot.deepCopy(false));
            }
        }
    }

    public EditCommandEntry.MovementEntry[] getEntries() { return entries; }
    public EditCommandEntry.MovementEntry getEntry() { return entries[0]; }
    public EditNode getSnapshot() { return entries[0].snapshot; }
    public long getNodeId() { return entries[0].snapshot != null ? entries[0].snapshot.getEditId() : -1; }
    public long getParentId() { return entries[0].parentEditId; }
    public int getIndex() { return entries[0].index; }
}
