/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.commands;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;

/**
 * Command that edits a single JsonTreeNodeData identified by its editId.
 * <p>
 * It stores deep copies of the old and new state and, on execute/undo, searches
 * the current tree model for the node with the same editId and copies the state
 * into that node's user object.
 * </p>
 */
public class WoodCommandEditNodeData implements WoodCommand {

    private final long editId;
    private final EditNodeAbstract oldState;
    private final EditNodeAbstract newState;
    private final String description;
    private String status;
    private boolean skipped;

    /**
     * Creates a new edit command for the given node data.
     *
     * @param current current node data (before or after the change); must not
     * be null
     * @param oldState deep copy of the state before the change; must not be
     * null
     * @param newState deep copy of the state after the change; must not be null
     */
    public WoodCommandEditNodeData(
            EditNodeAbstract current,
            EditNodeAbstract oldState,
            EditNodeAbstract newState) {
        if (current == null || oldState == null || newState == null) {
            throw new IllegalArgumentException("model, current, oldState and newState must not be null");
        }

        this.editId = current.getEditId();
        this.oldState = oldState.deepCopy(false);
        this.newState = newState.deepCopy(false);
        this.description = current.toString();
        this.status = STATUS_ACTION_DONE;
        this.skipped = false;
    }

    @Override
    public void execute(TreeModel model) {
        applyState(model, newState, STATUS_REDO_DONE);
    }

    @Override
    public void undo(TreeModel model) {
        if (skipped) {
            this.status = "";
            this.skipped = false;
            return;
        }
        applyState(model, oldState, STATUS_REVERTED);
    }

    @Override
    public void skip(TreeModel model) {
        this.status = STATUS_SKIPPED;
        this.skipped = true;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getCommandText() {
        return "Edit";
    }

    private void applyState(TreeModel model, EditNodeAbstract source, String newStatus) {
        if (model == null) {
            return;
        }
        DefaultMutableTreeNode node = findNodeByEditId(model, editId);
        if (node == null) {
            this.status = "Failed: node not found";
            return; // node no longer exists -> nothing to do
        }
        // Replace user object with a deep copy of the desired state.
        node.setUserObject(source.deepCopy(false));

        if (!(model instanceof DefaultTreeModel dtm)) {
            return;
        }
        dtm.nodeChanged(node);
        this.status = newStatus;
    }

}
