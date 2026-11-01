/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the display path of an edit node.
 */
public final class JsonJackNodePaths {

    private JsonJackNodePaths() {
    }

    /**
     * Builds the path of a node by concatenating the names upwards from
     * the node to the roots (format: "a > b > c").
     *
     * @param node the node
     * @return the path, or an empty string when the node is null
     */
    public static String buildNodePath(EditNode node) {
        if (node == null) {
            return "";
        }
        List<String> names = new ArrayList<>();
        EditNode current = node;
        while (current != null) {
            String name = current.getName();
            names.add(name == null ? "" : name);
            current = current.getParent();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = names.size() - 1; i >= 0; i--) {
            if (sb.length() > 0) {
                sb.append(" > ");
            }
            sb.append(names.get(i));
        }
        return sb.toString();
    }
}
