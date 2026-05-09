/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.commands;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import de.jare.jsoncasted.editor.core.EditNode;

/**
 * Command that adds one or more nodes (and their subtrees) under parent nodes.
 * <p>
 * Each parent is identified by its editId, and the nodes to add are stored as
 * deep copies. On execute the nodes are inserted, on undo they are removed.
 * </p>
 */
public class WoodCommandAddNodes extends AbstractNodeMovementCommand {

    private final MovementEntry[] entries;
    private final String description;

    public WoodCommandAddNodes(
            DefaultMutableTreeNode[] nodesToAdd,
            DefaultMutableTreeNode[] parentNodes,
            int... indices) {
        this.commandText = "Add nodes";

        if (nodesToAdd == null || parentNodes == null || nodesToAdd.length == 0) {
            throw new IllegalArgumentException("parentNodes and nodesToAdd must not be null/empty");
        }
        if (nodesToAdd.length != parentNodes.length) {
            throw new IllegalArgumentException("nodesToAdd and parentNodes length mismatch");
        }

        int length = nodesToAdd.length;
        this.entries = new MovementEntry[length];
        int lastIdx = -1;

        for (int i = 0; i < length; i++) {
            DefaultMutableTreeNode n = nodesToAdd[i];
            DefaultMutableTreeNode p = parentNodes[i];

            if (n == null) {
                throw new IllegalArgumentException("nodesToAdd[" + i + "] must not be null");
            }
            if (p == null) {
                throw new IllegalArgumentException("parentNodes[" + i + "] must not be null");
            }

            int idx = -1;
            if (indices != null) {
                if (indices.length > i) {
                    idx = indices[i];
                } else {
                    idx = (lastIdx < 0) ? -1 : (lastIdx + 1);
                }
            }
            lastIdx = idx;

            Object pData = p.getUserObject();
            if (!(pData instanceof EditNode parentData)) {
                throw new IllegalArgumentException("parentNodes[" + i + "] userObject must be JsonTreeNodeData");
            }

            entries[i] = new MovementEntry(parentData.getEditId(), idx, deepCopy(n));
        }

        this.description = (nodesToAdd.length == 1)
                ? "'" + nodesToAdd[0].getUserObject() + "'"
                : nodesToAdd.length + " nodes";
    }

    @Override
    public void executeMovement(TreeModel model) {
        checkAddNodes(model, entries, STATUS_REDO_DONE);
        addNodes(model, entries, getStatus());
    }

    @Override
    public void undoMovement(TreeModel model) {
        deleteNodes(model, entries, STATUS_REVERTED);
    }

    @Override
    public String getDescription() {
        return description;
    }

}
