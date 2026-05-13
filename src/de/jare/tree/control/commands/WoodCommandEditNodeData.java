/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright> 
 */
package de.jare.tree.control.commands;

import de.jare.tree.control.TreeNodeUtils;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.command.SetValueCommand;
import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.CommandAction;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;

/**
 * Command that edits a single node's value. 
 * This class now uses the editor's SetValueCommand internally for better integration.
 * 
 * <p>This command works with both the old TreeModel-based system and the new EditTree-based system.</p>
 * <p>It stores deep copies of the old and new state and, on execute/undo, searches
 * the current tree model for the node with the same editId and copies the state
 * into that node's user object.</p>
 */
public class WoodCommandEditNodeData implements WoodCommand {

    private final long editId;
    private final String oldValue;
    private final String newValue;
    private final String description;
    private String status;
    private boolean skipped;
    
    // New: Reference to EditCommand for editor integration
    private final EditCommand editCommand;

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
            EditNode current,
            EditNode oldState,
            EditNode newState) {
        if (current == null || oldState == null || newState == null) {
            throw new IllegalArgumentException("model, current, oldState and newState must not be null");
        }

        this.editId = current.getEditId();
        this.oldValue = oldState.getEditText();
        this.newValue = newState.getEditText();
        this.description = current.toString();
        this.status = STATUS_ACTION_DONE;
        this.skipped = false;
        
        // Create editor command for integration
        this.editCommand = new SetValueCommand(current, newValue);
    }

    // New constructor that takes EditNode and new value directly
    public WoodCommandEditNodeData(EditNode node, String newValue) {
        if (node == null) {
            throw new IllegalArgumentException("node must not be null");
        }
        
        this.editId = node.getEditId();
        this.oldValue = node.getEditText();
        this.newValue = newValue;
        this.description = node.toString();
        this.status = STATUS_ACTION_DONE;
        this.skipped = false;
        
        // Create editor command for integration
        this.editCommand = new SetValueCommand(node, newValue);
    }

    @Override
    public void execute(TreeModel model) {
        applyState(model, newValue, STATUS_REDO_DONE);
    }

    @Override
    public void undo(TreeModel model) {
        if (skipped) {
            this.status = "";
            this.skipped = false;
            return;
        }
        applyState(model, oldValue, STATUS_REVERTED);
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

    /**
     * Returns the underlying EditCommand for editor integration.
     * @return the EditCommand, or null if not available
     */
    public EditCommand getEditCommand() {
        return editCommand;
    }

    /**
     * Executes this command on an EditTree directly.
     * This is the preferred method for the new editor architecture.
     * 
     * @param tree the EditTree to execute on
     * @return the command result
     */
    public CommandResult execute(EditTree tree) {
        if (editCommand != null) {
            return editCommand.execute(tree);
        }
        return null;
    }

    /**
     * Undoes this command on an EditTree directly.
     * 
     * @param tree the EditTree to undo on
     * @return the command result
     */
    public CommandResult undo(EditTree tree) {
        if (editCommand != null && !skipped) {
            return editCommand.undo(tree);
        }
        return null;
    }

    private void applyState(TreeModel model, String value, String newStatus) {
        if (model == null) {
            return;
        }
        DefaultMutableTreeNode node = TreeNodeUtils.findNodeByEditId(model, editId);
        if (node == null) {
            this.status = "Failed: node not found";
            return; // node no longer exists -> nothing to do
        }
        
        Object uo = node.getUserObject();
        if (uo instanceof EditNode editNode) {
            editNode.setEditText(value);
        } else {
            // Fallback for non-EditNode user objects
            node.setUserObject(value);
        }

        if (!(model instanceof DefaultTreeModel dtm)) {
            return;
        }
        dtm.nodeChanged(node);
        this.status = newStatus;
    }

}
