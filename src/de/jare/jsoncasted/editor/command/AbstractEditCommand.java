/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;
import java.util.Set;

/**
 * Abstract base class for all edit commands in the JSON tree editor. Provides
 * common functionality for command type management and description handling.
 *
 * <p>
 * All edit commands must extend this class and implement the
 * {@link EditCommand} interface methods {@code execute()} and
 * {@code undo()}.</p>
 */
public abstract class AbstractEditCommand implements EditCommand {

    public static final UpdateAction[] NO_UPDATE_ACTIONS = new UpdateAction[0];

    private final CommandType type;
    private String description;
    boolean skipped;

    /**
     * Creates a new abstract edit command with the specified type.
     *
     * @param type the command type
     */
    protected AbstractEditCommand(CommandType type) {
        this.type = type;
        this.description = "";
        this.skipped = false;
    }

    /**
     * Creates a new abstract edit command with the specified type and
     * description.
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
    public void skipped() {
        skipped = true;
    }

    public boolean consumeSkipped() {
        boolean ret = skipped;
        skipped = false;
        return ret;
    }

    @Override
    public final CommandResult undo(EditTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }
        if (consumeSkipped()) {
            return new CommandResult(this, CommandAction.SKIPPED, null, null, null, null, null, NO_UPDATE_ACTIONS);
        }
        return doUndo(tree);
    }

    /**
     * Undoes cover.
     *
     * @param tree the tree to modify
     * @return the result describing the changes caused by this undo operation
     */
    protected abstract CommandResult doUndo(EditTree tree);

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[type=" + type + ", description='" + description + "']";
    }

    public final EditNode[] collectParentNodes(EditNode[] children) {
        Set<EditNode> parentNodes = new java.util.HashSet<>();
        for (EditNode node : children) {
            EditNode parent = node.getParent();
            if (parent != null) {
                parentNodes.add(parent);
            }
        }
        return parentNodes.toArray(new EditNode[parentNodes.size()]);
    }
}
