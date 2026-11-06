/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.tools;

import java.util.Map;

/**
 * One tool of the WJJ tooling, bound as a direct Java SST (in-process, no MCP hop)
 * (CONCEPT_Harness.md §13).
 *
 * @author Janusch Rentenatus
 */
public interface HarnessTool {

    /** e.g. "fs.read", "git.commit". */
    String name();

    /** Domain: fs | git | process | search | editor. */
    String scope();

    /** read | write | destructive - destructive is always at least "fragen". */
    RiskLevel risk();

    /** Write category for the permission policy: editor | disk-in | disk-out | external | system. */
    default WriteCategory writeCategory() {
        return WriteCategory.DISK_IN;
    }

    ToolResult run(Map<String, String> args);

    enum RiskLevel {
        READ, WRITE, DESTRUCTIVE
    }

    enum WriteCategory {
        EDITOR, DISK_IN, DISK_OUT, EXTERNAL, SYSTEM
    }
}
