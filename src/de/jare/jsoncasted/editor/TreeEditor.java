/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor;

import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.events.EditEvent;
import de.jare.jsoncasted.editor.events.EventBus;
import de.jare.jsoncasted.editor.history.HistoryManager;

import java.util.function.Consumer;

/**
 * Main facade class for the headless JSON tree editor core.
 * This class provides a unified interface to the editor's functionality,
 * including tree manipulation, undo/redo history, and event handling.
 * 
 * <p>This is the primary entry point for using the editor core. It coordinates
 * the tree structure, command history, and event distribution.</p>
 * 
 * <p>Usage example:</p>
 * <pre>
 * TreeEditor editor = new TreeEditor();
 * EditNode root = editor.getTree().getRoot();
 * 
 * // Add a node
 * EditNodeObject newNode = new EditNodeObject("newNode");
 * editor.execute(new AddNodeCommand(root.getId(), newNode));
 * 
 * // Undo
 * editor.undo();
 * 
 * // Listen for changes
 * editor.addListener(NodeChangeEvent.class, event -> {
 *     System.out.println("Node changed: " + event.getNode().getName());
 * });
 * </pre>
 */
public class TreeEditor {

    private final EditTree tree;
    private final HistoryManager historyManager;
    private final EventBus eventBus;

    /**
     * Creates a new TreeEditor with a default root node.
     * The root node is an object node named "root".
     */
    public TreeEditor() {
        this(new EditNodeObject("root"));
    }

    /**
     * Creates a new TreeEditor with the specified root node.
     * 
     * @param root the root node for the tree
     * @throws IllegalArgumentException if root is null
     */
    public TreeEditor(EditNode root) {
        if (root == null) {
            throw new IllegalArgumentException("Root node cannot be null");
        }
        this.eventBus = new EventBus();
        this.tree = new EditTree(root);
        this.historyManager = new HistoryManager(tree, eventBus);
    }

    /**
     * Creates a new TreeEditor with the specified root node and event bus.
     * 
     * @param root the root node for the tree
     * @param eventBus the event bus to use
     * @throws IllegalArgumentException if root is null
     */
    public TreeEditor(EditNode root, EventBus eventBus) {
        if (root == null) {
            throw new IllegalArgumentException("Root node cannot be null");
        }
        this.eventBus = eventBus != null ? eventBus : new EventBus();
        this.tree = new EditTree(root);
        this.historyManager = new HistoryManager(tree, this.eventBus);
    }

    /**
     * Returns the edit tree managed by this editor.
     * 
     * @return the edit tree
     */
    public EditTree getTree() {
        return tree;
    }

    /**
     * Returns the history manager for undo/redo operations.
     * 
     * @return the history manager
     */
    public HistoryManager getHistoryManager() {
        return historyManager;
    }

    /**
     * Returns the event bus for registering listeners.
     * 
     * @return the event bus
     */
    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * Executes a command on the tree.
     * The command is added to the undo history.
     * 
     * @param command the command to execute
     */
    public void execute(EditCommand command) {
        historyManager.execute(command);
    }

    /**
     * Undoes the last executed command.
     * 
     * @return the undone command, or null if nothing to undo
     */
    public EditCommand undo() {
        return historyManager.undo();
    }

    /**
     * Redoes the last undone command.
     * 
     * @return the redone command, or null if nothing to redo
     */
    public EditCommand redo() {
        return historyManager.redo();
    }

    /**
     * Returns whether an undo operation is available.
     * 
     * @return true if undo is available
     */
    public boolean canUndo() {
        return historyManager.canUndo();
    }

    /**
     * Returns whether a redo operation is available.
     * 
     * @return true if redo is available
     */
    public boolean canRedo() {
        return historyManager.canRedo();
    }

    /**
     * Clears all undo and redo history.
     */
    public void clearHistory() {
        historyManager.clear();
    }

    /**
     * Adds a listener for a specific event type.
     * 
     * @param <T> the event type
     * @param eventType the class of events to listen for
     * @param listener the consumer to be called when an event is fired
     */
    public <T> void addListener(Class<T> eventType, Consumer<T> listener) {
        eventBus.addListener(eventType, listener);
    }

    /**
     * Adds a listener for the base EditEvent type.
     * This will receive all events.
     * 
     * @param listener the consumer to be called when any event is fired
     */
    public void addListener(Consumer<EditEvent> listener) {
        eventBus.addListener(EditEvent.class, listener);
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
        return eventBus.removeListener(eventType, listener);
    }

    /**
     * Fires an event to all registered listeners.
     * 
     * @param <T> the event type
     * @param event the event to fire
     */
    public <T> void fireEvent(T event) {
        eventBus.fireEvent(event);
    }

    /**
     * Removes all listeners from the event bus.
     */
    public void clearListeners() {
        eventBus.clear();
    }

    @Override
    public String toString() {
        return "TreeEditor[tree=" + tree + 
               ", history=" + historyManager + 
               ", listeners=" + eventBus.getListenerCount() + "]";
    }
}
