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
    private final long oldParentId;
    private final int oldIndex;
    private final long newParentId;
    private final int newIndex;

    public MoveNodeCommand(EditNode node, long newParentId, int newIndex) {
        super(CommandType.MOVE_NODE);
        if (node == null) throw new IllegalArgumentException("Node cannot be null");
        this.nodeId = node.getEditId();
        EditNode parent = node.getParent();
        this.oldParentId = parent != null ? parent.getEditId() : -1;
        this.oldIndex = parent != null ? parent.getChildIndex(node) : -1;
        this.newParentId = newParentId;
        this.newIndex = newIndex;
        setDescription("Move node: " + node.getEditText());
    }

    public MoveNodeCommand(EditNode node, int newIndex) {
        this(node, node.getParent() != null ? node.getParent().getEditId() : -1, newIndex);
    }

    @Override
    public void execute(EditTree tree) {
        tree.moveNode(nodeId, newParentId, newIndex);
    }

    @Override
    public void undo(EditTree tree) {
        tree.moveNode(nodeId, oldParentId, oldIndex);
    }

    public long getNodeId() { return nodeId; }
    public long getOldParentId() { return oldParentId; }
    public int getOldIndex() { return oldIndex; }
    public long getNewParentId() { return newParentId; }
    public int getNewIndex() { return newIndex; }
}
