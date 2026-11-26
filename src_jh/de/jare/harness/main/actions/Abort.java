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
 * ABORT said "abort": hard-stop the task.
 *
 * @author Janusch Rentenatus
 */
public record Abort(String reason) implements HarnessAction {
    @Override public String type() { return "abort"; }
    @Override public Control execute(HarnessDriver d, TaskData t, CalcResult r) {
        d.abort(t, reason);
        return Control.STOP;
    }
    /** abort ist ein DOMAIN-ErrorSignal auf dem Flow-Kanal: CATCH feuert, kein harter Stopp (§15.6). */
    @Override public de.jare.harness.LoopAction loopAction() {
        return new LoopThrowError(de.jare.harness.ErrorSignal.domain(
                reason == null || reason.isBlank() ? "abort" : reason, "action:abort"));
    }
    @Override public Control control() { return Control.STOP; }
}
