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
 * Command that deletes one or more nodes (and their subtrees).
 * <p>
 * Each node is identified by its parent editId and its index at delete time. A
 * deep copy snapshot of the subtree is stored so that undo can re-insert it.
 * </p>
 */
public class WoodCommandDeleteNodes extends AbstractNodeMovementCommand {

    private final Entry[] entries;
    private final String description;

    /**
     * @param nodesToDelete die zu löschenden Knoten (aktueller Baumzustand)
     * @param parentNodes deren Elternknoten (gleiche Länge wie nodesToDelete)
     */
    public WoodCommandDeleteNodes(
            DefaultMutableTreeNode[] nodesToDelete,
            DefaultMutableTreeNode[] parentNodes) {
        this.commandText = "Delete nodes";

        if (nodesToDelete == null || parentNodes == null || nodesToDelete.length == 0) {
            throw new IllegalArgumentException("parentNodes and nodesToDelete must not be null/empty");
        }
        if (nodesToDelete.length != parentNodes.length) {
            throw new IllegalArgumentException("nodesToDelete and parentNodes length mismatch");
        }

        int length = nodesToDelete.length;
        this.entries = new Entry[length];

        for (int i = 0; i < length; i++) {
            DefaultMutableTreeNode n = nodesToDelete[i];
            DefaultMutableTreeNode p = parentNodes[i];

            if (n == null) {
                throw new IllegalArgumentException("nodesToDelete[" + i + "] must not be null");
            }
            if (p == null) {
                throw new IllegalArgumentException("parentNodes[" + i + "] must not be null");
            }

            Object pData = p.getUserObject();
            if (!(pData instanceof EditNode parentData)) {
                throw new IllegalArgumentException("parentNodes[" + i + "] userObject must be JsonTreeNodeData");
            }

            int idx = p.getIndex(n);
            if (idx < 0) {
                throw new IllegalArgumentException("nodesToDelete[" + i + "] is not a child of parentNodes[" + i + "]");
            }

            entries[i] = new Entry(parentData.getEditId(), idx, deepCopy(n));
        }

        if (nodesToDelete.length == 1) {
            this.description = "'" + nodesToDelete[0].getUserObject() + "'";
        } else {
            this.description = nodesToDelete.length + " nodes";
        }
    }

    @Override
    public void executeMovement(TreeModel model) {
        deleteNodes(model, entries, STATUS_REDO_DONE);
    }

    @Override
    public void undoMovement(TreeModel model) {
        checkAddNodes(model, entries, STATUS_REDO_DONE);
        addNodes(model, entries, getStatus());
    }

    @Override
    public String getDescription() {
        return description;
    }

}
