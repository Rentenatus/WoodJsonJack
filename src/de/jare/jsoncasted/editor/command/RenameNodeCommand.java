/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.command.EditCommandEntry.ContentEntry;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;
import java.util.Arrays;

public class RenameNodeCommand extends AbstractEditCommand {

    private final ContentEntry[] entries;

    /**
     * Creates a command to rename a single node.
     *
     * @param node the node to rename
     * @param newName the new name
     */
    public RenameNodeCommand(EditNode node, String newName) {
        super(CommandType.RENAME_NODE);
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }

        String oldName = node.getName();
        this.entries = new ContentEntry[]{
            new ContentEntry(node.getEditId(), oldName, newName)
        };

        setDescription("Rename node: " + text(oldName) + " -> " + text(newName));
    }

    /**
     * Creates a command to rename multiple nodes.
     *
     * @param nodes the nodes to rename
     * @param newNames the new names
     */
    public RenameNodeCommand(EditNode[] nodes, String[] newNames) {
        super(CommandType.RENAME_NODE);
        if (nodes == null || newNames == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        if (nodes.length != newNames.length) {
            throw new IllegalArgumentException("Arrays must have the same length");
        }
        if (nodes.length == 0) {
            throw new IllegalArgumentException("Arrays cannot be empty");
        }

        this.entries = new ContentEntry[nodes.length];

        for (int i = 0; i < nodes.length; i++) {
            EditNode node = nodes[i];
            if (node == null) {
                throw new IllegalArgumentException("Node at index " + i + " cannot be null");
            }

            this.entries[i] = new ContentEntry(
                    node.getEditId(),
                    node.getName(),
                    newNames[i]
            );
        }

        if (nodes.length == 1) {
            setDescription("Rename node: " + text(entries[0].oldValue) + " -> " + text(entries[0].newValue));
        } else {
            setDescription("Rename " + nodes.length + " nodes");
        }
    }

    /**
     * Creates a command from rename entries.
     *
     * @param entries the rename entries
     */
    public RenameNodeCommand(ContentEntry[] entries) {
        super(CommandType.RENAME_NODE);
        if (entries == null || entries.length == 0) {
            throw new IllegalArgumentException("Entries cannot be null or empty");
        }

        this.entries = copyAndValidate(entries);

        if (this.entries.length == 1) {
            setDescription("Rename node: " + text(this.entries[0].oldValue) + " -> " + text(this.entries[0].newValue));
        } else {
            setDescription("Rename " + this.entries.length + " nodes");
        }
    }

    @Override
    public CommandResult execute(EditTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }

        EditNode[] updated = new EditNode[entries.length];

        for (int i = 0; i < entries.length; i++) {
            ContentEntry entry = entries[i];
            EditNode node = tree.findNodeById(entry.nodeId);
            if (node == null) {
                throw new IllegalStateException(
                        "Cannot rename node: node with id " + entry.nodeId + " not found");
            }

            node.setName(entry.newValue);
            updated[i] = node;
        }

        return new CommandResult(
                this,
                CommandAction.EXECUTE,
                updated,
                null,
                null,
                updated
        );
    }

    @Override
    public CommandResult undo(EditTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }

        EditNode[] updated = new EditNode[entries.length];

        for (int i = 0; i < entries.length; i++) {
            ContentEntry entry = entries[i];
            EditNode node = tree.findNodeById(entry.nodeId);
            if (node == null) {
                throw new IllegalStateException(
                        "Cannot undo rename: node with id " + entry.nodeId + " not found");
            }

            node.setName(entry.oldValue);
            updated[i] = node;
        }

        return new CommandResult(
                this,
                CommandAction.UNDO,
                updated,
                null,
                null,
                updated
        );
    }

    public ContentEntry[] getEntries() {
        return Arrays.copyOf(entries, entries.length);
    }

    public long[] getNodeIds() {
        long[] ids = new long[entries.length];
        for (int i = 0; i < entries.length; i++) {
            ids[i] = entries[i].nodeId;
        }
        return ids;
    }

    public String[] getOldNames() {
        String[] values = new String[entries.length];
        for (int i = 0; i < entries.length; i++) {
            values[i] = entries[i].oldValue;
        }
        return values;
    }

    public String[] getNewNames() {
        String[] values = new String[entries.length];
        for (int i = 0; i < entries.length; i++) {
            values[i] = entries[i].newValue;
        }
        return values;
    }

    public long getNodeId() {
        return entries[0].nodeId;
    }

    public String getOldName() {
        return entries[0].oldValue;
    }

    public String getNewName() {
        return entries[0].newValue;
    }

    private static ContentEntry[] copyAndValidate(ContentEntry[] entries) {
        ContentEntry[] copy = new ContentEntry[entries.length];

        for (int i = 0; i < entries.length; i++) {
            ContentEntry entry = entries[i];
            if (entry == null) {
                throw new IllegalArgumentException("Entry at index " + i + " cannot be null");
            }
            if (entry.nodeId < 0) {
                throw new IllegalArgumentException("Entry nodeId at index " + i + " is invalid");
            }

            copy[i] = new ContentEntry(
                    entry.nodeId,
                    entry.oldValue,
                    entry.newValue
            );
        }

        return copy;
    }

    private static String text(String value) {
        return value != null ? value : "null";
    }

}
