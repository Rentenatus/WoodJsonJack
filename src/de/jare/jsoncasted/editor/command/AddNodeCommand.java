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
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Command that adds node(s) to the tree. When executed, the node(s) are
 * inserted at their specified parent and index. When undone, the node(s) are
 * removed from the tree.
 */
public class AddNodeCommand extends AbstractEditCommand {

    private static final UpdateAction[] ON_ADD_ACTIONS = new UpdateAction[]{UpdateAction.REBUILD_AFFECTED, UpdateAction.SELECT_ADDED};
    private static final UpdateAction[] ON_REMOVE_ACTIONS = new UpdateAction[]{UpdateAction.REBUILD_AFFECTED, UpdateAction.SELECT_UPDATED};

    private final MovementEntry[] entries;

    /**
     * Creates a command to add a single node.
     *
     * @param parent the parent node
     * @param node the node to add
     */
    public AddNodeCommand(EditNode parent, EditNodeAbstract node) {
        this(parent, node, -1);
    }

    /**
     * Creates a command to add a single node at a specific index.
     *
     * @param parent the parent node
     * @param node the node to add
     * @param index the index at which to insert the node, or -1 to append
     */
    public AddNodeCommand(EditNode parent, EditNodeAbstract node, int index) {
        this(new MovementEntry[]{
            new MovementEntry(
            requireNode(node),
            requireValidParent(parent), // parentEditId
            index // index
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

            EditNode parent = tree.findNodeByIdAndRange(entry.parentEditId, entry.parentLeftRange, entry.parentTimesRange);
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
    protected CommandResult doExecute(EditTree tree) {
        CommandAvailability checkResult = check(tree);
        if (checkResult.isDisallowed()) {
            throw new IllegalArgumentException("Action disallowed: " + checkResult.getMessageKey());
        }
        if (checkResult.isUseless()) {
            return null;
        }

        EditNodeAbstract[] added = new EditNodeAbstract[entries.length];
        Set<EditNodeAbstract> parentSet = new HashSet<>();
        Set<EditNodeAbstract> failedtSet = new HashSet<>();

        for (int i = 0; i < entries.length; i++) {
            MovementEntry entry = entries[i];

            // Snapshot liefert den Teilbaum, ID bleibt erhalten
            EditNodeAbstract newNode = entry.snapshot.deepCopy(false);
            if (tree.addNode(entry.parentEditId, newNode, entry.index)) {
                parentSet.add(tree.findNodeByIdAndRange(entry.parentEditId, entry.leftRange, entry.timesRange));
                added[i] = newNode;
            } else {
                failedtSet.add(newNode);
            }

        }
        final EditNodeAbstract[] parents = parentSet.toArray(new EditNodeAbstract[parentSet.size()]);

        return new CommandResult(
                this,
                CommandAction.EXECUTE,
                parents, // affectedNodes
                added, // addedNodes
                null, //removedNodes
                parents, // updatedNodes
                failedtSet.toArray(new EditNodeAbstract[failedtSet.size()]),
                ON_ADD_ACTIONS
        );
    }

    @Override
    public CommandResult doUndo(EditTree tree) {

        EditNodeAbstract[] removed = new EditNodeAbstract[entries.length];
        Set<EditNodeAbstract> parentSet = new HashSet<>();
        Set<EditNodeAbstract> failedtSet = new HashSet<>();

        // rueckwaerts, um Indizes stabil zu halten
        for (int i = entries.length - 1; i >= 0; i--) {
            MovementEntry entry = entries[i];

            // bevorzugt nodeId nutzen; fallback auf snapshot-Id, falls nodeId == -1
            long id = entry.nodeId >= 0 ? entry.nodeId : entry.snapshot.getEditId();

            EditNodeAbstract existingNode = tree.findNodeByIdAndRange(id, entry.leftRange, entry.timesRange);
            if (existingNode == null) {
                failedtSet.add(entry.snapshot);
                continue;
            }
            final EditNodeAbstract parent = existingNode.getParent();
            if (parent != null) {
                parentSet.add(parent);
            }
            tree.removeNode(existingNode.getEditId(), existingNode.getLeftRange(), existingNode.getTimesRange());
            removed[i] = existingNode;
        }
        final EditNodeAbstract[] parents = parentSet.toArray(new EditNodeAbstract[parentSet.size()]);

        return new CommandResult(
                this,
                CommandAction.UNDO,
                parents, // affectedNodes
                null, // addedNodes
                removed,//removedNodes
                parents, // updatedNodes
                failedtSet.toArray(new EditNodeAbstract[failedtSet.size()]),
                ON_REMOVE_ACTIONS
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

    /**
     * Validates that the given node is not null. If the node is null, an
     * IllegalArgumentException is thrown with a descriptive message. This
     * method is used to ensure that a valid node is provided when creating an
     * AddNodeCommand, as a null node would not be meaningful in the context of
     * adding a node to the tree.
     *
     * @param node the node to validate
     * @return the validated node if valid
     * @throws IllegalArgumentException if the node is null
     */
    private static EditNodeAbstract requireNode(EditNodeAbstract node) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        return node;
    }

    /**
     * Validates the given parent node.
     *
     * @param parent the parent node to validate
     * @return the validated parent node if valid
     * @throws IllegalArgumentException if the parent is null or has an invalid
     * edit ID
     */
    private static EditNode requireValidParent(EditNode parent) {
        if (parent == null || parent.getEditId() < 0) {
            throw new IllegalArgumentException("ParentId cannot be negative");
        }
        return parent;
    }

    /**
     * Creates a copy of the given entries array and validates each entry. This
     * method checks that each entry is not null, has a valid snapshot, a
     * non-negative parentEditId, and an index that is either -1 or
     * non-negative. If any entry fails validation, an IllegalArgumentException
     * is thrown with a descriptive message indicating the issue and the index
     * of the problematic entry. The method returns a new array containing
     * copies of the valid entries.
     *
     * @param entries the array of entries to copy and validate
     * @return a new array containing copies of the valid entries
     * @throws IllegalArgumentException if any entry is invalid
     */
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
                    entry.leftRange,
                    entry.timesRange,
                    entry.parentEditId,
                    entry.parentLeftRange,
                    entry.parentTimesRange,
                    entry.index,
                    entry.snapshot.deepCopy(false)
            );
        }
        return copy;
    }
}
