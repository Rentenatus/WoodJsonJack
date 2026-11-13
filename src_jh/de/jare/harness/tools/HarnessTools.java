/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code ctx.tools} facade (CONCEPT_Harness.md §13, §14): not the raw catalog, but
 * the bounded view - {@link #allowed(String, int)} returns at most K tools per WORK
 * call (exposure budget), selected by taskType × scope; {@link #call(String, Map)}
 * runs with the permission check. A denial raises TOOL_ERROR for the catch-mapping.
 *
 * @author Janusch Rentenatus
 */
public class HarnessTools {

    /** taskType -> scopes that are relevant for it (the selection table, §13). */
    private final Map<String, List<String>> taskTypeScopes = new LinkedHashMap<>();
    private final Map<String, HarnessTool> catalog = new LinkedHashMap<>();
    private final PermissionPolicy policy;

    public HarnessTools(PermissionPolicy policy) {
        this.policy = policy;
    }

    public HarnessTools register(HarnessTool tool) {
        catalog.put(tool.name(), tool);
        return this;
    }

    /** Maps a task type to the scopes exposed for it, e.g. CODE_EDIT -> [fs, search, editor]. */
    public HarnessTools exposeScopes(String taskType, List<String> scopes) {
        taskTypeScopes.put(taskType, scopes);
        return this;
    }

    /** The bounded selection: max K tools for the task type, hard-capped by the driver. */
    public List<HarnessTool> allowed(String taskType, int maxK) {
        List<String> scopes = taskTypeScopes.getOrDefault(taskType, List.of("fs"));
        List<HarnessTool> out = new ArrayList<>();
        for (HarnessTool t : catalog.values()) {
            if (out.size() >= maxK) {
                break;
            }
            if (scopes.contains(t.scope())) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * Executes a tool with the permission check.
     *
     * @throws de.jare.harness.HarnessException with type TOOL_ERROR on denial or tool failure
     */
    public ToolResult call(String name, Map<String, String> args, String reason) {
        HarnessTool tool = catalog.get(name);
        if (tool == null) {
            throw new de.jare.harness.HarnessException(de.jare.harness.HarnessException.Type.TOOL_ERROR,
                    "unknown tool: " + name);
        }
        String denial = policy.check(tool, reason);
        if (denial != null) {
            throw new de.jare.harness.HarnessException(de.jare.harness.HarnessException.Type.TOOL_ERROR, denial);
        }
        ToolResult result = tool.run(args);
        if (!result.ok()) {
            throw new de.jare.harness.HarnessException(de.jare.harness.HarnessException.Type.TOOL_ERROR,
                    result.error());
        }
        return result;
    }

    public PermissionPolicy getPolicy() {
        return policy;
    }
}
