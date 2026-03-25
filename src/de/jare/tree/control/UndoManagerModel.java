/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control;

import de.jare.tree.control.commands.WoodCommand;
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
 */
public class UndoManagerModel {

    final private WeakReference< TreeModel> weakTreeModel;

    private final Deque<WoodCommand> undoStack = new ArrayDeque<>();
    private final Deque<WoodCommand> redoStack = new ArrayDeque<>();
    private int limit = 100;

    public UndoManagerModel(TreeModel treeModel) {
        this.weakTreeModel = new WeakReference<>(treeModel);
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
     * Returns whether an undo operation is currently available.
     *
     * @param lokalModel
     * @return {@code true} if undo can be performed
     */
    public boolean canUndo(TreeModel lokalModel) {
        return !undoStack.isEmpty() && lokalModel != null;
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
