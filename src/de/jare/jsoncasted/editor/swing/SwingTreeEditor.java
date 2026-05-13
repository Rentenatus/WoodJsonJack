/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.swing;

import de.jare.jsoncasted.editor.TreeEditor;
import de.jare.jsoncasted.editor.clipboard.ClipboardManager;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.events.EditEvent;
import de.jare.jsoncasted.editor.events.SelectionEvent;
import de.jare.jsoncasted.editor.history.HistoryManager;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * Swing-specific bridge that integrates TreeEditor with Swing JTree components.
 * This class provides the glue between the pure editor model and Swing UI components.
 */
public class SwingTreeEditor {

    private final TreeEditor treeEditor;
    private final ClipboardManager clipboardManager;
    private final Map<TreeModel, EditTreeAdapter> modelAdapters;
    private TreeModel activeModel;
    private Object activeEditor;

    // Listeners for Swing integration
    private Consumer<SelectionEvent> selectionListener;
    private Consumer<String> commandListener;
    private Consumer<CommandResult> undoRedoListener;

    public SwingTreeEditor() {
        this(new EditNodeObject("root"));
    }

    public SwingTreeEditor(EditNode root) {
        this.treeEditor = new TreeEditor(root);
        this.clipboardManager = new ClipboardManager();
        this.modelAdapters = new HashMap<>();
        this.activeModel = null;
        this.activeEditor = null;
    }

    // -------------------------------------------------------------------------
    // Core accessors
    // -------------------------------------------------------------------------

    public TreeEditor getTreeEditor() {
        return treeEditor;
    }

    public EditTree getEditTree() {
        return treeEditor.getTree();
    }

    public HistoryManager getHistoryManager() {
        return treeEditor.getHistoryManager();
    }

    public ClipboardManager getClipboardManager() {
        return clipboardManager;
    }

    // -------------------------------------------------------------------------
    // Model management
    // -------------------------------------------------------------------------

    /**
     * Registers a Swing TreeModel with this editor.
     * Creates an adapter that synchronizes between the Swing model and EditTree.
     *
     * @param swingModel the Swing TreeModel to register
     * @param editTree the corresponding EditTree (may be null to create from root)
     * @return the created adapter
     */
    public EditTreeAdapter registerModel(TreeModel swingModel, EditTree editTree) {
        if (swingModel == null) {
            throw new IllegalArgumentException("Swing model cannot be null");
        }

        EditTreeAdapter adapter = new EditTreeAdapter(this, swingModel, editTree);
        modelAdapters.put(swingModel, adapter);

        if (activeModel == null) {
            setActiveModel(swingModel);
        }

        return adapter;
    }

    /**
     * Registers a Swing TreeModel with this editor, creating EditTree from root.
     *
     * @param swingModel the Swing TreeModel to register
     * @return the created adapter
     */
    public EditTreeAdapter registerModel(TreeModel swingModel) {
        Object root = swingModel.getRoot();
        EditNode editRoot = adaptToEditNode(root);
        EditTree editTree = new EditTree(editRoot);
        return registerModel(swingModel, editTree);
    }

    /**
     * Sets the currently active model for commands.
     *
     * @param model the active TreeModel
     */
    public void setActiveModel(TreeModel model) {
        this.activeModel = model;
        EditTreeAdapter adapter = modelAdapters.get(model);
        if (adapter != null) {
            // EditTree is immutable, so we just update the active model reference
            // The actual EditTree operations work on the treeEditor's internal tree
        }
    }

    /**
     * Sets the currently active editor component.
     *
     * @param editor the active editor component
     */
    public void setActiveEditor(Object editor) {
        this.activeEditor = editor;
    }

    public TreeModel getActiveModel() {
        return activeModel;
    }

    public Object getActiveEditor() {
        return activeEditor;
    }

    // -------------------------------------------------------------------------
    // Command execution
    // -------------------------------------------------------------------------

    /**
     * Executes a command on the active model's EditTree.
     *
     * @param command the command to execute
     * @return the command result, or null if no active model
     */
    public CommandResult execute(EditCommand command) {
        EditTreeAdapter adapter = getActiveAdapter();
        if (adapter == null) {
            return null;
        }
        return treeEditor.execute(command);
    }

    /**
     * Undoes the last command on the active model.
     *
     * @return the undo result, or null if no active model or nothing to undo
     */
    public CommandResult undo() {
        CommandResult result = treeEditor.undo();
        if (result != null && undoRedoListener != null) {
            undoRedoListener.accept(result);
        }
        return result;
    }

    /**
     * Redoes the last undone command on the active model.
     *
     * @return the redo result, or null if no active model or nothing to redo
     */
    public CommandResult redo() {
        CommandResult result = treeEditor.redo();
        if (result != null && undoRedoListener != null) {
            undoRedoListener.accept(result);
        }
        return result;
    }

    public boolean canUndo() {
        return treeEditor.canUndo();
    }

    public boolean canRedo() {
        return treeEditor.canRedo();
    }

    // -------------------------------------------------------------------------
    // Event handling
    // -------------------------------------------------------------------------

    public void setSelectionListener(Consumer<SelectionEvent> listener) {
        this.selectionListener = listener;
    }

    public void setCommandListener(Consumer<String> listener) {
        this.commandListener = listener;
    }

    public void setUndoRedoListener(Consumer<CommandResult> listener) {
        this.undoRedoListener = listener;
    }

    public void fireSelection(Object node, Object trigger, boolean rootSelected) {
        if (selectionListener != null) {
            // Convert node to EditNode if possible
            EditNode editNode = adaptToEditNode(node);
            SelectionEvent event = new SelectionEvent(trigger, editNode, null, rootSelected);
            selectionListener.accept(event);
        }
    }

    public void fireCommand(String commandId, Object trigger) {
        if (commandListener != null) {
            commandListener.accept(commandId);
        }
    }

    // -------------------------------------------------------------------------
    // Adapter methods
    // -------------------------------------------------------------------------

    private EditTreeAdapter getActiveAdapter() {
        if (activeModel == null) {
            return null;
        }
        return modelAdapters.get(activeModel);
    }

    /**
     * Adapts a Swing node object to an EditNode.
     */
    public EditNode adaptToEditNode(Object swingNode) {
        if (swingNode instanceof EditNode) {
            return (EditNode) swingNode;
        }
        if (swingNode instanceof DefaultMutableTreeNode dmtn) {
            Object uo = dmtn.getUserObject();
            if (uo instanceof EditNode) {
                return (EditNode) uo;
            }
            return new EditNodeObject(String.valueOf(uo));
        }
        return new EditNodeObject(String.valueOf(swingNode));
    }

    /**
     * Adapts an EditNode to a Swing DefaultMutableTreeNode.
     */
    public DefaultMutableTreeNode adaptToSwingNode(EditNode editNode) {
        return new DefaultMutableTreeNode(editNode);
    }

    // -------------------------------------------------------------------------
    // Clipboard operations
    // -------------------------------------------------------------------------

    /**
     * Copies selected nodes from a Swing tree to the clipboard.
     *
     * @param paths the selected tree paths
     * @param cut whether this is a cut operation
     */
    public void copyToClipboard(TreePath[] paths, boolean cut) {
        if (paths == null || paths.length == 0) {
            return;
        }

        EditTreeAdapter adapter = getActiveAdapter();
        if (adapter == null) {
            return;
        }

        long[] nodeIds = new long[paths.length];
        for (int i = 0; i < paths.length; i++) {
            Object node = paths[i].getLastPathComponent();
            if (node instanceof DefaultMutableTreeNode dmtn) {
                Object uo = dmtn.getUserObject();
                if (uo instanceof EditNode editNode) {
                    nodeIds[i] = editNode.getEditId();
                }
            }
        }

        if (cut) {
            clipboardManager.cutToActiveStash(adapter.getEditTree(), nodeIds);
        } else {
            clipboardManager.copyToActiveStash(adapter.getEditTree(), nodeIds);
        }
    }

    /**
     * Pastes nodes from clipboard to the target path.
     *
     * @param targetPath the target tree path
     * @return true if paste was successful
     */
    public boolean pasteFromClipboard(TreePath targetPath) {
        EditTreeAdapter adapter = getActiveAdapter();
        if (adapter == null) {
            return false;
        }

        Object target = targetPath.getLastPathComponent();
        if (!(target instanceof DefaultMutableTreeNode dmtn)) {
            return false;
        }

        Object uo = dmtn.getUserObject();
        if (!(uo instanceof EditNode targetNode)) {
            return false;
        }

        long[] pastedIds = clipboardManager.pasteFromActiveStash(
                adapter.getEditTree(),
                targetNode.getEditId(),
                -1
        );

        return pastedIds != null && pastedIds.length > 0;
    }

    // -------------------------------------------------------------------------
    // Inner classes
    // -------------------------------------------------------------------------

    /**
     * Adapter that synchronizes between a Swing TreeModel and an EditTree.
     */
    public static class EditTreeAdapter {
        private final SwingTreeEditor editor;
        private final TreeModel swingModel;
        private final EditTree editTree;

        public EditTreeAdapter(SwingTreeEditor editor, TreeModel swingModel, EditTree editTree) {
            this.editor = editor;
            this.swingModel = swingModel;
            this.editTree = editTree != null ? editTree : createEditTreeFromSwing(swingModel);
        }

        private EditTree createEditTreeFromSwing(TreeModel model) {
            Object root = model.getRoot();
            EditNode editRoot = editor.adaptToEditNode(root);
            return new EditTree(editRoot);
        }

        public TreeModel getSwingModel() {
            return swingModel;
        }

        public EditTree getEditTree() {
            return editTree;
        }

        /**
         * Synchronizes changes from EditTree to Swing TreeModel.
         */
        public void syncToSwing() {
            if (swingModel instanceof DefaultTreeModel dtm) {
                dtm.reload();
            }
        }

        /**
         * Finds an EditNode by ID in this adapter's tree.
         */
        public EditNode findNodeById(long id) {
            return editTree.findNodeById(id);
        }
    }
}
