/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

/**
 * A HarnessAction - the object-oriented steering object a calculator returns inside its
 * {@link CalcResult} (CONCEPT_Harness.md §15.5). Instead of the driver parsing decision strings
 * in if-cascades, the calculator CREATES the action and the harness driver EXECUTES it
 * polymorphically ({@link #execute(HarnessDriver, TaskData, CalcResult)}).
 *
 * <p>The interface stays in this package; the built-in representatives live in
 * {@code de.jare.harness.main.actions} (None, Split, ReasoningSubtask, Retry, Abort - factory
 * {@code Actions.fromDecision}). New actions are added there as new implementations of this
 * interface. Not every calculator needs a semantically matching action - a plain result is a
 * {@code None}. But every control flow of the automaton is an action the driver can execute.
 * The legacy {@code decision} string stays as the serialized/log form only.</p>
 *
 * @author Janusch Rentenatus
 */
public interface HarnessAction {

    /** What the driver's process loop does after executing the action. */
    enum Control {
        /** proceed with the next pipeline step */
        CONTINUE,
        /** skip the rest of this loop iteration, next iteration */
        LOOP,
        /** the task is finished, leave the loop */
        STOP
    }

    /** The action type name (same vocabulary as the legacy decision string). */
    String type();

    /** Executes this action on the driver; returns how the process loop continues. */
    Control execute(HarnessDriver driver, TaskData task, CalcResult result);
}
