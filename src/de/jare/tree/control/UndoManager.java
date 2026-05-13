/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control;

import de.jare.tree.control.commands.WoodCommand;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import de.jare.tree.control.listeners.UndoRedoListener;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.history.HistoryManager;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.command.CommandResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.tree.TreeModel;

/**
 * Global undo/redo dispatcher that keeps one {@link UndoManagerModel} per
 * {@link TreeModel} and delegates execute/undo/redo to the manager of the
 * currently active model.
 * 
 * <p>
 * When EditTree is available, this class can use HistoryManager internally
 * for EditCommand-based operations, while maintaining backward compatibility
 * with WoodCommand for TreeModel-based operations.
 * </p>
 */
public class UndoManager implements TreeFocusListener {

    private final List<UndoManagerModel> managers = new ArrayList<>();
    private UndoManagerModel activeManager;
    private final Orator<UndoRedoListener> undoRedoOrator = new Orator<>();

    @Override
    public void onNodeSelected(Object node, Object trigger, boolean rootSelected) {
        // NoOp
    }

    @Override
    public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
        if (editor != null) {
            EditTree editTree = editor.getEditTree();
            setActiveModel(editor.getModel(), editTree);
        } else {
            setActiveModel(null, null);
        }
    }

    public void addUndoRedoListener(int level, UndoRedoListener l) {
        undoRedoOrator.addListener(level, l);
    }

    public void removeUndoRedoListener(UndoRedoListener l) {
        undoRedoOrator.removeListener(l);
    }

    /**
     * Sets the currently active model. All subsequent execute/undo/redo calls
     * will operate on the manager associated with this model.
     *
     * @param model active tree model, may be {@code null}
     */
    public void setActiveModel(TreeModel model) {
        if (model == null) {
            this.activeManager = null;
        } else {
            this.activeManager = getManager(model);
        }
    }

    /**
     * Sets the active model with associated EditTree.
     *
     * @param model the TreeModel
     * @param editTree the associated EditTree, may be null
     */
    public void setActiveModel(TreeModel model, EditTree editTree) {
        if (model == null) {
            this.activeManager = null;
        } else {
            this.activeManager = getManager(model, editTree);
        }
    }

    public UndoManagerModel getActiveManager() {
        return activeManager;
    }

    /**
     * Sets the EditTree for the currently active model.
     *
     * @param editTree the EditTree to associate with the active model
     */
    public void setActiveEditTree(EditTree editTree) {
        if (activeManager != null) {
            activeManager.setEditTree(editTree);
        }
    }

    /**
     * Gets the EditTree from the currently active model.
     *
     * @return the EditTree, or null if not available
     */
    public EditTree getActiveEditTree() {
        return activeManager != null ? activeManager.getEditTree() : null;
    }

    /**
     * Gets the HistoryManager from the currently active model.
     *
     * @return the HistoryManager, or null if not available
     */
    public HistoryManager getActiveHistoryManager() {
        return activeManager != null ? activeManager.getHistoryManager() : null;
    }

    /**
     * Adds the given command on the active model.
     *
     * @param command
     */
    public void pushCommand(WoodCommand command) {
        if (activeManager != null) {
            activeManager.pushCommand(command);
            undoRedoOrator.say(l -> l.onAddCommand(activeManager.getTreeModel(), command));
        }
    }

    /**
     * Executes an EditCommand on the active model using HistoryManager.
     *
     * @param editCommand the EditCommand to execute
     * @return the CommandResult, or null if no active HistoryManager
     */
    public CommandResult pushEditCommand(EditCommand editCommand) {
        if (activeManager != null && activeManager.hasEditTree()) {
            CommandResult result = activeManager.pushEditCommand(editCommand);
            if (result != null) {
                // Fire event for EditCommand execution
                // Note: UndoRedoListener expects WoodCommand, so we need an adapter
                // For now, just notify that a command was added
                TreeModel model = activeManager.getTreeModel();
                if (model != null) {
                    // Create a wrapper WoodCommand if needed, or just fire a generic event
                    undoRedoOrator.say(l -> l.onAddCommand(model, null));
                }
            }
            return result;
        }
        return null;
    }

    /**
     * Performs undo on the active model.
     */
    public void undo() {
        if (activeManager != null) {
            WoodCommand cmd = activeManager.undo();
            if (cmd != null) {
                undoRedoOrator.say(l -> l.onUndo(activeManager.getTreeModel(), cmd));
            }
        }
    }

    /**
     * Performs undo using HistoryManager on the active model.
     *
     * @return the CommandResult, or null if no active HistoryManager
     */
    public CommandResult undoEdit() {
        if (activeManager != null && activeManager.hasEditTree()) {
            CommandResult result = activeManager.undoEdit();
            if (result != null) {
                undoRedoOrator.say(l -> l.onUndo(activeManager.getTreeModel(), null));
            }
            return result;
        }
        undo();
        return null;
    }

    /**
     * Performs redo on the active model.
     */
    public void redo() {
        if (activeManager != null) {
            WoodCommand cmd = activeManager.redo();
            if (cmd != null) {
                undoRedoOrator.say(l -> l.onRedo(activeManager.getTreeModel(), cmd));
            }
        }
    }

    /**
     * Performs redo using HistoryManager on the active model.
     *
     * @return the CommandResult, or null if no active HistoryManager
     */
    public CommandResult redoEdit() {
        if (activeManager != null && activeManager.hasEditTree()) {
            CommandResult result = activeManager.redoEdit();
            if (result != null) {
                undoRedoOrator.say(l -> l.onRedo(activeManager.getTreeModel(), null));
            }
            return result;
        }
        redo();
        return null;
    }

    public void skip_redo() {
        if (activeManager != null) {
            WoodCommand cmd = activeManager.skip_redo();
            if (cmd != null) {
                undoRedoOrator.say(l -> l.onRedo(activeManager.getTreeModel(), cmd));
            }
        }
    }

    /**
     * Performs skip_redo using HistoryManager on the active model.
     *
     * @return the skipped EditCommand, or null if no active HistoryManager
     */
    public EditCommand skipRedoEdit() {
        if (activeManager != null && activeManager.hasEditTree()) {
            EditCommand cmd = activeManager.skipRedoEdit();
            if (cmd != null) {
                undoRedoOrator.say(l -> l.onRedo(activeManager.getTreeModel(), null));
            }
            return cmd;
        }
        skip_redo();
        return null;
    }

    public boolean canUndo() {
        if (activeManager != null) {
            return activeManager.canUndo() || activeManager.canUndoEdit();
        }
        return false;
    }

    public boolean canRedo() {
        if (activeManager != null) {
            return activeManager.canRedo() || activeManager.canRedoEdit();
        }
        return false;
    }

    /**
     * Clears history of the active model only.
     */
    public void clearActive() {
        if (activeManager != null) {
            activeManager.clear();
            undoRedoOrator.say(l -> l.onClear(activeManager.getTreeModel()));
        }
    }

    /**
     * Clears history for all models.
     */
    public void clearAll() {
        for (UndoManagerModel m : managers) {
            m.clear();
        }
        managers.clear();
        activeManager = null;
    }

    /**
     * Finds or creates an {@link UndoManagerModel} for the given TreeModel. 
     * Also removes all manager instances whose TreeModel has already been
     * garbage collected.
     */
    private UndoManagerModel getManager(TreeModel model) {
        // remove dead managers and search for existing one
        UndoManagerModel found = null;
        Iterator<UndoManagerModel> it = managers.iterator();
        while (it.hasNext()) {
            UndoManagerModel next = it.next();
            TreeModel tm = next.getTreeModel();
            if (tm == null) {
                // TreeModel was GC'ed, drop this manager
                it.remove();
                continue;
            }
            if (tm == model) {
                found = next;
            }
        }

        if (found != null) {
            return found;
        }

        // create new manager for this model
        UndoManagerModel newManager = new UndoManagerModel(model);
        managers.add(newManager);
        return newManager;
    }

    /**
     * Finds or creates an {@link UndoManagerModel} for the given TreeModel and EditTree.
     * 
     * @param model the TreeModel
     * @param editTree the associated EditTree
     * @return the UndoManagerModel for this model
     */
    private UndoManagerModel getManager(TreeModel model, EditTree editTree) {
        // remove dead managers and search for existing one
        UndoManagerModel found = null;
        Iterator<UndoManagerModel> it = managers.iterator();
        while (it.hasNext()) {
            UndoManagerModel next = it.next();
            TreeModel tm = next.getTreeModel();
            if (tm == null) {
                // TreeModel was GC'ed, drop this manager
                it.remove();
                continue;
            }
            if (tm == model) {
                found = next;
                // Update EditTree if provided
                if (editTree != null && !editTree.equals(next.getEditTree())) {
                    next.setEditTree(editTree);
                }
            }
        }

        if (found != null) {
            return found;
        }

        // create new manager for this model with EditTree support
        UndoManagerModel newManager = new UndoManagerModel(model, editTree);
        managers.add(newManager);
        return newManager;
    }

    public List<String> getUndoLabels(int max) {
        if (canUndo()) {
            return activeManager.getUndoLabels(max);
        }
        return List.of();
    }

    public List<String> getRedoLabels(int max) {
        if (canRedo()) {
            return activeManager.getRedoLabels(max);
        }
        return List.of();
    }

}
