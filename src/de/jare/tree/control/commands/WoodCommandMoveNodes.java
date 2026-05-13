/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright> 
 */
package de.jare.tree.control.commands;

import de.jare.tree.control.TreeNodeUtils;
import de.jare.jsoncasted.editor.command.MoveNodeCommand;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.command.EditCommandEntry.MovementEntry;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;

/**
 * Command that moves one or more nodes to new parent/index positions.
 * <p>
 * This is a composite command that combines delete and add operations.
 * </p>
 * 
 * <p>This class now integrates with the editor's MoveNodeCommand for better compatibility
 * with the EditTree-based architecture.</p>
 */
public class WoodCommandMoveNodes extends AbstractNodeMovementCommand {

    private final AbstractNodeMovementCommand.MovementEntry[] deleteEntries;
    private final AbstractNodeMovementCommand.MovementEntry[] addEntries;
    private final String description;
    
    // Reference to editor command for integration
    private final EditCommand editCommand;

    /**
     * Creates a move command for a single node.
     */
    public WoodCommandMoveNodes(
            DefaultMutableTreeNode[] nodesToMove,
            DefaultMutableTreeNode[] oldParentNodes,
            DefaultMutableTreeNode[] newParentNodes,
            int[] oldIndices,
            int[] newIndices) {
        this.commandText = "Move nodes";

        if (nodesToMove == null || oldParentNodes == null || newParentNodes == null) {
            throw new IllegalArgumentException("Arguments must not be null");
        }
        if (nodesToMove.length != oldParentNodes.length 
                || nodesToMove.length != newParentNodes.length) {
            throw new IllegalArgumentException("Array length mismatch");
        }
        if (nodesToMove.length == 0) {
            throw new IllegalArgumentException("No nodes to move");
        }

        int length = nodesToMove.length;
        this.deleteEntries = new AbstractNodeMovementCommand.MovementEntry[length];
        this.addEntries = new AbstractNodeMovementCommand.MovementEntry[length];
        EditNode[] nodes = new EditNode[length];

        for (int i = 0; i < length; i++) {
            DefaultMutableTreeNode n = nodesToMove[i];
            DefaultMutableTreeNode oldP = oldParentNodes[i];
            DefaultMutableTreeNode newP = newParentNodes[i];

            Object nData = n.getUserObject();
            Object oldPData = oldP.getUserObject();
            Object newPData = newP.getUserObject();

            if (nData instanceof EditNode nodeData) {
                nodes[i] = nodeData;
            }

            int oldIdx = oldIndices != null && oldIndices.length > i ? oldIndices[i] : oldP.getIndex(n);
            int newIdx = newIndices != null && newIndices.length > i ? newIndices[i] : newP.getChildCount();

            if (oldPData instanceof EditNode oldParentData) {
                deleteEntries[i] = new AbstractNodeMovementCommand.MovementEntry(
                        oldParentData.getEditId(), oldIdx, TreeNodeUtils.deepCopy(n));
            } else {
                deleteEntries[i] = new AbstractNodeMovementCommand.MovementEntry(
                        -1, oldIdx, TreeNodeUtils.deepCopy(n));
            }

            if (newPData instanceof EditNode newParentData) {
                addEntries[i] = new AbstractNodeMovementCommand.MovementEntry(
                        newParentData.getEditId(), newIdx, TreeNodeUtils.deepCopy(n));
            } else {
                addEntries[i] = new AbstractNodeMovementCommand.MovementEntry(
                        -1, newIdx, TreeNodeUtils.deepCopy(n));
            }
        }

        // Create editor command for integration
        if (nodes.length > 0 && nodes[0] != null) {
            long newParentId = addEntries[0].parentEditId;
            int newIndex = addEntries[0].index;
            this.editCommand = new MoveNodeCommand(nodes, newParentId, newIndex);
        } else {
            this.editCommand = null;
        }

        this.description = (nodesToMove.length == 1)
                ? "'" + nodesToMove[0].getUserObject() + "'"
                : nodesToMove.length + " nodes";
    }

    /**
     * Returns the underlying editor command.
     * @return the EditCommand instance, or null if not available
     */
    public EditCommand getEditCommand() {
        return editCommand;
    }

    /**
     * Executes this command on an EditTree directly.
     * @param tree the EditTree to execute on
     * @return the command result, or null if no editor command available
     */
    public CommandResult execute(EditTree tree) {
        if (editCommand != null) {
            return editCommand.execute(tree);
        }
        return null;
    }

    /**
     * Undoes this command on an EditTree directly.
     * @param tree the EditTree to undo on
     * @return the command result, or null if no editor command available
     */
    public CommandResult undo(EditTree tree) {
        if (editCommand != null && !skipped) {
            return editCommand.undo(tree);
        }
        if (skipped) {
            this.status = "";
            this.skipped = false;
        }
        return null;
    }

    @Override
    public void executeMovement(TreeModel model) {
        deleteNodes(model, deleteEntries, STATUS_ACTION_DONE);
        checkNodesPos(model, addEntries, STATUS_ACTION_DONE);
        addNodes(model, addEntries, getStatus());
    }

    @Override
    public void undoMovement(TreeModel model) {
        deleteNodes(model, addEntries, STATUS_REVERTED);
        checkNodesPos(model, deleteEntries, STATUS_REVERTED);
        addNodes(model, deleteEntries, getStatus());
    }

    @Override
    public String getDescription() {
        return description;
    }

}
