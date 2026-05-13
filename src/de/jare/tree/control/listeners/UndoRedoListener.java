/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.listeners;

import de.jare.tree.control.commands.WoodCommand;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.command.CommandResult;
import javax.swing.tree.TreeModel;

/**
 * Listener interface for undo/redo events.
 * Supports both WoodCommand (TreeModel-based) and EditCommand (EditTree-based) operations.
 */
public interface UndoRedoListener {

    /**
     * Called when an undo operation is performed.
     *
     * @param model the TreeModel, may be null if only EditTree is available
     * @param command the WoodCommand that was undone, may be null if only EditCommand is available
     */
    void onUndo(TreeModel model, WoodCommand command);

    /**
     * Called when a redo operation is performed.
     *
     * @param model the TreeModel, may be null if only EditTree is available
     * @param command the WoodCommand that was redone, may be null if only EditCommand is available
     */
    void onRedo(TreeModel model, WoodCommand command);

    /**
     * Called when a command is added to the undo history.
     *
     * @param model the TreeModel, may be null if only EditTree is available
     * @param command the WoodCommand that was added, may be null if only EditCommand is available
     */
    default void onAddCommand(TreeModel model, WoodCommand command) {
        // NoOp
    }

    /**
     * Called when the undo/redo history is cleared.
     *
     * @param model the TreeModel, may be null if only EditTree is available
     */
    default void onClear(TreeModel model) {
        // NoOp
    }

    // ===== EditCommand-based event methods =====

    /**
     * Called when an EditCommand is executed.
     *
     * @param editTree the EditTree, may be null
     * @param editCommand the EditCommand that was executed
     * @param result the CommandResult
     */
    default void onEditCommandExecuted(Object editTree, EditCommand editCommand, CommandResult result) {
        // NoOp - backward compatible
    }

    /**
     * Called when an EditCommand is undone.
     *
     * @param editTree the EditTree, may be null
     * @param editCommand the EditCommand that was undone
     * @param result the CommandResult
     */
    default void onEditCommandUndone(Object editTree, EditCommand editCommand, CommandResult result) {
        // NoOp - backward compatible
    }

    /**
     * Called when an EditCommand is redone.
     *
     * @param editTree the EditTree, may be null
     * @param editCommand the EditCommand that was redone
     * @param result the CommandResult
     */
    default void onEditCommandRedone(Object editTree, EditCommand editCommand, CommandResult result) {
        // NoOp - backward compatible
    }

    /**
     * Called when an EditCommand is skipped (moved from redo to undo without execution).
     *
     * @param editTree the EditTree, may be null
     * @param editCommand the EditCommand that was skipped
     */
    default void onEditCommandSkipped(Object editTree, EditCommand editCommand) {
        // NoOp - backward compatible
    }

}
