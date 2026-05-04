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

    private final long nodeId;
    private final String oldValue;
    private final String newValue;

    public SetValueCommand(EditNode node, String newValue) {
        super(CommandType.SET_VALUE);
        if (node == null) throw new IllegalArgumentException("Node cannot be null");
        this.nodeId = node.getEditId();
        this.oldValue = node.getEditText();
        this.newValue = newValue;
        setDescription("Set value: " + node.getEditText() + " -> " + newValue);
    }

    @Override
    public void execute(EditTree tree) {
        EditNode node = tree.findNodeById(nodeId);
        if (node != null) node.setEditText(newValue);
    }

    @Override
    public void undo(EditTree tree) {
        EditNode node = tree.findNodeById(nodeId);
        if (node != null) node.setEditText(oldValue);
    }

    public long getNodeId() { return nodeId; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
}
