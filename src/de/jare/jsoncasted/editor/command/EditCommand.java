/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.core.EditTree;

/**
 * Interface for all commands that can be executed and undone in the editor.
 */
public interface EditCommand {

    void execute(EditTree tree);

    void undo(EditTree tree);

    String getDescription();

    CommandType getType();

    enum CommandType {
        ADD_NODE, DELETE_NODE, MOVE_NODE, SET_VALUE, RENAME_NODE, OTHER
    }
}
