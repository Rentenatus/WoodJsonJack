/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.core.EditNode;

/**
 * Entry classes for storing information in various command types.
 */
public class EditCommandEntry {

    /**
     * Entry for storing information about node movements and positions.
     * Contains the node editId, parent editId, original index, and a snapshot of the subtree.
     */
    public static class MovementEntry {
        public final long nodeId;                    // editId des Knotens
        public final long parentEditId;              // editId des Elternknotens
        public final int index;                      // urspruenglicher Index beim Parent
        public final EditNode snapshot; // Snapshot des Teilbaums

        public MovementEntry(long parentEditId, int index, EditNode snapshot) {
            this(-1, parentEditId, index, snapshot);
        }

        public MovementEntry(long nodeId, long parentEditId, int index, EditNode snapshot) {
            this.nodeId = nodeId;
            this.parentEditId = parentEditId;
            this.index = index;
            this.snapshot = snapshot;
        }
    }

    /**
     * Entry for storing value change information.
     * Contains the node id, old value, and new value.
     */
    public static class ValueEntry {
        public final long nodeId;
        public final String oldValue;
        public final String newValue;

        public ValueEntry(long nodeId, String oldValue, String newValue) {
            this.nodeId = nodeId;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }
}
