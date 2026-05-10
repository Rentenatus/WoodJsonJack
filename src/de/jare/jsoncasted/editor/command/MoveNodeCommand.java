/*
* Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v2.0 which
* accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import java.util.Arrays;
import java.util.Comparator;

import de.jare.jsoncasted.editor.command.EditCommand.CommandType;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;

public class MoveNodeCommand extends AbstractEditCommand {

    private final EditCommandEntry.MovementEntry[] oldEntries;
    private final EditCommandEntry.MovementEntry[] newEntries;

    /**
     * Creates a command to move a single node to a new parent and index.
     */
    public MoveNodeCommand(EditNode node, long newParentId, int newIndex) {
        super(CommandType.MOVE_NODE);
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }

        EditNode parent = node.getParent();
        long oldParentId = parent != null ? parent.getEditId() : -1;
        int oldIndex = parent != null ? parent.getChildIndex(node) : -1;

        if (oldParentId < 0 || oldIndex < 0) {
            throw new IllegalArgumentException("Node must have a valid parent and index");
        }
        if (newParentId < 0) {
            throw new IllegalArgumentException("New parentId cannot be negative");
        }
        if (newIndex < -1) {
            throw new IllegalArgumentException("New index cannot be < -1");
        }

        this.oldEntries = new EditCommandEntry.MovementEntry[]{
            new EditCommandEntry.MovementEntry(node.getEditId(), oldParentId, oldIndex, null)
        };
        this.newEntries = new EditCommandEntry.MovementEntry[]{
            new EditCommandEntry.MovementEntry(node.getEditId(), newParentId, newIndex, null)
        };

        setDescription("Move node: " + node.getName());
    }

    /**
     * Creates a command to move a single node to a new index within the same
     * parent.
     */
    public MoveNodeCommand(EditNode node, int newIndex) {
        this(node, requireParentId(node), newIndex);
    }

    /**
     * Creates a command to move multiple nodes to the same target parent at
     * starting index. Nodes will be placed sequentially starting at newIndex,
     * preserving their order.
     *
     * @param nodes the nodes to move
     * @param newParentId the target parent ID for all nodes
     * @param newIndex the starting index at the target parent, -1 = append
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

        // Normalisieren: nach Parent-ID und aktuellem Index sortieren,
        // damit Multi-Moves eine stabile Reihenfolge haben
        EditNode[] sortedNodes = Arrays.copyOf(nodes, nodes.length);
        Arrays.sort(sortedNodes, Comparator
                .comparingLong((EditNode n) -> {
                    EditNode p = n.getParent();
                    return p != null ? p.getEditId() : -1L;
                })
                .thenComparingInt(n -> {
                    EditNode p = n.getParent();
                    return p != null ? p.getChildIndex(n) : -1;
                }));

        this.oldEntries = new EditCommandEntry.MovementEntry[sortedNodes.length];
        this.newEntries = new EditCommandEntry.MovementEntry[sortedNodes.length];

        for (int i = 0; i < sortedNodes.length; i++) {
            EditNode node = sortedNodes[i];
            if (node == null) {
                throw new IllegalArgumentException("Node at index " + i + " cannot be null");
            }

            EditNode parent = node.getParent();
            long oldParentId = parent != null ? parent.getEditId() : -1;
            int oldIndex = parent != null ? parent.getChildIndex(node) : -1;

            if (oldParentId < 0 || oldIndex < 0) {
                throw new IllegalArgumentException("Node at index " + i + " must have a valid parent and index");
            }

            this.oldEntries[i] = new EditCommandEntry.MovementEntry(
                    node.getEditId(),
                    oldParentId,
                    oldIndex,
                    null
            );

            int targetIndex = newIndex < 0 ? -1 : newIndex + i;
            this.newEntries[i] = new EditCommandEntry.MovementEntry(
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
     * Creates a move command from entries arrays.
     *
     * @param oldEntries array of old entries (source positions)
     * @param newEntries array of new entries (target positions)
     */
    public MoveNodeCommand(EditCommandEntry.MovementEntry[] oldEntries,
            EditCommandEntry.MovementEntry[] newEntries) {
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

    @Override
    public CommandResult undo(EditTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }

        EditNode[] moved = moveAll(tree, newEntries, oldEntries, true);

        return new CommandResult(
                this,
                CommandAction.UNDO,
                moved,
                null,
                null,
                moved
        );
    }

    private EditNode[] moveAll(
            EditTree tree,
            EditCommandEntry.MovementEntry[] fromEntries,
            EditCommandEntry.MovementEntry[] toEntries,
            boolean reverse) {

        EditNode[] moved = new EditNode[fromEntries.length];

        int length = fromEntries.length;
        int start = reverse ? length - 1 : 0;
        int end = reverse ? -1 : length;
        int step = reverse ? -1 : 1;

        for (int i = start; i != end; i += step) {
            EditCommandEntry.MovementEntry from = fromEntries[i];
            EditCommandEntry.MovementEntry to = toEntries[i];

            EditNode node = tree.findNodeById(from.nodeId);
            if (node == null) {
                throw new IllegalStateException("Cannot move node with id " + from.nodeId + ": node not found");
            }

            int effectiveTargetIndex = resolveEffectiveTargetIndex(
                    tree,
                    node,
                    from.parentEditId, // statische Quelle
                    to.parentEditId,
                    to.index
            );
            tree.moveNode(node.getEditId(), to.parentEditId, effectiveTargetIndex);
            moved[i] = node;
        }

        return moved;
    }

    /**
     * Resolves the effective target index against the current tree state.
     *
     * Rules: - target index < 0 => append - different target parent => use
     * clamped target index - same parent (based on original parent): if node
     * originally lay before the requested target index, decrement by one
     * because removing the node shifts the list left
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

        // Nur dann korrigieren, wenn Quelle und Ziel *ursprünglich* identisch waren
        if (fromParentId == targetParentId) {
            EditNode originalParent = tree.findNodeById(fromParentId);
            if (originalParent != null) {
                int currentIndex = originalParent.getChildIndex(node);
                if (currentIndex >= 0 && currentIndex < clampedIndex) {
                    clampedIndex--;
                }
            }
        }

        return clampedIndex;
    }

    public EditCommandEntry.MovementEntry[] getOldEntries() {
        return Arrays.copyOf(oldEntries, oldEntries.length);
    }

    public EditCommandEntry.MovementEntry[] getNewEntries() {
        return Arrays.copyOf(newEntries, newEntries.length);
    }

    public EditCommandEntry.MovementEntry getOldEntry() {
        return oldEntries[0];
    }

    public EditCommandEntry.MovementEntry getNewEntry() {
        return newEntries[0];
    }

    public long getOldParentId() {
        return oldEntries[0].parentEditId;
    }

    public int getOldIndex() {
        return oldEntries[0].index;
    }

    public long getNewParentId() {
        return newEntries[0].parentEditId;
    }

    public int getNewIndex() {
        return newEntries[0].index;
    }

    private static long requireParentId(EditNode node) {
        if (node == null || node.getParent() == null) {
            throw new IllegalArgumentException("Node must have a parent");
        }
        return node.getParent().getEditId();
    }

    private static EditCommandEntry.MovementEntry[] copyAndValidate(
            EditCommandEntry.MovementEntry[] entries,
            boolean requireSourceIndex) {

        EditCommandEntry.MovementEntry[] copy = new EditCommandEntry.MovementEntry[entries.length];

        for (int i = 0; i < entries.length; i++) {
            EditCommandEntry.MovementEntry entry = entries[i];
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
                    throw new IllegalArgumentException("Source entry index at index " + i + " is invalid");
                }
            } else {
                if (entry.index < -1) {
                    throw new IllegalArgumentException("Target entry index at index " + i + " is invalid");
                }
            }

            copy[i] = new EditCommandEntry.MovementEntry(
                    entry.nodeId,
                    entry.parentEditId,
                    entry.index,
                    entry.snapshot
            );
        }

        return copy;
    }
}
