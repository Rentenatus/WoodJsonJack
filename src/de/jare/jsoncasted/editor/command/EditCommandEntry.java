/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.core.EditNode;

/**
 * Utility container for immutable command entry types used by edit commands.
 *
 * <p>
 * Each nested entry class stores the data required to execute and undo a
 * specific kind of command operation.</p>
 */
public final class EditCommandEntry {

    private EditCommandEntry() {
        throw new AssertionError("Utility class");
    }

    /**
     * Immutable entry describing a node position within the tree.
     *
     * <p>
     * This entry is used for add, delete, and move operations. Depending on the
     * command type, {@code snapshot} may contain a subtree copy used for
     * undo/redo reconstruction.</p>
     */
    public static final class MovementEntry {

        /**
         * The ID of the affected node, or {@code -1} if not yet known.
         */
        public final long nodeId;

        /**
         * The ID of the parent node.
         */
        public final long parentEditId;

        /**
         * The child index inside the parent, or {@code -1} for append
         * semantics.
         */
        public final int index;

        /**
         * Optional snapshot of the affected subtree.
         */
        public final EditNode snapshot;

        /**
         * Creates a movement entry without a node ID.
         *
         * @param parentEditId the parent node ID
         * @param index the child index
         * @param snapshot an optional subtree snapshot
         */
        public MovementEntry(long parentEditId, int index, EditNode snapshot) {
            this(-1, parentEditId, index, snapshot);
        }

        /**
         * Creates a movement entry.
         *
         * @param nodeId the affected node ID
         * @param parentEditId the parent node ID
         * @param index the child index
         * @param snapshot an optional subtree snapshot
         */
        public MovementEntry(long nodeId, long parentEditId, int index, EditNode snapshot) {
            this.nodeId = nodeId;
            this.parentEditId = parentEditId;
            this.index = index;
            this.snapshot = snapshot;
        }
    }

    /**
     * Immutable entry describing a text-based content change of a node.
     *
     * <p>
     * This entry is used for commands such as rename and set-value.</p>
     */
    public static final class ContentEntry {

        /**
         * The ID of the affected node.
         */
        public final long nodeId;

        /**
         * The previous value.
         */
        public final String oldValue;

        /**
         * The new value.
         */
        public final String newValue;

        /**
         * Creates a content entry.
         *
         * @param nodeId the affected node ID
         * @param oldValue the previous value
         * @param newValue the new value
         */
        public ContentEntry(long nodeId, String oldValue, String newValue) {
            this.nodeId = nodeId;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }
}
