/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.tools;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The editor.* tool group (CONCEPT_Harness.md §13): editor.open opens a project file in a
 * tab. Category editor (local in the editor) - the actual open is delegated to the UI via
 * the handler the HarnessController installs; without a bound UI it is a no-op result.
 *
 * @author Janusch Rentenatus
 */
public final class EditorTools {

    private EditorTools() {
    }

    public static HarnessTool open(Path projectRoot, Consumer<String> openHandler) {
        return new HarnessTool() {
            @Override
            public String name() {
                return "editor.open";
            }

            @Override
            public String scope() {
                return "editor";
            }

            @Override
            public RiskLevel risk() {
                return RiskLevel.WRITE;
            }

            @Override
            public WriteCategory writeCategory() {
                return WriteCategory.EDITOR;
            }

            @Override
            public ToolResult run(Map<String, String> args) {
                String rel = args.getOrDefault("path", "");
                if (rel.isBlank()) {
                    return ToolResult.error("no path given");
                }
                Path p = projectRoot.resolve(rel).normalize();
                if (!p.startsWith(projectRoot.normalize())) {
                    return ToolResult.error("path escapes project root");
                }
                if (openHandler != null) {
                    openHandler.accept(rel);
                }
                return ToolResult.ok("opened " + rel);
            }
        };
    }
}
