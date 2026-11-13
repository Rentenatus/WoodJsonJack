/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.main.actions;

import de.jare.harness.ErrorSignal;
import de.jare.harness.HarnessAction;
import de.jare.harness.LoopAction;

/**
 * Factory for the {@link LoopAction} representatives (package
 * {@code de.jare.harness.main.actions} - the interface stays in {@code de.jare.harness}).
 * Maps the legacy {@link HarnessAction.Control} / decision strings onto the flow channel,
 * so existing calculators keep working until they set a LoopAction explicitly.
 *
 * @author Janusch Rentenatus
 */
public final class LoopActions {

    private LoopActions() {
    }

    /** Never null - the default flow is {@link LoopContinue}. */
    public static LoopAction fromControl(HarnessAction.Control control) {
        if (control == null) {
            return new LoopContinue();
        }
        return switch (control) {
            case CONTINUE -> new LoopContinue();
            case LOOP -> new LoopRestart();
            case STOP -> new LoopBreak();
        };
    }

    /** Legacy mapping: decision string to flow. "abort" throws a DOMAIN signal (CATCH/ABORT quest handles it). */
    public static LoopAction fromDecision(String decision, String message) {
        if (decision == null) {
            return new LoopContinue();
        }
        return switch (decision) {
            case "abort" -> new LoopThrowError(ErrorSignal.domain(
                    message == null || message.isBlank() ? "abort" : message, "decision"));
            case "retry", "incomplete" -> new LoopRestart();
            case "split" -> new LoopContinue();
            default -> new LoopContinue();
        };
    }
}
