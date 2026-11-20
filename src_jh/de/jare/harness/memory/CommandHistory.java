/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.memory;

import de.jare.harness.TaskCommand;
import de.jare.harness.TaskData;

import java.util.ArrayList;
import java.util.List;

/**
 * The command history of one run (CONCEPT_Harness.md §18): the dispatcher AND the audit
 * log in one. Every {@link TaskCommand} is applied here - {@link #apply} executes it on
 * the target task, records an {@link Entry} and the command itself fires the change
 * event (exactly once per command, also for composites like {@code SplitInto}).
 *
 * <p>One history per {@link MemoryQuest} (per user call, RAM only - §18.4; NOT persisted
 * to {@code memory.json}, the {@code calls[]} projection already stores calculator
 * results). The entry's origin is the loop triple {@code loopId/loopItemId/trigger}
 * (same key as {@code CallLogEntry}); {@code invertible} starts as the command's own
 * flag (default {@code true}, §18.5) and is later demoted by a scan against the current
 * loop config - pull, never push. No undo yet (§18.3): entries are data, so a later undo
 * only has to skip non-invertible entries.</p>
 *
 * @author Janusch Rentenatus
 */
public class CommandHistory {

    /** One recorded command: the command data + origin triple + invertibility + description. */
    public static final class Entry {
        public final long seq;
        public final TaskCommand command;
        public final String loopId;
        public final String loopItemId;
        public final String trigger;
        public final boolean invertible;
        public final String description;

        Entry(long seq, TaskCommand command, String loopId, String loopItemId, String trigger) {
            this.seq = seq;
            this.command = command;
            this.loopId = loopId;
            this.loopItemId = loopItemId;
            this.trigger = trigger;
            this.invertible = command.invertible();
            this.description = command.describe();
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private long seq;

    /**
     * Executes the command on the target task and records it. When no history is wired
     * (ad-hoc engines in tests), the caller executes the command directly without recording.
     */
    public Entry apply(TaskCommand cmd, TaskData target, String loopId, String loopItemId, String trigger) {
        cmd.execute(target);
        Entry e = new Entry(++seq, cmd, loopId, loopItemId, trigger);
        entries.add(e);
        return e;
    }

    /** Executes without recording (no history wired). */
    public static void applyDirect(TaskCommand cmd, TaskData target) {
        cmd.execute(target);
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public int size() {
        return entries.size();
    }
}
