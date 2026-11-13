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
import de.jare.harness.TaskData;
import java.util.List;

/**
 * SPLIT: the task becomes a summary over N new subtasks - the action carries the N
 * atomic sub-prompts (fallback: {@code parts} generated part titles).
 *
 * @author Janusch Rentenatus
 */
public record Split(List<String> prompts, int parts) implements HarnessAction {
    @Override public String type() { return "split"; }
    @Override public Control execute(HarnessDriver d, TaskData t, CalcResult r) {
        d.splitInto(t, prompts == null || prompts.isEmpty() ? null : prompts, parts);
        return Control.STOP;
    }
}
