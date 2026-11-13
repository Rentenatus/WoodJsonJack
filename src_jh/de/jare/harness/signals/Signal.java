/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.signals;

/**
 * The fixed signal vocabulary of the driver (CONCEPT_Harness.md §13): each calculator in
 * the model may carry an {@code on} mapping Signal → child calculator, invoked by the
 * driver when the signal fires (e.g. TOOL_ERROR → CHECK for a retry).
 *
 * @author Janusch Rentenatus
 */
public enum Signal {
    TOOL_RESULT,
    TOOL_ERROR,
    ANSWER_RECEIVED,
    ANSWER_VERBOSE,
    RETRY
}
