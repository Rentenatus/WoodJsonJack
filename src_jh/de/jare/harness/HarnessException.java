/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

/**
 * Driver error vocabulary for the catch-mapping of calculators
 * (CONCEPT_Harness.md §13): the engine catches the leaf error, logs it and invokes
 * the configured catch calculator (e.g. fallback to the deterministic slot).
 *
 * @author Janusch Rentenatus
 */
public class HarnessException extends RuntimeException {

    public enum Type {
        MODEL_UNAVAILABLE,   // provider outage
        SCHEMA_VIOLATION,    // JSON parse fail of the model answer
        TOOL_ERROR,
        TIMEOUT,
        LIMIT,
        ABORTED
    }

    private final Type type;

    public HarnessException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public HarnessException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public Type getType() {
        return type;
    }
}
