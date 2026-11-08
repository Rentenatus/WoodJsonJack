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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The search.* tool group (CONCEPT_Harness.md §13): grep-like search sandboxed onto the
 * project root, read-only - always automode. (Semantic search is a later addition over
 * the same interface.)
 *
 * @author Janusch Rentenatus
 */
public final class SearchTools {

    private SearchTools() {
    }

    /** search.grep - regex search below a project-relative path, returns matching lines. */
    public static HarnessTool grep(Path projectRoot) {
        return new HarnessTool() {
            @Override
            public String name() {
                return "search.grep";
            }

            @Override
            public String scope() {
                return "search";
            }

            @Override
            public RiskLevel risk() {
                return RiskLevel.READ;
            }

            @Override
            public ToolResult run(Map<String, String> args) {
                String regex = args.getOrDefault("pattern", "");
                if (regex.isBlank()) {
                    return ToolResult.error("no pattern given");
                }
                Pattern pat;
                try {
                    pat = Pattern.compile(regex);
                } catch (PatternSyntaxException e) {
                    return ToolResult.error("bad regex: " + e.getMessage());
                }
                try {
                    Path base = projectRoot.resolve(args.getOrDefault("path", ".")).normalize();
                    if (!base.startsWith(projectRoot.normalize())) {
                        return ToolResult.error("path escapes project root");
                    }
                    StringBuilder out = new StringBuilder();
                    try (Stream<Path> s = Files.walk(base)) {
                        s.filter(Files::isRegularFile)
                                .filter(p -> p.toString().endsWith(".java")
                                        || p.toString().endsWith(".json")
                                        || p.toString().endsWith(".md"))
                                .forEach(p -> {
                                    try {
                                        int lineNo = 0;
                                        for (String line : Files.readAllLines(p)) {
                                            lineNo++;
                                            if (pat.matcher(line).find()) {
                                                out.append(base.relativize(p)).append(':')
                                                        .append(lineNo).append(": ").append(line).append('\n');
                                            }
                                        }
                                    } catch (IOException ignored) {
                                        // unreadable file - skip
                                    }
                                });
                    }
                    String result = out.toString();
                    return ToolResult.ok(result.isEmpty() ? "(no matches)" : result);
                } catch (IOException e) {
                    return ToolResult.error(e.getMessage());
                }
            }
        };
    }
}
