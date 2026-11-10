/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.main.actions;

import de.jare.harness.LoopAction;

/**
 * Default flow: next item of the current loop.
 *
 * @author Janusch Rentenatus
 */
public record LoopContinue() implements LoopAction {
    @Override public String type() { return "continue"; }
    @Override public Flow flow() { return Flow.CONTINUE; }
}
