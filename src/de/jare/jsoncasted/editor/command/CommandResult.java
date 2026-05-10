/*
* Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v2.0 which
* accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import java.util.Objects;

import de.jare.jsoncasted.editor.core.EditNode;

public final class CommandResult {

    private final EditCommand trigger;
    private final CommandAction action;
    private final EditNode[] affectedNodes;
    private final EditNode[] addedNodes;
    private final EditNode[] removedNodes;
    private final EditNode[] updatedNodes;

    public CommandResult(
            EditCommand trigger,
            CommandAction action,
            EditNode[] affectedNodes,
            EditNode[] addedNodes,
            EditNode[] removedNodes,
            EditNode[] updatedNodes) {
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.action = Objects.requireNonNull(action, "action");
        this.affectedNodes = affectedNodes != null ? affectedNodes.clone() : new EditNode[0];
        this.addedNodes = addedNodes != null ? addedNodes.clone() : new EditNode[0];
        this.removedNodes = removedNodes != null ? removedNodes.clone() : new EditNode[0];
        this.updatedNodes = updatedNodes != null ? updatedNodes.clone() : new EditNode[0];
    }

    public EditCommand getTrigger() {
        return trigger;
    }

    public CommandAction getAction() {
        return action;
    }

    public EditNode[] getAffectedNodes() {
        return affectedNodes.clone();
    }

    public EditNode[] getAddedNodes() {
        return addedNodes.clone();
    }

    public EditNode[] getRemovedNodes() {
        return removedNodes.clone();
    }

    public EditNode[] getUpdatedNodes() {
        return updatedNodes.clone();
    }

    @Override
    public String toString() {
        return "CommandResult{"
                + "action=" + action
                + ", trigger=" + formatTrigger(trigger)
                + ", affectedNodes=" + formatNodes(affectedNodes)
                + ", addedNodes=" + formatNodes(addedNodes)
                + ", removedNodes=" + formatNodes(removedNodes)
                + ", updatedNodes=" + formatNodes(updatedNodes)
                + '}';
    }

    private static String formatTrigger(EditCommand trigger) {
        if (trigger == null) {
            return "null";
        }
        return trigger.getClass().getSimpleName() + "[" + trigger.toString() + "]";
    }

    private static String formatNodes(EditNode[] nodes) {
        if (nodes == null || nodes.length == 0) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < nodes.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(formatNode(nodes[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    private static String formatNode(EditNode node) {
        if (node == null) {
            return "null";
        }

        EditNode parent = node.getParent();
        long parentId = parent != null ? parent.getEditId() : -1;

        return "EditNode{"
                + "id=" + node.getEditId()
                + ", name='" + node.getName() + '\''
                + ", parentId=" + parentId
                + '}';
    }
}
