/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.main.actions;

import de.jare.harness.LoopAction;

/**
 * Restart the current loop at its first EXECUTE item (START items do not fire again).
 *
 * @author Janusch Rentenatus
 */
public record LoopRestart() implements LoopAction {
    @Override public String type() { return "restart"; }
    @Override public Flow flow() { return Flow.RESTART; }
}
