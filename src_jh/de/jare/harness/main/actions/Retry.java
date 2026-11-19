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

/**
 * CHECK said "retry": count the retry, bump the attempt, loop again with a supplement.
 *
 * @author Janusch Rentenatus
 */
public record Retry(String reason) implements HarnessAction {
    @Override public String type() { return "retry"; }
    @Override public Control execute(HarnessDriver d, TaskData t, CalcResult r) {
        d.retry(t, reason);
        return Control.LOOP;
    }
    /** retry steuert den Loop als RESTART (§15.6). */
    @Override public de.jare.harness.LoopAction loopAction() { return new LoopRestart(); }
    @Override public Control control() { return Control.LOOP; }
}
