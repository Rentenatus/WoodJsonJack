/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.model;

/**
 * The lifecycle trigger of a {@link LoopItem} (CONCEPT_Harness.md §5 Loop-Regel, §15.6,
 * docs/working/DESIGN_Loops.md §3): WHEN the driver fires the item inside its loop.
 *
 * @author Janusch Rentenatus
 */
public enum Trigger {
    /** once when the loop is entered (e.g. the ABORT check at the start) - never again, not even on restart */
    START,
    /** every iteration, in list order (default) - the algorithmic slots */
    EXECUTE,
    /** on a DOMAIN error signal (fachlicher Fehler) - technical errors bypass CATCH */
    CATCH,
    /** once when the loop is left - in a finally, so also after Break, CATCH and technical errors */
    CLOSE
}
