/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.tools.EditorTools;
import de.jare.harness.tools.FsTools;
import de.jare.harness.tools.GitTools;
import de.jare.harness.tools.HarnessTool;
import de.jare.harness.tools.HarnessTools;
import de.jare.harness.tools.PermissionPolicy;
import de.jare.harness.tools.ProcessTools;
import de.jare.harness.tools.SearchTools;
import de.jare.harness.tools.ToolResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for the tooling layer (CONCEPT_Harness.md §13): the permission matrix
 * (read = automode, write by category, destructive always at least "fragen"),
 * the exposure budget (max K tools per task type), the sandboxing of fs.* onto the
 * project root, and TOOL_ERROR on denial/unknown tools.
 *
 * @author Janusch Rentenatus
 */
public class HarnessToolsNGTest {

    private static HarnessTools toolsWithFs(Path root, PermissionPolicy policy) {
        HarnessTools tools = new HarnessTools(policy);
        tools.register(FsTools.read(root));
        tools.register(FsTools.list(root));
        tools.register(FsTools.write(root));
        tools.exposeScopes("CODE_EDIT", List.of("fs"));
        tools.exposeScopes("RESEARCH", List.of("fs", "search"));
        return tools;
    }

    @Test
    public void testReadToolsRunInAutomode() throws Exception {
        Path root = Files.createTempDirectory("harness-tools");
        Files.writeString(root.resolve("a.txt"), "hello");
        HarnessTools tools = toolsWithFs(root, new PermissionPolicy()); // default: fragen for writes
        ToolResult r = tools.call("fs.read", Map.of("path", "a.txt"), "test");
        Assert.assertTrue(r.ok());
        Assert.assertEquals(r.output(), "hello");
    }

    @Test
    public void testWriteDeniedWhenCategoryIsNein() throws Exception {
        Path root = Files.createTempDirectory("harness-tools");
        PermissionPolicy policy = new PermissionPolicy()
                .set(HarnessTool.WriteCategory.DISK_IN, PermissionPolicy.Setting.NEIN);
        HarnessTools tools = toolsWithFs(root, policy);
        try {
            tools.call("fs.write", Map.of("path", "b.txt", "content", "x"), "test");
            Assert.fail("expected TOOL_ERROR");
        } catch (HarnessException e) {
            Assert.assertEquals(e.getType(), HarnessException.Type.TOOL_ERROR);
            Assert.assertTrue(e.getMessage().contains("nein"));
        }
        Assert.assertFalse(Files.exists(root.resolve("b.txt")));
    }

    @Test
    public void testWriteAsksAndRunsWhenApproved() throws Exception {
        Path root = Files.createTempDirectory("harness-tools");
        PermissionPolicy policy = new PermissionPolicy()
                .set(HarnessTool.WriteCategory.DISK_IN, PermissionPolicy.Setting.FRAGEN)
                .onApprovalRequest((tool, reason) -> true);
        HarnessTools tools = toolsWithFs(root, policy);
        ToolResult r = tools.call("fs.write", Map.of("path", "c.txt", "content", "data"), "test");
        Assert.assertTrue(r.ok());
        Assert.assertEquals(Files.readString(root.resolve("c.txt")), "data");
    }

    @Test
    public void testDestructiveIsAlwaysAtLeastFragen() {
        PermissionPolicy policy = new PermissionPolicy()
                .set(HarnessTool.WriteCategory.SYSTEM, PermissionPolicy.Setting.AUTOMODE);
        HarnessTool destructive = new HarnessTool() {
            @Override public String name() { return "system.wipe"; }
            @Override public String scope() { return "system"; }
            @Override public RiskLevel risk() { return RiskLevel.DESTRUCTIVE; }
            @Override public WriteCategory writeCategory() { return WriteCategory.SYSTEM; }
            @Override public ToolResult run(Map<String, String> args) { return ToolResult.ok("wiped"); }
        };
        HarnessTools tools = new HarnessTools(policy);
        tools.register(destructive);
        // automode on the category must NOT allow a destructive tool; default callback denies
        try {
            tools.call("system.wipe", Map.of(), "test");
            Assert.fail("destructive must ask, not run in automode");
        } catch (HarnessException e) {
            Assert.assertEquals(e.getType(), HarnessException.Type.TOOL_ERROR);
        }
    }

    @Test
    public void testExposureBudgetCapsAtK() throws Exception {
        Path root = Files.createTempDirectory("harness-tools");
        HarnessTools tools = toolsWithFs(root, new PermissionPolicy());
        for (int i = 0; i < 10; i++) {
            final int idx = i;
            tools.register(new HarnessTool() {
                @Override public String name() { return "search.t" + idx; }
                @Override public String scope() { return "search"; }
                @Override public RiskLevel risk() { return RiskLevel.READ; }
                @Override public ToolResult run(Map<String, String> args) { return ToolResult.ok(""); }
            });
        }
        List<HarnessTool> allowed = tools.allowed("RESEARCH", 3);
        Assert.assertEquals(allowed.size(), 3, "exposure budget must hard-cap at K=3");
        Assert.assertTrue(allowed.stream().allMatch(t -> t.scope().equals("fs") || t.scope().equals("search")));
        // a task type without search scope gets only fs tools
        List<HarnessTool> codeEdit = tools.allowed("CODE_EDIT", 5);
        Assert.assertTrue(codeEdit.stream().allMatch(t -> t.scope().equals("fs")));
    }

    @Test
    public void testFsSandboxBlocksEscape() throws Exception {
        Path root = Files.createTempDirectory("harness-tools");
        HarnessTools tools = toolsWithFs(root, new PermissionPolicy());
        try {
            tools.call("fs.read", Map.of("path", "../outside.txt"), "test");
            Assert.fail("expected TOOL_ERROR for path escape");
        } catch (HarnessException e) {
            Assert.assertEquals(e.getType(), HarnessException.Type.TOOL_ERROR);
        }
    }

    private static HarnessTools toolsWithFullCatalog(Path root, PermissionPolicy policy,
                                                     java.util.function.Consumer<String> editorHandler) {
        HarnessTools tools = new HarnessTools(policy);
        tools.register(FsTools.read(root));
        tools.register(FsTools.list(root));
        tools.register(FsTools.write(root));
        tools.register(FsTools.delete(root));
        tools.register(ProcessTools.run(root));
        tools.register(GitTools.status(root));
        tools.register(GitTools.diff(root));
        tools.register(GitTools.log(root));
        tools.register(GitTools.add(root));
        tools.register(GitTools.commit(root));
        tools.register(SearchTools.grep(root));
        tools.register(EditorTools.open(root, editorHandler));
        tools.exposeScopes("ALL", List.of("fs", "process", "git", "search", "editor"));
        return tools;
    }

    @Test
    public void testFullCatalogCoversAllScopesWithCorrectRiskAndCategory() throws Exception {
        Path root = Files.createTempDirectory("harness-catalog");
        HarnessTools tools = toolsWithFullCatalog(root, new PermissionPolicy(), rel -> { });
        List<HarnessTool> all = tools.allowed("ALL", 100);
        Assert.assertEquals(all.size(), 12, "fs x4 + process + git x5 + search + editor (§13)");
        for (String scope : List.of("fs", "process", "git", "search", "editor")) {
            Assert.assertTrue(all.stream().anyMatch(t -> t.scope().equals(scope)), "scope missing: " + scope);
        }
        java.util.function.Function<String, HarnessTool> byName = n -> all.stream()
                .filter(t -> t.name().equals(n)).findFirst().orElseThrow();
        Assert.assertEquals(byName.apply("fs.delete").risk(), HarnessTool.RiskLevel.DESTRUCTIVE);
        Assert.assertEquals(byName.apply("fs.delete").writeCategory(), HarnessTool.WriteCategory.DISK_IN);
        Assert.assertEquals(byName.apply("git.commit").risk(), HarnessTool.RiskLevel.DESTRUCTIVE);
        Assert.assertEquals(byName.apply("process.run").writeCategory(), HarnessTool.WriteCategory.SYSTEM);
        Assert.assertEquals(byName.apply("editor.open").writeCategory(), HarnessTool.WriteCategory.EDITOR);
        Assert.assertEquals(byName.apply("search.grep").risk(), HarnessTool.RiskLevel.READ);
        Assert.assertEquals(byName.apply("git.status").risk(), HarnessTool.RiskLevel.READ);
    }

    @Test
    public void testFsDeleteDestructiveDeniedEvenInAutomode() throws Exception {
        Path root = Files.createTempDirectory("harness-tools");
        Files.writeString(root.resolve("doomed.txt"), "x");
        PermissionPolicy automode = new PermissionPolicy()
                .set(HarnessTool.WriteCategory.DISK_IN, PermissionPolicy.Setting.AUTOMODE);
        HarnessTools tools = toolsWithFullCatalog(root, automode, rel -> { });
        try {
            tools.call("fs.delete", Map.of("path", "doomed.txt"), "test");
            Assert.fail("destructive must be at least fragen even when the category is automode (§13)");
        } catch (HarnessException e) {
            Assert.assertEquals(e.getType(), HarnessException.Type.TOOL_ERROR);
        }
        Assert.assertTrue(Files.exists(root.resolve("doomed.txt")), "file must survive a denied delete");
    }

    @Test
    public void testSearchGrepFindsMatchesSandboxedToProject() throws Exception {
        Path root = Files.createTempDirectory("harness-search");
        Files.writeString(root.resolve("Main.java"), "public class Main { void run() {} }");
        Files.writeString(root.resolve("notes.md"), "nothing here");
        HarnessTools tools = toolsWithFullCatalog(root, new PermissionPolicy(), rel -> { });
        ToolResult r = tools.call("search.grep", Map.of("pattern", "void run"), "test");
        Assert.assertTrue(r.ok());
        Assert.assertTrue(r.output().contains("Main.java:1"), r.output());
        try {
            tools.call("search.grep", Map.of("pattern", "x", "path", ".."), "test");
            Assert.fail("expected TOOL_ERROR for path escape");
        } catch (HarnessException e) {
            Assert.assertEquals(e.getType(), HarnessException.Type.TOOL_ERROR);
        }
    }

    @Test
    public void testEditorOpenDelegatesToUiHandler() throws Exception {
        Path root = Files.createTempDirectory("harness-editor");
        Files.writeString(root.resolve("a.json"), "{}");
        java.util.List<String> opened = new java.util.ArrayList<>();
        HarnessTools tools = toolsWithFullCatalog(root, new PermissionPolicy(), opened::add);
        // editor category defaults to "fragen" - approve via callback
        tools.getPolicy().onApprovalRequest((tool, reason) -> true);
        ToolResult r = tools.call("editor.open", Map.of("path", "a.json"), "test");
        Assert.assertTrue(r.ok());
        Assert.assertEquals(opened, List.of("a.json"));
    }
}
