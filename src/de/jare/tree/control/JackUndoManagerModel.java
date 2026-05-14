/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control;

import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.events.EventBus;
import de.jare.jsoncasted.editor.history.HistoryManager;
import de.jare.jsoncasted.tools.SimpleStringSplitter;
import de.jare.tree.control.commands.WoodCommand;
import de.jare.tree.control.model.JackTreeModel;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Simple undo/redo manager for the tree editor.
 * <p>
 * Commands must implement {@link WoodCommand} and are pushed via
 * {@link #executeCommand(WoodCommand)}. The manager keeps two stacks for undo
 * and redo operations.
 * </p>
 */
public class JackUndoManagerModel implements SimpleStringSplitter {

    private final WeakReference< JackTreeModel> weakTreeModel;
    private final HistoryManager historyManager;
    private final EventBus eventBus;

    public JackUndoManagerModel(JackTreeModel treeModel) {
        this.weakTreeModel = new WeakReference<>(treeModel);
        eventBus = new EventBus();
        historyManager = new HistoryManager(treeModel.getEditTree(), eventBus);
    }

    public JackTreeModel getTreeModel() {
        return weakTreeModel.get();
    }

    /**
     * Adds a listener for a specific event type. The listener will be notified
     * whenever an event of the specified type is fired.
     *
     * @param <T> the event type
     * @param eventType the class of events to listen for
     * @param listener the consumer to be called when an event is fired
     * @throws IllegalArgumentException if eventType or listener is null
     */
    public <T> void addListener(Class<T> eventType, Consumer<T> listener) {
        if (eventBus == null) {
            throw new NullPointerException("EventBus not set.");
        }
        eventBus.addListener(eventType, listener);
    }

    /**
     * Removes a listener for a specific event type.
     *
     * @param <T> the event type
     * @param eventType the class of events
     * @param listener the consumer to remove
     * @return true if the listener was removed
     */
    public <T> boolean removeListener(Class<T> eventType, Consumer<T> listener) {
        if (eventBus == null) {
            return false;
        }
        return eventBus.removeListener(eventType, listener);
    }

    /**
     * Add the given command and pushes it onto the undo stack.The redo stack is
     * cleared.
     *
     * @param command command to execute; must not be {@code null}
     * @return
     */
    public CommandResult executeCommand(EditCommand command) {
        if (command == null || getTreeModel() == null) {
            return null;
        }
        return historyManager.execute(command);
    }

    /**
     * Performs an undo operation if possible.
     *
     * @return
     */
    public CommandResult undo() {
        JackTreeModel lokalModel = getTreeModel();
        if (lokalModel == null) {
            return null;
        }
        return historyManager.undo();

    }

    /**
     * Performs a redo operation if possible.
     *
     * @return
     */
    public CommandResult redo() {
        JackTreeModel lokalModel = getTreeModel();
        if (lokalModel == null) {
            return null;
        }
        return historyManager.redo();
    }

    /**
     * Performs a redo operation if possible.
     *
     * @return
     */
    public EditCommand skip_redo() {
        JackTreeModel lokalModel = getTreeModel();
        if (lokalModel == null) {
            return null;
        }
        return historyManager.skipRedo();
    }

    /**
     * Sets the maximum number of commands kept in the undo history. Older
     * entries are discarded when the limit is exceeded.
     *
     * @param limit positive maximum size of the undo stack
     */
    public void setLimit(int limit) {
        historyManager.setLimit(limit);
    }

    /**
     * Returns the configured maximum number of undoable commands.
     *
     * @return current limit
     */
    public int getLimit() {
        return historyManager.getLimit();
    }

    public int size() {
        return undoSize() + redoSize();
    }

    public int undoSize() {
        return historyManager.undoSize();
    }

    public int redoSize() {
        return historyManager.redoSize();
    }

    public EditCommand getRedo(int index) {
        return historyManager.getRedo(index);
    }

    public EditCommand getUndo(int index) {
        return historyManager.getUndo(index);
    }

    boolean canUndo() {
        return historyManager.canUndo();
    }

    boolean canRedo() {
        return historyManager.canRedo();
    }

    void clear() {
        historyManager.clear();
    }

    public List<String> getUndoLabels(int max) {
        return maskLabels(historyManager.getUndoLabels(max));
    }

    public List<String> getRedoLabels(int max) {
        return maskLabels(historyManager.getRedoLabels(max));
    }

    public List<String> maskLabels(List<String[]> labels) {
        List<String> ret = new ArrayList<>(labels.size());
        for (String[] label : labels) {
            // Hier muss noch die Maskierung von CommandTypeText rein.
            ret.add(simpleConcat(label, ""));
        }
        return ret;
    }

    boolean containsHistory(HistoryManager source) {
        return historyManager == source;
    }

}
