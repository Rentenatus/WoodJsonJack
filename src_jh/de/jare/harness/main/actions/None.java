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
 * No steering - the plain result of a calculator (no-op action, guards against NPE).
 *
 * @author Janusch Rentenatus
 */
public record None() implements HarnessAction {
    @Override public String type() { return "none"; }
    @Override public Control execute(HarnessDriver d, TaskData t, CalcResult r) { return Control.CONTINUE; }
    @Override public de.jare.harness.LoopAction loopAction() { return new LoopContinue(); }
    @Override public Control control() { return Control.CONTINUE; }
}
