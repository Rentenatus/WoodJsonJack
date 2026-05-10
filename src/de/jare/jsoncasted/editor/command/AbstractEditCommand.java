/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

/**
 * Abstract base class for all edit commands in the JSON tree editor.
 * Provides common functionality for command type management and description handling.
 *
 * <p>All edit commands must extend this class and implement the {@link EditCommand}
 * interface methods {@code execute()} and {@code undo()}.</p>
 */
public abstract class AbstractEditCommand implements EditCommand {

    private final CommandType type;
    private String description;

    /**
     * Creates a new abstract edit command with the specified type.
     *
     * @param type the command type
     */
    protected AbstractEditCommand(CommandType type) {
        this.type = type;
        this.description = "";
    }

    /**
     * Creates a new abstract edit command with the specified type and description.
     *
     * @param type the command type
     * @param description a human-readable description of the command
     */
    protected AbstractEditCommand(CommandType type, String description) {
        this.type = type;
        this.description = description;
    }

    @Override
    public CommandType getType() {
        return type;
    }

    @Override
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of this command.
     *
     * @param description the description to set
     */
    protected void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[type=" + type + ", description='" + description + "']";
    }
}
