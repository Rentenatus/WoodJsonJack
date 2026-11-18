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
        ABORTED,
        PARAMETER            // unknown/invalid config param of a calculator (counted as PARAM_ERROR, §6)
    }

    private final Type type;
    private final String paramKey;   // for Type.PARAMETER: the offending param name, else null

    public HarnessException(Type type, String message) {
        this(type, message, null, null);
    }

    public HarnessException(Type type, String message, Throwable cause) {
        this(type, message, null, cause);
    }

    public HarnessException(Type type, String message, String paramKey) {
        this(type, message, paramKey, null);
    }

    public HarnessException(Type type, String message, String paramKey, Throwable cause) {
        super(message, cause);
        this.type = type;
        this.paramKey = paramKey;
    }

    public Type getType() {
        return type;
    }

    /** The offending config param name when {@link #getType()} is {@link Type#PARAMETER}, else null. */
    public String getParamKey() {
        return paramKey;
    }
}
