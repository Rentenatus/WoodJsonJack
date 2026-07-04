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
 * Model für den Selektions-Stack eines einzelnen JTree.
 * <p>
 * Hält nur die Stack-Daten (Entries, Position), keinerlei Listener oder
 * UI-Logik. Der Dispatcher/Listener ist SelectionStackManager.
 * </p>
 */
public class SelectionStackManagerModel {

    private final WeakReference<TreeFocusComponent> weakTree;

    /**
     * Stack der Selektions-Einträge. Die "Vergangenheit" (ältere Selektionen)
     * liegt am Anfang, die "Zukunft" (neuere Selektionen) am Ende.
     */
    private final Deque<SelectionStackEntry> stack = new ArrayDeque<>();

    /**
     * Aktuelle Position im Stack (0-basiert). -1 bedeutet: noch keine
     * Selektion.
     */
    private int currentPos = -1;

    public SelectionStackManagerModel(TreeFocusComponent tree) {
        this.weakTree = new WeakReference<>(Objects.requireNonNull(tree));
    }

    public TreeFocusComponent getTree() {
        return weakTree.get();
    }

    /**
     * Prüft, ob dieses Model zu dem JTree mit dem gegebenen TreeModel
     * gehört.(Identitätsvergleich des TreeModel des gespeicherten JTree.)
     *
     * @param model
     * @return
     */
    public boolean isFor(TreeModel model) {
        TreeFocusComponent tree = getTree();
        return tree != null && tree.getModel() == model;
    }

    /**
     * Neue Selektion an das Ende des Stacks anhängen.Schneidet ggf. alle
     * "Forward"-Einträge hinter currentPos ab.
     *
     * @param entry
     */
    public void addSelection(SelectionStackEntry entry) {
        if (entry == null) {
            return;
        }
        // Wenn wir nicht am Ende sind, alle Einträge nach currentPos verwerfen
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
        // Neuen Eintrag anhängen, Position auf letztes Element
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
     * Ein Schritt zurück im Stack.Gibt den neuen aktuellen Entry, oder null,
     * falls nicht möglich.
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
     * Ein Schritt vorwärts im Stack.Gibt den neuen aktuellen Entry, oder null,
     * falls nicht möglich.
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
     * Liefert bis zu max Labels für die "Vergangenheit" (Backward), relativ zur
     * aktuellen Position.Format z.B.: "1: letztes Label", "2: vorletztes
     * Label", ...
     *
     * @param max
     * @return
     */
    public List<String> getBackwardLabels(int max) {
        List<String> result = new ArrayList<>();
        if (currentPos <= 0 || stack.isEmpty() || max <= 0) {
            return result;
        }

        // Wir laufen rückwärts von currentPos - 1
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
     * Liefert bis zu max Labels für die "Zukunft" (Forward), relativ zur
     * aktuellen Position.Format z.B.: "1: nächstes Label", "2: übernächstes
     * Label", ...
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
     * Stack und Position komplett zurücksetzen.
     */
    public void clear() {
        stack.clear();
        currentPos = -1;
    }

    void addSynonym(long oldNodeId, long newNodeId) {
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
