/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright> 
 */
package de.jare.tree.control.commands;

import de.jare.tree.control.TreeNodeUtils;
import de.jare.jsoncasted.editor.command.DeleteNodeCommand;
import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;

/**
 * Command that deletes one or more nodes from their parent nodes.
 * <p>
 * Each node to delete is identified by its snapshot. On execute the nodes are
 * removed from their parents, on undo they are restored.
 * </p>
 * 
 * <p>This class now integrates with the editor's DeleteNodeCommand for better compatibility
 * with the EditTree-based architecture.</p>
 */
public class WoodCommandDeleteNodes extends AbstractNodeMovementCommand {

    private final MovementEntry[] entries;
    private final String description;
    
    // Reference to editor command for integration
    private final EditCommand editCommand;

    public WoodCommandDeleteNodes(
            DefaultMutableTreeNode[] nodesToRemove,
            DefaultMutableTreeNode[] parentNodes) {
        this.commandText = "Delete nodes";

        if (nodesToRemove == null || parentNodes == null || nodesToRemove.length == 0) {
            throw new IllegalArgumentException("nodesToRemove and parentNodes must not be null/empty");
        }
        if (nodesToRemove.length != parentNodes.length) {
            throw new IllegalArgumentException("nodesToRemove and parentNodes length mismatch");
        }

        int length = nodesToRemove.length;
        this.entries = new MovementEntry[length];
        EditNode[] nodesToDelete = new EditNode[length];

        for (int i = 0; i < length; i++) {
            DefaultMutableTreeNode n = nodesToRemove[i];
            DefaultMutableTreeNode p = parentNodes[i];

            if (n == null || p == null) {
                throw new IllegalArgumentException("nodesToRemove[" + i + "] or parentNodes[" + i + "] must not be null");
            }

            Object nData = n.getUserObject();
            Object pData = p.getUserObject();
            
            if (nData instanceof EditNode nodeData) {
                nodesToDelete[i] = nodeData;
            }
            
            if (pData instanceof EditNode parentData) {
                entries[i] = new MovementEntry(parentData.getEditId(), p.getIndex(n), TreeNodeUtils.deepCopy(n));
            } else {
                entries[i] = new MovementEntry(-1, p.getIndex(n), TreeNodeUtils.deepCopy(n));
            }
        }

        // Create editor command for integration
        this.editCommand = nodesToDelete.length > 0 ? new DeleteNodeCommand(nodesToDelete) : null;

        this.description = (nodesToRemove.length == 1)
                ? "'" + nodesToRemove[0].getUserObject() + "'"
                : nodesToRemove.length + " nodes";
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
        deleteNodes(model, entries, STATUS_REDO_DONE);
    }

    @Override
    public void undoMovement(TreeModel model) {
        checkAddNodes(model, entries, STATUS_REVERTED);
        addNodes(model, entries, getStatus());
    }

    @Override
    public String getDescription() {
        return description;
    }

}
