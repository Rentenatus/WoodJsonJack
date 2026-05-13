/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control;

import de.jare.tree.control.commands.WoodCommand;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.history.HistoryManager;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.command.CommandResult;

import java.lang.ref.WeakReference;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.swing.tree.TreeModel;

/**
 * Simple undo/redo manager for the tree editor.
 * <p>
 * Commands must implement {@link WoodCommand} and are pushed via
 * {@link #executeCommand(WoodCommand)}. The manager keeps two stacks for undo
 * and redo operations.
 * </p>
 * <p>
 * When an EditTree is available, this class can use HistoryManager internally
 * for EditCommand-based operations, while maintaining backward compatibility
 * with WoodCommand for TreeModel-based operations.
 * </p>
 */
public class UndoManagerModel {

    final private WeakReference< TreeModel> weakTreeModel;
    private WeakReference<EditTree> weakEditTree;
    private HistoryManager historyManager;

    private final Deque<WoodCommand> undoStack = new ArrayDeque<>();
    private final Deque<WoodCommand> redoStack = new ArrayDeque<>();
    private int limit = 100;

    public UndoManagerModel(TreeModel treeModel) {
        this.weakTreeModel = new WeakReference<>(treeModel);
    }

    /**
     * Creates an UndoManagerModel with both TreeModel and EditTree support.
     *
     * @param treeModel the Swing TreeModel
     * @param editTree the EditTree for editor-based operations
     */
    public UndoManagerModel(TreeModel treeModel, EditTree editTree) {
        this.weakTreeModel = new WeakReference<>(treeModel);
        this.weakEditTree = new WeakReference<>(editTree);
        if (editTree != null) {
            this.historyManager = new HistoryManager(editTree);
        }
    }

    /**
     * Sets the EditTree for this manager and creates a HistoryManager for it.
     *
     * @param editTree the EditTree to use
     */
    public void setEditTree(EditTree editTree) {
        this.weakEditTree = new WeakReference<>(editTree);
        this.historyManager = editTree != null ? new HistoryManager(editTree) : null;
    }

    /**
     * Gets the EditTree associated with this manager.
     *
     * @return the EditTree, or null if not set
     */
    public EditTree getEditTree() {
        return weakEditTree != null ? weakEditTree.get() : null;
    }

    /**
     * Checks if this manager has an EditTree and HistoryManager available.
     *
     * @return true if EditTree is available
     */
    public boolean hasEditTree() {
        return getEditTree() != null && historyManager != null;
    }

    /**
     * Gets the HistoryManager for EditCommand-based operations.
     *
     * @return the HistoryManager, or null if not available
     */
    public HistoryManager getHistoryManager() {
        return historyManager;
    }

    public TreeModel getTreeModel() {
        return weakTreeModel.get();
    }

    /**
     * Add the given command and pushes it onto the undo stack. The redo stack
     * is cleared.
     *
     * @param command command to execute; must not be {@code null}
     */
    public void pushCommand(WoodCommand command) {
        if (command == null || getTreeModel() == null) {
            return;
        }
        undoStack.push(command);
        redoStack.clear();
        trimToLimit();
    }

    /**
     * Pushes an EditCommand to the HistoryManager if EditTree is available.
     * Otherwise falls back to WoodCommand handling.
     *
     * @param editCommand the EditCommand to execute
     * @return the CommandResult, or null if no EditTree is available
     */
    public CommandResult pushEditCommand(EditCommand editCommand) {
        if (historyManager != null) {
            return historyManager.execute(editCommand);
        }
        return null;
    }

    /**
     * Performs an undo operation if possible.
     *
     * @return
     */
    public WoodCommand undo() {
        TreeModel lokalModel = getTreeModel();
        if (!canUndo(lokalModel)) {
            return null;
        }
        WoodCommand cmd = undoStack.pop();
        cmd.undo(lokalModel);
        redoStack.push(cmd);
        return cmd;
    }

    /**
     * Performs an undo operation using HistoryManager if available.
     *
     * @return the CommandResult, or null if no EditTree is available or nothing to undo
     */
    public CommandResult undoEdit() {
        if (historyManager != null) {
            return historyManager.undo();
        }
        return null;
    }

    /**
     * Performs a redo operation if possible.
     *
     * @return
     */
    public WoodCommand redo() {
        TreeModel lokalModel = getTreeModel();
        if (!canRedo(lokalModel)) {
            return null;
        }
        WoodCommand cmd = redoStack.pop();
        cmd.execute(lokalModel);
        undoStack.push(cmd);
        return cmd;
    }

    /**
     * Performs a redo operation using HistoryManager if available.
     *
     * @return the CommandResult, or null if no EditTree is available or nothing to redo
     */
    public CommandResult redoEdit() {
        if (historyManager != null) {
            return historyManager.redo();
        }
        return null;
    }

    /**
     * Performs a redo operation if possible.
     *
     * @return
     */
    public WoodCommand skip_redo() {
        TreeModel lokalModel = getTreeModel();
        if (!canRedo(lokalModel)) {
            return null;
        }
        WoodCommand cmd = redoStack.pop();
        cmd.skip(lokalModel);
        undoStack.push(cmd);
        return cmd;
    }

    /**
     * Performs a skip_redo operation using HistoryManager if available.
     *
     * @return the skipped EditCommand, or null if no EditTree is available or nothing to skip
     */
    public EditCommand skipRedoEdit() {
        if (historyManager != null) {
            return historyManager.skipRedo();
        }
        return null;
    }

    /**
     * Returns whether an undo operation is currently available.
     *
     * @param lokalModel
     * @return {@code true} if undo can be performed
     */
    public boolean canUndo(TreeModel lokalModel) {
        return !undoStack.isEmpty() && lokalModel != null;
    }

    /**
     * Returns whether an undo operation is currently available using HistoryManager.
     *
     * @return true if EditTree is available and undo can be performed
     */
    public boolean canUndoEdit() {
        return historyManager != null && historyManager.canUndo();
    }

    /**
     * Returns whether a redo operation is currently available.
     *
     * @param lokalModel
     * @return {@code true} if redo can be performed
     */
    public boolean canRedo(TreeModel lokalModel) {
        return !redoStack.isEmpty() && lokalModel != null;
    }

    /**
     * Returns whether a redo operation is currently available using HistoryManager.
     *
     * @return true if EditTree is available and redo can be performed
     */
    public boolean canRedoEdit() {
        return historyManager != null && historyManager.canRedo();
    }

    /**
     * Returns whether an undo operation is currently available.
     *
     * @return {@code true} if undo can be performed
     */
    public boolean canUndo() {
        return !undoStack.isEmpty() && getTreeModel() != null;
    }

    /**
     * Returns whether a redo operation is currently available.
     *
     * @return {@code true} if redo can be performed
     */
    public boolean canRedo() {
        return !redoStack.isEmpty() && getTreeModel() != null;
    }

    /**
     * Clears all undo and redo history.
     */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
        if (historyManager != null) {
            historyManager.clear();
        }
    }

    /**
     * Sets the maximum number of commands kept in the undo history. Older
     * entries are discarded when the limit is exceeded.
     *
     * @param limit positive maximum size of the undo stack
     */
    public void setLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        this.limit = limit;
        trimToLimit();
        if (historyManager != null) {
            historyManager.setLimit(limit);
        }
    }

    /**
     * Returns the configured maximum number of undoable commands.
     *
     * @return current limit
     */
    public int getLimit() {
        return limit;
    }

    private void trimToLimit() {
        if (limit <= 0) {
            return;
        }
        while (undoStack.size() > limit) {
            undoStack.removeLast();
        }
    }

    public int size() {
        return undoStack.size() + redoStack.size();
    }

    public int unoSize() {
        return undoStack.size();
    }

    public int redoSize() {
        return redoStack.size();
    }

    /**
     * Returns the total size including both WoodCommand and EditCommand stacks.
     *
     * @return the total size
     */
    public int getTotalSize() {
        int baseSize = undoStack.size() + redoStack.size();
        if (historyManager != null) {
            baseSize += historyManager.getTotalSize();
        }
        return baseSize;
    }

    public WoodCommand getRedo(int index) {
        if (index < 0 || index >= redoStack.size()) {
            return null;
        }
        return redoStack.stream().skip(index).findFirst().orElse(null);
    }

    public WoodCommand getUndo(int index) {
        if (index < 0 || index >= undoStack.size()) {
            return null;
        }
        return undoStack.stream().skip(index).findFirst().orElse(null);
    }

    public List<String> getUndoLabels(int max) {
        List<String> result = new ArrayList<>();
        int count = Math.min(max, undoStack.size());
        // 0 = naechstes Undo (oberstes Element)
        for (int i = 0; i < count; i++) {
            WoodCommand cmd = getUndo(i);
            if (cmd == null) {
                break;
            }
            result.add((i + 1) + ": " + cmd.getCommandText() + " - " + cmd.getDescription());
        }
        return result;
    }

    public List<String> getRedoLabels(int max) {
        List<String> result = new ArrayList<>();
        int count = Math.min(max, redoStack.size());
        // 0 = naechstes Redo (oberstes Element)
        for (int i = 0; i < count; i++) {
            WoodCommand cmd = getRedo(i);
            if (cmd == null) {
                break;
            }
            result.add((i + 1) + ": " + cmd.getCommandText() + " - " + cmd.getDescription());
        }
        return result;
    }

}
