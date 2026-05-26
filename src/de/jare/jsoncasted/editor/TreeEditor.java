/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor;

import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.events.EventBus;
import de.jare.jsoncasted.editor.events.HistoryManager;

/**
 * Main facade for the headless JSON tree editor core.
 *
 * <p>
 * This class combines the editable tree model, command history, and event
 * dispatching behind a single API. It is the primary entry point for loading,
 * modifying, exporting, and inspecting editor state.</p>
 */
public class TreeEditor {

    private final EditTree tree;
    private final HistoryManager historyManager;

    /**
     * Creates a new editor with a default object root named {@code root}.
     */
    public TreeEditor() {
        this("root");
    }

    /**
     * Creates a new editor for the given root node.
     *
     * @param rootText
     * @throws IllegalArgumentException if {@code root} is {@code null}
     */
    public TreeEditor(String rootText) {
        this(rootText, new EventBus());
    }

    /**
     * Creates a new editor for the given root node and event bus.
     *
     * @param rootText
     * @param eventBus the event bus to use, or {@code null} to create a default
     * one
     * @throws IllegalArgumentException if {@code root} is {@code null}
     */
    public TreeEditor(String rootText, EventBus eventBus) {
        this.tree = new EditTree(rootText);
        this.historyManager = new HistoryManager(tree, eventBus != null ? eventBus : new EventBus());
    }

    // -------------------------------------------------------------------------
    // Core accessors
    // -------------------------------------------------------------------------
    /**
     * Returns the editable tree managed by this editor.
     *
     * @return the tree instance
     */
    public EditTree getTree() {
        return tree;
    }

    /**
     * Returns the history manager used for undo/redo operations.
     *
     * @return the history manager
     */
    public HistoryManager getHistoryManager() {
        return historyManager;
    }

    // -------------------------------------------------------------------------
    // Command execution / history facade
    // -------------------------------------------------------------------------
    /**
     * Executes the given command and records it in the undo history.
     *
     * @param command the command to execute
     * @return the command result, or {@code null} if nothing was executed
     */
    public CommandResult execute(EditCommand command) {
        return historyManager.execute(command);
    }

    /**
     * Undoes the most recently executed command.
     *
     * @return the undo result, or {@code null} if no undo is available
     */
    public CommandResult undo() {
        return historyManager.undo();
    }

    /**
     * Redoes the most recently undone command.
     *
     * @return the redo result, or {@code null} if no redo is available
     */
    public CommandResult redo() {
        return historyManager.redo();
    }

    /**
     * Skips the current redo command without executing it and moves it back to
     * the undo stack.
     *
     * @return the skipped command, or {@code null} if no redo is available
     */
    public EditCommand skipRedo() {
        return historyManager.skipRedo();
    }

    /**
     * Returns whether an undo operation is currently available.
     *
     * @return {@code true} if undo is possible
     */
    public boolean canUndo() {
        return historyManager.canUndo();
    }

    /**
     * Returns whether a redo operation is currently available.
     *
     * @return {@code true} if redo is possible
     */
    public boolean canRedo() {
        return historyManager.canRedo();
    }

    /**
     * Clears the complete undo/redo history.
     */
    public void clearHistory() {
        historyManager.clear();
    }

    // -------------------------------------------------------------------------
    // Debug / inspection helpers
    // -------------------------------------------------------------------------
    /**
     * Returns a compact debug representation of the editor state.
     *
     * @return a one-line debug string
     */
    @Override
    public String toString() {
        return toDebugString();
    }

    /**
     * Returns a compact debug representation of the editor, including tree,
     * history, listener count, and validation state.
     *
     * @return a one-line debug string
     */
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TreeEditor{")
                .append("tree=").append(tree != null ? tree.toString() : "null")
                .append(", history=").append(historyManager != null ? historyManager.toString() : "null");
        sb.append('}');
        return sb.toString();
    }

    /**
     * Returns a readable representation of the undo and redo stacks.
     *
     * @return the formatted history output
     */
    public String toHistoryString() {
        if (historyManager == null) {
            return "<no history>";
        }

        StringBuilder sb = new StringBuilder();

        sb.append("Undo[").append(historyManager.getUndoSize()).append("]:\n");
        for (EditCommand cmd : historyManager.getUndoCommands()) {
            sb.append("  - ").append(formatCommand(cmd)).append('\n');
        }

        sb.append("Redo[").append(historyManager.getRedoSize()).append("]:\n");
        for (EditCommand cmd : historyManager.getRedoCommands()) {
            sb.append("  - ").append(formatCommand(cmd)).append('\n');
        }

        return sb.toString();
    }

    /**
     * Returns a formatted representation of the complete tree.
     *
     * @return the formatted tree output
     */
    public String toTreeString() {
        if (tree == null || tree.getRoot() == null) {
            return "<empty tree>";
        }
        return toTreeString(tree.getRoot());
    }

    /**
     * Returns a formatted representation of the subtree starting at the node
     * with the given ID.
     *
     * @param startNodeId the start node ID
     * @return the formatted subtree output, or a not-found marker
     */
    public String toTreeString(long startNodeId) {
        if (tree == null) {
            return "<empty tree>";
        }

        EditNode startNode = tree.findNodeById(startNodeId);
        if (startNode == null) {
            return "<node not found: " + startNodeId + ">";
        }

        return toTreeString(startNode);
    }

    /**
     * Returns a formatted representation of the subtree starting at the given
     * node.
     *
     * @param startNode the subtree root
     * @return the formatted subtree output
     */
    public String toTreeString(EditNode startNode) {
        if (startNode == null) {
            return "<null node>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Subtree from ").append(formatNodeHeader(startNode)).append('\n');
        appendNode(sb, startNode, 0, getIndexInParent(startNode));
        return sb.toString();
    }

    /**
     * Appends a formatted subtree representation to the given string builder.
     *
     * @param sb the target builder
     * @param node the current node
     * @param depth the current depth
     * @param indexInParent the index within the parent, or {@code -1} for root
     */
    private void appendNode(StringBuilder sb, EditNode node, int depth, int indexInParent) {
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }

        sb.append(indexInParent >= 0 ? "[" + indexInParent + "] " : "")
                .append(formatNodeHeader(node))
                .append('\n');

        for (int i = 0; i < node.getChildCount(); i++) {
            appendNode(sb, node.getChildAt(i), depth + 1, i);
        }
    }

    /**
     * Formats a single node for debug output.
     *
     * @param node the node to format
     * @return the formatted node header
     */
    private String formatNodeHeader(EditNode node) {
        EditNode parent = node.getParent();
        long parentId = parent != null ? parent.getEditId() : -1;

        StringBuilder sb = new StringBuilder();
        sb.append(node.getClass().getSimpleName())
                .append(" {name =").append(node.getName())
                .append(" {id=").append(node.getEditId())
                .append(", parentId=").append(parentId);

        try {
            String text = node.getEditText();
            if (text != null) {
                sb.append(", text='").append(text).append('\'');
            }
        } catch (Exception ignore) {
            // Some node types might not support getEditText()
        }

        sb.append('}');
        return sb.toString();
    }

    /**
     * Returns the index of the given node within its parent.
     *
     * @param node the node to inspect
     * @return the child index, or {@code -1} if the node has no parent
     */
    private int getIndexInParent(EditNode node) {
        if (node == null || node.getParent() == null) {
            return -1;
        }
        return node.getParent().getChildIndex(node);
    }

    /**
     * Formats a command for history output.
     *
     * @param cmd the command to format
     * @return the formatted command string
     */
    private String formatCommand(EditCommand cmd) {
        if (cmd == null) {
            return "null";
        }
        return cmd.getClass().getSimpleName() + "[" + cmd.toString() + "]";
    }
}
