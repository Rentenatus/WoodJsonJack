/*
* Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v2.0 which
* accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.command.EditCommand.CommandType;
import de.jare.jsoncasted.editor.command.EditCommandEntry.MovementEntry;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;
import java.util.Arrays;

/**
 * Command that adds node(s) to the tree. When executed, the node(s) are
 * inserted at their specified parent and index. When undone, the node(s) are
 * removed from the tree.
 */
public class AddNodeCommand extends AbstractEditCommand {

    private final MovementEntry[] entries;

    /**
     * Creates a command to add a single node.
     *
     * @param parentId the ID of the parent node
     * @param node the node to add
     */
    public AddNodeCommand(long parentId, EditNode node) {
        this(parentId, node, -1);
    }

    /**
     * Creates a command to add a single node at a specific index.
     *
     * @param parentId the ID of the parent node
     * @param node the node to add
     * @param index the index at which to insert the node, or -1 to append
     */
    public AddNodeCommand(long parentId, EditNode node, int index) {
        this(new MovementEntry[]{
            new MovementEntry(
            requireNode(node).getEditId(), // nodeId
            requireValidParentId(parentId), // parentEditId
            index, // index
            node.deepCopy(false) // snapshot
            )
        });
    }

    /**
     * Creates a command to add multiple nodes from entries.
     *
     * @param entries array of entries to add
     */
    public AddNodeCommand(MovementEntry[] entries) {
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
    public CommandAvailability check(EditTree tree) {
        if (tree == null) {
            return CommandAvailability.disallowed(
                    "editor.command.tree.missing");
        }

        for (int i = 0; i < entries.length; i++) {
            MovementEntry entry = entries[i];

            EditNode parent = tree.findNodeById(entry.parentEditId);
            if (parent == null) {
                return CommandAvailability.disallowed(
                        "editor.command.add.parentMissing",
                        Long.toString(entry.parentEditId),
                        Integer.toString(i));
            }

            if (entry.index < -1 || entry.index > parent.getChildCount()) {
                return CommandAvailability.disallowed(
                        "editor.command.add.indexInvalid",
                        Integer.toString(entry.index),
                        Integer.toString(parent.getChildCount()),
                        Integer.toString(i));
            }

            EditNode child = entry.snapshot;
            if (child == null) {
                return CommandAvailability.disallowed(
                        "editor.command.add.snapshotMissing",
                        Integer.toString(i));
            }

            long id = entry.nodeId >= 0 ? entry.nodeId : child.getEditId();
            if (tree.containsNode(id)) {
                return CommandAvailability.disallowed(
                        "editor.command.add.idConflict",
                        Long.toString(id),
                        Integer.toString(i));
            }

            if (!child.canBeChildOf(parent)) {
                return CommandAvailability.disallowed(
                        "editor.command.add.childNotAllowedForParent",
                        child.getTypeKey(),
                        parent.getTypeKey(),
                        Integer.toString(i));
            }
        }

        return CommandAvailability.allowed("editor.command.add.allowed");
    }

    @Override
    public CommandResult execute(EditTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }

        EditNode[] added = new EditNode[entries.length];

        for (int i = 0; i < entries.length; i++) {
            MovementEntry entry = entries[i];

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
    public CommandResult doUndo(EditTree tree) {

        EditNode[] removed = new EditNode[entries.length];

        // rueckwaerts, um Indizes stabil zu halten
        for (int i = entries.length - 1; i >= 0; i--) {
            MovementEntry entry = entries[i];

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

    /**
     * Returns a defensive copy of the entries array.
     *
     * @return a copy of the entries array
     */
    public MovementEntry[] getEntries() {
        return Arrays.copyOf(entries, entries.length);
    }

    /**
     * Returns the parent ID of the first entry.
     *
     * @return the parent node ID
     */
    public long getParentId() {
        return entries[0].parentEditId;
    }

    /**
     * Returns the snapshot node of the first entry.
     *
     * @return the node snapshot
     */
    public EditNode getNode() {
        return entries[0].snapshot;
    }

    /**
     * Returns the index of the first entry.
     *
     * @return the insertion index
     */
    public int getIndex() {
        return entries[0].index;
    }

    /**
     * Returns the edit ID of the first entry's node.
     *
     * @return the node edit ID, or -1 if snapshot is null
     */
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

    private static MovementEntry[] copyAndValidate(MovementEntry[] entries) {
        MovementEntry[] copy = new MovementEntry[entries.length];

        for (int i = 0; i < entries.length; i++) {
            MovementEntry entry = entries[i];
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

            // nodeId aus Entry mit uebernehmen, Snapshot geklont
            copy[i] = new MovementEntry(
                    entry.nodeId,
                    entry.parentEditId,
                    entry.index,
                    entry.snapshot.deepCopy(false)
            );
        }

        return copy;
    }
}
