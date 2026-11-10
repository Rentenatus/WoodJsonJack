/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import java.util.List;

/**
 * The HarnessDriver - the operations a {@link HarnessAction} can execute on the automaton.
 * Implemented by the {@link HarnessEngine}; keeps the steering object-oriented (the action
 * calls driver operations, the driver never parses decision strings).
 *
 * @author Janusch Rentenatus
 */
public interface HarnessDriver {

    /** SPLIT: rewrite the task as summary and process one subtask per sub-prompt (or parts). */
    void splitInto(TaskData task, List<String> prompts, int parts);

    /** COMPLETE/incomplete: add one reasoning subtask. */
    void addReasoningSubtask(TaskData task);

    /** CHECK/retry: count the retry and bump the attempt with a prompt supplement. */
    void retry(TaskData task, String reason);

    /** ABORT: hard-stop the task. */
    void abort(TaskData task, String reason);
}
