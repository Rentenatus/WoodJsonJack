/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.main.actions;

import de.jare.harness.ErrorSignal;
import de.jare.harness.LoopAction;

/**
 * Raise an {@link ErrorSignal} into the loop. DOMAIN fires the CATCH items of the current
 * loop (a Throw inside CATCH unwinds one loop level up); TECHNICAL bypasses CATCH, fails
 * the quest hard and unwinds to the top - no Throwable ever crosses the loop boundary.
 *
 * @author Janusch Rentenatus
 */
public record LoopThrowError(ErrorSignal signal) implements LoopAction {
    @Override public String type() { return "throw"; }
    @Override public Flow flow() { return Flow.THROW; }
}
