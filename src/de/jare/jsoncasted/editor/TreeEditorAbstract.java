/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor;

import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.events.HistoryManager;

/**
 * Abstract base class for tree editors that provides common command execution,
 * undo/redo functionality, and tree model validation.
 *
 * <p>
 * This class encapsulates the shared logic between different tree editor
 * implementations, particularly the interaction with a {@link HistoryManager}
 * and the ability to check whether the underlying tree model is available.
 * </p>
 *
 * <p>
 * Subclasses must implement {@link #missTreeModel()} to indicate whether the
 * tree model is currently unavailable (e.g., due to garbage collection or
 * initialization state). This allows uniform handling of null/absent model
 * scenarios across all editor variants.
 * </p>
 *
 * @param <T> the type of the tree model used by this editor
 */
public abstract class TreeEditorAbstract<T> {

    /**
     * The history manager used for undo/redo operations.
     */
    protected final HistoryManager historyManager;

    /**
     * The tree model managed by this editor.
     */
    protected final T treeModel;

    /**
     * Creates a new abstract tree editor with the given tree model and history
     * manager.
     *
     * @param treeModel the tree model to use; may be {@code null} for
     * weak-reference-based implementations
     * @param historyManager the history manager to use; must not be
     * {@code null}
     * @throws IllegalArgumentException if historyManager is {@code null}
     */
    protected TreeEditorAbstract(T treeModel, HistoryManager historyManager) {
        if (historyManager == null) {
            throw new IllegalArgumentException("HistoryManager must not be null");
        }
        this.treeModel = treeModel;
        this.historyManager = historyManager;
    }

    /**
     * Returns whether the tree model is currently missing/unavailable.
     *
     * <p>
     * The default implementation checks if {@link #treeModel} is {@code null}.
     * Subclasses that use weak references or other indirect storage mechanisms
     * should override this method accordingly.
     * </p>
     *
     * @return {@code true} if the tree model is missing; {@code false}
     * otherwise
     */
    protected boolean missTreeModel() {
        return treeModel == null;
    }

    /**
     * Returns the underlying tree model object.
     *
     * @return the tree model, or {@code null} if unavailable
     */
    protected T getTreeModel() {
        return treeModel;
    }

    // -------------------------------------------------------------------------
    // Command execution / history facade
    // -------------------------------------------------------------------------
    /**
     * Executes the given command and records it in the undo history.
     *
     * @param command the command to execute
     * @return the command result, or {@code null} if the tree model is missing
     * or nothing was executed
     */
    public CommandResult execute(EditCommand command) {
        if (missTreeModel()) {
            return null;
        }
        return historyManager.execute(command);
    }

    /**
     * Undoes the most recently executed command.
     *
     * @return the undo result, or {@code null} if the tree model is missing or
     * no undo is available
     */
    public CommandResult undo() {
        if (missTreeModel()) {
            return null;
        }
        return historyManager.undo();
    }

    /**
     * Redoes the most recently undone command.
     *
     * @return the redo result, or {@code null} if the tree model is missing or
     * no redo is available
     */
    public CommandResult redo() {
        if (missTreeModel()) {
            return null;
        }
        return historyManager.redo();
    }

    /**
     * Skips the current redo command without executing it and moves it back to
     * the undo stack.
     *
     * @return the skipped command, or {@code null} if the tree model is missing
     * or no redo is available
     */
    public EditCommand skipRedo() {
        if (missTreeModel()) {
            return null;
        }
        return historyManager.skipRedo();
    }

    /**
     * Returns whether an undo operation is currently available.
     *
     * @return {@code false} if the tree model is missing; otherwise delegates
     * to the history manager
     */
    public boolean canUndo() {
        if (missTreeModel()) {
            return false;
        }
        return historyManager.canUndo();
    }

    /**
     * Returns whether a redo operation is currently available.
     *
     * @return {@code false} if the tree model is missing; otherwise delegates
     * to the history manager
     */
    public boolean canRedo() {
        if (missTreeModel()) {
            return false;
        }
        return historyManager.canRedo();
    }

    /**
     * Clears the complete undo/redo history.
     */
    public void clearHistory() {
        historyManager.clear();
    }

    // -------------------------------------------------------------------------
    // Convenience methods for tree model checks
    // -------------------------------------------------------------------------
    /**
     * Returns whether the tree model is currently available. This is the
     * inverse of {@link #missTreeModel()}.
     *
     * @return {@code true} if the tree model is available; {@code false}
     * otherwise
     */
    public boolean hasTreeModel() {
        return !missTreeModel();
    }

    /**
     * Returns the history manager used by this editor.
     *
     * @return the history manager instance
     */
    public HistoryManager getHistoryManager() {
        return historyManager;
    }
}
