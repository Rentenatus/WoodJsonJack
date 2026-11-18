/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.jsoncasted.lang.JsonNode;

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
        /** split: create N subtasks from the carried prompts, run one subloop (loopId, default "sub") per subtask, then aggregate the consumption deltas */
        SPLIT,
        /** error signal: DOMAIN fires CATCH of the current loop, TECHNICAL unwinds to the top */
        THROW
    }

    /** The action type name (log form). */
    String type();

    /** The flow effect the driver interprets. */
    Flow flow();

    /**
     * Extra carried data for the persisted projection of this action (the "loopAction" object of
     * a task's "call" node, CONCEPT_Harness.md §3): e.g. the split prompts of a {@code LoopSplit}
     * or the error signal of a {@code LoopThrowError}. Default null - then only type+flow are
     * projected. Custom actions override this, exactly like the calculators.
     */
    default JsonNode detail() {
        return null;
    }
}
