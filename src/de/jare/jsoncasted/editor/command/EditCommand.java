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
 *
 * Implementations are expected to modify the provided {@link EditTree} and
 * return a {@link CommandResult} describing the affected nodes.
 */
public interface EditCommand {

    /**
     * Checks
     *
     * @param tree the tree to modify
     * @return availability
     */
    public CommandAvailability check(EditTree tree);

    /**
     * Executes this command on the given tree.
     *
     * @param tree the tree to modify
     * @return the result describing the changes caused by this execution
     */
    CommandResult execute(EditTree tree);

    /**
     * Undoes this command on the given tree.
     *
     * @param tree the tree to modify
     * @return the result describing the changes caused by this undo operation
     */
    CommandResult undo(EditTree tree);

    /**
     * Returns a human-readable description of this command.
     *
     * @return the command description
     */
    String getDescription();

    /**
     * Returns the command type.
     *
     * @return the command type
     */
    CommandType getType();

    /**
     * Fixed set of supported command categories.
     */
    enum CommandType {
        ADD_NODE,
        DELETE_NODE,
        MOVE_NODE,
        SET_VALUE,
        RENAME_NODE,
        OTHER
    }
}
