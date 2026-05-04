/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor;

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
 * Main facade class for the headless JSON tree editor core.
 * This class provides a unified interface to the editor's functionality,
 * including tree manipulation, undo/redo history, and event handling.
 * 
 * <p>This is the primary entry point for using the editor core. It coordinates
 * the tree structure, command history, and event distribution.</p>
 * 
 * <p>Usage example:</p>
 * <pre>
 * TreeEditor editor = new TreeEditor();
 * EditNode root = editor.getTree().getRoot();
 * 
 * // Add a node
 * EditNodeObject newNode = new EditNodeObject("newNode");
 * editor.execute(new AddNodeCommand(root.getId(), newNode));
 * 
 * // Undo
 * editor.undo();
 * 
 * // Listen for changes
 * editor.addListener(NodeChangeEvent.class, event -> {
 *     System.out.println("Node changed: " + event.getNode().getName());
 * });
 * </pre>
 */
public class TreeEditor {

    private final EditTree tree;
    private final HistoryManager historyManager;
    private final EventBus eventBus;

    /**
     * Creates a new TreeEditor with a default root node.
     * The root node is an object node named "root".
     */
    public TreeEditor() {
        this(new EditNodeObject("root"));
    }

    /**
     * Creates a new TreeEditor with the specified root node.
     * 
     * @param root the root node for the tree
     * @throws IllegalArgumentException if root is null
     */
    public TreeEditor(EditNode root) {
        if (root == null) {
            throw new IllegalArgumentException("Root node cannot be null");
        }
        this.eventBus = new EventBus();
        this.tree = new EditTree(root);
        this.historyManager = new HistoryManager(tree, eventBus);
    }

    /**
     * Creates a new TreeEditor with the specified root node and event bus.
     * 
     * @param root the root node for the tree
     * @param eventBus the event bus to use
     * @throws IllegalArgumentException if root is null
     */
    public TreeEditor(EditNode root, EventBus eventBus) {
        if (root == null) {
            throw new IllegalArgumentException("Root node cannot be null");
        }
        this.eventBus = eventBus != null ? eventBus : new EventBus();
        this.tree = new EditTree(root);
        this.historyManager = new HistoryManager(tree, this.eventBus);
    }

    /**
     * Returns the edit tree managed by this editor.
     * 
     * @return the edit tree
     */
    public EditTree getTree() {
        return tree;
    }

    /**
     * Returns the history manager for undo/redo operations.
     * 
     * @return the history manager
     */
    public HistoryManager getHistoryManager() {
        return historyManager;
    }

    /**
     * Returns the event bus for registering listeners.
     * 
     * @return the event bus
     */
    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * Executes a command on the tree.
     * The command is added to the undo history.
     * 
     * @param command the command to execute
     */
    public void execute(EditCommand command) {
        historyManager.execute(command);
    }

    /**
     * Undoes the last executed command.
     * 
     * @return the undone command, or null if nothing to undo
     */
    public EditCommand undo() {
        return historyManager.undo();
    }

    /**
     * Redoes the last undone command.
     * 
     * @return the redone command, or null if nothing to redo
     */
    public EditCommand redo() {
        return historyManager.redo();
    }

    /**
     * Returns whether an undo operation is available.
     * 
     * @return true if undo is available
     */
    public boolean canUndo() {
        return historyManager.canUndo();
    }

    /**
     * Returns whether a redo operation is available.
     * 
     * @return true if redo is available
     */
    public boolean canRedo() {
        return historyManager.canRedo();
    }

    /**
     * Clears all undo and redo history.
     */
    public void clearHistory() {
        historyManager.clear();
    }

    /**
     * Adds a listener for a specific event type.
     * 
     * @param <T> the event type
     * @param eventType the class of events to listen for
     * @param listener the consumer to be called when an event is fired
     */
    public <T> void addListener(Class<T> eventType, Consumer<T> listener) {
        eventBus.addListener(eventType, listener);
    }

    /**
     * Adds a listener for the base EditEvent type.
     * This will receive all events.
     * 
     * @param listener the consumer to be called when any event is fired
     */
    public void addListener(Consumer<EditEvent> listener) {
        eventBus.addListener(EditEvent.class, listener);
    }

    /**
     * Removes a listener for a specific event type.
     * 
     * @param <T> the event type
     * @param eventType the class of events
     * @param listener the consumer to remove
     * @return true if the listener was removed
     */
    public <T> boolean removeListener(Class<T> eventType, Consumer<T> listener) {
        return eventBus.removeListener(eventType, listener);
    }

    /**
     * Fires an event to all registered listeners.
     * 
     * @param <T> the event type
     * @param event the event to fire
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

    // ========== Import from JsonResource ==========

    /**
     * Imports a JsonResource and returns the root EditNode.
     * This does NOT modify the current editor's tree - it only converts the data.
     * Use TreeEditor.fromJsonResource() to create a new editor with the imported content.
     * 
     * @param resource the JsonResource to import
     * @return the root EditNode of the converted tree
     * @throws IllegalArgumentException if resource is null or has no root
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
     * Imports a JsonNode and returns the root EditNode.
     * 
     * @param node the JsonNode to convert
     * @return the root EditNode of the converted tree
     * @throws IllegalArgumentException if node is null
     */
    public static EditNode importFromJsonNode(JsonNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        return convertJsonNodeToEditNode(node, null);
    }

    /**
     * Creates a new TreeEditor with the content from a JsonResource.
     * 
     * @param resource the JsonResource to load
     * @return a new TreeEditor instance with the imported content
     */
    public static TreeEditor fromJsonResource(JsonResource resource) {
        EditNode root = importFromJsonNode(resource.getRoot());
        return new TreeEditor(root);
    }

    /**
     * Creates a new TreeEditor with the content from a JsonNode.
     * 
     * @param node the JsonNode to load
     * @return a new TreeEditor instance with the imported content
     */
    public static TreeEditor fromJsonNode(JsonNode node) {
        EditNode root = importFromJsonNode(node);
        return new TreeEditor(root);
    }

    /**
     * Converts a JsonNode to an EditNode recursively.
     * 
     * Tree structure:
     * - JSON Object -> EditNodeObject containing EditNodeProperty children
     * - Each EditNodeProperty has propName = JSON key, and its child is the JSON value
     * - JSON Array -> EditNodeObject with name="__array__" as child of EditNodeProperty
     * - JSON Primitive -> EditNodeProperty with primValue set
     * 
     * @param jsonNode the JsonNode to convert
     * @param propertyName the name/key for this node (for object properties), null for root
     * @return the corresponding EditNode
     */
    private static EditNode convertJsonNodeToEditNode(JsonNode jsonNode, String propertyName) {
        JsonNodeType type = jsonNode.getType();
        
        if (type == JsonNodeType.OBJECT) {
            EditNodeObject editNode = new EditNodeObject(propertyName != null ? propertyName : "");
            Map<String, JsonNode> objectValues = jsonNode.asObjectValues();
            if (objectValues != null) {
                for (Map.Entry<String, JsonNode> entry : objectValues.entrySet()) {
                    // Each property becomes an EditNodeProperty with the value as its child
                    EditNodeProperty prop = new EditNodeProperty(entry.getKey());
                    EditNode valueNode = convertJsonNodeToEditNode(entry.getValue(), null);
                    prop.addChild(valueNode);
                    editNode.addChild(prop);
                }
            }
            return editNode;
        } 
        else if (type == JsonNodeType.ARRAY) {
            EditNodeObject editNode = new EditNodeObject("__array__");
            List<JsonNode> arrayValues = jsonNode.asArray();
            if (arrayValues != null) {
                for (int i = 0; i < arrayValues.size(); i++) {
                    EditNode child = convertJsonNodeToEditNode(arrayValues.get(i), null);
                    editNode.addChild(child);
                }
            }
            return editNode;
        } 
        else {
            // Primitive types: STRING, NUMBER, LONG, BOOLEAN, NULL
            EditNodeProperty editNode = new EditNodeProperty(propertyName != null ? propertyName : "");
            String value = convertJsonValueToString(jsonNode);
            editNode.setPrimValue(value);
            // Store type hint for export
            editNode.setType(type.name().toLowerCase());
            return editNode;
        }
    }

    /**
     * Converts a JsonNode primitive value to a String representation.
     * 
     * @param jsonNode the JsonNode with a primitive type
     * @return the string representation, or null for NULL type
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

    // ========== Export to JsonResource ==========

    /**
     * Exports the current tree to a JsonResource.
     * 
     * @return a new JsonResource containing the tree data
     */
    public JsonResource exportToJsonResource() {
        EditNode root = tree.getRoot();
        JsonNode jsonRoot = convertEditNodeToJsonNode(root);
        return JsonResource.forRoot(jsonRoot);
    }

    /**
     * Exports the current tree to a JsonNode.
     * 
     * @return the root JsonNode of the exported tree
     */
    public JsonNode exportToJsonNode() {
        EditNode root = tree.getRoot();
        return convertEditNodeToJsonNode(root);
    }

    /**
     * Converts an EditNode to a JsonNode recursively.
     * 
     * Tree structure:
     * - EditNodeObject children are EditNodeProperty (representing JSON object properties)
     * - Each EditNodeProperty has a propName (JSON key) and one child (the JSON value)
     * - If the value child is EditNodeObject with name="__array__", it's a JSON array
     * - If the value child is EditNodeProperty with primValue, it's a JSON primitive
     * - If the value child is EditNodeObject (other), it's a nested JSON object
     * 
     * @param editNode the EditNode to convert
     * @return the corresponding JsonNode
     */
    private JsonNode convertEditNodeToJsonNode(EditNode editNode) {
        if (editNode instanceof EditNodeObject) {
            EditNodeObject obj = (EditNodeObject) editNode;
            String name = obj.getName();
            
            // Check if this is an array representation
            if ("__array__".equals(name)) {
                JsonNode arrayNode = JsonNode.arrayNode();
                for (int i = 0; i < obj.getChildCount(); i++) {
                    EditNode child = obj.getChildAt(i);
                    JsonNode jsonChild = convertEditNodeToJsonNode(child);
                    if (jsonChild != null) {
                        arrayNode.add(jsonChild);
                    }
                }
                return arrayNode;
            }
            
            // Default: treat as object
            JsonNode objectNode = JsonNode.objectNode();
            
            // Children should be EditNodeProperty with propName as key
            for (int i = 0; i < obj.getChildCount(); i++) {
                EditNode child = obj.getChildAt(i);
                if (child instanceof EditNodeProperty) {
                    EditNodeProperty prop = (EditNodeProperty) child;
                    String key = prop.getPropName();
                    if (key == null || key.isEmpty()) {
                        key = String.valueOf(i);
                    }
                    
                    // The value is the first child of the property (if it has children)
                    // or the primValue of the property (if it's a primitive)
                    JsonNode value = null;
                    if (prop.getChildCount() > 0) {
                        // Value is stored as child
                        value = convertEditNodeToJsonNode(prop.getChildAt(0));
                    } else {
                        // It's a primitive property
                        value = convertEditNodePropertyToJsonNode(prop);
                    }
                    
                    if (value != null) {
                        objectNode.put(key, value);
                    }
                } else if (child instanceof EditNodeObject) {
                    // Direct EditNodeObject child - use name as key
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
        } 
        else if (editNode instanceof EditNodeProperty) {
            EditNodeProperty prop = (EditNodeProperty) editNode;
            
            // If the property has children, convert the first child as the value
            if (prop.getChildCount() > 0) {
                return convertEditNodeToJsonNode(prop.getChildAt(0));
            }
            
            // It's a primitive property
            return convertEditNodePropertyToJsonNode(prop);
        }
        else {
            // Fallback for any other EditNode type
            return JsonNode.nullNode();
        }
    }

    /**
     * Converts an EditNodeProperty (primitive) to a JsonNode.
     * 
     * @param prop the EditNodeProperty with primValue
     * @return the corresponding JsonNode primitive
     */
    private JsonNode convertEditNodePropertyToJsonNode(EditNodeProperty prop) {
        String primValue = prop.getPrimValue();
        String type = prop.getType();
        
        // Primitive value - determine type from type field or value
        if (primValue == null) {
            return JsonNode.nullNode();
        }
        
        // Try to parse based on type hint
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
        
        // Try to infer type from value
        return JsonNode.varNode(primValue);
    }

    @Override
    public String toString() {
        return "TreeEditor[tree=" + tree + 
               ", history=" + historyManager + 
               ", listeners=" + eventBus.getListenerCount() + "]";
    }
}
