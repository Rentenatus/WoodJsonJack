/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.history;

import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.events.EventBus;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Manages the undo and redo history for the editor.
 * Keeps track of executed commands and allows undoing and redoing changes.
 */
public class HistoryManager {

    private final EditTree tree;
    private final EventBus eventBus;
    
    private final Deque<EditCommand> undoStack = new ArrayDeque<>();
    private final Deque<EditCommand> redoStack = new ArrayDeque<>();
    
    private int limit = 100;

    /**
     * Creates a new HistoryManager for the specified tree.
     * 
     * @param tree the edit tree this manager operates on
     */
    public HistoryManager(EditTree tree) {
        this(tree, null);
    }

    /**
     * Creates a new HistoryManager with an event bus.
     * 
     * @param tree the edit tree this manager operates on
     * @param eventBus the event bus for firing history events
     */
    public HistoryManager(EditTree tree, EventBus eventBus) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }
        this.tree = tree;
        this.eventBus = eventBus;
    }

    /**
     * Executes the given command and adds it to the undo history.
     * The redo history is cleared when a new command is executed.
     * 
     * @param command the command to execute
     */
    public void execute(EditCommand command) {
        if (command == null) {
            return;
        }

        // Execute the command
        command.execute(tree);

        // Add to undo stack
        undoStack.push(command);
        
        // Clear redo stack
        redoStack.clear();

        // Trim to limit
        trimToLimit();

        // Fire event
        if (eventBus != null) {
            eventBus.fireEvent(new HistoryEvent(
                this,
                HistoryEvent.ChangeType.EXECUTED,
                command,
                undoStack.size(),
                redoStack.size()
            ));
        }
    }

    /**
     * Undoes the last executed command.
     * The undone command is moved to the redo stack.
     * 
     * @return the undone command, or null if nothing to undo
     */
    public EditCommand undo() {
        if (!canUndo()) {
            return null;
        }

        EditCommand command = undoStack.pop();
        command.undo(tree);
        redoStack.push(command);

        // Fire event
        if (eventBus != null) {
            eventBus.fireEvent(new HistoryEvent(
                this,
                HistoryEvent.ChangeType.UNDONE,
                command,
                undoStack.size(),
                redoStack.size()
            ));
        }

        return command;
    }

    /**
     * Redoes the last undone command.
     * The redone command is moved back to the undo stack.
     * 
     * @return the redone command, or null if nothing to redo
     */
    public EditCommand redo() {
        if (!canRedo()) {
            return null;
        }

        EditCommand command = redoStack.pop();
        command.execute(tree);
        undoStack.push(command);

        // Fire event
        if (eventBus != null) {
            eventBus.fireEvent(new HistoryEvent(
                this,
                HistoryEvent.ChangeType.REDONE,
                command,
                undoStack.size(),
                redoStack.size()
            ));
        }

        return command;
    }

    /**
     * Returns whether an undo operation is available.
     * 
     * @return true if there are commands to undo
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Returns whether a redo operation is available.
     * 
     * @return true if there are commands to redo
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Clears all undo and redo history.
     */
    public void clear() {
        undoStack.clear();
        redoStack.clear();

        // Fire event
        if (eventBus != null) {
            eventBus.fireEvent(new HistoryEvent(
                this,
                HistoryEvent.ChangeType.CLEARED,
                null,
                0,
                0
            ));
        }
    }

    /**
     * Sets the maximum number of commands to keep in history.
     * Older commands are discarded when the limit is exceeded.
     * 
     * @param limit the maximum number of commands (must be > 0)
     */
    public void setLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be > 0");
        }
        this.limit = limit;
        trimToLimit();
    }

    /**
     * Returns the current history limit.
     * 
     * @return the limit
     */
    public int getLimit() {
        return limit;
    }

    /**
     * Returns the number of commands in the undo stack.
     * 
     * @return the undo stack size
     */
    public int getUndoSize() {
        return undoStack.size();
    }

    /**
     * Returns the number of commands in the redo stack.
     * 
     * @return the redo stack size
     */
    public int getRedoSize() {
        return redoStack.size();
    }

    /**
     * Returns an iterable over the undo stack commands (from most recent to oldest).
     */
    public Iterable<EditCommand> getUndoCommands() {
        return () -> undoStack.descendingIterator();
    }

    /**
     * Returns an iterable over the redo stack commands (from most recent to oldest).
     */
    public Iterable<EditCommand> getRedoCommands() {
        return () -> redoStack.descendingIterator();
    }

    /**
     * Returns the total number of commands in history.
     * 
     * @return the total size
     */
    public int getTotalSize() {
        return undoStack.size() + redoStack.size();
    }

    /**
     * Trims the undo stack to the configured limit.
     */
    private void trimToLimit() {
        while (undoStack.size() > limit) {
            undoStack.removeLast();
        }
    }

    /**
     * Returns the event bus used by this manager.
     * 
     * @return the event bus, may be null
     */
    public EventBus getEventBus() {
        return eventBus;
    }

    @Override
    public String toString() {
        return "HistoryManager[undo=" + undoStack.size() + 
               ", redo=" + redoStack.size() + 
               ", limit=" + limit + "]";
    }
}
