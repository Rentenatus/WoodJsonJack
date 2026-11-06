/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

/**
 * The one global calculator interface (CONCEPT_Harness.md §2). Every calculator answers
 * one encapsulated question; the deterministic driver treats all kinds alike and
 * serializes results polymorph via {@link #kind()}.
 *
 * @author Janusch Rentenatus
 */
public interface HarnessCalculator {

    /** The fixed purpose of this calculator as discriminator. */
    Kind kind();

    /**
     * Executes the encapsulated question for the current task.
     *
     * @param ctx the facade context - never raw access to the engine
     * @return the structured result; prompt texts never leave this boundary
     */
    CalcResult calculate(CalcContext ctx);
}
