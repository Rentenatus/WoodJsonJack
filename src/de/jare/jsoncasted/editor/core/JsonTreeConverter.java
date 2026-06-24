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
import de.jare.jsoncasted.lang.JsonTerms;
import de.jare.jsoncasted.parserwriter.JsonParseException;
import de.jare.jsoncasted.parserservice.JsonParserService;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Converter utility for creating EditTree structures from JSON files and
 * strings. Uses JsonParserService from jsoncasted.parserservice package.
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
        String rootName = file.getName();
        int dotIndex = rootName.lastIndexOf('.');
        if (dotIndex > 0) {
            rootName = rootName.substring(0, dotIndex);
        }
        JsonResource resource = JsonParserService.parse(file, JsonDebugLevel.SIMPLE);
        if (resource == null) {
            throw new IOException("Failed to parse file: " + file.getAbsolutePath());
        }
        return convertRessourceToEditTree(resource, rootName);
    }

    /**
     * Creates an EditTree from a JSON string.
     *
     * @param jsonString the JSON string to parse
     * @param rootName
     * @return a new EditTree containing the JSON content
     * @throws IOException if parsing fails
     * @throws JsonParseException if JSON parsing fails
     */
    public static EditTree fromJsonString(String jsonString, String rootName) throws IOException, JsonParseException {
        JsonResource resource = JsonParserService.parse(jsonString, JsonDebugLevel.SIMPLE);
        if (resource == null) {
            throw new IOException("Failed to parse JSON string");
        }
        return convertRessourceToEditTree(resource, rootName);
    }

    public static EditTree convertRessourceToEditTree(JsonResource resource, String rootName) {
        EditTimes weightMonitor = new EditTimes();
        EditNodeAbstract root = importFromJsonNode(resource.getRoot(), rootName, weightMonitor);
        return new EditTree(root, weightMonitor);
    }

    /**
     * Imports a JSON node into an EditNode structure.
     *
     * @param jsonNode the JSON node to import
     * @param rootName
     * @param weightMonitor
     * @return the root EditNode
     */
    public static EditNodeAbstract importFromJsonNode(JsonNode jsonNode, String rootName, EditTimes weightMonitor) {
        if (jsonNode == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        EditNodeObject rootNode = new EditNodeObject(rootName);
        convertJsonNodeToEditNode(rootNode, jsonNode, weightMonitor);
        return rootNode;
    }

    private static void convertJsonNodeToEditNode(EditNodeObject rootNode, JsonNode jsonNode, EditTimes weightMonitor) {
        Map<String, JsonNode> objectValues = jsonNode.asObjectValues();
        if (objectValues != null) {
            for (Map.Entry<String, JsonNode> entry : objectValues.entrySet()) {
                if (JsonTerms.TERM_WOOD_PROVIDERS.equals(entry.getKey())) {
                    continue;
                }
                buildEditProperty(rootNode, entry, weightMonitor);
            }
        }
    }

    private static void buildEditProperty(EditNodeObject parent, Map.Entry<String, JsonNode> entry,
            EditTimes weightMonitor) {
        String propertyName = entry.getKey();
        EditNodeProperty editNode = new EditNodeProperty(propertyName != null ? propertyName : "mm");
        parent.addChild(editNode, weightMonitor);
        JsonNode jsonNode = entry.getValue();
        JsonNodeType type = jsonNode.getType();

        if (type == JsonNodeType.ARRAY) {
            List<JsonNode> arrayValues = jsonNode.asArray();
            if (arrayValues != null) {
                for (JsonNode value : arrayValues) {
                    buildEditObject(editNode, value, weightMonitor);
                }
            }
        } else if (type == JsonNodeType.OBJECT) {
            buildEditObject(editNode, jsonNode, weightMonitor);
        } else {
            editNode.setValue(convertJsonValueToString(jsonNode));
        }
        editNode.setType(type);
    }

    /**
     * Converts a JSON object node to an EditNodeObject with property children.
     * Objects can only contain properties.
     *
     * @param jsonNode the JSON object node
     * @param propertyName the property name, or null for root
     * @param weightMonitor the weight monitor for tree construction
     * @return the EditNodeObject with property children
     */
    private static void buildEditObject(EditNodeProperty parent, JsonNode jsonNode,
            EditTimes weightMonitor) {
        System.out.println("1 +++++++++++++++   " + jsonNode);
        JsonNodeType type = jsonNode.getType();
        if (type == JsonNodeType.OBJECT) {

        }
        EditNodeObject valueNode = new EditNodeObject("Node");
        convertJsonNodeToEditNode(valueNode, jsonNode, weightMonitor);
        parent.addChild(valueNode, weightMonitor);
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
        } else if (type == JsonNodeType.LONG) {
            return String.valueOf(jsonNode.asLong());
        } else if (type == JsonNodeType.NUMBER) {
            return String.valueOf(jsonNode.asNumber());
        } else if (type == JsonNodeType.BOOLEAN) {
            return String.valueOf(jsonNode.asBoolean());
        } else if (type == JsonNodeType.NULL) {
            return null;
        }
        return jsonNode.asText();
    }
}
