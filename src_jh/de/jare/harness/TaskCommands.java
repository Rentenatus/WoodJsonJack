/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * The command catalog (CONCEPT_Harness.md §18): the {@link TaskCommand} implementations.
 * They live in the {@code de.jare.harness} package on purpose - the {@link TaskData}
 * mutators are package-private, so only commands (and tests of the same package) can
 * change the tree; every command fires the change event exactly once after applying.
 *
 * @author Janusch Rentenatus
 */
public final class TaskCommands {

    private TaskCommands() {
    }

    public record SetStatus(Status status) implements TaskCommand {
        @Override public void execute(TaskData t) { t.setStatus(status); t.fire(); }
        @Override public String describe() { return "setStatus(" + status + ")"; }
    }

    public record SetScore(int score) implements TaskCommand {
        @Override public void execute(TaskData t) { t.setScore(score); t.fire(); }
        @Override public String describe() { return "setScore(" + score + ")"; }
    }

    public record IncrementAttempt() implements TaskCommand {
        @Override public void execute(TaskData t) { t.incrementAttempt(); t.fire(); }
        @Override public String describe() { return "incrementAttempt()"; }
    }

    public record AppendPromptSupplement(String text) implements TaskCommand {
        @Override public void execute(TaskData t) { t.appendPromptSupplement(text); t.fire(); }
        @Override public String describe() { return "appendPromptSupplement(" + abbrev(text) + ")"; }
    }

    public record RewriteAsSummary() implements TaskCommand {
        @Override public void execute(TaskData t) { t.rewriteAsSummary(); t.fire(); }
        @Override public String describe() { return "rewriteAsSummary()"; }
    }

    public record CloseTask(String artifact) implements TaskCommand {
        @Override public void execute(TaskData t) { t.close(artifact); t.fire(); }
        @Override public String describe() { return "close(" + abbrev(artifact) + ")"; }
    }

    public record LogCall(CallLogEntry entry) implements TaskCommand {
        @Override public void execute(TaskData t) { t.logCall(entry); t.fire(); }
        @Override public String describe() { return "logCall(" + entry.getAufrufId() + ")"; }
    }

    public record AppendCalcCall(String calcName, Kind kind, String slotId, CalcResult res,
                                 List<String> input) implements TaskCommand {
        @Override public void execute(TaskData t) { t.appendCalcCall(calcName, kind, slotId, res, input); t.fire(); }
        @Override public String describe() { return "appendCalcCall(" + calcName + ")"; }
    }

    /** SPLIT / COMPLETE / REASONING: adds one subtask; the created child is exposed via {@link #created()}. */
    public static final class AddSubtask implements TaskCommand {
        private final long childId;
        private final String taskType;
        private final String title;
        private TaskData created;

        public AddSubtask(long childId, String taskType, String title) {
            this.childId = childId;
            this.taskType = taskType;
            this.title = title;
        }

        @Override public void execute(TaskData t) {
            created = t.addSubtask(childId, taskType, title);
            t.fire();
        }

        @Override public String describe() { return "addSubtask(" + childId + ", " + abbrev(title) + ")"; }

        /** The child this command created (valid after {@link #execute}). */
        public TaskData created() { return created; }
    }

    /**
     * The composite SPLIT command (§18.2): {@code rewriteAsSummary} + N x {@code addSubtask}
     * + status in ONE history entry with ONE change event. The command covers only the
     * tree mutation - running the N subloops stays engine flow. The created children are
     * exposed via {@link #children()} after execution.
     */
    public static final class SplitInto implements TaskCommand {
        private final String loopId;
        private final List<String> prompts;
        private final String taskType;
        private final LongSupplier idSupplier;
        private final List<TaskData> children = new ArrayList<>();

        public SplitInto(String loopId, List<String> prompts, String taskType, LongSupplier idSupplier) {
            this.loopId = loopId;
            this.prompts = prompts == null ? List.of() : List.copyOf(prompts);
            this.taskType = taskType;
            this.idSupplier = idSupplier;
        }

        @Override public void execute(TaskData t) {
            t.rewriteAsSummary();
            for (String p : prompts) {
                children.add(t.addSubtask(idSupplier.getAsLong(), taskType, p));
            }
            t.fire();   // exactly one change event for the whole composite
        }

        @Override public String describe() {
            return "splitInto(loop=\"" + loopId + "\", parts=" + prompts.size() + ")";
        }

        public String loopId() { return loopId; }

        /** The children created by this split (valid after {@link #execute}). */
        public List<TaskData> children() { return List.copyOf(children); }
    }

    private static String abbrev(String s) {
        if (s == null) {
            return "";
        }
        String one = s.replace('\n', ' ');
        return one.length() <= 40 ? one : one.substring(0, 37) + "...";
    }
}
