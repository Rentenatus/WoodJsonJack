/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

/**
 * The FLOW channel of a calculator result, next to the domain {@link HarnessAction}
 * (CONCEPT_Harness.md §15.6, docs/working/DESIGN_Loops.md §4): where the loop goes after
 * this item. The interface stays in this package; the built-in representatives live in
 * {@code de.jare.harness.main.actions} ({@code LoopContinue}, {@code LoopRestart},
 * {@code LoopBreak}, {@code LoopSubLoop}, {@code LoopThrowError} - factory
 * {@code LoopActions}). A calculator that steers nothing returns the default
 * {@code LoopContinue}.
 *
 * @author Janusch Rentenatus
 */
public interface LoopAction {

    /** What the driver's loop does after this item. */
    enum Flow {
        /** next item of the current loop */
        CONTINUE,
        /** restart the current loop at its first EXECUTE item (START does not fire again) */
        RESTART,
        /** leave the current loop (its CLOSE items fire), return to the caller loop's next item */
        BREAK,
        /** push the named loop onto the call stack and enter it (depth-checked) */
        SUBLOOP,
        /** error signal: DOMAIN fires CATCH of the current loop, TECHNICAL unwinds to the top */
        THROW
    }

    /** The action type name (log form). */
    String type();

    /** The flow effect the driver interprets. */
    Flow flow();
}
