/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright> 
 */
package de.jare.tree.control.commands;

import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.core.EditTree;
import javax.swing.tree.TreeModel;

/**
 * Represents a single undoable command in the tree editor.
 * <p>
 * Implementations encapsulate a single logical change (add node, delete node,
 * rename, value change, move, ...) and know how to execute and undo that
 * change.
 * </p>
 * 
 * <p>This interface now supports integration with the editor's EditCommand system.
 * Commands that have been migrated to use EditCommand can override the
 * {@link #execute(EditTree)} and {@link #undo(EditTree)} methods for direct
 * integration with the new EditTree-based architecture.</p>
 * 
 * <p>Note: The WoodUtils interface methods (findNodeByEditId, deepCopy) are now
 * available through TreeNodeUtils for backward compatibility.</p>
 */
public interface WoodCommand {

    public static final String STATUS_ACTION_DONE = "Action done";
    public static final String STATUS_REDO_DONE = "Redo done";
    public static final String STATUS_REVERTED = "Reverted";
    public static final String STATUS_SKIPPED = "Skipped";

    /**
     * Executes the command, applying its change to the Swing TreeModel.
     * This is the legacy method for backward compatibility.
     *
     * @param model the Swing TreeModel to modify
     */
    void execute(TreeModel model);

    /**
     * Undoes the command, restoring the model to the state before
     * {@link #execute()} was called.
     * This is the legacy method for backward compatibility.
     *
     * @param model the Swing TreeModel to restore
     */
    void undo(TreeModel model);

    /**
     * Skip redo operation.
     * This is the legacy method for backward compatibility.
     *
     * @param model the Swing TreeModel
     */
    void skip(TreeModel model);

    // ========================================================================
    // New methods for EditTree integration
    // ========================================================================

    /**
     * Executes the command on an EditTree directly.
     * Implementations that have been migrated to use EditCommand should override
     * this method.
     *
     * @param tree the EditTree to modify
     * @return the command result, or null if not supported
     */
    default CommandResult execute(EditTree tree) {
        // Default implementation: not supported for legacy commands
        return null;
    }

    /**
     * Undoes the command on an EditTree directly.
     * Implementations that have been migrated to use EditCommand should override
     * this method.
     *
     * @param tree the EditTree to restore
     * @return the command result, or null if not supported
     */
    default CommandResult undo(EditTree tree) {
        // Default implementation: not supported for legacy commands
        return null;
    }

    /**
     * Returns the underlying EditCommand for editor integration.
     * Commands that have been migrated should override this method.
     *
     * @return the EditCommand instance, or null if not available
     */
    default EditCommand getEditCommand() {
        // Default implementation: not available for legacy commands
        return null;
    }

    /**
     * Checks if this command supports EditTree execution.
     *
     * @return true if EditTree execution is supported
     */
    default boolean supportsEditTree() {
        return getEditCommand() != null;
    }

    // ========================================================================
    // Legacy UI support methods
    // ========================================================================

    /**
     * Human-readable description details for UI (e.g. menu/tool tip).
     *
     * @return short description of this command
     */
    default String getDescription() {
        return "";
    }

    /**
     * Human-readable description for UI (e.g. menu/tool tip).
     *
     * @return short description of this command
     */
    default String getCommandText() {
        return getClass().getSimpleName();
    }

    default String getStatus() {
        return "";
    }

}
