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
    private final EditNode snapshot;
    private final long parentId;
    private final int index;

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
        // Preserve original ID for undo restoration
        this.snapshot = node.deepCopy(false);
        EditNode parent = node.getParent();
        this.parentId = parent != null ? parent.getEditId() : -1;
        this.index = parent != null ? parent.getChildIndex(node) : -1;
        setDescription("Delete node: " + node.getEditText());
    }

    @Override
    public void execute(EditTree tree) {
        tree.removeNode(nodeId);
    }

    @Override
    public void undo(EditTree tree) {
        EditNode parent = tree.findNodeById(parentId);
        if (parent != null) {
            // Restore from snapshot, preserving the original ID
            parent.addChild(snapshot.deepCopy(false), index);
        } else {
            // Fallback: add to root
            tree.addNode(tree.getRoot().getEditId(), snapshot.deepCopy(false));
        }
    }

    public long getNodeId() { return nodeId; }
    public EditNode getSnapshot() { return snapshot; }
    public long getParentId() { return parentId; }
    public int getIndex() { return index; }
}
