/*
* Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v2.0 which
* accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import java.util.Arrays;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;

public class AddNodeCommand extends AbstractEditCommand {

    private final EditCommandEntry.MovementEntry[] entries;

    /**
     * Creates a command to add a single node.
     */
    public AddNodeCommand(long parentId, EditNode node) {
        this(parentId, node, -1);
    }

    /**
     * Creates a command to add a single node at a specific index.
     */
    public AddNodeCommand(long parentId, EditNode node, int index) {
        this(new EditCommandEntry.MovementEntry[]{
            new EditCommandEntry.MovementEntry(
            requireNode(node).getEditId(), // nodeId
            requireValidParentId(parentId), // parentEditId
            index,
            node.deepCopy(false) // snapshot
            )
        });
    }

    /**
     * Creates a command to add multiple nodes from entries.
     *
     * @param entries array of entries to add
     */
    public AddNodeCommand(EditCommandEntry.MovementEntry[] entries) {
        super(CommandType.ADD_NODE);
        if (entries == null || entries.length == 0) {
            throw new IllegalArgumentException("Entries cannot be null or empty");
        }

        this.entries = copyAndValidate(entries);

        if (this.entries.length == 1) {
            setDescription("Add node: " + this.entries[0].snapshot.getName());
        } else {
            setDescription("Add " + this.entries.length + " nodes");
        }
    }

    @Override
    public CommandResult execute(EditTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }

        EditNode[] added = new EditNode[entries.length];

        for (int i = 0; i < entries.length; i++) {
            EditCommandEntry.MovementEntry entry = entries[i];

            // Snapshot liefert den Teilbaum, ID bleibt erhalten
            EditNode newNode = entry.snapshot.deepCopy(false);
            tree.addNode(entry.parentEditId, newNode, entry.index);
            added[i] = newNode;
        }

        return new CommandResult(
                this,
                CommandAction.EXECUTE,
                added,
                added,
                null,
                null
        );
    }

    @Override
    public CommandResult undo(EditTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }

        EditNode[] removed = new EditNode[entries.length];

        // rückwärts, um Indizes stabil zu halten
        for (int i = entries.length - 1; i >= 0; i--) {
            EditCommandEntry.MovementEntry entry = entries[i];

            // bevorzugt nodeId nutzen; fallback auf snapshot-Id, falls nodeId == -1
            long id = entry.nodeId >= 0 ? entry.nodeId : entry.snapshot.getEditId();

            EditNode existingNode = tree.findNodeById(id);
            if (existingNode == null) {
                throw new IllegalStateException(
                        "Cannot undo add: node with id " + id + " not found");
            }
            tree.removeNode(existingNode.getEditId());
            removed[i] = existingNode;
        }

        return new CommandResult(
                this,
                CommandAction.UNDO,
                removed,
                null,
                removed,
                null
        );
    }

    public EditCommandEntry.MovementEntry[] getEntries() {
        return Arrays.copyOf(entries, entries.length);
    }

    public long getParentId() {
        return entries[0].parentEditId;
    }

    public EditNode getNode() {
        return entries[0].snapshot;
    }

    public int getIndex() {
        return entries[0].index;
    }

    public long getEditId() {
        return entries[0].snapshot != null ? entries[0].snapshot.getEditId() : -1;
    }

    private static EditNode requireNode(EditNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        return node;
    }

    private static long requireValidParentId(long parentId) {
        if (parentId < 0) {
            throw new IllegalArgumentException("ParentId cannot be negative");
        }
        return parentId;
    }

    private static EditCommandEntry.MovementEntry[] copyAndValidate(EditCommandEntry.MovementEntry[] entries) {
        EditCommandEntry.MovementEntry[] copy = new EditCommandEntry.MovementEntry[entries.length];

        for (int i = 0; i < entries.length; i++) {
            EditCommandEntry.MovementEntry entry = entries[i];
            if (entry == null) {
                throw new IllegalArgumentException("Entry at index " + i + " cannot be null");
            }
            if (entry.snapshot == null) {
                throw new IllegalArgumentException("Entry snapshot at index " + i + " cannot be null");
            }
            if (entry.parentEditId < 0) {
                throw new IllegalArgumentException("Entry parentEditId at index " + i + " is invalid");
            }
            if (entry.index < -1) {
                throw new IllegalArgumentException("Entry index at index " + i + " is invalid");
            }

            // nodeId aus Entry mit übernehmen, Snapshot geklont
            copy[i] = new EditCommandEntry.MovementEntry(
                    entry.nodeId,
                    entry.parentEditId,
                    entry.index,
                    entry.snapshot.deepCopy(false)
            );
        }

        return copy;
    }
}
