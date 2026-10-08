/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.tree.control;

import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.command.JackUpdateAction;
import static de.jare.jsoncasted.editor.command.JackUpdateAction.SELECT_ADDED;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.SimpleEntry;
import de.jare.ndimcol.primlong.SortedSeasonSetLong;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import de.jare.tree.control.listeners.UndoRedoListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * Global selection stack manager.
 * <p>
 * - Manages one SelectionStackManagerModel (stack data) per JTree. - Reacts to
 * TreeFocusListener events (active editor, selected node).
 * - Restores selections via Backward/Forward.
 * </p>
 *
 * @author Jansuch Rentenatus
 */
public class SelectionStackManager implements TreeFocusListener, UndoRedoListener, WoodUtils {

    private final List<SelectionStackManagerModel> managers = new ArrayList<>();
    private SelectionStackManagerModel activeManager;
    private boolean ignoreSelectionChanges = false;

    @Override
    public void onNodeSelected(DefaultMutableTreeNode node, Object trigger, boolean rootSelected) {
        if (ignoreSelectionChanges || activeManager == null) {
            return;
        }
        TreeFocusComponent tree = activeManager.getTree();
        if (tree == null) {
            return;
        }

        // collect all currently selected paths
        TreePath[] paths = tree.getTree().getSelectionPaths();
        if (paths == null || paths.length == 0) {
            return;
        }

        List<Long> newEditIds = new ArrayList<>(paths.length);
        SortedSeasonSetLong checkIds = new SortedSeasonSetLong();
        for (TreePath path : paths) {
            Object last = path.getLastPathComponent();
            if (last instanceof DefaultMutableTreeNode dmtn) {
                Object userObject = dmtn.getUserObject();
                if (userObject instanceof EditNode userData) {
                    long editId = userData.getEditId();
                    newEditIds.add(editId);
                    checkIds.add(editId);
                }
            }
        }
        if (newEditIds.isEmpty()) {
            return;
        }

        if (!areEqual(checkIds, newEditIds)) {
            // keep the label simple for now: node.toString();
            String label = node != null ? node.toString() : "Selection";
            addSelection(newEditIds, label);
        }
    }

    private boolean areEqual(SortedSeasonSetLong checkIds, List<Long> newEditIds) {
        SelectionStackEntry currentEntry = activeManager.getCurrentEntry();
        if (currentEntry == null) {
            return false;
        }
        // unterschiedliche Anzahl -> sicher verschieden
        if (currentEntry.getEditIds().size() != newEditIds.size()) {
            return false;
        }
        // remove all ids of the old entry from the set
        for (Long cEntry : currentEntry.getEditIds()) {
            if (!checkIds.remove(cEntry)) {
                return false;
            }
        }
        return checkIds.isEmpty();
    }

    @Override
    public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
        setActiveModel(editor);
    }

    /**
     * Sets the currently active model. All subsequent backward/forward calls
     * operate on the manager associated with this tree.
     *
     * @param tree active tree, may be {@code null}
     */
    public void setActiveModel(TreeFocusComponent tree) {
        if (tree == null) {
            this.activeManager = null;
        } else {
            this.activeManager = getManager(tree);
        }
    }

    public SelectionStackManagerModel getActiveManager() {
        return activeManager;
    }

    /**
     * Appends a new selection to the end of the stack.
     *
     * @param editIds editIds of the selected nodes
     * @param label label for display/tooltip
     */
    public void addSelection(List<Long> editIds, String label) {
        if (ignoreSelectionChanges || activeManager == null) {
            return;
        }
        activeManager.addSelection(new SelectionStackEntry(editIds, label));
    }

    /**
     * Finds or creates a SelectionStackManagerModel for the given JTree. Also
     * removes all manager instances whose JTree has already been garbage
     * collected.
     */
    private SelectionStackManagerModel getManager(TreeFocusComponent tree) {
        // remove dead managers and search for existing one
        SelectionStackManagerModel found = null;
        Iterator<SelectionStackManagerModel> it = managers.iterator();
        while (it.hasNext()) {
            SelectionStackManagerModel next = it.next();
            TreeFocusComponent jt = next.getTree();
            if (jt == null) {
                // JTree was GC'ed, drop this manager
                it.remove();
                continue;
            }
            if (jt == tree) {
                found = next;
            }
        }

        if (found != null) {
            return found;
        }

        // create new manager for this tree
        SelectionStackManagerModel newManager = new SelectionStackManagerModel(tree);
        managers.add(newManager);
        return newManager;
    }

    public boolean canBackward() {
        if (activeManager == null) {
            return false;
        }
        return activeManager.canBackward();
    }

    public boolean canForward() {
        if (activeManager == null) {
            return false;
        }
        return activeManager.canForward();
    }

    /**
     * Navigates one step back in the selection stack and restores the
     * selection in the JTree.
     */
    public void selectionBackward() {
        if (activeManager == null) {
            return;
        }
        SelectionStackEntry entry = activeManager.goBackward();
        if (entry == null) {
            return;
        }
        restoreSelection(activeManager.getTree(), entry);
    }

    /**
     * Navigates one step forward in the selection stack and restores the
     * selection in the JTree.
     */
    public void selectionForward() {
        if (activeManager == null) {
            return;
        }
        SelectionStackEntry entry = activeManager.goForward();
        if (entry == null) {
            return;
        }
        restoreSelection(activeManager.getTree(), entry);
    }

    /**
     * Restores the selection by editIds. Prevents this programmatic change
     * from landing on the stack again.
     */
    private void restoreSelection(TreeFocusComponent editTree, SelectionStackEntry entry) {
        if (editTree == null) {
            return;
        }
        ignoreSelectionChanges = true;
        try {
            List<TreePath> paths = new ArrayList<>();
            for (Long editId : entry.getEditIds()) {
                DefaultMutableTreeNode node = findNodeByEditId(editTree.getModel(), editId);
                if (node != null) {
                    paths.add(new TreePath(node.getPath()));
                }
            }
            editTree.getTree().clearSelection();
            if (!paths.isEmpty()) {
                editTree.getTree().setSelectionPaths(paths.toArray(TreePath[]::new));
            }
        } finally {
            ignoreSelectionChanges = false;
        }
    }

    /**
     * Labels for the tooltip/popup of the backward button (max entries).
     *
     * @param max
     * @return
     */
    public List<String> getBackwardLabels(int max) {
        if (activeManager == null) {
            return List.of();
        }
        return activeManager.getBackwardLabels(max);
    }

    /**
     * Labels for the tooltip/popup of the forward button (max entries).
     *
     * @param max
     * @return
     */
    public List<String> getForwardLabels(int max) {
        if (activeManager == null) {
            return List.of();
        }
        return activeManager.getForwardLabels(max);
    }

    @Override
    public void onExecute(Integer level, TreeModel model, CommandResult result) {
        if (activeManager == null) {
            return;
        }
        TreeFocusComponent tree = activeManager.getTree();
        if (tree == null) {
            return;
        }

        for (JackUpdateAction update : result.getUpdateActions()) {
            if (SELECT_ADDED.equals(update)) {
                EditNodeAbstract[] added = result.getAddedNodes();
                SimpleEntry[] templates = result.getTemplateEntries();
                for (int i = 0; i < added.length; i++) {
                    activeManager.addSynonym(templates[i].nodeId, added[i].getEditId());
                }
            }
        }
    }

    @Override
    public void onUndo(Integer level, TreeModel model, CommandResult historyEvent) {
        //NoOp;
    }

    @Override
    public void onSkipped(Integer level, TreeModel model, EditCommand command) {
        //NoOp;
    }

}
