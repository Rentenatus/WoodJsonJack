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
            new MovementEntry(node.getEditId(), oldParentId, oldIndex, null)
        };
        this.newEntries = new MovementEntry[]{
            new MovementEntry(node.getEditId(), newParentId, newIndex, null)
        };

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
     * @param newParentId the target parent ID
     * @param newIndex the starting target index, or {@code -1} to append
     */
    public MoveNodeCommand(EditNode[] nodes, long newParentId, int newIndex) {
        super(CommandType.MOVE_NODE);

        if (nodes == null) {
            throw new IllegalArgumentException("Nodes cannot be null");
        }
        if (nodes.length == 0) {
            throw new IllegalArgumentException("Nodes cannot be empty");
        }
        if (newParentId < 0) {
            throw new IllegalArgumentException("New parentId cannot be negative");
        }
        if (newIndex < -1) {
            throw new IllegalArgumentException("New index cannot be < -1");
        }

        EditNode[] sortedNodes = Arrays.copyOf(nodes, nodes.length);
        Arrays.sort(sortedNodes, Comparator
                .comparingLong((EditNode n) -> {
                    EditNode parent = n.getParent();
                    return parent != null ? parent.getEditId() : -1L;
                })
                .thenComparingInt(n -> {
                    EditNode parent = n.getParent();
                    return parent != null ? parent.getChildIndex(n) : -1;
                }));

        this.oldEntries = new MovementEntry[sortedNodes.length];
        this.newEntries = new MovementEntry[sortedNodes.length];

        for (int i = 0; i < sortedNodes.length; i++) {
            EditNode node = sortedNodes[i];
            if (node == null) {
                throw new IllegalArgumentException("Node at index " + i + " cannot be null");
            }

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
                    null
            );

            int targetIndex = newIndex < 0 ? -1 : newIndex + i;
            this.newEntries[i] = new MovementEntry(
                    node.getEditId(),
                    newParentId,
                    targetIndex,
                    null
            );
        }

        if (sortedNodes.length == 1) {
            setDescription("Move node: " + sortedNodes[0].getName());
        } else {
            setDescription("Move " + sortedNodes.length + " nodes");
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

            if (isAncestorOf(node, targetParent)) {
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

        MovementEntry oldEntry = oldEntries[0];
        MovementEntry newEntry = newEntries[0];

        EditNode node = tree.findNodeById(oldEntry.nodeId);
        if (node == null || node.getParent() == null) {
            return false;
        }

        EditNode currentParent = node.getParent();
        if (currentParent.getEditId() != newEntry.parentEditId) {
            return false;
        }

        int currentIndex = currentParent.getChildIndex(node);
        if (currentIndex < 0) {
            return false;
        }

        int effectiveTargetIndex = resolveEffectiveTargetIndex(
                tree,
                node,
                oldEntry.parentEditId,
                newEntry.parentEditId,
                newEntry.index
        );

        return effectiveTargetIndex == currentIndex;
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

        EditNode[] moved = moveAll(tree, oldEntries, newEntries, false);

        return new CommandResult(
                this,
                CommandAction.EXECUTE,
                moved,
                null,
                null,
                moved
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

        EditNode[] moved = moveAll(tree, newEntries, oldEntries, false);

        return new CommandResult(
                this,
                CommandAction.UNDO,
                moved,
                null,
                null,
                moved
        );
    }

    /**
     * Moves all nodes from the given source entries to the target entries.
     *
     * @param tree the target tree
     * @param fromEntries source entries
     * @param toEntries target entries
     * @param reverse whether to process entries in reverse order
     * @return the moved nodes
     */
    private EditNode[] moveAll(
            EditTree tree,
            MovementEntry[] fromEntries,
            MovementEntry[] toEntries,
            boolean reverse) {

        EditNode[] moved = new EditNode[fromEntries.length];

        int length = fromEntries.length;
        int start = reverse ? length - 1 : 0;
        int end = reverse ? -1 : length;
        int step = reverse ? -1 : 1;
        boolean singleMove = length == 1;

        for (int i = start; i != end; i += step) {
            MovementEntry from = fromEntries[i];
            MovementEntry to = toEntries[i];

            EditNode node = tree.findNodeById(from.nodeId);
            if (node == null) {
                throw new IllegalStateException(
                        "Cannot move node with id " + from.nodeId + ": node not found");
            }

            int effectiveTargetIndex = singleMove
                    ? resolveEffectiveTargetIndex(
                            tree,
                            node,
                            from.parentEditId,
                            to.parentEditId,
                            to.index
                    )
                    : clampTargetIndex(tree, to.parentEditId, to.index);

            tree.moveNode(node.getEditId(), to.parentEditId, effectiveTargetIndex);
            moved[i] = node;
        }

        return moved;
    }

    /**
     * Clamps a requested index into the valid range of the current target
     * parent.
     *
     * @param tree the target tree
     * @param targetParentId the target parent ID
     * @param requestedIndex the requested index
     * @return the clamped index, or {@code -1} for append
     */
    private int clampTargetIndex(EditTree tree, long targetParentId, int requestedIndex) {
        if (requestedIndex < 0) {
            return -1;
        }

        EditNode targetParent = tree.findNodeById(targetParentId);
        if (targetParent == null) {
            return requestedIndex;
        }

        int childCount = targetParent.getChildCount();
        return Math.max(0, Math.min(requestedIndex, childCount));
    }

    /**
     * Resolves the effective target index for a single-node move.
     *
     * <p>
     * If the node is moved within the same parent and currently lies before the
     * requested target index, the index is decremented to account for the
     * removal shift.</p>
     *
     * @param tree the target tree
     * @param node the node being moved
     * @param fromParentId the original parent ID
     * @param targetParentId the target parent ID
     * @param requestedIndex the requested target index
     * @return the effective target index
     */
    private int resolveEffectiveTargetIndex(
            EditTree tree,
            EditNode node,
            long fromParentId,
            long targetParentId,
            int requestedIndex) {

        if (requestedIndex < 0) {
            return -1;
        }

        EditNode targetParent = tree.findNodeById(targetParentId);
        if (targetParent == null) {
            return requestedIndex;
        }

        int childCount = targetParent.getChildCount();
        int clampedIndex = Math.max(0, Math.min(requestedIndex, childCount));

        if (fromParentId == targetParentId) {
            EditNode currentParent = node.getParent();
            if (currentParent != null && currentParent.getEditId() == fromParentId) {
                int currentIndex = currentParent.getChildIndex(node);
                if (currentIndex >= 0 && currentIndex < clampedIndex) {
                    clampedIndex--;
                }
            }
        }

        return clampedIndex;
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
     * Returns the first source entry.
     *
     * @return the first source entry
     */
    public MovementEntry getOldEntry() {
        return oldEntries[0];
    }

    /**
     * Returns the first target entry.
     *
     * @return the first target entry
     */
    public MovementEntry getNewEntry() {
        return newEntries[0];
    }

    /**
     * Returns the source parent ID of the first entry.
     *
     * @return the source parent ID
     */
    public long getOldParentId() {
        return oldEntries[0].parentEditId;
    }

    /**
     * Returns the source index of the first entry.
     *
     * @return the source index
     */
    public int getOldIndex() {
        return oldEntries[0].index;
    }

    /**
     * Returns the target parent ID of the first entry.
     *
     * @return the target parent ID
     */
    public long getNewParentId() {
        return newEntries[0].parentEditId;
    }

    /**
     * Returns the target index of the first entry.
     *
     * @return the target index
     */
    public int getNewIndex() {
        return newEntries[0].index;
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
}
