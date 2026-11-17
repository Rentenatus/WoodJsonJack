/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.io;

import de.jare.jsoncasted.lang.JsonNode;

/**
 * Small shared JsonNode accessors - previously copy-pasted as private helpers into
 * {@code CallLogEntry}, {@code HarnessModelCodec}, {@code HarnessInstanceCodec} and
 * {@code RagStore}. All are null-tolerant on the node itself.
 *
 * @author Janusch Rentenatus
 */
public final class JsonNodes {

    private JsonNodes() {
    }

    /** The object member with this key, or null when the node is no object / key absent. */
    public static JsonNode child(JsonNode node, String key) {
        if (node == null || !node.isObject()) {
            return null;
        }
        return node.asObjectValues().get(key);
    }

    /** Text value of an object member (default when absent or null). */
    public static String text(JsonNode node, String key, String def) {
        JsonNode v = child(node, key);
        return v == null || v.isNull() ? def : v.asText();
    }

    /** Long value of an object member (default when absent or null). */
    public static long longOf(JsonNode node, String key, long def) {
        JsonNode v = child(node, key);
        return v == null || v.isNull() ? def : v.asLong();
    }

    /** Double value of an object member - NUMBER or LONG node (default when absent or null). */
    public static double doubleOf(JsonNode node, String key, double def) {
        JsonNode v = child(node, key);
        if (v == null || v.isNull()) {
            return def;
        }
        Double d = v.asNumber();
        return d != null ? d : v.asLong();
    }
}
