package de.jare.tree.control;

import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/**
 * Globaler Selektions-Stack-Manager.
 * <p>
 * - Verwaltet pro JTree ein SelectionStackManagerModel (Stack-Daten).
 * - Reagiert auf TreeFocusListener-Events (aktiver Editor, selektierter Knoten).
 * - Stellt Selektionen über Backward/Forward wieder her.
 * - Supports both TreeModel-based (DefaultMutableTreeNode) and EditTree-based (EditNode) selections.
 * </p>
 */
public class SelectionStackManager implements TreeFocusListener {

    private final List<SelectionStackManagerModel> managers = new ArrayList<>();
    private SelectionStackManagerModel activeManager;
    private boolean ignoreSelectionChanges = false;

    @Override
    public void onNodeSelected(Object node, Object trigger, boolean rootSelected) {
        if (ignoreSelectionChanges || activeManager == null) {
            return;
        }
        TreeFocusComponent tree = activeManager.getTree();
        if (tree == null) {
            return;
        }

        // alle aktuell selektierten Pfade einsammeln
        TreePath[] paths = tree.getTree().getSelectionPaths();
        if (paths == null || paths.length == 0) {
            return;
        }

        List<Long> newEditIds = new ArrayList<>(paths.length);
        for (TreePath path : paths) {
            Object last = path.getLastPathComponent();
            if (last instanceof DefaultMutableTreeNode dmtn) {
                Object userObject = dmtn.getUserObject();
                if (userObject instanceof EditNode userData) {
                    long editId = userData.getEditId();
                    newEditIds.add(editId);
                }
            }
        }
        if (newEditIds.isEmpty()) {
            return;
        }

        if (!areEqual(newEditIds)) {
            // Label hier erstmal simpel auf node.toString();
            String label = node != null ? node.toString() : "Selection";
            addSelection(newEditIds, label);
        }
    }

    /**
     * Checks if the current selection matches the given edit IDs.
     * Uses a simple Set-based comparison instead of SortedSeasonSetLong.
     *
     * @param newEditIds the new selection edit IDs
     * @return true if the selection hasn't changed
     */
    private boolean areEqual(List<Long> newEditIds) {
        SelectionStackEntry currentEntry = activeManager.getCurrentEntry();
        if (currentEntry == null) {
            return false;
        }
        // unterschiedliche Anzahl -> sicher verschieden
        if (currentEntry.getEditIds().size() != newEditIds.size()) {
            return false;
        }
        // Convert to Set for efficient comparison
        Set<Long> currentSet = new HashSet<>(currentEntry.getEditIds());
        Set<Long> newSet = new HashSet<>(newEditIds);
        return currentSet.equals(newSet);
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
     * Neue Selektion an das Ende des Stacks anhängen.
     *
     * @param editIds editIds der selektierten Knoten
     * @param label Label für Anzeige/Tooltip
     */
    public void addSelection(List<Long> editIds, String label) {
        if (ignoreSelectionChanges || activeManager == null) {
            return;
        }
        activeManager.addSelection(new SelectionStackEntry(editIds, label));
    }

    /**
     * Neue Selektion mit EditTree-Unterstützung hinzufügen.
     *
     * @param tree the EditTree
     * @param editIds editIds der selektierten Knoten
     * @param label Label für Anzeige/Tooltip
     */
    public void addEditSelection(EditTree tree, List<Long> editIds, String label) {
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
     * Navigiere einen Schritt zurück im Selektions-Stack und stelle die
     * Selektion im JTree wieder her.
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
     * Navigiere einen Schritt vorwärts im Selektions-Stack und stelle die
     * Selektion im JTree wieder her.
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
     * Stellt die Selektion anhand der editIds wieder her. Verhindert, dass
     * diese programmatische Änderung erneut im Stack landet.
     */
    private void restoreSelection(TreeFocusComponent editTree, SelectionStackEntry entry) {
        if (editTree == null) {
            return;
        }
        ignoreSelectionChanges = true;
        try {
            List<TreePath> paths = new ArrayList<>();
            for (Long editId : entry.getEditIds()) {
                DefaultMutableTreeNode node = TreeNodeUtils.findNodeByEditId(editTree.getModel(), editId);
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
     * Restores selection in an EditTree-based tree.
     *
     * @param editTree the TreeFocusComponent with EditTree
     * @param entry the selection entry
     */
    private void restoreEditSelection(TreeFocusComponent editTree, SelectionStackEntry entry) {
        if (editTree == null || !(editTree instanceof EditTree)) {
            // Fallback to TreeModel-based restoration
            restoreSelection(editTree, entry);
            return;
        }
        
        EditTree tree = (EditTree) editTree;
        ignoreSelectionChanges = true;
        try {
            List<EditNode> nodes = new ArrayList<>();
            for (Long editId : entry.getEditIds()) {
                EditNode node = TreeNodeUtils.findEditNodeById(tree, editId);
                if (node != null) {
                    nodes.add(node);
                }
            }
            // Note: Actual selection restoration depends on the UI component
            // This method needs to be implemented by the specific UI component
        } finally {
            ignoreSelectionChanges = false;
        }
    }

    /**
     * Labels für Tooltip/Popup des Backward-Buttons (max-Einträge).
     *
     * @param max maximum number of labels
     * @return list of backward labels
     */
    public List<String> getBackwardLabels(int max) {
        if (activeManager == null) {
            return List.of();
        }
        return activeManager.getBackwardLabels(max);
    }

    /**
     * Labels für Tooltip/Popup des Forward-Buttons (max-Einträge).
     *
     * @param max maximum number of labels
     * @return list of forward labels
     */
    public List<String> getForwardLabels(int max) {
        if (activeManager == null) {
            return List.of();
        }
        return activeManager.getForwardLabels(max);
    }

    /**
     * Clears all selection history for all managers.
     */
    public void clearAll() {
        for (SelectionStackManagerModel manager : managers) {
            manager.clear();
        }
        managers.clear();
        activeManager = null;
    }

    /**
     * Clears the selection history for the active manager only.
     */
    public void clearActive() {
        if (activeManager != null) {
            activeManager.clear();
        }
    }

}
