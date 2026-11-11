/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.main.chat;

import de.jare.harness.HarnessService;
import de.jare.harness.memory.ProjectMemory;
import de.jare.harness.memory.ProjectMemory.Nugget;
import java.io.File;
import java.util.List;

/**
 * The chat command service (CONCEPT_Harness.md §17): every input starting with {@code /} is a
 * command, never a prompt - the UI stays dumb, Enter/Ask-AI hands the raw text here and this
 * service decides command vs. run. Commands operate on the project-local memory
 * ({@code <projekt>/.wood/harness/memory.json}, the file is the source of truth between runs;
 * the next run reloads it). Own service because the tendency is more and more {@code /cmd}s.
 *
 * <p>Commands: {@code /clear} (drop the whole memory), {@code /forget <id>} (drop one entry),
 * {@code /memory} (list entries with their uses/hits feedback), {@code /help}.</p>
 *
 * @author Janusch Rentenatus
 */
public class ChatCommandService {

    /**
     * The dispatch result: {@code handled=true} means the input was a command - {@code reply}
     * is the text for the UI and no harness run is started. {@code handled=false} means a
     * normal prompt - {@code prompt} is passed to the harness run unchanged.
     */
    public record CommandResult(boolean handled, String reply, String prompt) {
        public static CommandResult run(String prompt) {
            return new CommandResult(false, null, prompt);
        }

        public static CommandResult handled(String reply) {
            return new CommandResult(true, reply, null);
        }
    }

    private final HarnessService service;

    public ChatCommandService(HarnessService service) {
        this.service = service;
    }

    /**
     * Dispatches the raw user input: {@code /}-prefixed input is executed as a command,
     * everything else is returned as a normal run prompt.
     */
    public CommandResult dispatch(String input) {
        if (input == null || input.isBlank() || !input.trim().startsWith("/")) {
            return CommandResult.run(input);
        }
        String[] parts = input.trim().split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : "";
        try {
            return switch (cmd) {
                case "/help" -> CommandResult.handled(help());
                case "/clear" -> clear();
                case "/forget" -> forget(args);
                case "/memory" -> CommandResult.handled(memoryListing());
                default -> CommandResult.handled("Unknown command " + cmd + " - try /help");
            };
        } catch (Exception e) {
            return CommandResult.handled("Command " + cmd + " failed: " + e.getMessage());
        }
    }

    private String help() {
        return "Commands: /clear (clear project memory) | /forget <id> | /memory (list memory) | /help";
    }

    private CommandResult clear() throws Exception {
        ProjectMemory mem = loadMemory();
        int n = mem.size();
        mem.clear();
        save(mem);
        return CommandResult.handled("Project memory cleared (" + n + " entries removed).");
    }

    private CommandResult forget(String args) throws Exception {
        if (args.isEmpty()) {
            return CommandResult.handled("Usage: /forget <id> - see /memory for ids");
        }
        long id;
        try {
            id = Long.parseLong(args.split("\\s+")[0]);
        } catch (NumberFormatException e) {
            return CommandResult.handled("Usage: /forget <id> - '" + args + "' is not a number");
        }
        ProjectMemory mem = loadMemory();
        if (!mem.forget(id)) {
            return CommandResult.handled("No memory entry with id " + id + ".");
        }
        save(mem);
        return CommandResult.handled("Memory entry " + id + " forgotten.");
    }

    private String memoryListing() throws Exception {
        ProjectMemory mem = loadMemory();
        List<Nugget> live = mem.live(System.currentTimeMillis());
        if (live.isEmpty()) {
            return "Project memory is empty.";
        }
        StringBuilder sb = new StringBuilder("Project memory (" + live.size() + " entries):");
        for (Nugget n : live) {
            String who = n.kind == ProjectMemory.Kind.CHAT ? n.role : "nugget:" + n.source;
            String text = n.text.length() > 60 ? n.text.substring(0, 57) + "..." : n.text;
            sb.append("\n  #").append(n.id).append(" [").append(n.kind).append("] ").append(who)
                    .append(": ").append(text)
                    .append(" (uses=").append(n.uses).append(", hits=").append(n.hits).append(")");
        }
        return sb.toString();
    }

    private ProjectMemory loadMemory() throws Exception {
        File f = service == null ? null : service.memoryFile();
        return ProjectMemory.load(f);   // missing file -> empty memory
    }

    private void save(ProjectMemory mem) throws Exception {
        if (service != null) {
            mem.save(service.memoryFile());
        }
    }
}
