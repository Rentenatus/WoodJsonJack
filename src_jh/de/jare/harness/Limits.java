/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

/**
 * The ABORT counters of one run (CONCEPT_Harness.md §2, §5 abortLimits, §14 ctx.limits).
 * The ABORT calculator is consulted in every loop; the engine also enforces them
 * deterministically as a hard stop.
 *
 * @author Janusch Rentenatus
 */
public class Limits {

    public static final String ITERATIONS = "iterations";
    public static final String DEPTH = "depth";
    public static final String RETRIES = "retries";
    public static final String TOOL_CALLS = "toolCalls";

    private final int maxIterations;
    private final int maxDepth;
    private final int maxRetries;
    private final int maxToolCalls;

    private int iterations;
    private int retries;
    private int toolCalls;
    private int extraCountersApproved;  // LIMIT-Ausnahme: bis zu 2 extra (§13)
    private String extraJustification = "";

    public Limits(int maxIterations, int maxDepth, int maxRetries, int maxToolCalls) {
        this.maxIterations = maxIterations;
        this.maxDepth = maxDepth;
        this.maxRetries = maxRetries;
        this.maxToolCalls = maxToolCalls;
    }

    public int getCounter(String name) {
        switch (name) {
            case ITERATIONS: return iterations;
            case RETRIES: return retries;
            case TOOL_CALLS: return toolCalls;
            case DEPTH: return 0; // depth is derived from the task tree, not counted here
            default: return 0;
        }
    }

    public int remaining(String name) {
        switch (name) {
            case ITERATIONS: return maxIterations - iterations;
            case RETRIES: return maxRetries - retries;
            case TOOL_CALLS: return maxToolCalls - toolCalls;
            case DEPTH: return maxDepth;
            default: return 0;
        }
    }

    public void countIteration() {
        iterations++;
    }

    public void countRetry() {
        retries++;
    }

    public void countToolCall() {
        toolCalls++;
    }

    public boolean isAborted(int taskDepth) {
        return iterations >= maxIterations || retries >= maxRetries
                || toolCalls >= maxToolCalls + extraCountersApproved || taskDepth >= maxDepth;
    }

    /**
     * LIMIT-Ausnahme (§13): ein Kalkulator kann im Ausnahmefall bis zu 2 extra Zähler
     * genehmigen, wenn er es begründet - die Begründung landet im Log.
     *
     * @return true when approved (n ≤ 2 and not already exhausted)
     */
    public boolean approveExtraCounters(int n, String justification) {
        if (n < 1 || n > 2 || extraCountersApproved + n > 2) {
            return false;
        }
        extraCountersApproved += n;
        extraJustification = justification;
        return true;
    }

    public int getExtraCountersApproved() {
        return extraCountersApproved;
    }

    public String getExtraJustification() {
        return extraJustification;
    }

    /** Immutable point-in-time view of the counters (§15.6: consumption inheritance/deltas). */
    public record Snapshot(int iterations, int retries, int toolCalls) {
    }

    /** Difference of two snapshots - the consumption of a loop run. */
    public record Delta(int iterations, int retries, int toolCalls) {
        public Delta plus(Delta o) {
            return new Delta(iterations + o.iterations, retries + o.retries, toolCalls + o.toolCalls);
        }

        @Override
        public String toString() {
            return "iterations+" + iterations + ",retries+" + retries + ",toolCalls+" + toolCalls;
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(iterations, retries, toolCalls);
    }

    public Delta deltaSince(Snapshot before) {
        return new Delta(iterations - before.iterations(), retries - before.retries(),
                toolCalls - before.toolCalls());
    }
}
