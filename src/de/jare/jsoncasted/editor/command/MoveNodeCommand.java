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
import java.util.Comparator;

/**
 * Command that moves one or more nodes to new parent/index positions.
 *
 * <p>
 * The command stores both source and target positions explicitly. For
 * multi-node moves, nodes are normalized by source parent and source index to
 * ensure a stable processing order.</p>
 *
 * <p>
 * Single-node moves support index adjustment when reordering inside the same
 * parent. Multi-node moves use the stored target indices as absolute positions
 * and therefore do not apply same-parent correction logic.</p>
 */
public class MoveNodeCommand extends AbstractEditCommand {

    private static final UpdateAction[] UPDATE_ACTIONS = new UpdateAction[]{UpdateAction.REBUILD_AFFECTED, UpdateAction.SELECT_UPDATED};

    private final MovementEntry[] oldEntries;
    private final MovementEntry[] newEntries;

    /**
     * Creates a command to move a single node to a new parent and index.
     *
     * @param node the node to move
     * @param newParentId the target parent ID
     * @param newIndex the target index, or {@code -1} to append
     */
    public MoveNodeCommand(EditNode node, long newParentId, int newIndex) {
        super(CommandType.MOVE_NODE);

        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (newParentId < 0) {
            throw new IllegalArgumentException("New parentId cannot be negative");
        }
        if (newIndex < -1) {
            throw new IllegalArgumentException("New index cannot be < -1");
        }

        EditNode parent = node.getParent();
        long oldParentId = parent != null ? parent.getEditId() : -1;
        int oldIndex = parent != null ? parent.getChildIndex(node) : -1;

        if (oldParentId < 0 || oldIndex < 0) {
            throw new IllegalArgumentException("Node must have a valid parent and index");
        }

        this.oldEntries = new MovementEntry[]{
            new MovementEntry(node.getEditId(), oldParentId, oldIndex, node.deepCopy(false))
        };
        if (oldParentId == newParentId && (newIndex >= oldIndex)) {
            this.newEntries = new MovementEntry[]{
                new MovementEntry(node.getEditId(), newParentId, newIndex - 1, node.deepCopy(false))
            };
        } else {
            this.newEntries = new MovementEntry[]{
                new MovementEntry(node.getEditId(), newParentId, newIndex, null)
            };
        }

        setDescription("Move node: " + node.getName());
    }

    /**
     * Creates a command to move a single node to a new index within its current
     * parent.
     *
     * @param node the node to move
     * @param newIndex the target index, or {@code -1} to append
     */
    public MoveNodeCommand(EditNode node, int newIndex) {
        this(node, requireParentId(node), newIndex);
    }

    /**
     * Creates a command to move multiple nodes to the same target parent,
     * starting at the given index.
     *
     * <p>
     * If {@code newIndex >= 0}, nodes are assigned consecutive target indices
     * starting at that position. If {@code newIndex == -1}, all nodes are
     * appended in normalized order.</p>
     *
     * @param nodes the nodes to move
     * @param newParent the target parent
     * @param newIndex the starting target index, or {@code -1} to append
     */
    public MoveNodeCommand(EditNode[] nodes, EditNode newParent, int newIndex) {
        super(CommandType.MOVE_NODE);

        if (newParent == null) {
            throw new NullPointerException("New parent cannot be null");
        }
        long newParentId = newParent.getEditId();

        if (nodes == null) {
            throw new NullPointerException("Nodes cannot be null");
        }
        final int length = nodes.length;
        if (length == 0) {
            throw new IllegalArgumentException("Nodes cannot be empty");
        }
        if (newIndex < -1) {
            throw new IllegalArgumentException("New index cannot be < -1");
        }

        if (newIndex == -1 || newIndex > newParent.getChildCount()) {
            newIndex = newParent.getChildCount();
        }

        EditNode[] sortedNodes = Arrays.copyOf(nodes, length);
        Arrays.sort(sortedNodes, Comparator
                .comparingLong((EditNode n) -> {
                    EditNode parent = n.getParent();
                    return parent != null ? parent.getEditId() : -1L;
                })
                .thenComparingInt(n -> {
                    EditNode parent = n.getParent();
                    return parent != null ? parent.getChildIndex(n) : -1;
                }));

        this.oldEntries = new MovementEntry[length];
        this.newEntries = new MovementEntry[length];

        int shift = 0;
        for (int i = 0; i < length; i++) {
            EditNode node = nodes[i];
            if (node == null) {
                throw new IllegalArgumentException("Node at index " + i + " cannot be null");
            }
            if (node.getParent() == null) {
                throw new IllegalArgumentException("Node at index " + i + " must have a parent");
            }
            if (node.getParent().getEditId() != newParentId) {
                continue; // No same-parent correction needed for nodes moving to a different parent
            }
            int childIndex = node.getParent().getChildIndex(node);
            if (newIndex > childIndex) {
                shift++;
            }
        }

        for (int i = 0; i < length; i++) {
            EditNode node = sortedNodes[i];
            EditNode parent = node.getParent();
            long oldParentId = parent != null ? parent.getEditId() : -1;
            int oldIndex = parent != null ? parent.getChildIndex(node) : -1;

            if (oldParentId < 0 || oldIndex < 0) {
                throw new IllegalArgumentException(
                        "Node at index " + i + " must have a valid parent and index");
            }

            this.oldEntries[i] = new MovementEntry(
                    node.getEditId(),
                    oldParentId,
                    oldIndex,
                    node.deepCopy(false)
            );

            int targetIndex = newIndex - shift;
            this.newEntries[length - 1 - i] = new MovementEntry(
                    node.getEditId(),
                    newParentId,
                    targetIndex,
                    node.deepCopy(false)
            );
        }

        if (nodes.length == 1) {
            setDescription("Move node: " + nodes[0].getName());
        } else {
            setDescription("Move " + nodes.length + " nodes");
        }
    }

    /**
     * Creates a move command from explicit source and target entries.
     *
     * @param oldEntries source positions
     * @param newEntries target positions
     */
    public MoveNodeCommand(MovementEntry[] oldEntries,
            MovementEntry[] newEntries) {
        super(CommandType.MOVE_NODE);

        if (oldEntries == null || newEntries == null) {
            throw new IllegalArgumentException("Entries cannot be null");
        }
        if (oldEntries.length != newEntries.length) {
            throw new IllegalArgumentException("Arrays must have the same length");
        }
        if (oldEntries.length == 0) {
            throw new IllegalArgumentException("Arrays cannot be empty");
        }

        this.oldEntries = copyAndValidate(oldEntries, true);
        this.newEntries = copyAndValidate(newEntries, false);

        if (this.oldEntries.length == 1) {
            setDescription("Move node");
        } else {
            setDescription("Move " + this.oldEntries.length + " nodes");
        }
    }

    @Override
    public CommandAvailability check(EditTree tree) {
        if (tree == null) {
            return CommandAvailability.disallowed("editor.command.tree.missing");
        }

        if (isNoOpMove(tree)) {
            return CommandAvailability.useless("editor.command.move.useless");
        }

        for (int i = 0; i < newEntries.length; i++) {
            MovementEntry oldEntry = oldEntries[i];
            MovementEntry newEntry = newEntries[i];

            EditNode node = tree.findNodeById(oldEntry.nodeId);
            if (node == null) {
                return CommandAvailability.disallowed(
                        "editor.command.move.nodeMissing",
                        Long.toString(oldEntry.nodeId),
                        Integer.toString(i));
            }

            EditNode sourceParent = tree.findNodeById(oldEntry.parentEditId);
            if (sourceParent == null) {
                return CommandAvailability.disallowed(
                        "editor.command.move.sourceParentMissing",
                        Long.toString(oldEntry.parentEditId),
                        Integer.toString(i));
            }

            EditNode targetParent = tree.findNodeById(newEntry.parentEditId);
            if (targetParent == null) {
                return CommandAvailability.disallowed(
                        "editor.command.move.targetParentMissing",
                        Long.toString(newEntry.parentEditId),
                        Integer.toString(i));
            }

            int sourceIndex = sourceParent.getChildIndex(node);
            if (sourceIndex < 0) {
                return CommandAvailability.disallowed(
                        "editor.command.move.nodeNotChildOfSourceParent",
                        Long.toString(oldEntry.nodeId),
                        Long.toString(oldEntry.parentEditId),
                        Integer.toString(i));
            }

            int targetChildCount = targetParent.getChildCount();
            int requestedIndex = newEntry.index;
            if (requestedIndex < -1 || requestedIndex > targetChildCount) {
                return CommandAvailability.disallowed(
                        "editor.command.move.indexInvalid",
                        Integer.toString(requestedIndex),
                        Integer.toString(targetChildCount),
                        Integer.toString(i));
            }

            if (isAncestorOf(targetParent, node)) {
                return CommandAvailability.disallowed(
                        "editor.command.move.wouldCreateCycle",
                        Long.toString(node.getEditId()),
                        Long.toString(targetParent.getEditId()),
                        Integer.toString(i));
            }

            if (!node.canBeChildOf(targetParent)) {
                return CommandAvailability.disallowed(
                        "editor.command.move.childNotAllowedForParent",
                        node.getTypeKey(),
                        targetParent.getTypeKey(),
                        Integer.toString(i));
            }
        }

        return CommandAvailability.allowed("editor.command.move.allowed");
    }

    private boolean isNoOpMove(EditTree tree) {
        if (oldEntries.length != 1 || newEntries.length != 1) {
            return false;
        }

        int minIndex = Integer.MAX_VALUE;
        int maxInddex = -1;
        for (int i = 0; i < newEntries.length; i++) {
            MovementEntry oldEntry = oldEntries[i];
            MovementEntry newEntry = newEntries[i];

            EditNode node = tree.findNodeById(oldEntry.nodeId);
            if (node == null || node.getParent() == null) {
                return false;
            }

            EditNode currentParent = node.getParent();
            if (currentParent.getEditId() != newEntry.parentEditId) {
                return false;
            }

            int currentIndex = currentParent.getChildIndex(node);
            minIndex = Math.min(minIndex, currentIndex);
            maxInddex = Math.max(maxInddex, currentIndex);
            if (currentIndex < 0) {
                return false;
            }
        }

        if (maxInddex - minIndex > newEntries.length - 1) {
            return false;
        }

        MovementEntry newEntry = newEntries[0];
        int newIndex = newEntry.index;

        System.out.println("§§§§§§§§§§§§§§§§§§§§§§§§§§§ " + newIndex + " == " + minIndex);

        return newIndex == minIndex;
    }

    /**
     * Returns true if candidateAncestor is an ancestor of node (strict).
     */
    private static boolean isAncestorOf(EditNode node, EditNode candidateAncestor) {
        EditNode current = node.getParent();
        while (current != null) {
            if (current == candidateAncestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Executes the move operation.
     *
     * @param tree the target tree
     * @return the command result
     */
    @Override
    public CommandResult execute(EditTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }

        boolean[] successfullyRemoved = removeAll(tree, oldEntries);
        EditNode[] moved = addAll(tree, newEntries, successfullyRemoved);

        return new CommandResult(
                this,
                CommandAction.EXECUTE,
                collectParentNodes(moved),
                null,
                null,
                moved,
                UPDATE_ACTIONS
        );
    }

    /**
     * Undoes the move operation.
     *
     * <p>
     * Undo uses the stored original indices as absolute target positions.
     * Therefore, multi-node undo is processed in forward order.</p>
     *
     * @param tree the target tree
     * @return the command result
     */
    @Override
    public CommandResult doUndo(EditTree tree) {

        boolean[] successfullyRemoved = removeAll(tree, newEntries);
        EditNode[] moved = addAll(tree, oldEntries, successfullyRemoved);

        return new CommandResult(
                this,
                CommandAction.UNDO,
                collectParentNodes(moved),
                null,
                null,
                moved,
                UPDATE_ACTIONS
        );
    }

    /**
     * Returns the stored source entries.
     *
     * @return a defensive copy of the source entries
     */
    public MovementEntry[] getOldEntries() {
        return Arrays.copyOf(oldEntries, oldEntries.length);
    }

    /**
     * Returns the stored target entries.
     *
     * @return a defensive copy of the target entries
     */
    public MovementEntry[] getNewEntries() {
        return Arrays.copyOf(newEntries, newEntries.length);
    }

    /**
     * Returns the parent ID of the given node.
     *
     * @param node the node to inspect
     * @return the parent ID
     */
    private static long requireParentId(EditNode node) {
        if (node == null || node.getParent() == null) {
            throw new IllegalArgumentException("Node must have a parent");
        }
        return node.getParent().getEditId();
    }

    /**
     * Copies and validates movement entries.
     *
     * @param entries the entries to copy
     * @param requireSourceIndex whether the index must be non-negative
     * @return the validated copy
     */
    private static MovementEntry[] copyAndValidate(
            MovementEntry[] entries,
            boolean requireSourceIndex) {

        MovementEntry[] copy = new MovementEntry[entries.length];

        for (int i = 0; i < entries.length; i++) {
            MovementEntry entry = entries[i];
            if (entry == null) {
                throw new IllegalArgumentException("Entry at index " + i + " cannot be null");
            }
            if (entry.nodeId < 0) {
                throw new IllegalArgumentException("Entry nodeId at index " + i + " is invalid");
            }
            if (entry.parentEditId < 0) {
                throw new IllegalArgumentException("Entry parentEditId at index " + i + " is invalid");
            }
            if (requireSourceIndex) {
                if (entry.index < 0) {
                    throw new IllegalArgumentException(
                            "Source entry index at index " + i + " is invalid");
                }
            } else {
                if (entry.index < -1) {
                    throw new IllegalArgumentException(
                            "Target entry index at index " + i + " is invalid");
                }
            }

            copy[i] = new MovementEntry(
                    entry.nodeId,
                    entry.parentEditId,
                    entry.index,
                    entry.snapshot
            );
        }

        return copy;
    }

    private boolean[] removeAll(EditTree tree, MovementEntry[] entries) {
        boolean[] successfullyRemoved = new boolean[entries.length];
        for (int i = 0; i < entries.length; i++) {
            MovementEntry entry = entries[i];
            EditNode node = tree.findNodeById(entry.nodeId);
            if (node != null && node.getParent() != null) {
                node.getParent().removeChild(node);
                successfullyRemoved[i] = true;
            }
        }
        return successfullyRemoved;
    }

    private EditNode[] addAll(EditTree tree, MovementEntry[] entries, boolean[] successfullyRemoved) {
        EditNode[] moved = new EditNode[entries.length];
        for (int i = 0; i < entries.length; i++) { 
            if (!successfullyRemoved[i]) {
                continue; // Skip adding if removal was not successful
            }
            MovementEntry entry = entries[i];
            EditNode parent = tree.findNodeById(entry.parentEditId);
            if (parent == null) {
                continue;
            }

            EditNode nodeToAdd = entry.snapshot != null
                    ? entry.snapshot.deepCopy()
                    : tree.findNodeById(entry.nodeId);

            if (nodeToAdd == null) {
                continue;
            }

            int insertIndex = entry.index;
            if (insertIndex < 0 || insertIndex > parent.getChildCount()) {
                insertIndex = parent.getChildCount();
            }

            parent.addChild(nodeToAdd, insertIndex);
            moved[i] = nodeToAdd;
        }
        return moved;
    }

}
