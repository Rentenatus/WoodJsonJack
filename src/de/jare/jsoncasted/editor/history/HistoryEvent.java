/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.history;

import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.events.EditEvent;

/**
 * Event fired when the history state changes (command executed, undone, redone, cleared).
 */
public class HistoryEvent implements EditEvent {

    /**
     * Type of history change.
     */
    public enum ChangeType {
        /** A command was executed */
        EXECUTED,
        /** A command was undone */
        UNDONE,
        /** A command was redone */
        REDONE,
        /** A command was skipped (moved from redo to undo without execution) */
        SKIPPED,
        /** History was cleared */
        CLEARED
    }

    private final Object source;
    private final long timestamp;
    private final ChangeType changeType;
    private final EditCommand command;
    private final int undoSize;
    private final int redoSize;

    /**
     * Creates a new history event. 
     * 
     * @param source the source of this event
     * @param changeType the type of history change
     * @param command the command involved (may be null for CLEARED)
     * @param undoSize the current size of the undo stack
     * @param redoSize the current size of the redo stack
     */
    public HistoryEvent(
            Object source,
            ChangeType changeType,
            EditCommand command,
            int undoSize,
            int redoSize) {
        this.source = source;
        this.timestamp = System.currentTimeMillis();
        this.changeType = changeType;
        this.command = command;
        this.undoSize = undoSize;
        this.redoSize = redoSize;
    }

    @Override
    public Object getSource() {
        return source;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("History ").append(changeType).append(": ");
        if (command != null) {
            sb.append("command=").append(command.getDescription());
        }
        sb.append(", undoSize=").append(undoSize);
        sb.append(", redoSize=").append(redoSize);
        return sb.toString();
    }

    /**
     * Returns the type of history change. 
     * 
     * @return the change type
     */
    public ChangeType getChangeType() {
        return changeType;
    }

    /**
     * Returns the command involved in this event. 
     * 
     * @return the command, may be null
     */
    public EditCommand getCommand() {
        return command;
    }

    /**
     * Returns the current size of the undo stack. 
     * 
     * @return the undo stack size
     */
    public int getUndoSize() {
        return undoSize;
    }

    /**
     * Returns the current size of the redo stack. 
     * 
     * @return the redo stack size
     */
    public int getRedoSize() {
        return redoSize;
    }

    @Override
    public String toString() {
        return "HistoryEvent[" + changeType + 
               ", command=" + (command != null ? command.getClass().getSimpleName() : "null") +
               ", undoSize=" + undoSize + 
               ", redoSize=" + redoSize + "]";
    }
}
