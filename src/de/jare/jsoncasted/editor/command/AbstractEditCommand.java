/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.core.EditNodeAbstract;
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

    private CommandAction lastAction;
    private int lastUpdatedCount;
    private int lastFailedCount;

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

    /**
     * Returns the last action performed by this command.
     *
     * @return the last command action
     */
    public CommandAction getLastAction() {
        return lastAction;
    }

    /**
     * Returns the number of nodes updated in the last operation.
     *
     * @return the count of updated nodes
     */
    public int getLastUpdatedCount() {
        return lastUpdatedCount;
    }

    /**
     * Returns the number of nodes that failed in the last operation.
     *
     * @return the count of failed nodes
     */
    public int getLastFailedCount() {
        return lastFailedCount;
    }

    @Override
    public void skipped() {
        skipped = true;
        lastAction = CommandAction.SKIPPED;
        lastUpdatedCount = 0;
        lastFailedCount = 0;
    }

    public boolean consumeSkipped() {
        boolean ret = skipped;
        skipped = false;
        return ret;
    }

    @Override
    public final CommandResult execute(EditTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }
        CommandResult result = doExecute(tree);
        if (result != null) {
            lastAction = result.getAction();
            lastUpdatedCount = result.getUpdatedNodes().length;
            lastFailedCount = result.getFailedNodes().length;
        }
        return result;
    }

    /**
     * Executes the command on the given tree. Subclasses must implement this
     * method.
     *
     * @param tree the tree to modify
     * @return the result describing the changes caused by this execution
     */
    protected abstract CommandResult doExecute(EditTree tree);

    @Override
    public final CommandResult undo(EditTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }
        if (consumeSkipped()) {
            lastAction = CommandAction.SKIPPED;
            lastUpdatedCount = 0;
            lastFailedCount = 0;
            return new CommandResult(this, CommandAction.SKIPPED, null, null, null, null, null, NO_UPDATE_ACTIONS);
        }
        CommandResult result = doUndo(tree);
        if (result != null) {
            lastAction = result.getAction();
            lastUpdatedCount = result.getUpdatedNodes().length;
            lastFailedCount = result.getFailedNodes().length;
        }
        return result;
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

    /**
     *
     * @param children1
     * @param children2orNull
     * @return
     */
    public final EditNodeAbstract[] unionParentNodes(EditNodeAbstract[] children1, EditNodeAbstract[] children2orNull) {
        Set<EditNodeAbstract> union = new java.util.HashSet<>();
        for (EditNodeAbstract node : children1) {
            EditNodeAbstract parent = node.getParent();
            if (parent != null) {
                removeChildrenOf(union, parent);
                if (!hasParentIn(union, parent)) {
                    union.add(parent);
                }
            }
        }
        if (children2orNull != null) {
            for (EditNodeAbstract node : children2orNull) {
                EditNodeAbstract parent = node.getParent();
                if (parent != null) {
                    removeChildrenOf(union, parent);
                    if (!hasParentIn(union, parent)) {
                        union.add(parent);
                    }
                }
            }
        }
        return union.toArray(new EditNodeAbstract[union.size()]);
    }

    /**
     * Checks if the specified node has any parent in the given set of nodes.
     * This method iterates through the set of nodes and checks if the provided
     * node has any of them as a parent. If a parent is found, it returns true;
     * otherwise, it returns false after checking all nodes in the set. This is
     * useful for determining if a node is a descendant of any node in a
     * collection, which can help in managing relationships between nodes in a
     * tree structure.
     *
     * @param club the set of nodes to check against
     * @param node the node for which to check parent relationships
     * @return true if the node has a parent in the set, false otherwise
     */
    public final boolean hasParentIn(Set<EditNodeAbstract> club, EditNodeAbstract node) {
        for (EditNodeAbstract member : club) {
            if (node.hasParent(member)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes any nodes from the given set that are parents of the specified
     * node. This method iterates through the set of nodes and checks if any of
     * them are parents of the provided node. If a parent is found, it is
     * removed from the set. This is useful for maintaining a collection of
     * nodes that should not include any ancestors of a particular node,
     * ensuring that only relevant nodes are kept in the set.
     *
     * @param club the set of nodes from which to remove parents
     * @param node the node for which to remove parent nodes from the set
     */
    public final void removeChildrenOf(Set<EditNodeAbstract> club, EditNodeAbstract node) {
        for (EditNodeAbstract member : club) {
            if (member.hasParent(node)) {
                club.remove(member);
                return;
            }
        }
    }

    /**
     * Returns the union of two arrays of nodes, eliminating duplicates. If the
     * second array is null, only the first array is considered. The resulting
     * array contains all unique nodes from both input arrays. This method is
     * useful for combining sets of nodes while ensuring that each node appears
     * only once in the result.
     *
     * @param nodes1 the first array of nodes to union
     * @param nodes2orNull the second array of nodes to union, which may be null
     * @return an array containing the unique nodes from both input arrays
     */
    public final EditNodeAbstract[] unionNodes(EditNodeAbstract[] nodes1, EditNodeAbstract[] nodes2orNull) {
        Set<EditNodeAbstract> union = new java.util.HashSet<>();
        for (EditNodeAbstract node : nodes1) {
            union.add(node);
        }
        if (nodes2orNull != null) {
            for (EditNodeAbstract node : nodes2orNull) {
                union.add(node);
            }
        }
        return union.toArray(new EditNodeAbstract[union.size()]);
    }
}
