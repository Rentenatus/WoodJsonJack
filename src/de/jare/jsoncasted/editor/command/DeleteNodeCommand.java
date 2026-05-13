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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Command that deletes node(s) from the tree. When executed, the node(s) are
 * removed from their parent(s). When undone, the node(s) are restored from deep
 * copy snapshots.
 *
 * Snapshots preserve the original node IDs to ensure undo/redo works correctly.
 *
 * For multi-selection, descendant nodes are ignored if one of their ancestors
 * is already part of the delete set. This prevents duplicate restoration on
 * undo.
 */
public class DeleteNodeCommand extends AbstractEditCommand {

    private final MovementEntry[] entries;

    /**
     * Creates a new delete node command for a single node.
     *
     * @param node the node to delete
     */
    public DeleteNodeCommand(EditNode node) {
        this(toEntries(new EditNode[]{requireNode(node, 0)}));
    }

    /**
     * Creates a new delete node command for multiple nodes.
     *
     * @param nodes the nodes to delete
     */
    public DeleteNodeCommand(EditNode[] nodes) {
        this(toEntries(nodes));
    }

    /**
     * Creates a delete command from entries array.
     *
     * @param entries array of entries to delete
     */
    public DeleteNodeCommand(MovementEntry[] entries) {
        super(CommandType.DELETE_NODE);
        if (entries == null || entries.length == 0) {
            throw new IllegalArgumentException("Entries cannot be null or empty");
        }
        this.entries = copyAndValidate(entries);

        if (this.entries.length == 1) {
            setDescription("Delete node: " + this.entries[0].snapshot.getName());
        } else {
            setDescription("Delete " + this.entries.length + " nodes");
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
            long nodeId = resolveNodeId(tree, entry);

            EditNode node = tree.findNodeById(nodeId);
            if (node == null) {
                return CommandAvailability.disallowed(
                        "editor.command.delete.nodeMissing",
                        Long.toString(nodeId),
                        Integer.toString(i));
            }

            if (node == tree.getRoot()) {
                return CommandAvailability.disallowed(
                        "editor.command.delete.rootNotAllowed",
                        Long.toString(nodeId));
            }

            EditNode parent = node.getParent();
            if (parent == null) {
                return CommandAvailability.disallowed(
                        "editor.command.delete.parentMissing",
                        Long.toString(nodeId),
                        Integer.toString(i));
            }

            int currentIndex = parent.getChildIndex(node);
            if (currentIndex < 0) {
                return CommandAvailability.disallowed(
                        "editor.command.delete.nodeNotChildOfParent",
                        Long.toString(nodeId),
                        Long.toString(parent.getEditId()),
                        Integer.toString(i));
            }
        }

        return CommandAvailability.allowed("editor.command.delete.allowed");
    }

    @Override
    public CommandResult execute(EditTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }

        MovementEntry[] deleteOrder = Arrays.copyOf(entries, entries.length);
        Arrays.sort(deleteOrder, Comparator
                .comparingLong((MovementEntry e)
                        -> depthOf(tree.findNodeById(resolveNodeId(tree, e)), tree))
                .reversed()
                .thenComparingInt((MovementEntry e) -> e.index)
                .reversed());

        EditNode[] removed = new EditNode[deleteOrder.length];

        int idx = 0;
        for (MovementEntry entry : deleteOrder) {
            long id = resolveNodeId(tree, entry);
            EditNode node = tree.findNodeById(id);
            if (node != null) {
                tree.removeNode(node.getEditId());
                removed[idx++] = node;
            }
        }

        if (idx < removed.length) {
            removed = Arrays.copyOf(removed, idx);
        }

        return new CommandResult(
                this,
                CommandAction.EXECUTE,
                removed,
                null,
                removed,
                null
        );
    }

    @Override
    public CommandResult undo(EditTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }

        MovementEntry[] restoreOrder = Arrays.copyOf(entries, entries.length);
        Arrays.sort(restoreOrder, Comparator
                .comparingInt((MovementEntry e) -> ancestorDepth(e.snapshot))
                .thenComparingLong(e -> e.parentEditId)
                .thenComparingInt(e -> e.index));

        EditNode[] restored = new EditNode[restoreOrder.length];

        int idx = 0;
        for (MovementEntry entry : restoreOrder) {
            EditNode parent = tree.findNodeById(entry.parentEditId);
            if (parent == null) {
                throw new IllegalStateException(
                        "Cannot undo delete: parent node with id " + entry.parentEditId + " not found");
            }

            EditNode restoredNode = entry.snapshot.deepCopy(false);
            tree.addNode(entry.parentEditId, restoredNode, entry.index);
            restored[idx++] = restoredNode;
        }

        if (idx < restored.length) {
            restored = Arrays.copyOf(restored, idx);
        }

        return new CommandResult(
                this,
                CommandAction.UNDO,
                restored,
                restored,
                null,
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
     * Returns the first entry.
     *
     * @return the first movement entry
     */
    public MovementEntry getEntry() {
        return entries[0];
    }

    /**
     * Returns the snapshot of the first entry.
     *
     * @return the node snapshot
     */
    public EditNode getSnapshot() {
        return entries[0].snapshot;
    }

    /**
     * Returns the node ID of the first entry.
     *
     * @return the node edit ID, or -1 if snapshot is null
     */
    public long getNodeId() {
        return entries[0].snapshot != null ? entries[0].snapshot.getEditId() : -1;
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
     * Returns the index of the first entry.
     *
     * @return the child index
     */
    public int getIndex() {
        return entries[0].index;
    }

    private static MovementEntry[] toEntries(EditNode[] nodes) {
        if (nodes == null || nodes.length == 0) {
            throw new IllegalArgumentException("Nodes cannot be null or empty");
        }

        EditNode[] validated = new EditNode[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            validated[i] = requireNode(nodes[i], i);
        }

        List<EditNode> normalized = normalizeNodes(validated);
        MovementEntry[] result = new MovementEntry[normalized.size()];

        for (int i = 0; i < normalized.size(); i++) {
            EditNode node = normalized.get(i);
            EditNode parent = node.getParent();
            if (parent == null) {
                throw new IllegalArgumentException("Node '" + node.getName() + "' has no parent and cannot be deleted");
            }

            int index = parent.getChildIndex(node);
            if (index < 0) {
                throw new IllegalArgumentException(
                        "Node '" + node.getName() + "' is not a child of its parent");
            }

            result[i] = new MovementEntry(
                    node.getEditId(), // nodeId
                    parent.getEditId(), // parentEditId
                    index,
                    node.deepCopy(false) // snapshot
            );
        }

        return result;
    }

    private static EditNode requireNode(EditNode node, int index) {
        if (node == null) {
            throw new IllegalArgumentException("Node at index " + index + " cannot be null");
        }
        return node;
    }

    private static MovementEntry[] copyAndValidate(MovementEntry[] entries) {
        MovementEntry[] copy = Arrays.copyOf(entries, entries.length);

        for (int i = 0; i < copy.length; i++) {
            MovementEntry entry = copy[i];
            if (entry == null) {
                throw new IllegalArgumentException("Entry at index " + i + " cannot be null");
            }
            if (entry.snapshot == null) {
                throw new IllegalArgumentException("Entry snapshot at index " + i + " cannot be null");
            }
            if (entry.parentEditId < 0) {
                throw new IllegalArgumentException("Entry parentEditId at index " + i + " is invalid");
            }
            if (entry.index < 0) {
                throw new IllegalArgumentException("Entry index at index " + i + " is invalid");
            }
        }

        return copy;
    }

    private static List<EditNode> normalizeNodes(EditNode[] nodes) {
        Map<Long, EditNode> unique = new LinkedHashMap<>();
        for (EditNode node : nodes) {
            unique.put(node.getEditId(), node);
        }

        List<EditNode> result = new ArrayList<>();
        for (EditNode node : unique.values()) {
            if (!hasSelectedAncestor(node, unique)) {
                result.add(node);
            }
        }

        result.sort(Comparator
                .comparingLong((EditNode n) -> n.getParent().getEditId())
                .thenComparingInt(n -> n.getParent().getChildIndex(n)));

        return result;
    }

    private static boolean hasSelectedAncestor(EditNode node, Map<Long, EditNode> selectedNodes) {
        EditNode parent = node.getParent();
        while (parent != null) {
            if (selectedNodes.containsKey(parent.getEditId())) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private static int ancestorDepth(EditNode node) {
        int depth = 0;
        EditNode current = node;
        while (current != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }

    private static int depthOf(EditNode node, EditTree tree) {
        if (node == null) {
            return Integer.MAX_VALUE;
        }

        int depth = 0;
        EditNode current = node;
        while (current != null && current != tree.getRoot()) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }

    private static long resolveNodeId(EditTree tree, MovementEntry entry) {
        if (entry.nodeId >= 0) {
            return entry.nodeId;
        }
        return entry.snapshot.getEditId();
    }
}
