/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control;

import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.events.HistoryEvent;
import de.jare.jsoncasted.editor.events.HistoryEvent.ChangeType;
import de.jare.jsoncasted.editor.events.HistoryListener;
import de.jare.tree.control.commands.WoodCommand;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import de.jare.tree.control.listeners.UndoRedoListener;
import de.jare.tree.control.model.JackTreeModel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.tree.TreeModel;

/**
 * Global undo/redo dispatcher that keeps one {@link UndoManagerModel} per
 * {@link TreeModel} and delegates execute/undo/redo to the manager of the
 * currently active model.
 */
public class JackUndoManager implements TreeFocusListener, HistoryListener {

    private final List<JackUndoManagerModel> managers = new ArrayList<>();
    private JackUndoManagerModel activeManager;
    private final Orator<UndoRedoListener> undoRedoOrator = new Orator<>();

    @Override
    public void onNodeSelected(Object node, Object trigger, boolean rootSelected) {
        // NoOp
    }

    @Override
    public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
        setActiveModel(editor != null ? editor.getModel() : null);
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
    public void setActiveModel(JackTreeModel model) {
        if (model == null) {
            this.activeManager = null;
        } else {
            this.activeManager = getManager(model);
        }
    }

    public JackUndoManagerModel getActiveManager() {
        return activeManager;
    }

    /**
     * Adds the given command on the active model.
     *
     * @param command
     * @return
     */
    public CommandResult executeCommand(EditCommand command) {
        if (activeManager != null) {
            CommandResult result = activeManager.executeCommand(command);
            if (result != null) {
                undoRedoOrator.say(l -> l.onAddCommand(activeManager.getTreeModel()));
                return result;
            }
            if (activeManager.getTreeModel() == null) {
                managers.remove(activeManager);
                activeManager = null;
            }
        }
        return null;
    }

    /**
     * Performs undo on the active model.
     */
    public void undo() {
        if (activeManager != null) {
            CommandResult cmd = activeManager.undo();
            if (cmd != null) {
                undoRedoOrator.say(l -> l.onUndo(activeManager.getTreeModel()));
            }
        }
    }

    /**
     * Performs redo on the active model.
     */
    public void redo() {
        if (activeManager != null) {
            CommandResult cmd = activeManager.redo();
            if (cmd != null) {
                undoRedoOrator.say(l -> l.onExecute(activeManager.getTreeModel()));
            }
        }
    }

    public void skip_redo() {
        if (activeManager != null) {
            EditCommand cmd = activeManager.skip_redo();
            if (cmd != null) {
                undoRedoOrator.say(l -> l.onExecute(activeManager.getTreeModel()));
            }
        }
    }

    public boolean canUndo() {
        return activeManager != null && activeManager.canUndo();
    }

    public boolean canRedo() {
        return activeManager != null && activeManager.canRedo();
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
        for (JackUndoManagerModel m : managers) {
            m.clear();
        }
        managers.clear();
        activeManager = null;
    }

    @Override
    public void onClear(HistoryEvent historyEvent) {

    }

    @Override
    public void onAction(HistoryEvent historyEvent) {
        JackTreeModel model = null;
        for (JackUndoManagerModel m : managers) {
            if (m.containsHistory(historyEvent.getSource())) {
                model = m.getTreeModel();
            }
        }
        if (model != null) {
            model.onHistoryEvent(historyEvent);
        }
        sayUndoRedoEvent(historyEvent, model);
    }

    protected void sayUndoRedoEvent(HistoryEvent historyEvent, final TreeModel model) {
        HistoryEvent.ChangeType changeType = historyEvent.getChangeType();
        switch (changeType) {
            case ChangeType.EXECUTED:
                undoRedoOrator.say(l -> l.onExecute(model));
                break;
            case ChangeType.UNDONE:
                undoRedoOrator.say(l -> l.onUndo(model));
                break;
            case ChangeType.REDONE:
                undoRedoOrator.say(l -> l.onRedo(model));
                break;
            case ChangeType.CLEARED:
                undoRedoOrator.say(l -> l.onClear(model));
                break;
            case ChangeType.SKIPPED:
                undoRedoOrator.say(l -> l.onSkipped(model));
                break;
            default:
                break;
        }
    }

    /**
     * Finds or creates an {@link UndoManagerModel} for the given TreeModel.
     * Also removes all manager instances whose TreeModel has already been
     * garbage collected.
     */
    private JackUndoManagerModel getManager(JackTreeModel model) {
        // remove dead managers and search for existing one
        JackUndoManagerModel found = null;
        Iterator<JackUndoManagerModel> it = managers.iterator();
        while (it.hasNext()) {
            JackUndoManagerModel next = it.next();
            JackTreeModel tm = next.getTreeModel();
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
        JackUndoManagerModel newManager = new JackUndoManagerModel(model);
        managers.add(newManager);
        newManager.addListener(this);
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
