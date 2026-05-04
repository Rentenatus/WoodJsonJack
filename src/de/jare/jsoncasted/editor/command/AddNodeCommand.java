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

    private final long parentId;
    private final EditNode node;
    private final int index;

    public AddNodeCommand(long parentId, EditNode node) {
        this(parentId, node, -1);
    }

    public AddNodeCommand(long parentId, EditNode node, int index) {
        super(CommandType.ADD_NODE);
        if (node == null) throw new IllegalArgumentException("Node cannot be null");
        this.parentId = parentId;
        this.node = node;
        this.index = index;
        setDescription("Add node: " + node.getEditText());
    }

    @Override
    public void execute(EditTree tree) {
        tree.addNode(parentId, node, index);
    }

    @Override
    public void undo(EditTree tree) {
        EditNode existingNode = tree.findNodeById(node.getEditId());
        if (existingNode != null) {
            tree.removeNode(node.getEditId());
        }
    }

    public long getParentId() { return parentId; }
    public EditNode getNode() { return node; }
    public int getIndex() { return index; }
}
