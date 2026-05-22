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

public interface UndoRedoListener {

    void onExecute(TreeModel model, CommandResult historyEvent);

    void onUndo(TreeModel model, CommandResult historyEvent);

    default void onRedo(TreeModel model, CommandResult historyEvent) {
        onExecute(model, historyEvent);
    }

    void onSkipped(TreeModel model, EditCommand command);

    default void onAddCommand(TreeModel model, EditCommand command) {
        // NoOp
    }

    default void onClear(TreeModel model) {
        // NoOp
    }

}
