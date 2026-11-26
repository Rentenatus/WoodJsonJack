/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

/**
 * The HarnessDriver - the operations a {@link HarnessAction} can execute on the automaton.
 * Implemented by the {@link HarnessEngine}; keeps the steering object-oriented (the action
 * calls driver operations, the driver never parses decision strings). SPLIT is no legacy
 * driver operation anymore - it runs as {@code LoopSplit} on the flow channel (§15.6).
 *
 * @author Janusch Rentenatus
 */
public interface HarnessDriver {

    /** COMPLETE/incomplete: add one reasoning subtask. */
    void addReasoningSubtask(TaskData task);

    /** CHECK/retry: count the retry and bump the attempt with a prompt supplement. */
    void retry(TaskData task, String reason);

    /** ABORT: hard-stop the task. */
    void abort(TaskData task, String reason);
}
