/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.tree.control;

import java.util.List;

/**
 * Ereignis für Selektions‑Stack‑Änderungen.
 */
public class SelectionStackEntry {

    private final List<Long> editIds;  // editIds der selektierten Knoten
    private final String label;        // Beschriftung 

    public SelectionStackEntry(List<Long> editIds, String label) {
        this.editIds = List.copyOf(editIds);
        this.label = label;
    }

    public static SelectionStackEntry stackEvent(List<Long> editIds, String label) {
        return new SelectionStackEntry(editIds, label);
    }

    public List<Long> getEditIds() {
        return editIds;
    }

    public String getLabel() {
        return label;
    }

}
