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

    void onExecute(Integer level, TreeModel model, CommandResult historyEvent);

    void onUndo(Integer level, TreeModel model, CommandResult historyEvent);

    default void onRedo(Integer level, TreeModel model, CommandResult historyEvent) {
        onExecute(level, model, historyEvent);
    }

    void onSkipped(Integer level, TreeModel model, EditCommand command);

    default void onAddCommand(Integer level, TreeModel model, EditCommand command) {
        // NoOp
    }

    default void onClear(Integer level, TreeModel model) {
        // NoOp
    }

}
