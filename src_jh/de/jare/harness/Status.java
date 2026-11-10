/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

/**
 * Lifecycle status of a {@link TaskData}. EVALUATE sets it, CLOSE rolls it upwards
 * (CONCEPT_Harness.md §3).
 *
 * @author Janusch Rentenatus
 */
public enum Status {
    PENDING,
    RUNNING,
    RETRY,
    DONE,
    FAILED,
    ABORTED,
    /** the input was not usable (e.g. nothing to split) - no error, just no work */
    INVALID
}
