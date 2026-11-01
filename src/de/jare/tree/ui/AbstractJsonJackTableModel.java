/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.lang.JsonNodeType;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.tree.control.JackUndoManager;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import de.jare.tree.control.listeners.UndoRedoListener;
import de.jare.tree.control.model.JackTreeModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.TreeModel;

/**
 * Base class of the two pane table models: the shared plumbing around editor selection, the
 * undo/redo refresh and the write-back readiness. The row semantics of the panes - attribute
 * rows versus field rows with their sandbox overrides - live in the subclasses.
 *
 * <p>The base captures the tree model, the edit tree and the model descriptor of the selected
 * editor ({@link #onEditorSelected}); a subclass may react to a tree change
 * ({@link #onEditorTreeChanged(EditTree, EditTree)}) - the fields model drops its sandbox
 * overrides there. UndoRedo events refresh the pane when they ran on the pane's own tree model
 * ({@link #refreshPane()}); a second editor side by side never clears the view.</p>
 *
 * @author Janusch Rentenatus
 */
public abstract class AbstractJsonJackTableModel extends AbstractTableModel
        implements TreeFocusListener, UndoRedoListener {

    private JackTreeModel treeModel;
    private EditTree editTree;
    private JsonModelDescriptor jsonModelDescriptor;
    private final JackUndoManager undoMan;

    /**
     * Creates a local-only model: no change ever leaves the pane, the write-back exceptions
     * included.
     */
    protected AbstractJsonJackTableModel() {
        this(null);
    }

    /**
     * Creates the model with the undo manager of the master control - the write-back exceptions
     * execute their commands through it.
     *
     * @param undoMan the undo manager of the master control, may be {@code null}
     */
    protected AbstractJsonJackTableModel(JackUndoManager undoMan) {
        this.undoMan = undoMan;
    }

    /**
     * Returns the undo manager of the master control, or null for a local-only model.
     *
     * @return the undo manager, or null
     */
    protected final JackUndoManager getUndoMan() {
        return undoMan;
    }

    /**
     * Returns the edit tree of the selected editor, or null before the first selection.
     *
     * @return the edit tree, or null
     */
    protected final EditTree getEditTree() {
        return editTree;
    }

    /**
     * Returns the JsonModelDescriptor of the selected editor.
     *
     * @return the model descriptor, or null if not set
     */
    public JsonModelDescriptor getJsonModelDescriptor() {
        return jsonModelDescriptor;
    }

    /**
     * Sets the JsonModelDescriptor, pulled from the selected editor.
     *
     * @param descriptor the model descriptor to set
     */
    public void setJsonModelDescriptor(JsonModelDescriptor descriptor) {
        this.jsonModelDescriptor = descriptor;
    }

    /**
     * Captures the tree model, the edit tree and the model descriptor of the selected editor.
     * A tree change notifies {@link #onEditorTreeChanged(EditTree, EditTree)}.
     */
    @Override
    public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
        if (editor == null) {
            return;
        }
        if (editor.getModel() instanceof JackTreeModel model) {
            final EditTree previous = this.editTree;
            this.editTree = model.getEditTree();
            this.treeModel = model;
            if (previous != this.editTree) {
                onEditorTreeChanged(previous, this.editTree);
            }
            setJsonModelDescriptor(model.getJsonModelDescriptor());
        }
    }

    /**
     * Called when the selected editor works on another edit tree than before. The default
     * implementation does nothing; the fields model drops its sandbox overrides here.
     *
     * @param previous the edit tree before the change, may be null
     * @param current the edit tree after the change, may be null
     */
    protected void onEditorTreeChanged(EditTree previous, EditTree current) {
        // NoOp by default
    }

    // ========== UndoRedoListener ==========

    /**
     * Refreshes the pane when a command ran on the pane's own tree: execute, redo and undo -
     * from this pane, the tree or the other pane - must show their result here. The event
     * carries the tree model of the executing editor, so a second editor side by side never
     * clears this view.
     */
    @Override
    public void onExecute(Integer level, TreeModel model, CommandResult historyEvent) {
        refreshIfOwnModel(model);
    }

    @Override
    public void onUndo(Integer level, TreeModel model, CommandResult historyEvent) {
        refreshIfOwnModel(model);
    }

    @Override
    public void onSkipped(Integer level, TreeModel model, de.jare.jsoncasted.editor.command.EditCommand command) {
        // NoOp - a skipped command changes nothing
    }

    private void refreshIfOwnModel(TreeModel model) {
        if (model != null && model == this.treeModel) {
            refreshPane();
        }
    }

    /**
     * Refreshes the rows of the pane from the current state - called when a command ran on the
     * pane's own tree model.
     */
    protected abstract void refreshPane();

    // ========== Write-back helpers ==========

    /**
     * Checks whether a write-back of the pane is wired at all: the undo manager must be set
     * and the selected editor known.
     *
     * @return true when a write-back may run
     */
    protected final boolean isWriteBackReady() {
        return undoMan != null && editTree != null;
    }

    /**
     * Checks whether the given node type is primitive (a scalar value a pane edit may write
     * back in both parse modes).
     *
     * @param type the node type
     * @return true for STRING, NUMBER, LONG, BOOLEAN and NULL
     */
    protected static boolean isPrimitiveType(JsonNodeType type) {
        return switch (type) {
            case STRING, NUMBER, LONG, BOOLEAN, NULL ->
                true;
            default ->
                false;
        };
    }

    /**
     * Returns the text form of the given value.
     *
     * @param value the value, may be null
     * @return the text, or null
     */
    protected static String asText(Object value) {
        return value == null ? null : value.toString();
    }
}
