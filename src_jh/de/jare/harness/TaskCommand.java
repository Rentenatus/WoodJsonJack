/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

/**
 * One mutation of a {@link TaskData} as DATA (CONCEPT_Harness.md §18): TaskData is never
 * mutated directly - every change is a {@code TaskCommand} executed through the command
 * dispatcher ({@code CommandHistory}), which records it in the history and fires the
 * change event exactly once per command. Commands are plain data (records), so a later
 * undo can invert them without a redesign; {@link #invertible()} starts {@code true} and
 * is demoted only by a history scan against the loop config or by an explicit engine
 * mark (§18.5). The implementations live in {@link TaskCommands} (same package, so they
 * may call the package-private TaskData mutators).
 *
 * @author Janusch Rentenatus
 */
public interface TaskCommand {

    /** Applies the mutation to the task (package-private mutators; the caller fires once). */
    void execute(TaskData task);

    /** One-line audit description for the history entry. */
    String describe();

    /**
     * Whether this command could be inverted by a later undo. Default {@code true}
     * (§18.5): demotion happens via history scan (origin loop item gone from the
     * current loop config) or an explicit engine mark - never guessed here.
     */
    default boolean invertible() {
        return true;
    }
}
