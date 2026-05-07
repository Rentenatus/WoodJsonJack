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

    private final long nodeId;
    private final EditCommandEntry.Entry oldEntry;
    private final EditCommandEntry.Entry newEntry;

    public MoveNodeCommand(EditNode node, long newParentId, int newIndex) {
        super(CommandType.MOVE_NODE);
        if (node == null) throw new IllegalArgumentException("Node cannot be null");
        this.nodeId = node.getEditId();
        EditNode parent = node.getParent();
        long oldParentId = parent != null ? parent.getEditId() : -1;
        int oldIndex = parent != null ? parent.getChildIndex(node) : -1;
        this.oldEntry = new EditCommandEntry.Entry(oldParentId, oldIndex, null);
        this.newEntry = new EditCommandEntry.Entry(newParentId, newIndex, null);
        setDescription("Move node: " + node.getName());
    }

    public MoveNodeCommand(EditNode node, int newIndex) {
        this(node, node.getParent() != null ? node.getParent().getEditId() : -1, newIndex);
    }

    @Override
    public void execute(EditTree tree) {
        tree.moveNode(nodeId, newEntry.parentEditId, newEntry.index);
    }

    @Override
    public void undo(EditTree tree) {
        tree.moveNode(nodeId, oldEntry.parentEditId, oldEntry.index);
    }

    public long getNodeId() { return nodeId; }
    public long getOldParentId() { return oldEntry.parentEditId; }
    public int getOldIndex() { return oldEntry.index; }
    public long getNewParentId() { return newEntry.parentEditId; }
    public int getNewIndex() { return newEntry.index; }
}
