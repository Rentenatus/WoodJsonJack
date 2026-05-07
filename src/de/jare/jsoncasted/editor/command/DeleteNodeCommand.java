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
 * Command that deletes a node from the tree. 
 * When executed, the node is removed from its parent. 
 * When undone, the node is restored from a deep copy snapshot. 
 * 
 * Note: The snapshot preserves the original node ID to ensure undo/redo works correctly. 
 */
public class DeleteNodeCommand extends AbstractEditCommand {

    private final long nodeId;
    private final EditCommandEntry.Entry entry;

    /**
     * Creates a new delete node command. 
     * Takes a snapshot of the node to be deleted for undo restoration. 
     * The snapshot preserves the original ID (regenerateEditId=false). 
     * 
     * @param node the node to delete
     */
    public DeleteNodeCommand(EditNode node) {
        super(CommandType.DELETE_NODE);
        if (node == null) throw new IllegalArgumentException("Node cannot be null");
        this.nodeId = node.getEditId();
        EditNode parent = node.getParent();
        long parentId = parent != null ? parent.getEditId() : -1;
        int index = parent != null ? parent.getChildIndex(node) : -1;
        // Preserve original ID for undo restoration
        this.entry = new EditCommandEntry.Entry(parentId, index, node.deepCopy(false));
        setDescription("Delete node: " + node.getName());
    }

    @Override
    public void execute(EditTree tree) {
        tree.removeNode(nodeId);
    }

    @Override
    public void undo(EditTree tree) {
        EditNode parent = tree.findNodeById(entry.parentEditId);
        if (parent != null) {
            // Restore from snapshot, preserving the original ID
            parent.addChild(entry.snapshot.deepCopy(false), entry.index);
        } else {
            // Fallback: add to root
            tree.addNode(tree.getRoot().getEditId(), entry.snapshot.deepCopy(false));
        }
    }

    public long getNodeId() { return nodeId; }
    public EditCommandEntry.Entry getEntry() { return entry; }
    public EditNode getSnapshot() { return entry.snapshot; }
    public long getParentId() { return entry.parentEditId; }
    public int getIndex() { return entry.index; }
}
