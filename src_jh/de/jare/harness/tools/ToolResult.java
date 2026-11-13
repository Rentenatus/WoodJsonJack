/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.tools;

/**
 * Result of a tool call (CONCEPT_Harness.md §13): ok or error - the error feeds the
 * TOOL_ERROR signal / catch-mapping of the calling calculator.
 *
 * @author Janusch Rentenatus
 */
public record ToolResult(boolean ok, String output, String error) {

    public static ToolResult ok(String output) {
        return new ToolResult(true, output, null);
    }

    public static ToolResult error(String error) {
        return new ToolResult(false, null, error);
    }
}
