/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

public abstract class AbstractEditCommand implements EditCommand {

    private final CommandType type;
    private String description;

    protected AbstractEditCommand(CommandType type) {
        this.type = type;
        this.description = "";
    }

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

    protected void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[type=" + type + ", description='" + description + "']";
    }
}
