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

    private final EditCommandEntry.Entry entry;

    public AddNodeCommand(long parentId, EditNode node) {
        this(parentId, node, -1);
    }

    public AddNodeCommand(long parentId, EditNode node, int index) {
        super(CommandType.ADD_NODE);
        if (node == null) throw new IllegalArgumentException("Node cannot be null");
        this.entry = new EditCommandEntry.Entry(parentId, index, node);
        setDescription("Add node: " + node.getName());
    }

    @Override
    public void execute(EditTree tree) {
        tree.addNode(entry.parentEditId, entry.snapshot, entry.index);
    }

    @Override
    public void undo(EditTree tree) {
        EditNode existingNode = tree.findNodeById(entry.snapshot.getEditId());
        if (existingNode != null) {
            tree.removeNode(existingNode.getEditId());
        }
    }

    public long getParentId() { return entry.parentEditId; }
    public EditNode getNode() { return entry.snapshot; }
    public int getIndex() { return entry.index; }
    public long getEditId() { return entry.snapshot != null ? entry.snapshot.getEditId() : -1; }
}
