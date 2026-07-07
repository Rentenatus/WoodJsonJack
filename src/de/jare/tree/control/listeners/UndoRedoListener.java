/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.listeners;

import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import javax.swing.tree.TreeModel;

/**
 * Listener interface for undo and redo operations on tree models.
 * <p>
 * Implementations are notified when commands are executed, undone, or redone,
 * allowing components to update their state in response to history changes.
 * This is typically used by UI components like toolbars that need to update
 * their button states or display history information.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public interface UndoRedoListener {

    /**
     * Called when a command is executed on a tree model.
     *
     * @param level the priority level of this listener
     * @param model the tree model on which the command was executed
     * @param historyEvent the result of the command execution
     */
    void onExecute(Integer level, TreeModel model, CommandResult historyEvent);

    /**
     * Called when an undo operation is performed on a tree model.
     *
     * @param level the priority level of this listener
     * @param model the tree model on which the undo was performed
     * @param historyEvent the result of the undo operation
     */
    void onUndo(Integer level, TreeModel model, CommandResult historyEvent);

    /**
     * Called when a redo operation is performed on a tree model.
     * <p>
     * The default implementation delegates to {@link #onExecute} since redo
     * is semantically similar to executing a command.
     * </p>
     *
     * @param level the priority level of this listener
     * @param model the tree model on which the redo was performed
     * @param historyEvent the result of the redo operation
     */
    default void onRedo(Integer level, TreeModel model, CommandResult historyEvent) {
        onExecute(level, model, historyEvent);
    }

    /**
     * Called when a command is skipped during undo/redo operations.
     * <p>
     * This typically happens when a command cannot be undone or redone.
     * </p>
     *
     * @param level the priority level of this listener
     * @param model the tree model on which the command was skipped
     * @param command the command that was skipped
     */
    void onSkipped(Integer level, TreeModel model, EditCommand command);

    /**
     * Called when a new command is added to the history of a tree model.
     * <p>
     * The default implementation does nothing.
     * </p>
     *
     * @param level the priority level of this listener
     * @param model the tree model to which the command was added
     * @param command the command that was added to the history
     */
    default void onAddCommand(Integer level, TreeModel model, EditCommand command) {
        // NoOp
    }

    /**
     * Called when the history of a tree model is cleared.
     * <p>
     * The default implementation does nothing.
     * </p>
     *
     * @param level the priority level of this listener
     * @param model the tree model whose history was cleared
     */
    default void onClear(Integer level, TreeModel model) {
        // NoOp
    }

}
