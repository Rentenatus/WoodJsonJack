/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.main.actions;

import de.jare.harness.LoopAction;

/**
 * Enter the named subloop: pushed onto the driver's call stack (depth-checked against
 * {@code maxDepth}), executed recursively; when it breaks, control returns here.
 *
 * @author Janusch Rentenatus
 */
public record LoopSubLoop(String loopId) implements LoopAction {
    @Override public String type() { return "subloop"; }
    @Override public Flow flow() { return Flow.SUBLOOP; }
}
