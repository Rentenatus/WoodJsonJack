/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright> 
 */
package de.jare.tree.control.commands;

import de.jare.tree.control.TreeNodeUtils;
import de.jare.jsoncasted.editor.command.AddNodeCommand;
import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;

/**
 * Command that adds one or more nodes (and their subtrees) under parent nodes.
 * <p>
 * Each parent is identified by its editId, and the nodes to add are stored as
 * deep copies. On execute the nodes are inserted, on undo they are removed.
 * </p>
 * 
 * <p>This class now integrates with the editor's AddNodeCommand for better compatibility
 * with the EditTree-based architecture.</p>
 */
public class WoodCommandAddNodes extends AbstractNodeMovementCommand {

    private final MovementEntry[] entries;
    private final String description;
    
    // Reference to editor command for integration
    private final EditCommand[] editCommands;

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
        this.editCommands = new EditCommand[length];
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
                throw new IllegalArgumentException("parentNodes[" + i + "] userObject must be EditNode");
            }

            Object nData = n.getUserObject();
            if (nData instanceof EditNode nodeData) {
                // Create editor command for this entry
                this.editCommands[i] = new AddNodeCommand(parentData.getEditId(), nodeData, idx);
            }

            entries[i] = new MovementEntry(parentData.getEditId(), idx, TreeNodeUtils.deepCopy(n));
        }

        this.description = (nodesToAdd.length == 1)
                ? "'" + nodesToAdd[0].getUserObject() + "'"
                : nodesToAdd.length + " nodes";
    }

    /**
     * Returns the underlying editor commands.
     * @return array of EditCommand instances
     */
    public EditCommand[] getEditCommands() {
        return editCommands;
    }

    /**
     * Executes this command on an EditTree directly.
     * @param tree the EditTree to execute on
     * @return the command result, or null if no editor commands available
     */
    public CommandResult execute(EditTree tree) {
        if (editCommands == null || editCommands.length == 0) {
            return null;
        }
        
        CommandResult result = null;
        for (EditCommand cmd : editCommands) {
            if (cmd != null) {
                result = cmd.execute(tree);
            }
        }
        return result;
    }

    /**
     * Undoes this command on an EditTree directly.
     * @param tree the EditTree to undo on
     * @return the command result, or null if no editor commands available
     */
    public CommandResult undo(EditTree tree) {
        if (editCommands == null || editCommands.length == 0 || skipped) {
            if (skipped) {
                this.status = "";
                this.skipped = false;
            }
            return null;
        }
        
        CommandResult result = null;
        for (int i = editCommands.length - 1; i >= 0; i--) {
            EditCommand cmd = editCommands[i];
            if (cmd != null) {
                result = cmd.undo(tree);
            }
        }
        return result;
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
