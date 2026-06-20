/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.lang.JsonNode;
import de.jare.jsoncasted.lang.JsonNodeType;
import de.jare.jsoncasted.lang.JsonResource;
import de.jare.jsoncasted.parserwriter.JsonParseException;
import de.jare.jsoncasted.parserservice.JsonParserService;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Converter utility for creating EditTree structures from JSON files and strings.
 * Uses JsonParserService from jsoncasted.parserservice package.
 */
public final class JsonTreeConverter {

    private JsonTreeConverter() {
        // Utility class
    }

    /**
     * Creates an EditTree from a JSON file.
     *
     * @param file the JSON file to load
     * @return a new EditTree containing the JSON content
     * @throws IOException if the file cannot be read
     * @throws JsonParseException if JSON parsing fails
     */
    public static EditTree fromJsonFile(File file) throws IOException, JsonParseException {
        JsonResource resource = JsonParserService.parse(file, JsonDebugLevel.SIMPLE);
        if (resource == null) {
            throw new IOException("Failed to parse file: " + file.getAbsolutePath());
        }
        EditNodeAbstract root = importFromJsonNode(resource.getRoot());
        return new EditTree(root);
    }

    /**
     * Creates an EditTree from a JSON string.
     *
     * @param jsonString the JSON string to parse
     * @return a new EditTree containing the JSON content
     * @throws IOException if parsing fails
     * @throws JsonParseException if JSON parsing fails
     */
    public static EditTree fromJsonString(String jsonString) throws IOException, JsonParseException {
        JsonResource resource = JsonParserService.parse(jsonString, JsonDebugLevel.SIMPLE);
        if (resource == null) {
            throw new IOException("Failed to parse JSON string");
        }
        EditNodeAbstract root = importFromJsonNode(resource.getRoot());
        return new EditTree(root);
    }

    /**
     * Imports a JSON node into an EditNode structure.
     *
     * @param node the JSON node to import
     * @return the root EditNode
     */
    public static EditNodeAbstract importFromJsonNode(JsonNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        return convertJsonNodeToEditNode(node, null, new EditTimes());
    }

    /**
     * Recursively converts a JSON node into the corresponding editable node model.
     *
     * @param jsonNode the source JSON node
     * @param propertyName the property name for object members, or null for root/array values
     * @param weightMonitor the weight monitor for tree construction
     * @return the converted editable node
     */
    private static EditNodeAbstract convertJsonNodeToEditNode(JsonNode jsonNode, String propertyName, EditTimes weightMonitor) {
        JsonNodeType type = jsonNode.getType();

        if (type == JsonNodeType.OBJECT) {
            EditNodeObject editNode = new EditNodeObject(propertyName != null ? propertyName : "");
            Map<String, JsonNode> objectValues = jsonNode.asObjectValues();
            if (objectValues != null) {
                for (Map.Entry<String, JsonNode> entry : objectValues.entrySet()) {
                    EditNodeProperty prop = new EditNodeProperty(entry.getKey());
                    EditNodeAbstract valueNode = convertJsonNodeToEditNode(entry.getValue(), null, weightMonitor);
                    editNode.addChild(prop, weightMonitor);
                    prop.addChild(valueNode, weightMonitor);
                }
            }
            return editNode;
        } else if (type == JsonNodeType.ARRAY) {
            EditNodeObject editNode = new EditNodeObject(propertyName != null ? propertyName : "__array__");
            List<JsonNode> arrayValues = jsonNode.asArray();
            if (arrayValues != null) {
                for (JsonNode value : arrayValues) {
                    EditNodeAbstract child = convertJsonNodeToEditNode(value, null, weightMonitor);
                    editNode.addChild(child, weightMonitor);
                }
            }
            return editNode;
        } else {
            EditNodeProperty editNode = new EditNodeProperty(propertyName != null ? propertyName : "");
            String value = convertJsonValueToString(jsonNode);
            editNode.setValue(value);
            // Only primitive nodes have a type
            editNode.setType(type);
            return editNode;
        }
    }

    /**
     * Converts a primitive JSON node value to its string representation.
     *
     * @param jsonNode the primitive JSON node
     * @return the string value, or null for JSON null
     */
    private static String convertJsonValueToString(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull()) {
            return null;
        }
        JsonNodeType type = jsonNode.getType();
        if (type == JsonNodeType.STRING) {
            return jsonNode.asText();
        } else if (type == JsonNodeType.NUMBER || type == JsonNodeType.LONG) {
            return String.valueOf(jsonNode.asLong());
        } else if (type == JsonNodeType.BOOLEAN) {
            return String.valueOf(jsonNode.asBoolean());
        }
        return jsonNode.asText();
    }
}
