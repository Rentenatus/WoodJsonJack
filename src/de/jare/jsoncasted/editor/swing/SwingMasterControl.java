/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.swing;

import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.events.SelectionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * Swing-specific master controller that coordinates between multiple tree editors.
 * This is the new version that uses TreeEditor and HistoryManager internally.
 * 
 * <p>This class replaces the old MasterControl from de.jare.tree.control package.</p>
 */
public class SwingMasterControl {

    private final SwingTreeEditor swingTreeEditor;
    private final List<Consumer<SelectionEvent>> selectionListeners;
    private final List<Consumer<String>> commandListeners;
    private final List<Consumer<CommandResult>> undoRedoListeners;
    
    private Object activeEditor;

    public SwingMasterControl() {
        this(new EditNodeObject("root"));
    }

    public SwingMasterControl(EditNode root) {
        this.swingTreeEditor = new SwingTreeEditor(root);
        this.selectionListeners = new ArrayList<>();
        this.commandListeners = new ArrayList<>();
        this.undoRedoListeners = new ArrayList<>();
        this.activeEditor = null;
        
        // Set up listeners on the swing tree editor
        this.swingTreeEditor.setSelectionListener(this::fireSelectionEvent);
        this.swingTreeEditor.setCommandListener(this::fireCommandEvent);
        this.swingTreeEditor.setUndoRedoListener(this::fireUndoRedoEvent);
    }

    // -------------------------------------------------------------------------
    // Core accessors
    // -------------------------------------------------------------------------

    public SwingTreeEditor getSwingTreeEditor() {
        return swingTreeEditor;
    }

    public EditTree getEditTree() {
        return swingTreeEditor.getEditTree();
    }

    // -------------------------------------------------------------------------
    // Editor registration
    // -------------------------------------------------------------------------

    /**
     * Registers a Swing TreeModel with this controller.
     *
     * @param model the TreeModel to register
     * @return the adapter for this model
     */
    public SwingTreeEditor.EditTreeAdapter registerEditor(TreeModel model) {
        return swingTreeEditor.registerModel(model);
    }

    /**
     * Sets the currently active editor.
     *
     * @param editor the active editor component
     * @param trigger the trigger object
     */
    public void setActiveEditor(Object editor, Object trigger) {
        Object previous = this.activeEditor;
        if (previous == editor) {
            return;
        }
        this.activeEditor = editor;
        
        // Fire editor selection event
        fireEditorSelected(editor, trigger);
    }

    public Object getActiveEditor() {
        return activeEditor;
    }

    // -------------------------------------------------------------------------
    // Command execution
    // -------------------------------------------------------------------------

    /**
     * Executes a command on the active editor.
     *
     * @param command the command to execute
     * @return the command result
     */
    public CommandResult execute(EditCommand command) {
        return swingTreeEditor.execute(command);
    }

    /**
     * Executes a command by its string ID.
     *
     * @param commandId the command identifier
     * @param trigger the trigger object
     */
    public void fireCommand(String commandId, Object trigger) {
        swingTreeEditor.fireCommand(commandId, trigger);
    }

    // -------------------------------------------------------------------------
    // Undo/Redo
    // -------------------------------------------------------------------------

    public CommandResult undo() {
        return swingTreeEditor.undo();
    }

    public CommandResult redo() {
        return swingTreeEditor.redo();
    }

    public boolean canUndo() {
        return swingTreeEditor.canUndo();
    }

    public boolean canRedo() {
        return swingTreeEditor.canRedo();
    }

    // -------------------------------------------------------------------------
    // Clipboard operations
    // -------------------------------------------------------------------------

    /**
     * Copies selected nodes to clipboard.
     *
     * @param trigger the source editor
     * @param paths the selected tree paths
     * @param cut whether this is a cut operation
     */
    public void copySelection(Object trigger, TreePath[] paths, boolean cut) {
        swingTreeEditor.copyToClipboard(paths, cut);
    }

    /**
     * Pastes nodes from clipboard to the target path.
     *
     * @param trigger the target editor
     * @param path the target tree path
     * @return true if paste was successful
     */
    public boolean pasteClipboard(Object trigger, TreePath path) {
        return swingTreeEditor.pasteFromClipboard(path);
    }

    /**
     * Checks if nodes can be pasted to the target.
     *
     * @param targetData the target EditNode
     * @return true if paste is possible
     */
    public boolean canPasteTo(EditNode targetData) {
        // TODO: Implement paste availability check
        return true;
    }

    // -------------------------------------------------------------------------
    // Listener management
    // -------------------------------------------------------------------------

    /**
     * Adds a selection listener.
     *
     * @param level the priority level
     * @param listener the listener to add
     */
    public void addSelectionListener(int level, Consumer<SelectionEvent> listener) {
        selectionListeners.add(listener);
    }

    /**
     * Adds a selection listener with default priority.
     *
     * @param listener the listener to add
     */
    public void addSelectionListener(Consumer<SelectionEvent> listener) {
        addSelectionListener(0, listener);
    }

    /**
     * Removes a selection listener.
     *
     * @param listener the listener to remove
     */
    public void removeSelectionListener(Consumer<SelectionEvent> listener) {
        selectionListeners.remove(listener);
    }

    /**
     * Adds a command listener.
     *
     * @param listener the listener to add
     */
    public void addCommandListener(Consumer<String> listener) {
        commandListeners.add(listener);
    }

    /**
     * Removes a command listener.
     *
     * @param listener the listener to remove
     */
    public void removeCommandListener(Consumer<String> listener) {
        commandListeners.remove(listener);
    }

    /**
     * Adds an undo/redo listener.
     *
     * @param listener the listener to add
     */
    public void addUndoRedoListener(Consumer<CommandResult> listener) {
        undoRedoListeners.add(listener);
    }

    /**
     * Removes an undo/redo listener.
     *
     * @param listener the listener to remove
     */
    public void removeUndoRedoListener(Consumer<CommandResult> listener) {
        undoRedoListeners.remove(listener);
    }

    // -------------------------------------------------------------------------
    // Event firing
    // -------------------------------------------------------------------------

    /**
     * Fires a selection change event.
     *
     * @param node the selected node
     * @param trigger the trigger object
     * @param rootSelected whether the root is selected
     */
    public void fireSelection(Object node, Object trigger, boolean rootSelected) {
        // Convert node to EditNode if possible
        EditNode editNode = convertToEditNode(node);
        SelectionEvent event = new SelectionEvent(trigger, editNode, null, rootSelected);
        for (Consumer<SelectionEvent> listener : selectionListeners) {
            listener.accept(event);
        }
    }

    private void fireSelectionEvent(SelectionEvent event) {
        for (Consumer<SelectionEvent> listener : selectionListeners) {
            listener.accept(event);
        }
    }

    private void fireEditorSelected(Object editor, Object trigger) {
        // Notify all selection listeners about editor change
        // Convert editor to EditNode if possible
        EditNode editNode = convertToEditNode(editor);
        SelectionEvent event = new SelectionEvent(trigger, editNode, null, false);
        for (Consumer<SelectionEvent> listener : selectionListeners) {
            listener.accept(event);
        }
    }

    private void fireCommandEvent(String commandId) {
        for (Consumer<String> listener : commandListeners) {
            listener.accept(commandId);
        }
    }

    private void fireUndoRedoEvent(CommandResult result) {
        for (Consumer<CommandResult> listener : undoRedoListeners) {
            listener.accept(result);
        }
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    /**
     * Converts an Object to EditNode if possible.
     *
     * @param obj the object to convert
     * @return the EditNode, or null if conversion is not possible
     */
    private EditNode convertToEditNode(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof EditNode) {
            return (EditNode) obj;
        }
        if (obj instanceof DefaultMutableTreeNode dmtn) {
            Object uo = dmtn.getUserObject();
            if (uo instanceof EditNode) {
                return (EditNode) uo;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Utility methods
    // -------------------------------------------------------------------------

    /**
     * Returns a list of undo labels for display.
     *
     * @param max maximum number of labels to return
     * @return list of undo label strings
     */
    public List<String> getUndoLabels(int max) {
        List<String> labels = new ArrayList<>();
        // TODO: Implement based on HistoryManager
        return labels;
    }

    /**
     * Returns a list of redo labels for display.
     *
     * @param max maximum number of labels to return
     * @return list of redo label strings
     */
    public List<String> getRedoLabels(int max) {
        List<String> labels = new ArrayList<>();
        // TODO: Implement based on HistoryManager
        return labels;
    }
}
