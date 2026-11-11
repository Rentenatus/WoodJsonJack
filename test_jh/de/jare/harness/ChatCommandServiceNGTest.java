/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.main.chat.ChatCommandService;
import de.jare.harness.main.chat.ChatCommandService.CommandResult;
import de.jare.harness.memory.ProjectMemory;
import de.jare.harness.memory.ProjectMemory.Nugget;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * The chat command service (CONCEPT_Harness.md §17): {@code /}-prefixed input is a command and
 * never starts a harness run; normal input passes through unchanged. Commands operate on the
 * project-local memory file of the service's project dir.
 *
 * @author Janusch Rentenatus
 */
public class ChatCommandServiceNGTest {

    private HarnessService serviceWithMemory(Path project, String... chats) throws Exception {
        HarnessService service = new HarnessService(project.toFile());
        ProjectMemory mem = ProjectMemory.load(service.memoryFile());
        for (int i = 0; i < chats.length; i += 2) {
            mem.addChat(chats[i], chats[i + 1]);
        }
        mem.save(service.memoryFile());
        return service;
    }

    @Test
    public void testPlainPromptPassesThrough() throws Exception {
        Path project = Files.createTempDirectory("chatcmd-plain");
        ChatCommandService cmds = new ChatCommandService(serviceWithMemory(project));
        CommandResult r = cmds.dispatch("wie viel ist 2*4?");
        assertFalse(r.handled(), "no / prefix -> normal run");
        assertEquals(r.prompt(), "wie viel ist 2*4?");
        assertNull(r.reply());
        assertFalse(cmds.dispatch("  ").handled());
        assertFalse(cmds.dispatch(null).handled());
    }

    @Test
    public void testClearEmptiesMemoryFile() throws Exception {
        Path project = Files.createTempDirectory("chatcmd-clear");
        HarnessService service = serviceWithMemory(project, "user", "hallo", "assistant", "hi");
        CommandResult r = new ChatCommandService(service).dispatch("/clear");
        assertTrue(r.handled());
        assertTrue(r.reply().contains("2 entries removed"));
        assertEquals(ProjectMemory.load(service.memoryFile()).size(), 0);
    }

    @Test
    public void testForgetById() throws Exception {
        Path project = Files.createTempDirectory("chatcmd-forget");
        HarnessService service = serviceWithMemory(project, "user", "eins", "assistant", "zwei");
        ProjectMemory before = ProjectMemory.load(service.memoryFile());
        long firstId = before.getEntries().get(0).id;
        CommandResult r = new ChatCommandService(service).dispatch("/forget " + firstId);
        assertTrue(r.handled());
        assertTrue(r.reply().contains("forgotten"));
        ProjectMemory after = ProjectMemory.load(service.memoryFile());
        assertEquals(after.size(), 1);
        assertNull(after.get(firstId));
        // unknown id and bad usage are handled replies, not runs
        assertTrue(new ChatCommandService(service).dispatch("/forget 999").reply().contains("No memory entry"));
        assertTrue(new ChatCommandService(service).dispatch("/forget").reply().contains("Usage"));
        assertTrue(new ChatCommandService(service).dispatch("/forget abc").reply().contains("not a number"));
    }

    @Test
    public void testMemoryListingShowsUsesHits() throws Exception {
        Path project = Files.createTempDirectory("chatcmd-memory");
        HarnessService service = serviceWithMemory(project, "user", "hallo welt");
        ProjectMemory mem = ProjectMemory.load(service.memoryFile());
        Nugget n = mem.addNugget("User mag kurze Antworten", "nugget.extract");
        mem.recordUse(n.id);
        mem.recordHit(n.id);
        mem.save(service.memoryFile());
        String listing = new ChatCommandService(service).dispatch("/memory").reply();
        assertTrue(listing.contains("2 entries"));
        assertTrue(listing.contains("#" + n.id));
        assertTrue(listing.contains("nugget:nugget.extract"));
        assertTrue(listing.contains("uses=1, hits=1"));
    }

    @Test
    public void testDecideRecordsStandingDecision() throws Exception {
        Path project = Files.createTempDirectory("chatcmd-decide");
        HarnessService service = serviceWithMemory(project, "user", "hallo");
        CommandResult r = new ChatCommandService(service).dispatch("/decide ab jetzt nach jedem Schritt commiten");
        assertTrue(r.handled());
        assertTrue(r.reply().contains("Decision #"));
        ProjectMemory mem = ProjectMemory.load(service.memoryFile());
        Nugget d = mem.getEntries().stream().filter(n -> n.kind == ProjectMemory.Kind.DECISION).findFirst().orElse(null);
        assertNotNull(d, "decision persisted in memory.json");
        assertEquals(d.text, "ab jetzt nach jedem Schritt commiten");
        assertEquals(d.source, "user:/decide");
        assertEquals(d.ttlMs, ProjectMemory.DEFAULT_TTL_DECISION_MS);
        // visible in the listing with its kind label
        String listing = new ChatCommandService(service).dispatch("/memory").reply();
        assertTrue(listing.contains("decision:user:/decide"));
        // usage without text
        assertTrue(new ChatCommandService(service).dispatch("/decide").reply().contains("Usage"));
    }

    @Test
    public void testDecisionsListsOnlyDecisionsAsIdText() throws Exception {
        Path project = Files.createTempDirectory("chatcmd-decisions");
        HarnessService service = serviceWithMemory(project, "user", "hallo", "assistant", "hi");
        ChatCommandService cmds = new ChatCommandService(service);
        cmds.dispatch("/decide nicht mehr commiten");
        CommandResult r = cmds.dispatch("/decide nach jedem Schritt commiten");
        assertTrue(r.handled());
        String listing = cmds.dispatch("/decisions").reply();
        String[] lines = listing.split("\n");
        assertEquals(lines.length, 2, "only the two decisions, chat entries not listed");
        for (String line : lines) {
            assertTrue(line.matches("\\d+ .*"), "each line is '<id> <text>': " + line);
        }
        assertTrue(listing.contains("nicht mehr commiten"));
        assertTrue(listing.contains("nach jedem Schritt commiten"));
        assertFalse(listing.contains("hallo"), "chat entries are not in /decisions");
        // the id from the listing can be used with /forget directly
        long id = Long.parseLong(lines[0].split(" ")[0]);
        assertTrue(cmds.dispatch("/forget " + id).reply().contains("forgotten"));
        assertEquals(cmds.dispatch("/decisions").reply().split("\n").length, 1);
        // no decisions -> deterministic empty reply
        Path empty = Files.createTempDirectory("chatcmd-decisions-empty");
        assertEquals(new ChatCommandService(new HarnessService(empty.toFile())).dispatch("/decisions").reply(),
                "No decisions.");
    }

    @Test
    public void testHelpAndUnknownCommand() throws Exception {
        Path project = Files.createTempDirectory("chatcmd-help");
        ChatCommandService cmds = new ChatCommandService(serviceWithMemory(project));
        String help = cmds.dispatch("/help").reply();
        assertTrue(help.contains("/clear"));
        assertTrue(help.contains("/forget"));
        assertTrue(help.contains("/decide"));
        assertTrue(help.contains("/decisions"));
        assertTrue(help.contains("/memory"));
        CommandResult unknown = cmds.dispatch("/dance");
        assertTrue(unknown.handled(), "unknown / command is still a command (no run)");
        assertTrue(unknown.reply().contains("Unknown command"));
    }

    @Test
    public void testEmptyMemoryListing() throws Exception {
        Path project = Files.createTempDirectory("chatcmd-empty");
        HarnessService service = new HarnessService(project.toFile());
        String reply = new ChatCommandService(service).dispatch("/memory").reply();
        assertTrue(reply.contains("empty"));
    }
}
