/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.tools;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The process.* tool group (CONCEPT_Harness.md §13): tests/builds run with the project
 * root as working directory and a hard timeout. Category system - a spawned process is
 * system level, never automode by default.
 *
 * @author Janusch Rentenatus
 */
public final class ProcessTools {

    public static final long DEFAULT_TIMEOUT_MS = 60_000;

    private ProcessTools() {
    }

    /** process.run - runs a command (e.g. the test suite) inside the project, category system. */
    public static HarnessTool run(Path projectRoot) {
        return run(projectRoot, DEFAULT_TIMEOUT_MS);
    }

    public static HarnessTool run(Path projectRoot, long timeoutMs) {
        return new HarnessTool() {
            @Override
            public String name() {
                return "process.run";
            }

            @Override
            public String scope() {
                return "process";
            }

            @Override
            public RiskLevel risk() {
                return RiskLevel.WRITE;
            }

            @Override
            public WriteCategory writeCategory() {
                return WriteCategory.SYSTEM;
            }

            @Override
            public ToolResult run(Map<String, String> args) {
                String cmd = args.getOrDefault("command", "");
                if (cmd.isBlank()) {
                    return ToolResult.error("no command given");
                }
                try {
                    ProcessBuilder pb = new ProcessBuilder(cmd.split("\\s+"));
                    pb.directory(projectRoot.toFile());
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    boolean finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
                    if (!finished) {
                        p.destroyForcibly();
                        return ToolResult.error("timeout after " + timeoutMs + " ms");
                    }
                    String out = new String(p.getInputStream().readAllBytes());
                    return ToolResult.ok("exit=" + p.exitValue() + "\n" + out);
                } catch (IOException | InterruptedException e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    return ToolResult.error(e.getMessage());
                }
            }
        };
    }
}
