/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.command;

import de.jare.jsoncasted.editor.command.EditCommand.CommandType;
import de.jare.jsoncasted.editor.command.EditCommandEntry.AttributeEntry;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Command that sets attributes of node(s) in the tree. When executed, the
 * node(s) attributes are updated. When undone, the previous attribute values
 * are restored.
 */
public class SetAttributeCommand extends AbstractEditCommand {

    private final AttributeEntry[] entries;

    /**
     * Creates a command to set attributes of a single node.
     *
     * @param node the node whose attributes will be set
     * @param newAttributes the new attributes to set
     */
    public SetAttributeCommand(EditNode node, Map<String, Object> newAttributes) {
        super(CommandType.SET_ATTRIBUTE);
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (newAttributes == null || newAttributes.isEmpty()) {
            throw new IllegalArgumentException("Attributes cannot be null or empty");
        }

        // Get current attributes and extract only the keys that are being modified
        Map<String, Object> currentAttributes = node.getAttributes();
        Map<String, Object> oldAttributes = new HashMap<>();
        for (String key : newAttributes.keySet()) {
            if (currentAttributes.containsKey(key)) {
                oldAttributes.put(key, currentAttributes.get(key));
            }
        }

        this.entries = new AttributeEntry[]{
            new AttributeEntry(node, oldAttributes, newAttributes)
        };
        setDescription("Set attributes for node " + node.getEditId());
    }

    /**
     * Creates a command to set attributes for multiple nodes.
     *
     * @param nodes the nodes to update
     * @param newAttributes the new attributes for each node
     */
    public SetAttributeCommand(EditNode[] nodes, Map<String, Object>[] newAttributes) {
        super(CommandType.SET_ATTRIBUTE);
        if (nodes == null || newAttributes == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        if (nodes.length != newAttributes.length) {
            throw new IllegalArgumentException("Arrays must have the same length");
        }
        if (nodes.length == 0) {
            throw new IllegalArgumentException("Arrays cannot be empty");
        }

        this.entries = new AttributeEntry[nodes.length];

        for (int i = 0; i < nodes.length; i++) {
            EditNode node = nodes[i];
            Map<String, Object> attrs = newAttributes[i];
            if (node == null) {
                throw new IllegalArgumentException("Node at index " + i + " cannot be null");
            }
            if (attrs == null || attrs.isEmpty()) {
                throw new IllegalArgumentException("Attributes at index " + i + " cannot be null or empty");
            }

            // Get current attributes and extract only the keys that are being modified
            Map<String, Object> currentAttributes = node.getAttributes();
            Map<String, Object> oldAttributes = new HashMap<>();
            for (String key : attrs.keySet()) {
                if (currentAttributes.containsKey(key)) {
                    oldAttributes.put(key, currentAttributes.get(key));
                }
            }

            this.entries[i] = new AttributeEntry(node, oldAttributes, attrs);
        }

        if (nodes.length == 1) {
            setDescription("Set attributes for node " + nodes[0].getEditId());
        } else {
            setDescription("Set attributes for " + nodes.length + " nodes");
        }
    }

    /**
     * Creates a command from attribute entries array.
     *
     * @param entries array of attribute entries
     */
    public SetAttributeCommand(AttributeEntry[] entries) {
        super(CommandType.SET_ATTRIBUTE);
        if (entries == null || entries.length == 0) {
            throw new IllegalArgumentException("Entries cannot be null or empty");
        }

        this.entries = copyAndValidate(entries);

        if (this.entries.length == 1) {
            setDescription("Set attributes for node " + this.entries[0].nodeId);
        } else {
            setDescription("Set attributes for " + this.entries.length + " nodes");
        }
    }

    @Override
    public CommandAvailability check(EditTree tree) {
        if (tree == null) {
            return CommandAvailability.disallowed(
                    "editor.command.tree.missing");
        }

        for (int i = 0; i < entries.length; i++) {
            AttributeEntry entry = entries[i];

            EditNode node = tree.findNodeByIdAndRange(entry);
            if (node == null) {
                return CommandAvailability.disallowed(
                        "editor.command.setAttribute.nodeMissing",
                        Long.toString(entry.nodeId),
                        Integer.toString(i));
            }
        }

        return CommandAvailability.allowed(
                "editor.command.setAttribute.allowed");
    }

    @Override
    protected CommandResult doExecute(EditTree tree) {
        EditNodeAbstract[] updated = new EditNodeAbstract[entries.length];

        for (int i = 0; i < entries.length; i++) {
            AttributeEntry entry = entries[i];
            EditNodeAbstract node = tree.findNodeByIdAndRange(entry);
            if (node == null) {
                throw new IllegalStateException(
                        "Cannot set attributes: node with id " + entry.nodeId + " not found");
            }

            // Set the new attributes
            node.setAttributes(entry.newAttributes);
            updated[i] = node;
        }

        return new CommandResult(
                this,
                CommandAction.EXECUTE,
                updated,
                null,
                null,
                updated,
                null,
                NO_UPDATE_ACTIONS
        );
    }

    @Override
    public CommandResult doUndo(EditTree tree) {

        EditNodeAbstract[] updated = new EditNodeAbstract[entries.length];

        for (int i = 0; i < entries.length; i++) {
            AttributeEntry entry = entries[i];
            EditNodeAbstract node = tree.findNodeByIdAndRange(entry);
            if (node == null) {
                throw new IllegalStateException(
                        "Cannot undo set attributes: node with id " + entry.nodeId + " not found");
            }

            // Restore the old attributes (only the ones that were modified)
            node.setAttributes(entry.oldAttributes);
            updated[i] = node;
        }

        return new CommandResult(
                this,
                CommandAction.UNDO,
                updated,
                null,
                null,
                updated,
                null,
                NO_UPDATE_ACTIONS
        );
    }

    /**
     * Returns a defensive copy of the entries array.
     *
     * @return a copy of the entries array
     */
    public AttributeEntry[] getEntries() {
        return Arrays.copyOf(entries, entries.length);
    }

    /**
     * Returns the node IDs of all entries.
     *
     * @return array of node IDs
     */
    public long[] getNodeIds() {
        long[] ids = new long[entries.length];
        for (int i = 0; i < entries.length; i++) {
            ids[i] = entries[i].nodeId;
        }
        return ids;
    }

    /**
     * Returns the old attributes of all entries.
     *
     * @return array of old attribute maps
     */
    public Map<String, Object>[] getOldAttributes() {
        Map<String, Object>[] attrs = new Map[entries.length];
        for (int i = 0; i < entries.length; i++) {
            attrs[i] = entries[i].oldAttributes;
        }
        return attrs;
    }

    /**
     * Returns the new attributes of all entries.
     *
     * @return array of new attribute maps
     */
    public Map<String, Object>[] getNewAttributes() {
        Map<String, Object>[] attrs = new Map[entries.length];
        for (int i = 0; i < entries.length; i++) {
            attrs[i] = entries[i].newAttributes;
        }
        return attrs;
    }

    private static AttributeEntry[] copyAndValidate(AttributeEntry[] entries) {
        AttributeEntry[] copy = new AttributeEntry[entries.length];

        for (int i = 0; i < entries.length; i++) {
            AttributeEntry entry = entries[i];
            if (entry == null) {
                throw new IllegalArgumentException("Entry at index " + i + " cannot be null");
            }
            if (entry.nodeId < 0) {
                throw new IllegalArgumentException("Entry nodeId at index " + i + " is invalid");
            }
            if (entry.newAttributes == null || entry.newAttributes.isEmpty()) {
                throw new IllegalArgumentException("Entry newAttributes at index " + i + " cannot be null or empty");
            }

            copy[i] = new AttributeEntry(
                    entry.nodeId,
                    entry.leftRange,
                    entry.timesRange,
                    entry.oldAttributes,
                    entry.newAttributes
            );
        }
        return copy;
    }
}
