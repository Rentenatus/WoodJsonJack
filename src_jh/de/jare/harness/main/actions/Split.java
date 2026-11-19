/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.main.actions;

import de.jare.harness.CalcResult;
import de.jare.harness.HarnessAction;
import de.jare.harness.HarnessDriver;
import de.jare.harness.LoopAction;
import de.jare.harness.TaskData;
import de.jare.harness.model.Loop;
import java.util.List;

/**
 * SPLIT: the task becomes a summary over N new subtasks - the action carries the N
 * atomic sub-prompts (fallback: {@code parts} generated part titles).
 *
 * <p>Migrated to the flow channel (§15.6): {@link #loopAction()} is a {@link LoopSplit}, so the
 * engine's {@code handleSplit} runs the subloop per subtask — the legacy
 * {@code HarnessDriver.splitInto} path is gone. {@link #execute} is therefore not called by the
 * loop anymore (SPLIT flow skips the domain action to avoid a double split).</p>
 *
 * @author Janusch Rentenatus
 */
public record Split(List<String> prompts, int parts) implements HarnessAction {
    @Override public String type() { return "split"; }
    @Override public Control execute(HarnessDriver d, TaskData t, CalcResult r) {
        return Control.STOP;   // the split itself runs as LoopSplit on the flow channel
    }
    /** SPLIT as loop action: one subloop ("sub") per carried sub-prompt, parts as fallback. */
    @Override public LoopAction loopAction() {
        return new LoopSplit(prompts, Loop.SUB, parts);
    }
    @Override public Control control() { return Control.STOP; }
}
