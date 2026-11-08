/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

/**
 * Metrics-only entry of the call log (CONCEPT_Harness.md §6): never prompt texts,
 * only the structured key figure set. {@code parent1} is stored redundantly
 * (aufrufId/score/status for fast aggregation without a join), {@code parent2} is a
 * plain link - parent3 and deeper stay reachable by walking the chain, so they are
 * not stored.
 *
 * @author Janusch Rentenatus
 */
public class CallLogEntry {

    /** Redundant snapshot of the immediate parent call. */
    public static class Parent1 {
        public final long aufrufId;
        public final Integer score;
        public final Status status;

        public Parent1(long aufrufId, Integer score, Status status) {
            this.aufrufId = aufrufId;
            this.score = score;
            this.status = status;
        }
    }

    private final long aufrufId;
    private final long taskId;
    private final String taskType;
    private final Kind kind;
    private final String slotId;
    private final String modelVersion;
    private final int attempt;
    private final long dauerMs;
    private final int tokensIn;
    private final int tokensOut;
    private final Integer score;
    private final Status status;
    private final String decision;
    private final Parent1 parent1;     // redundant
    private final Long parent2;        // link only - parent3+ walkable via parent2's parent1

    public CallLogEntry(long aufrufId, long taskId, String taskType, Kind kind, String slotId,
                        String modelVersion, int attempt, long dauerMs, int tokensIn, int tokensOut,
                        Integer score, Status status, String decision, Parent1 parent1, Long parent2) {
        this.aufrufId = aufrufId;
        this.taskId = taskId;
        this.taskType = taskType;
        this.kind = kind;
        this.slotId = slotId;
        this.modelVersion = modelVersion;
        this.attempt = attempt;
        this.dauerMs = dauerMs;
        this.tokensIn = tokensIn;
        this.tokensOut = tokensOut;
        this.score = score;
        this.status = status;
        this.decision = decision;
        this.parent1 = parent1;
        this.parent2 = parent2;
    }

    public long getAufrufId() { return aufrufId; }
    public long getTaskId() { return taskId; }
    public String getTaskType() { return taskType; }
    public Kind getKind() { return kind; }
    public String getSlotId() { return slotId; }
    public String getModelVersion() { return modelVersion; }
    public int getAttempt() { return attempt; }
    public long getDauerMs() { return dauerMs; }
    public int getTokensIn() { return tokensIn; }
    public int getTokensOut() { return tokensOut; }
    public Integer getScore() { return score; }
    public Status getStatus() { return status; }
    public String getDecision() { return decision; }
    public Parent1 getParent1() { return parent1; }
    public Long getParent2() { return parent2; }
}
