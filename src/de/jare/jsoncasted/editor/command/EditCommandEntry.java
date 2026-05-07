/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.core.EditNode;

/**
 * Entry class for storing information about deleted nodes in DeleteNodeCommand.
 * Contains the parent editId, original index, and a snapshot of the deleted subtree.
 */
public class EditCommandEntry {

    public final long parentEditId;              // editId des Elternknotens
    public final int index;                      // urspruenglicher Index beim Parent
    public final EditNode snapshot; // Snapshot des geloeschten Teilbaums

    public EditCommandEntry(long parentEditId, int index, EditNode snapshot) {
        this.parentEditId = parentEditId;
        this.index = index;
        this.snapshot = snapshot;
    }
}
