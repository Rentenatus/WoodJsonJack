/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.swing.command;

import de.jare.tree.control.TreeNodeUtils;
import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.swing.SwingTreeEditor;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;

/**
 * Adapter that wraps an EditCommand to work with Swing TreeModel.
 * This allows the new editor commands to be used with the existing Swing-based UI.
 */
public class WoodCommandAdapter implements de.jare.tree.control.commands.WoodCommand {

    private final EditCommand editCommand;
    private final SwingTreeEditor swingTreeEditor;
    private final EditNode[] affectedNodes;
    private String status;
    private boolean skipped;

    public WoodCommandAdapter(EditCommand editCommand, SwingTreeEditor swingTreeEditor) {
        this.editCommand = editCommand;
        this.swingTreeEditor = swingTreeEditor;
        this.affectedNodes = null;
        this.status = STATUS_ACTION_DONE;
        this.skipped = false;
    }

    public WoodCommandAdapter(EditCommand editCommand, SwingTreeEditor swingTreeEditor, EditNode[] affectedNodes) {
        this.editCommand = editCommand;
        this.swingTreeEditor = swingTreeEditor;
        this.affectedNodes = affectedNodes;
        this.status = STATUS_ACTION_DONE;
        this.skipped = false;
    }

    @Override
    public void execute(TreeModel model) {
        SwingTreeEditor.EditTreeAdapter adapter = swingTreeEditor.registerModel(model);
        if (adapter != null) {
            CommandResult result = editCommand.execute(adapter.getEditTree());
            status = STATUS_ACTION_DONE;
        }
    }

    @Override
    public void undo(TreeModel model) {
        if (skipped) {
            this.status = "";
            this.skipped = false;
            return;
        }
        SwingTreeEditor.EditTreeAdapter adapter = swingTreeEditor.registerModel(model);
        if (adapter != null) {
            CommandResult result = editCommand.undo(adapter.getEditTree());
            status = STATUS_REVERTED;
        }
    }

    @Override
    public void skip(TreeModel model) {
        this.status = STATUS_SKIPPED;
        this.skipped = true;
    }

    @Override
    public String getDescription() {
        return editCommand.getDescription();
    }

    @Override
    public String getCommandText() {
        return editCommand.getClass().getSimpleName();
    }

    @Override
    public String getStatus() {
        return status;
    }

    // -------------------------------------------------------------------------
    // Factory methods for creating adapters from editor commands
    // -------------------------------------------------------------------------

    public static WoodCommandAdapter fromEditCommand(EditCommand command, SwingTreeEditor editor) {
        return new WoodCommandAdapter(command, editor);
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    /**
     * Finds an EditNode by ID in a Swing TreeModel.
     */
    protected EditNode findEditNode(TreeModel model, long editId) {
        DefaultMutableTreeNode treeNode = TreeNodeUtils.findNodeByEditId(model, editId);
        if (treeNode != null) {
            Object uo = treeNode.getUserObject();
            if (uo instanceof EditNode) {
                return (EditNode) uo;
            }
        }
        return null;
    }

    /**
     * Converts a TreeModel to an EditTree for command execution.
     */
    protected EditTree toEditTree(TreeModel model) {
        SwingTreeEditor.EditTreeAdapter adapter = swingTreeEditor.registerModel(model);
        return adapter != null ? adapter.getEditTree() : null;
    }

    public EditCommand getEditCommand() {
        return editCommand;
    }

    public EditNode[] getAffectedNodes() {
        return affectedNodes;
    }
}
