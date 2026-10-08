/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.tree.control;

import de.jare.tree.control.listeners.TreeFocusComponent;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import javax.swing.tree.TreeModel;

/**
 * Model for the selection stack of a single JTree.
 * <p>
 * Holds only the stack data (entries, position), no listeners or UI logic.
 * The dispatcher/listener is SelectionStackManager.
 * </p>
 *
 * @author Jansuch Rentenatus
 */
public class SelectionStackManagerModel {

    private final WeakReference<TreeFocusComponent> weakTree;

    /**
     * Stack of selection entries. The "past" (older selections) sits at the
     * front, the "future" (newer selections) at the end.
     */
    private final Deque<SelectionStackEntry> stack = new ArrayDeque<>();

    /**
     * Current position in the stack (0-based). -1 means: no selection yet.
     */
    private int currentPos = -1;

    public SelectionStackManagerModel(TreeFocusComponent tree) {
        this.weakTree = new WeakReference<>(Objects.requireNonNull(tree));
    }

    public TreeFocusComponent getTree() {
        return weakTree.get();
    }

    /**
     * Checks whether this model belongs to the JTree with the given TreeModel
     * (identity comparison of the TreeModel of the stored JTree).
     *
     * @param model
     * @return
     */
    public boolean isFor(TreeModel model) {
        TreeFocusComponent tree = getTree();
        return tree != null && tree.getModel() == model;
    }

    /**
     * Appends a new selection to the end of the stack. Trims all
     * "forward" entries behind currentPos if necessary.
     *
     * @param entry
     */
    public void addSelection(SelectionStackEntry entry) {
        if (entry == null) {
            return;
        }
        // when we are not at the end, discard all entries after currentPos
        if (currentPos >= 0 && currentPos < stack.size() - 1) {
            int keep = currentPos + 1;
            Deque<SelectionStackEntry> newStack = new ArrayDeque<>(keep);
            int i = 0;
            for (SelectionStackEntry e : stack) {
                if (i++ >= keep) {
                    break;
                }
                newStack.addLast(e);
            }
            stack.clear();
            stack.addAll(newStack);
        }
        // append the new entry, position on the last element
        stack.addLast(entry);
        currentPos = stack.size() - 1;
    }

    public boolean canBackward() {
        return currentPos > 0 && !stack.isEmpty();
    }

    public boolean canForward() {
        return currentPos >= 0 && currentPos < stack.size() - 1;
    }

    /**
     * One step back in the stack. Returns the new current entry, or null
     * if not possible.
     *
     * @return
     */
    public SelectionStackEntry goBackward() {
        if (!canBackward()) {
            return null;
        }
        currentPos--;
        return getCurrentEntry();
    }

    /**
     * One step forward in the stack. Returns the new current entry, or null
     * if not possible.
     *
     * @return
     */
    public SelectionStackEntry goForward() {
        if (!canForward()) {
            return null;
        }
        currentPos++;
        return getCurrentEntry();
    }

    public SelectionStackEntry getCurrentEntry() {
        if (currentPos < 0 || currentPos >= stack.size()) {
            return null;
        }
        int i = 0;
        for (SelectionStackEntry e : stack) {
            if (i++ == currentPos) {
                return e;
            }
        }
        return null;
    }

    /**
     * Returns up to max labels for the "past" (backward), relative to the
     * current position. Format e.g.: "1: last label", "2: second to last
     * label", ...
     *
     * @param max
     * @return
     */
    public List<String> getBackwardLabels(int max) {
        List<String> result = new ArrayList<>();
        if (currentPos <= 0 || stack.isEmpty() || max <= 0) {
            return result;
        }

        // we walk backwards from currentPos - 1
        SelectionStackEntry[] array = stack.toArray(SelectionStackEntry[]::new);
        int index = currentPos - 1;
        while (index >= 0 && result.size() < max) {
            SelectionStackEntry entry = array[index];
            int distance = currentPos - index;
            result.add(distance + ": " + entry.getLabel());
            index--;
        }
        return result;
    }

    /**
     * Returns up to max labels for the "future" (forward), relative to the
     * current position. Format e.g.: "1: next label", "2: label after
     * next", ...
     *
     * @param max
     * @return
     */
    public List<String> getForwardLabels(int max) {
        List<String> result = new ArrayList<>();
        if (currentPos < 0 || stack.isEmpty() || max <= 0) {
            return result;
        }
        if (currentPos >= stack.size() - 1) {
            return result;
        }
        SelectionStackEntry[] array = stack.toArray(SelectionStackEntry[]::new);
        for (int i = currentPos + 1; i < array.length && result.size() < max; i++) {
            SelectionStackEntry entry = array[i];
            int distance = i - currentPos;
            result.add(distance + ": " + entry.getLabel());
        }
        return result;
    }

    /**
     * Completely resets the stack and position.
     */
    public void clear() {
        stack.clear();
        currentPos = -1;
    }

    void addSynonym(long oldNodeId, long newNodeId) {
        if (oldNodeId == newNodeId || newNodeId < 0) {
            return;
        }
        for (int i = 0; i < stack.size(); i++) {
            SelectionStackEntry entry = stack.pollFirst();
            if (entry.getEditIds().contains(oldNodeId)) {
                java.util.List<Long> newEditIds = new java.util.ArrayList<>(entry.getEditIds());
                if (!newEditIds.contains(newNodeId)) {
                    newEditIds.add(newNodeId);
                }
                entry = new SelectionStackEntry(newEditIds, entry.getLabel());
            }
            stack.addLast(entry);
        }
    }

}
