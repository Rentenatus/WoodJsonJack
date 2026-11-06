/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The fs.* tool group (CONCEPT_Harness.md §13): read tools are sandboxed onto the
 * project root and run in automode; write tools are categorized disk-in / disk-out.
 *
 * @author Janusch Rentenatus
 */
public final class FsTools {

    private FsTools() {
    }

    public static HarnessTool read(Path projectRoot) {
        return new HarnessTool() {
            @Override
            public String name() {
                return "fs.read";
            }

            @Override
            public String scope() {
                return "fs";
            }

            @Override
            public RiskLevel risk() {
                return RiskLevel.READ;
            }

            @Override
            public ToolResult run(Map<String, String> args) {
                try {
                    Path p = projectRoot.resolve(args.getOrDefault("path", "")).normalize();
                    if (!p.startsWith(projectRoot.normalize())) {
                        return ToolResult.error("path escapes project root");
                    }
                    if (!Files.isRegularFile(p)) {
                        return ToolResult.error("not a file: " + p);
                    }
                    return ToolResult.ok(Files.readString(p));
                } catch (IOException e) {
                    return ToolResult.error(e.getMessage());
                }
            }
        };
    }

    public static HarnessTool list(Path projectRoot) {
        return new HarnessTool() {
            @Override
            public String name() {
                return "fs.list";
            }

            @Override
            public String scope() {
                return "fs";
            }

            @Override
            public RiskLevel risk() {
                return RiskLevel.READ;
            }

            @Override
            public ToolResult run(Map<String, String> args) {
                try {
                    Path p = projectRoot.resolve(args.getOrDefault("path", ".")).normalize();
                    if (!p.startsWith(projectRoot.normalize())) {
                        return ToolResult.error("path escapes project root");
                    }
                    try (Stream<Path> s = Files.list(p)) {
                        return ToolResult.ok(s.map(x -> x.getFileName().toString())
                                .sorted().collect(Collectors.joining("\n")));
                    }
                } catch (IOException e) {
                    return ToolResult.error(e.getMessage());
                }
            }
        };
    }

    /** fs.write - a writing tool, category disk-in (inside the project). */
    public static HarnessTool write(Path projectRoot) {
        return new HarnessTool() {
            @Override
            public String name() {
                return "fs.write";
            }

            @Override
            public String scope() {
                return "fs";
            }

            @Override
            public RiskLevel risk() {
                return RiskLevel.WRITE;
            }

            @Override
            public WriteCategory writeCategory() {
                return WriteCategory.DISK_IN;
            }

            @Override
            public ToolResult run(Map<String, String> args) {
                try {
                    Path p = projectRoot.resolve(args.getOrDefault("path", "")).normalize();
                    if (!p.startsWith(projectRoot.normalize())) {
                        return ToolResult.error("path escapes project root");
                    }
                    Files.writeString(p, args.getOrDefault("content", ""));
                    return ToolResult.ok("written " + p.getFileName());
                } catch (IOException e) {
                    return ToolResult.error(e.getMessage());
                }
            }
        };
    }
}
