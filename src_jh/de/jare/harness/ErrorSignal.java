/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

/**
 * An error as DATA, never as a Throwable crossing the loop boundary
 * (CONCEPT_Harness.md §15.6, docs/working/DESIGN_Loops.md §5). Carried by
 * {@code LoopThrowError}. Two severities: {@link Severity#DOMAIN} (fachlicher Fehler —
 * fires the CATCH items of the current loop) and {@link Severity#TECHNICAL}
 * (Störung/Bug — bypasses CATCH, hard-fails the quest, unwinds to the top).
 *
 * @author Janusch Rentenatus
 */
public record ErrorSignal(Severity severity, String reason, String source) {

    public enum Severity {
        /** business error of a calculator (e.g. model JSON not parseable) - CATCH fires */
        DOMAIN,
        /** disturbance/bug (provider down, NPE, codec error) - CATCH is skipped */
        TECHNICAL
    }

    public static ErrorSignal domain(String reason, String source) {
        return new ErrorSignal(Severity.DOMAIN, reason, source);
    }

    public static ErrorSignal technical(String reason, String source) {
        return new ErrorSignal(Severity.TECHNICAL, reason, source);
    }

    /** Wraps any Throwable at the loop boundary into a TECHNICAL signal. */
    public static ErrorSignal fromThrowable(Throwable t, String source) {
        String reason = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        return new ErrorSignal(Severity.TECHNICAL, reason + " (" + t.getClass().getSimpleName() + ")", source);
    }

    /**
     * Maps the {@link HarnessException} vocabulary (§13) onto the two severities:
     * SCHEMA_VIOLATION / LIMIT / ABORTED / PARAMETER are business-treatable (DOMAIN,
     * catch-mapping + PARAM_ERROR metric), provider outage, timeout, tool errors and
     * everything unknown are TECHNICAL.
     */
    public static ErrorSignal fromHarnessException(HarnessException e, String source) {
        Severity sev = switch (e.getType()) {
            case SCHEMA_VIOLATION, LIMIT, ABORTED, PARAMETER -> Severity.DOMAIN;
            case MODEL_UNAVAILABLE, TOOL_ERROR, TIMEOUT -> Severity.TECHNICAL;
        };
        return new ErrorSignal(sev, e.getMessage(), source);
    }

    public boolean isDomain() {
        return severity == Severity.DOMAIN;
    }
}
