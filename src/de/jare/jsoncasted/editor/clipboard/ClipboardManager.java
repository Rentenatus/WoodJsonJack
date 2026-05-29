/*
 * Copyright (c) 2025, Janusch Rentenatus.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.clipboard;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.tree.control.Orator;

/**
 * Manages multiple clipboard stashes and provides operations for copying,
 * cutting, and pasting nodes at the EditTree/EditNode level.
 *
 * <p>
 * In the revised model, the clipboard is neutral:
 * <ul>
 * <li>Stashes store subtree snapshots (EditNode arrays)</li>
 * <li>No cut/move semantics are encoded in the stash</li>
 * <li>Paste decides per target tree whether edit IDs can be reused or must be
 * regenerated</li>
 * </ul>
 * </p>
 */
public class ClipboardManager {

    /**
     * Functional interface for clipboard change listeners.
     */
    @FunctionalInterface
    public interface ClipboardChangeListener {
        void onClipboardChanged(String stashName);
    }

    public static final String DEFAULT_STASH_NAME = "default";
    public static final String CLIPBOARD_STASH_NAME = "clipboard 1";

    private final java.util.Map<String, ClipboardStash> stashes;
    private String activeStashName;
    private final java.util.List<String> stashOrder;
    private final Orator<ClipboardChangeListener> clipboardOrator = new Orator<>();

    /**
     * Creates a new clipboard manager with the default stash.
     */
    public ClipboardManager() {
        this.stashes = new java.util.LinkedHashMap<>();
        this.stashOrder = new java.util.ArrayList<>();
        this.activeStashName = DEFAULT_STASH_NAME;

        // Create default stash
        createStash(DEFAULT_STASH_NAME);

        // Create clipboard stash for UI integration
        createStash(CLIPBOARD_STASH_NAME);
    }

    // ========================================================================
    // Listener Management
    // ========================================================================

    /**
     * Adds a listener to be notified when clipboard content changes.
     *
     * @param listener the listener to add
     */
    public void addClipboardChangeListener(ClipboardChangeListener listener) {
        clipboardOrator.addListener(listener);
    }

    /**
     * Adds a listener to be notified when clipboard content changes with priority.
     *
     * @param level the priority level
     * @param listener the listener to add
     */
    public void addClipboardChangeListener(int level, ClipboardChangeListener listener) {
        clipboardOrator.addListener(level, listener);
    }

    /**
     * Removes a previously added listener.
     *
     * @param listener the listener to remove
     */
    public void removeClipboardChangeListener(ClipboardChangeListener listener) {
        clipboardOrator.removeListener(listener);
    }

    /**
     * Notifies all registered listeners about a change in the specified stash.
     *
     * @param stashName the name of the stash that changed, or null for all stashes
     */
    private void fireClipboardChanged(String stashName) {
        clipboardOrator.say(listener -> listener.onClipboardChanged(stashName));
    }

    /**
     * Sets the content of a specific stash and notifies listeners.
     * This is the preferred way to modify stash content, as it ensures
     * proper event notification.
     *
     * @param stashName the name of the stash
     * @param nodes the nodes to store in the stash
     */
    public void setStashContent(String stashName, EditNodeAbstract[] nodes) {
        ClipboardStash stash = getStash(stashName);
        if (stash != null) {
            stash.setNodes(nodes);
            fireClipboardChanged(stashName);
        }
    }

    /**
     * Creates a new named stash.
     *
     * @param name the name of the stash
     * @return the created stash
     * @throws IllegalArgumentException if a stash with this name already exists
     */
    public ClipboardStash createStash(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Stash name cannot be null or empty");
        }
        if (stashes.containsKey(name)) {
            throw new IllegalArgumentException("Stash with name " + name + " already exists");
        }

        ClipboardStash stash = new ClipboardStash(name);
        stashes.put(name, stash);
        stashOrder.add(name);
        fireClipboardChanged(name);
        return stash;
    }

    /**
     * Removes a stash by name.
     *
     * @param name the name of the stash to remove
     * @return the removed stash, or null if not found
     * @throws IllegalArgumentException if trying to remove the active stash
     */
    public ClipboardStash removeStash(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Stash name cannot be null");
        }
        if (name.equals(activeStashName)) {
            throw new IllegalArgumentException(
                    "Cannot remove the active stash. Switch to another stash first.");
        }

        ClipboardStash removed = stashes.remove(name);
        if (removed != null) {
            stashOrder.remove(name);
            fireClipboardChanged(name);
        }
        return removed;
    }

    /**
     * Switches to the specified stash.
     *
     * @param name the name of the stash to switch to
     * @throws IllegalArgumentException if the stash does not exist
     */
    public void switchToStash(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Stash name cannot be null");
        }
        if (!stashes.containsKey(name)) {
            throw new IllegalArgumentException("Stash with name " + name + " does not exist");
        }
        if (!name.equals(activeStashName)) {
            this.activeStashName = name;
            fireClipboardChanged(name);
        }
    }

    /**
     * Returns the currently active stash.
     *
     * @return the active stash
     */
    public ClipboardStash getActiveStash() {
        return getStash(activeStashName);
    }

    /**
     * Returns a stash by name.
     *
     * @param name the name of the stash
     * @return the stash, or null if not found
     */
    public ClipboardStash getStash(String name) {
        if (name == null) {
            return null;
        }
        return stashes.get(name);
    }

    /**
     * Returns the name of the currently active stash.
     *
     * @return the active stash name
     */
    public String getActiveStashName() {
        return activeStashName;
    }

    /**
     * Returns all stash names in the order they were created.
     *
     * @return array of stash names
     */
    public String[] getStashNames() {
        return stashOrder.toArray(new String[0]);
    }

    /**
     * Returns the number of stashes.
     *
     * @return the stash count
     */
    public int getStashCount() {
        return stashes.size();
    }

    // ========================================================================
    // Copy/Cut/Paste Operations
    // ========================================================================
    /**
     * Copies nodes to the active stash.
     *
     * @param tree the source tree
     * @param nodeIds the IDs of the nodes to copy
     */
    public void copyToActiveStash(EditTree tree, long[] nodeIds) {
        copyToStash(activeStashName, tree, nodeIds);
    }

    /**
     * Copies nodes to a specific stash.
     *
     * <p>
     * Snapshots keep their edit IDs. Whether these IDs can be reused in the
     * target tree is decided later during paste.</p>
     *
     * @param stashName the name of the target stash
     * @param tree the source tree
     * @param nodeIds the IDs of the nodes to copy
     * @throws IllegalArgumentException if the stash does not exist
     */
    public void copyToStash(String stashName, EditTree tree, long[] nodeIds) {
        if (nodeIds == null || nodeIds.length == 0) {
            return;
        }
        ClipboardStash stash = getStash(stashName);
        if (stash == null) {
            throw new IllegalArgumentException("Stash with name " + stashName + " does not exist");
        }

        EditNodeAbstract[] nodes = new EditNodeAbstract[nodeIds.length];
        for (int i = 0; i < nodeIds.length; i++) {
            EditNodeAbstract node = tree.findNodeById(nodeIds[i]);
            if (node != null) {
                // Snapshot with existing edit IDs
                nodes[i] = node.deepCopy(false);
            }
        }
        setStashContent(stashName, nodes);
    }

    /**
     * Copies nodes to the active stash.
     *
     * @param tree the source tree
     * @param nodes the nodes to copy
     */
    public void copyToActiveStash(EditTree tree, EditNodeAbstract[] nodes) {
        copyToStash(activeStashName, tree, nodes);
    }

    /**
     * Copies nodes to a specific stash.
     *
     * @param stashName the name of the target stash
     * @param tree the source tree
     * @param nodes the nodes to copy
     * @throws IllegalArgumentException if the stash does not exist
     */
    public void copyToStash(String stashName, EditTree tree, EditNodeAbstract[] nodes) {
        if (nodes == null || nodes.length == 0) {
            return;
        }
        ClipboardStash stash = getStash(stashName);
        if (stash == null) {
            throw new IllegalArgumentException("Stash with name " + stashName + " does not exist");
        }

        EditNodeAbstract[] copiedNodes = new EditNodeAbstract[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            copiedNodes[i] = nodes[i] != null ? nodes[i].deepCopy(false) : null;
        }
        setStashContent(stashName, copiedNodes);
    }

    /**
     * Cuts nodes to the active stash.
     *
     * <p>
     * Semantically:
     * <ul>
     * <li>Snapshots are stored in the stash (deepCopy(false))</li>
     * <li>Actual removal aus dem Tree erfolgt im zugehörigen Cut-Command</li>
     * </ul>
     * Dieser Manager speichert nur die Snapshots.</p>
     *
     * @param tree the source tree
     * @param nodeIds the IDs of the nodes to cut
     */
    public void cutToActiveStash(EditTree tree, long[] nodeIds) {
        cutToStash(activeStashName, tree, nodeIds);
    }

    /**
     * Cuts nodes to a specific stash.
     *
     * <p>
     * Dieser Manager erzeugt lediglich Snapshots. Ob und wann die
     * Originalknoten aus dem Baum entfernt werden, entscheidet der aufrufende
     * Command (z.B. CutToStashCommand).</p>
     *
     * @param stashName the name of the target stash
     * @param tree the source tree
     * @param nodeIds the IDs of the nodes to cut
     * @throws IllegalArgumentException if the stash does not exist
     */
    public void cutToStash(String stashName, EditTree tree, long[] nodeIds) {
        if (nodeIds == null || nodeIds.length == 0) {
            return;
        }
        ClipboardStash stash = getStash(stashName);
        if (stash == null) {
            throw new IllegalArgumentException("Stash with name " + stashName + " does not exist");
        }

        EditNodeAbstract[] snapshots = new EditNodeAbstract[nodeIds.length];
        for (int i = 0; i < nodeIds.length; i++) {
            EditNodeAbstract node = tree.findNodeById(nodeIds[i]);
            if (node != null) {
                snapshots[i] = node.deepCopy(false);
            }
        }
        setStashContent(stashName, snapshots);
    }

    /**
     * Pastes nodes from the active stash to the target tree.
     *
     * @param tree the target tree
     * @param parentId the ID of the parent node where nodes will be inserted
     * @return the IDs of the pasted nodes
     */
    public long[] pasteFromActiveStash(EditTree tree, long parentId) {
        return pasteFromStash(activeStashName, tree, parentId, -1);
    }

    /**
     * Pastes nodes from the active stash to the target tree at a specific
     * index.
     *
     * @param tree the target tree
     * @param parentId the ID of the parent node where nodes will be inserted
     * @param index the index at which to insert the nodes, or -1 to append
     * @return the IDs of the pasted nodes
     */
    public long[] pasteFromActiveStash(EditTree tree, long parentId, int index) {
        return pasteFromStash(activeStashName, tree, parentId, index);
    }

    /**
     * Pastes nodes from a specific stash to the target tree.
     *
     * <p>
     * Decision logic:
     * <ul>
     * <li>If all edit IDs of the stash content (inkl. Subtrees) are free in the
     * target tree: paste with id-preserving copies (deepCopy(false)).</li>
     * <li>Otherwise: paste with regenerated IDs (deepCopy(true)).</li>
     * </ul>
     * </p>
     *
     * @param stashName the name of the source stash
     * @param tree the target tree
     * @param parentId the ID of the parent node where nodes will be inserted
     * @param index the index at which to insert the nodes, or -1 to append
     * @return the IDs of the pasted nodes
     * @throws IllegalArgumentException if the stash does not exist
     */
    public long[] pasteFromStash(String stashName, EditTree tree, long parentId, int index) {
        ClipboardStash stash = getStash(stashName);
        if (stash == null) {
            throw new IllegalArgumentException("Stash with name " + stashName + " does not exist");
        }

        EditNodeAbstract[] nodes = stash.getNodes();
        if (nodes == null || nodes.length == 0) {
            return new long[0];
        }

        // Entscheide global, ob IDs wiederverwendet werden können.
        boolean reuseIds = areAllEditIdsFree(tree, nodes);

        long[] pastedIds = new long[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] == null) {
                pastedIds[i] = -1;
                continue;
            }
            EditNodeAbstract nodeCopy = nodes[i].deepCopy(!reuseIds);
            if (index >= 0) {
                tree.addNode(parentId, nodeCopy, index + i);
            } else {
                tree.addNode(parentId, nodeCopy);
            }
            pastedIds[i] = nodeCopy.getEditId();
        }

        // Stash bleibt erhalten, Paste ist wiederholbar.
        return pastedIds;
    }

    /**
     * Pastes nodes from a specific stash to the target tree, pasting each node
     * at its corresponding index.
     *
     * <p>
     * Die selbe ID-Entscheidung (reuse vs. regenerate) wird für die gesamte
     * Stash-Operation getroffen.</p>
     *
     * @param stashName the name of the source stash
     * @param tree the target tree
     * @param parentIds the parent IDs for each node
     * @param indices the insertion indices for each node, or -1 to append
     * @return the IDs of the pasted nodes
     * @throws IllegalArgumentException if the stash does not exist or arrays
     * don't match
     */
    public long[] pasteFromStash(String stashName,
            EditTree tree,
            long[] parentIds,
            int[] indices) {
        ClipboardStash stash = getStash(stashName);
        if (stash == null) {
            throw new IllegalArgumentException("Stash with name " + stashName + " does not exist");
        }

        EditNodeAbstract[] nodes = stash.getNodes();
        if (nodes == null || nodes.length == 0) {
            return new long[0];
        }

        if (parentIds == null || indices == null
                || parentIds.length != nodes.length
                || indices.length != nodes.length) {
            throw new IllegalArgumentException(
                    "parentIds and indices must have the same length as the number of nodes");
        }

        boolean reuseIds = areAllEditIdsFree(tree, nodes);

        long[] pastedIds = new long[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] == null) {
                pastedIds[i] = -1;
                continue;
            }
            EditNodeAbstract nodeCopy = nodes[i].deepCopy(!reuseIds);
            tree.addNode(parentIds[i], nodeCopy, indices[i]);
            pastedIds[i] = nodeCopy.getEditId();
        }

        return pastedIds;
    }

    /**
     * Clears the active stash.
     */
    public void clearActiveStash() {
        ClipboardStash stash = getActiveStash();
        if (stash != null) {
            stash.clear();
            fireClipboardChanged(activeStashName);
        }
    }

    /**
     * Clears a specific stash.
     *
     * @param stashName the name of the stash to clear
     */
    public void clearStash(String stashName) {
        ClipboardStash stash = getStash(stashName);
        if (stash != null) {
            stash.clear();
            fireClipboardChanged(stashName);
        }
    }

    /**
     * Clears all stashes.
     */
    public void clearAllStashes() {
        for (ClipboardStash stash : stashes.values()) {
            stash.clear();
        }
        fireClipboardChanged(null);
    }

    /**
     * Renames a stash.
     *
     * @param oldName the current name of the stash
     * @param newName the new name for the stash
     * @throws IllegalArgumentException if the old stash doesn't exist or new
     * name already exists
     */
    public void renameStash(String oldName, String newName) {
        if (oldName == null || newName == null) {
            throw new IllegalArgumentException("Stash names cannot be null");
        }
        if (!stashes.containsKey(oldName)) {
            throw new IllegalArgumentException("Stash with name " + oldName + " does not exist");
        }
        if (stashes.containsKey(newName) && !newName.equals(oldName)) {
            throw new IllegalArgumentException("Stash with name " + newName + " already exists");
        }

        ClipboardStash stash = stashes.remove(oldName);
        stashes.put(newName, stash);

        int index = stashOrder.indexOf(oldName);
        if (index >= 0) {
            stashOrder.set(index, newName);
        }

        if (activeStashName.equals(oldName)) {
            activeStashName = newName;
        }
        fireClipboardChanged(newName);
    }

    /**
     * Returns the clipboard content from the active stash as EditNode array.
     *
     * @return the nodes from the active stash
     */
    public EditNode[] getActiveStashContent() {
        ClipboardStash stash = getActiveStash();
        return stash != null ? stash.getNodes() : new EditNode[0];
    }

    /**
     * Returns whether the active stash is empty.
     *
     * @return true if the active stash has no nodes
     */
    public boolean isActiveStashEmpty() {
        ClipboardStash stash = getActiveStash();
        return stash == null || stash.isEmpty();
    }

    @Override
    public String toString() {
        return "ClipboardManager["
                + "activeStash=" + activeStashName
                + ", stashCount=" + stashes.size()
                + "]";
    }

    // ========================================================================
    // Helper: ID-Kollisionsprüfung
    // ========================================================================
    private boolean areAllEditIdsFree(EditTree tree, EditNode[] nodes) {
        if (nodes == null) {
            return true;
        }
        for (EditNode node : nodes) {
            if (node != null && !areAllEditIdsFree(tree, node)) {
                return false;
            }
        }
        return true;
    }

    private boolean areAllEditIdsFree(EditTree tree, EditNode node) {
        if (tree.containsNode(node.getEditId())) {
            return false;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            EditNode child = node.getChildAt(i);
            if (child != null && !areAllEditIdsFree(tree, child)) {
                return false;
            }
        }
        return true;
    }
}
