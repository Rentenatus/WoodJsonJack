/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.prompts;

import de.jare.harness.memory.WorkCache;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PromptFormatter - resolves {@code {data.<key>}} placeholders in a prompt template against the
 * runtime WorkCache (the "data cache" of the run). A calculator that produced a value writes it
 * into the cache (e.g. scoring writes {@code count}), a following calculator formats its prompt
 * template with it: {@code "You decompose the user prompt into {data.count} atomic quests ..."}.
 * Unknown keys stay as-is (visible in the prompt, never silently dropped); a null cache leaves
 * the template unchanged.
 *
 * @author Janusch Rentenatus
 */
public final class PromptFormatter {

    private static final Pattern DATA_PLACEHOLDER = Pattern.compile("\\{data\\.([A-Za-z0-9_\\-]+)}");

    private PromptFormatter() {
    }

    /** Replaces every {@code {data.<key>}} with {@code cache.get(key)}; unknown keys stay literal. */
    public static String format(String template, WorkCache cache) {
        if (template == null || cache == null) {
            return template;
        }
        Matcher m = DATA_PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = cache.get(m.group(1));
            m.appendReplacement(sb, value == null ? Matcher.quoteReplacement(m.group(0))
                    : Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Only the AUFTRAG (user task) segment of the assembled prompt list, as plain text.
     * Scoring/decompose calculators must see ONLY the user's actual prompt - sending the
     * HISTORY/DATA/SYSTEM segments too makes small models count or echo the segment headers
     * ("history:call-1", "memory:chat-12", ...) as quests. Falls back to all prompts when no
     * AUFTRAG segment is present.
     */
    public static java.util.List<String> userPromptOnly(de.jare.harness.CalcContext ctx) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String p : ctx.prompts()) {
            if (p != null && p.startsWith("AUFTRAG:")) {
                int nl = p.indexOf('\n');
                String text = nl < 0 ? p.substring("AUFTRAG:".length()) : p.substring(nl + 1);
                // the conversation segment carries labeled lines (AUFTRAG/ANTWORT/BEWERTUNG/ZUSATZ) -
                // take ONLY the AUFTRAG line, stop at the next label
                StringBuilder line = new StringBuilder();
                for (String ln : text.split("\n", -1)) {
                    if (ln.startsWith("ANTWORT:") || ln.startsWith("BEWERTUNG:") || ln.startsWith("ZUSATZ:")) {
                        break;
                    }
                    if (line.length() > 0) {
                        line.append('\n');
                    }
                    line.append(ln);
                }
                String s = line.toString();
                if (s.startsWith("AUFTRAG:")) {
                    s = s.substring("AUFTRAG:".length()).strip();
                }
                out.add(s);
            }
        }
        return out.isEmpty() ? new java.util.ArrayList<>(ctx.prompts()) : out;
    }

    /**
     * The standardized in-run conversation of a task as four labeled lines - the new
     * convention for loop and TaskData: EVERY loop/execute starts with this conversation,
     * even when everything except AUFTRAG is empty in the first round:
     * <pre>
     * AUFTRAG:    original user prompt (task.getPrompt(), not the label title)
     * ANTWORT:    current answer (leaf artifacts / summary, empty before WORK)
     * BEWERTUNG:  the EVALUATE results of this task (dimension = score verdict), empty before EVALUATE
     * ZUSATZ:     promptSupplement - the improvement note of a failed CHECK round
     * </pre>
     */
    public static java.util.List<String> conversationLines(de.jare.harness.TaskView task) {
        java.util.List<String> out = new java.util.ArrayList<>();
        String auftrag = task.getPrompt() == null ? "" : task.getPrompt();
        String antwort = de.jare.harness.Answers.answerOf(task);
        StringBuilder bewertung = new StringBuilder();
        for (de.jare.harness.TaskData.CalcCall c : task.getCalls()) {
            if ("EVALUATE".equalsIgnoreCase(c.getKind())) {
                bewertung.append(c.getPayload().getOrDefault("dimension", c.getDecision()))
                        .append(" = score ").append(c.getScore())
                        .append(' ').append(c.getPayload().getOrDefault("verdict", ""))
                        .append('\n');
            }
        }
        String zusatz = task.getPromptSupplement() == null ? "" : task.getPromptSupplement();
        out.add("AUFTRAG: " + auftrag.strip());
        out.add("ANTWORT: " + antwort.strip());
        out.add("BEWERTUNG: " + bewertung.toString().strip());
        out.add("ZUSATZ: " + zusatz.strip());
        return out;
    }

    /** The conversation as labeled prompt segments (one per line, via {@link #conversationLines}). */
    public static java.util.List<String> conversation(de.jare.harness.TaskView task) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String line : conversationLines(task)) {
            int sp = line.indexOf(' ');
            out.add(line.substring(0, sp) + ":\n" + line.substring(sp + 1));
        }
        return out;
    }

    /**
     * The conversation as ONE prompt segment (category AUFTRAG, ref "conversation") carrying the
     * four labeled lines - appended to the assembled prompt list of every execute() so each
     * loop iteration starts with the full conversation.
     */
    public static PromptSegment conversationSegment(de.jare.harness.TaskView task) {
        return PromptSegment.of(PromptSegment.Category.AUFTRAG, "conversation",
                String.join("\n", conversationLines(task)));
    }
}
