/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

/**
 * The twelve fixed purposes of a {@link HarnessCalculator} (CONCEPT_Harness.md §2).
 * One global interface, the kind acts as the discriminator.
 *
 * @author Janusch Rentenatus
 */
public enum Kind {
    RELEVANCE,
    SPLIT,
    COMPLETE,
    WORK,
    EVALUATE,
    CHECK,
    ABORT,
    SUMMARIZE,
    CLOSE,
    RAG_STORE,
    RAG_RETRIEVE,
    RAG_PRUNE
}
