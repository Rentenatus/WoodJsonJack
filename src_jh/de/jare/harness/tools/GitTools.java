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
 * The git.* tool group (CONCEPT_Harness.md §13): status/diff/log read the repo at the
 * project root (automode); add/commit write it - commit is destructive and therefore
 * always at least "fragen" (§13). All operations are category disk-in.
 *
 * @author Janusch Rentenatus
 */
public final class GitTools {

    private static final long TIMEOUT_MS = 30_000;

    private GitTools() {
    }

    public static HarnessTool status(Path projectRoot) {
        return readTool("git.status", projectRoot, "status", "--short");
    }

    public static HarnessTool diff(Path projectRoot) {
        return readTool("git.diff", projectRoot, "diff", "--stat");
    }

    public static HarnessTool log(Path projectRoot) {
        return readTool("git.log", projectRoot, "log", "--oneline", "-10");
    }

    /** git.add - writing, category disk-in. */
    public static HarnessTool add(Path projectRoot) {
        return writeTool("git.add", "add", projectRoot);
    }

    /** git.commit - destructive per §13 (named example), always at least "fragen". */
    public static HarnessTool commit(Path projectRoot) {
        return new HarnessTool() {
            @Override
            public String name() {
                return "git.commit";
            }

            @Override
            public String scope() {
                return "git";
            }

            @Override
            public RiskLevel risk() {
                return RiskLevel.DESTRUCTIVE;
            }

            @Override
            public WriteCategory writeCategory() {
                return WriteCategory.DISK_IN;
            }

            @Override
            public ToolResult run(Map<String, String> args) {
                String message = args.getOrDefault("message", "");
                if (message.isBlank()) {
                    return ToolResult.error("no commit message given");
                }
                return exec(projectRoot, "commit", "-m", message);
            }
        };
    }

    private static HarnessTool readTool(String name, Path root, String... gitArgs) {
        return readToolAt(root, name, gitArgs);
    }

    private static HarnessTool readToolAt(Path root, String name, String... gitArgs) {
        return new HarnessTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String scope() {
                return "git";
            }

            @Override
            public RiskLevel risk() {
                return RiskLevel.READ;
            }

            @Override
            public ToolResult run(Map<String, String> args) {
                return exec(root, gitArgs);
            }
        };
    }

    private static HarnessTool writeTool(String name, String verb, Path root) {
        return new HarnessTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String scope() {
                return "git";
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
                return exec(root, verb, args.getOrDefault("path", "."));
            }
        };
    }

    private static ToolResult exec(Path root, String... gitArgs) {
        String[] cmd = new String[gitArgs.length + 1];
        cmd[0] = "git";
        System.arraycopy(gitArgs, 0, cmd, 1, gitArgs.length);
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(root.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                return ToolResult.error("git timeout");
            }
            String out = new String(p.getInputStream().readAllBytes());
            if (p.exitValue() != 0) {
                return ToolResult.error("git exit=" + p.exitValue() + ": " + out);
            }
            return ToolResult.ok(out);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return ToolResult.error(e.getMessage());
        }
    }
}
