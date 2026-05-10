/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor;

import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.events.EditEvent;
import de.jare.jsoncasted.editor.events.EventBus;
import de.jare.jsoncasted.editor.history.HistoryManager;
import de.jare.jsoncasted.lang.JsonNode;
import de.jare.jsoncasted.lang.JsonNodeType;
import de.jare.jsoncasted.lang.JsonResource;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Main facade for the headless JSON tree editor core.
 *
 * <p>
 * This class combines the editable tree model, command history, and event
 * dispatching behind a single API. It is the primary entry point for loading,
 * modifying, exporting, and inspecting editor state.</p>
 */
public class TreeEditor {

    private final EditTree tree;
    private final HistoryManager historyManager;
    private final EventBus eventBus;

    /**
     * Creates a new editor with a default object root named {@code root}.
     */
    public TreeEditor() {
        this(new EditNodeObject("root"));
    }

    /**
     * Creates a new editor for the given root node.
     *
     * @param root the root node of the editable tree
     * @throws IllegalArgumentException if {@code root} is {@code null}
     */
    public TreeEditor(EditNode root) {
        this(root, new EventBus());
    }

    /**
     * Creates a new editor for the given root node and event bus.
     *
     * @param root the root node of the editable tree
     * @param eventBus the event bus to use, or {@code null} to create a default
     * one
     * @throws IllegalArgumentException if {@code root} is {@code null}
     */
    public TreeEditor(EditNode root, EventBus eventBus) {
        if (root == null) {
            throw new IllegalArgumentException("Root node cannot be null");
        }
        this.eventBus = eventBus != null ? eventBus : new EventBus();
        this.tree = new EditTree(root);
        this.historyManager = new HistoryManager(tree, this.eventBus);
    }

    // -------------------------------------------------------------------------
    // Core accessors
    // -------------------------------------------------------------------------
    /**
     * Returns the editable tree managed by this editor.
     *
     * @return the tree instance
     */
    public EditTree getTree() {
        return tree;
    }

    /**
     * Returns the history manager used for undo/redo operations.
     *
     * @return the history manager
     */
    public HistoryManager getHistoryManager() {
        return historyManager;
    }

    /**
     * Returns the event bus used by this editor.
     *
     * @return the event bus
     */
    public EventBus getEventBus() {
        return eventBus;
    }

    // -------------------------------------------------------------------------
    // Command execution / history facade
    // -------------------------------------------------------------------------
    /**
     * Executes the given command and records it in the undo history.
     *
     * @param command the command to execute
     * @return the command result, or {@code null} if nothing was executed
     */
    public CommandResult execute(EditCommand command) {
        return historyManager.execute(command);
    }

    /**
     * Undoes the most recently executed command.
     *
     * @return the undo result, or {@code null} if no undo is available
     */
    public CommandResult undo() {
        return historyManager.undo();
    }

    /**
     * Redoes the most recently undone command.
     *
     * @return the redo result, or {@code null} if no redo is available
     */
    public CommandResult redo() {
        return historyManager.redo();
    }

    /**
     * Skips the current redo command without executing it and moves it back to
     * the undo stack.
     *
     * @return the skipped command, or {@code null} if no redo is available
     */
    public EditCommand skipRedo() {
        return historyManager.skipRedo();
    }

    /**
     * Returns whether an undo operation is currently available.
     *
     * @return {@code true} if undo is possible
     */
    public boolean canUndo() {
        return historyManager.canUndo();
    }

    /**
     * Returns whether a redo operation is currently available.
     *
     * @return {@code true} if redo is possible
     */
    public boolean canRedo() {
        return historyManager.canRedo();
    }

    /**
     * Clears the complete undo/redo history.
     */
    public void clearHistory() {
        historyManager.clear();
    }

    // -------------------------------------------------------------------------
    // Event handling
    // -------------------------------------------------------------------------
    /**
     * Registers a listener for a specific event type.
     *
     * @param <T> the event type
     * @param eventType the event class to listen for
     * @param listener the listener to register
     */
    public <T> void addListener(Class<T> eventType, Consumer<T> listener) {
        eventBus.addListener(eventType, listener);
    }

    /**
     * Registers a listener for the base {@link EditEvent} type.
     *
     * <p>
     * This listener receives all editor events published through the event
     * bus.</p>
     *
     * @param listener the listener to register
     */
    public void addListener(Consumer<EditEvent> listener) {
        eventBus.addListener(EditEvent.class, listener);
    }

    /**
     * Removes a listener for a specific event type.
     *
     * @param <T> the event type
     * @param eventType the event class
     * @param listener the listener to remove
     * @return {@code true} if the listener was removed
     */
    public <T> boolean removeListener(Class<T> eventType, Consumer<T> listener) {
        return eventBus.removeListener(eventType, listener);
    }

    /**
     * Fires an event through the editor's event bus.
     *
     * @param <T> the event type
     * @param event the event to publish
     */
    public <T> void fireEvent(T event) {
        eventBus.fireEvent(event);
    }

    /**
     * Removes all listeners from the event bus.
     */
    public void clearListeners() {
        eventBus.clear();
    }

    // -------------------------------------------------------------------------
    // Import from JsonResource / JsonNode
    // -------------------------------------------------------------------------
    /**
     * Converts a {@link JsonResource} into an editable tree root node.
     *
     * <p>
     * This method does not modify an existing editor instance. It only performs
     * the conversion.</p>
     *
     * @param resource the resource to import
     * @return the converted root node
     * @throws IllegalArgumentException if {@code resource} is {@code null} or
     * has no root
     */
    public static EditNode importFromJsonResource(JsonResource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("Resource cannot be null");
        }
        JsonNode rootNode = resource.getRoot();
        if (rootNode == null) {
            throw new IllegalArgumentException("Resource has no root node");
        }
        return convertJsonNodeToEditNode(rootNode, null);
    }

    /**
     * Converts a {@link JsonNode} into an editable tree root node.
     *
     * @param node the JSON node to import
     * @return the converted root node
     * @throws IllegalArgumentException if {@code node} is {@code null}
     */
    public static EditNode importFromJsonNode(JsonNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        return convertJsonNodeToEditNode(node, null);
    }

    /**
     * Creates a new editor from the given {@link JsonResource}.
     *
     * @param resource the resource to load
     * @return a new editor containing the imported content
     */
    public static TreeEditor fromJsonResource(JsonResource resource) {
        EditNode root = importFromJsonNode(resource.getRoot());
        return new TreeEditor(root);
    }

    /**
     * Creates a new editor from the given {@link JsonNode}.
     *
     * @param node the node to load
     * @return a new editor containing the imported content
     */
    public static TreeEditor fromJsonNode(JsonNode node) {
        EditNode root = importFromJsonNode(node);
        return new TreeEditor(root);
    }

    /**
     * Recursively converts a JSON node into the corresponding editable node
     * model.
     *
     * @param jsonNode the source JSON node
     * @param propertyName the property name for object members, or {@code null}
     * for root/array values
     * @return the converted editable node
     */
    private static EditNode convertJsonNodeToEditNode(JsonNode jsonNode, String propertyName) {
        JsonNodeType type = jsonNode.getType();

        if (type == JsonNodeType.OBJECT) {
            EditNodeObject editNode = new EditNodeObject(propertyName != null ? propertyName : "");
            Map<String, JsonNode> objectValues = jsonNode.asObjectValues();
            if (objectValues != null) {
                for (Map.Entry<String, JsonNode> entry : objectValues.entrySet()) {
                    EditNodeProperty prop = new EditNodeProperty(entry.getKey());
                    EditNode valueNode = convertJsonNodeToEditNode(entry.getValue(), null);
                    prop.addChild(valueNode);
                    editNode.addChild(prop);
                }
            }
            return editNode;
        } else if (type == JsonNodeType.ARRAY) {
            EditNodeObject editNode = new EditNodeObject("__array__");
            List<JsonNode> arrayValues = jsonNode.asArray();
            if (arrayValues != null) {
                for (JsonNode value : arrayValues) {
                    EditNode child = convertJsonNodeToEditNode(value, null);
                    editNode.addChild(child);
                }
            }
            return editNode;
        } else {
            EditNodeProperty editNode = new EditNodeProperty(propertyName != null ? propertyName : "");
            String value = convertJsonValueToString(jsonNode);
            editNode.setPrimValue(value);
            editNode.setType(type.name().toLowerCase());
            return editNode;
        }
    }

    /**
     * Converts a primitive JSON node value to its string representation.
     *
     * @param jsonNode the primitive JSON node
     * @return the string value, or {@code null} for JSON null
     */
    private static String convertJsonValueToString(JsonNode jsonNode) {
        if (jsonNode == null) {
            return null;
        }

        JsonNodeType type = jsonNode.getType();
        switch (type) {
            case STRING:
                return jsonNode.asText();
            case NUMBER:
                return String.valueOf(jsonNode.asNumber());
            case LONG:
                return String.valueOf(jsonNode.asLong());
            case BOOLEAN:
                return String.valueOf(jsonNode.asBoolean());
            case NULL:
                return null;
            default:
                return null;
        }
    }

    // -------------------------------------------------------------------------
    // Export to JsonResource / JsonNode
    // -------------------------------------------------------------------------
    /**
     * Exports the current editor tree to a {@link JsonResource}.
     *
     * @return a new resource containing the exported root node
     */
    public JsonResource exportToJsonResource() {
        EditNode root = tree.getRoot();
        JsonNode jsonRoot = convertEditNodeToJsonNode(root);
        return JsonResource.forRoot(jsonRoot);
    }

    /**
     * Exports the current editor tree to a {@link JsonNode}.
     *
     * @return the exported root node
     */
    public JsonNode exportToJsonNode() {
        EditNode root = tree.getRoot();
        return convertEditNodeToJsonNode(root);
    }

    /**
     * Recursively converts an editable node into a JSON node.
     *
     * @param editNode the editable node to convert
     * @return the converted JSON node
     */
    private JsonNode convertEditNodeToJsonNode(EditNode editNode) {
        if (editNode instanceof EditNodeObject) {
            EditNodeObject obj = (EditNodeObject) editNode;
            String name = obj.getName();

            if ("__array__".equals(name)) {
                JsonNode arrayNode = JsonNode.arrayNode();
                for (int i = 0; i < obj.getChildCount(); i++) {
                    JsonNode jsonChild = convertEditNodeToJsonNode(obj.getChildAt(i));
                    if (jsonChild != null) {
                        arrayNode.add(jsonChild);
                    }
                }
                return arrayNode;
            }

            JsonNode objectNode = JsonNode.objectNode();
            for (int i = 0; i < obj.getChildCount(); i++) {
                EditNode child = obj.getChildAt(i);
                if (child instanceof EditNodeProperty) {
                    EditNodeProperty prop = (EditNodeProperty) child;
                    String key = prop.getPropName();
                    if (key == null || key.isEmpty()) {
                        key = String.valueOf(i);
                    }

                    JsonNode value;
                    if (prop.getChildCount() > 0) {
                        value = convertEditNodeToJsonNode(prop.getChildAt(0));
                    } else {
                        value = convertEditNodePropertyToJsonNode(prop);
                    }

                    if (value != null) {
                        objectNode.put(key, value);
                    }
                } else if (child instanceof EditNodeObject) {
                    String key = child.getName();
                    if (key == null || key.isEmpty()) {
                        key = String.valueOf(i);
                    }
                    JsonNode value = convertEditNodeToJsonNode(child);
                    if (value != null) {
                        objectNode.put(key, value);
                    }
                }
            }
            return objectNode;
        } else if (editNode instanceof EditNodeProperty) {
            EditNodeProperty prop = (EditNodeProperty) editNode;
            if (prop.getChildCount() > 0) {
                return convertEditNodeToJsonNode(prop.getChildAt(0));
            }
            return convertEditNodePropertyToJsonNode(prop);
        } else {
            return JsonNode.nullNode();
        }
    }

    /**
     * Converts a primitive {@link EditNodeProperty} into a JSON primitive node.
     *
     * @param prop the property node to convert
     * @return the converted JSON node
     */
    private JsonNode convertEditNodePropertyToJsonNode(EditNodeProperty prop) {
        String primValue = prop.getPrimValue();
        String type = prop.getType();

        if (primValue == null) {
            return JsonNode.nullNode();
        }

        if (type != null) {
            switch (type.toLowerCase()) {
                case "string":
                    return JsonNode.stringNode(primValue);
                case "number":
                    try {
                        return JsonNode.numberNode(Double.parseDouble(primValue));
                    } catch (NumberFormatException e) {
                        return JsonNode.stringNode(primValue);
                    }
                case "long":
                    try {
                        return JsonNode.longNode(Long.parseLong(primValue));
                    } catch (NumberFormatException e) {
                        return JsonNode.stringNode(primValue);
                    }
                case "boolean":
                    return JsonNode.booleanNode(Boolean.parseBoolean(primValue));
                case "null":
                    return JsonNode.nullNode();
            }
        }

        return JsonNode.varNode(primValue);
    }

    // -------------------------------------------------------------------------
    // Debug / inspection helpers
    // -------------------------------------------------------------------------
    /**
     * Returns a compact debug representation of the editor state.
     *
     * @return a one-line debug string
     */
    @Override
    public String toString() {
        return toDebugString();
    }

    /**
     * Returns a compact debug representation of the editor, including tree,
     * history, listener count, and validation state.
     *
     * @return a one-line debug string
     */
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TreeEditor{")
                .append("tree=").append(tree != null ? tree.toString() : "null")
                .append(", history=").append(historyManager != null ? historyManager.toString() : "null")
                .append(", listeners=").append(eventBus != null ? eventBus.getListenerCount() : 0);

        if (tree != null) {
            sb.append(", valid=").append(tree.validate());
        }

        sb.append('}');
        return sb.toString();
    }

    /**
     * Returns a readable representation of the undo and redo stacks.
     *
     * @return the formatted history output
     */
    public String toHistoryString() {
        if (historyManager == null) {
            return "<no history>";
        }

        StringBuilder sb = new StringBuilder();

        sb.append("Undo[").append(historyManager.getUndoSize()).append("]:\n");
        for (EditCommand cmd : historyManager.getUndoCommands()) {
            sb.append("  - ").append(formatCommand(cmd)).append('\n');
        }

        sb.append("Redo[").append(historyManager.getRedoSize()).append("]:\n");
        for (EditCommand cmd : historyManager.getRedoCommands()) {
            sb.append("  - ").append(formatCommand(cmd)).append('\n');
        }

        return sb.toString();
    }

    /**
     * Returns a formatted representation of the complete tree.
     *
     * @return the formatted tree output
     */
    public String toTreeString() {
        if (tree == null || tree.getRoot() == null) {
            return "<empty tree>";
        }
        return toTreeString(tree.getRoot());
    }

    /**
     * Returns a formatted representation of the subtree starting at the node
     * with the given ID.
     *
     * @param startNodeId the start node ID
     * @return the formatted subtree output, or a not-found marker
     */
    public String toTreeString(long startNodeId) {
        if (tree == null) {
            return "<empty tree>";
        }

        EditNode startNode = tree.findNodeById(startNodeId);
        if (startNode == null) {
            return "<node not found: " + startNodeId + ">";
        }

        return toTreeString(startNode);
    }

    /**
     * Returns a formatted representation of the subtree starting at the given
     * node.
     *
     * @param startNode the subtree root
     * @return the formatted subtree output
     */
    public String toTreeString(EditNode startNode) {
        if (startNode == null) {
            return "<null node>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Subtree from ").append(formatNodeHeader(startNode)).append('\n');
        appendNode(sb, startNode, 0, getIndexInParent(startNode));
        return sb.toString();
    }

    /**
     * Appends a formatted subtree representation to the given string builder.
     *
     * @param sb the target builder
     * @param node the current node
     * @param depth the current depth
     * @param indexInParent the index within the parent, or {@code -1} for root
     */
    private void appendNode(StringBuilder sb, EditNode node, int depth, int indexInParent) {
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }

        sb.append(indexInParent >= 0 ? "[" + indexInParent + "] " : "")
                .append(formatNodeHeader(node))
                .append('\n');

        for (int i = 0; i < node.getChildCount(); i++) {
            appendNode(sb, node.getChildAt(i), depth + 1, i);
        }
    }

    /**
     * Formats a single node for debug output.
     *
     * @param node the node to format
     * @return the formatted node header
     */
    private String formatNodeHeader(EditNode node) {
        EditNode parent = node.getParent();
        long parentId = parent != null ? parent.getEditId() : -1;

        StringBuilder sb = new StringBuilder();
        sb.append(node.getName())
                .append(" {id=").append(node.getEditId())
                .append(", parentId=").append(parentId);

        try {
            String text = node.getEditText();
            if (text != null) {
                sb.append(", text='").append(text).append('\'');
            }
        } catch (Exception ignore) {
            // Some node types might not support getEditText()
        }

        sb.append('}');
        return sb.toString();
    }

    /**
     * Returns the index of the given node within its parent.
     *
     * @param node the node to inspect
     * @return the child index, or {@code -1} if the node has no parent
     */
    private int getIndexInParent(EditNode node) {
        if (node == null || node.getParent() == null) {
            return -1;
        }
        return node.getParent().getChildIndex(node);
    }

    /**
     * Formats a command for history output.
     *
     * @param cmd the command to format
     * @return the formatted command string
     */
    private String formatCommand(EditCommand cmd) {
        if (cmd == null) {
            return "null";
        }
        return cmd.getClass().getSimpleName() + "[" + cmd.toString() + "]";
    }
}
