/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.io.JsonNodes;
import de.jare.jsoncasted.lang.JsonNode;

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
    private final String message;                                   // calculator message (may be empty)
    private final java.util.Map<String, String> payload;            // calculator payload content (may be empty)
    private final Parent1 parent1;     // redundant
    private final Long parent2;        // link only - parent3+ walkable via parent2's parent1
    private final String loopId;       // loop the item ran in (null outside a configured loop, §15.6 metrics)
    private final String loopItemId;   // the fired loop item id
    private final String trigger;      // START|EXECUTE|CATCH|CLOSE of the fired item
    private final String paramError;   // offending param key when this call ended in a PARAMETER error

    public CallLogEntry(long aufrufId, long taskId, String taskType, Kind kind, String slotId,
                        String modelVersion, int attempt, long dauerMs, int tokensIn, int tokensOut,
                        Integer score, Status status, String decision, Parent1 parent1, Long parent2) {
        this(aufrufId, taskId, taskType, kind, slotId, modelVersion, attempt, dauerMs, tokensIn, tokensOut,
                score, status, decision, "", java.util.Map.of(), parent1, parent2);
    }

    public CallLogEntry(long aufrufId, long taskId, String taskType, Kind kind, String slotId,
                        String modelVersion, int attempt, long dauerMs, int tokensIn, int tokensOut,
                        Integer score, Status status, String decision, String message,
                        java.util.Map<String, String> payload, Parent1 parent1, Long parent2) {
        this(aufrufId, taskId, taskType, kind, slotId, modelVersion, attempt, dauerMs, tokensIn, tokensOut,
                score, status, decision, message, payload, parent1, parent2, null, null, null, null);
    }

    public CallLogEntry(long aufrufId, long taskId, String taskType, Kind kind, String slotId,
                        String modelVersion, int attempt, long dauerMs, int tokensIn, int tokensOut,
                        Integer score, Status status, String decision, String message,
                        java.util.Map<String, String> payload, Parent1 parent1, Long parent2,
                        String loopId, String loopItemId, String trigger, String paramError) {
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
        this.message = message == null ? "" : message;
        this.payload = payload == null ? java.util.Map.of() : payload;
        this.parent1 = parent1;
        this.parent2 = parent2;
        this.loopId = loopId;
        this.loopItemId = loopItemId;
        this.trigger = trigger;
        this.paramError = paramError;
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
    /** The calculator's message content of this call ("" if none). */
    public String getMessage() { return message; }
    /** The calculator's payload content of this call (never null). */
    public java.util.Map<String, String> getPayload() { return payload; }
    public Parent1 getParent1() { return parent1; }
    public Long getParent2() { return parent2; }
    /** The loop this call ran in (null outside a configured loop); metrics key prefix {@code "loop:"}. */
    public String getLoopId() { return loopId; }
    public String getLoopItemId() { return loopItemId; }
    public String getTrigger() { return trigger; }
    /** The offending param key when this call failed with a PARAMETER error, else null. */
    public String getParamError() { return paramError; }

    /** Serializes this entry to its JsonNode form (persisted in the call log / node log member). */
    public JsonNode toJson() {
        JsonNode n = JsonNode.objectNode();
        n.put("aufrufId", JsonNode.longNode(aufrufId));
        n.put("taskId", JsonNode.longNode(taskId));
        n.put("taskType", JsonNode.stringNode(taskType));
        n.put("kind", JsonNode.stringNode(kind.name()));
        n.put("slotId", JsonNode.stringNode(slotId));
        n.put("modelVersion", JsonNode.stringNode(modelVersion));
        n.put("attempt", JsonNode.longNode(attempt));
        n.put("dauerMs", JsonNode.longNode(dauerMs));
        n.put("tokensIn", JsonNode.longNode(tokensIn));
        n.put("tokensOut", JsonNode.longNode(tokensOut));
        if (score != null) {
            n.put("score", JsonNode.longNode(score));
        }
        n.put("status", JsonNode.stringNode(status.name()));
        n.put("decision", JsonNode.stringNode(decision));
        if (!message.isEmpty()) {
            n.put("message", JsonNode.stringNode(message));
        }
        if (!payload.isEmpty()) {
            JsonNode p = JsonNode.objectNode();
            for (var e : payload.entrySet()) {
                // truncated for persistence - rawAnswer can be long and would blow up the
                // saved log on long runs (OOM in the JSON writer)
                String v = e.getValue();
                p.put(e.getKey(), JsonNode.stringNode(v != null && v.length() > 500
                        ? v.substring(0, 500) + "…(" + v.length() + " chars)" : v));
            }
            n.put("payload", p);
        }
        if (parent1 != null) {
            JsonNode p1 = JsonNode.objectNode();
            p1.put("aufrufId", JsonNode.longNode(parent1.aufrufId));
            if (parent1.score != null) {
                p1.put("score", JsonNode.longNode(parent1.score));
            }
            p1.put("status", JsonNode.stringNode(parent1.status.name()));
            n.put("parent1", p1);
        }
        if (parent2 != null) {
            n.put("parent2", JsonNode.longNode(parent2));
        }
        if (loopId != null) {
            n.put("loopId", JsonNode.stringNode(loopId));
        }
        if (loopItemId != null) {
            n.put("loopItemId", JsonNode.stringNode(loopItemId));
        }
        if (trigger != null) {
            n.put("trigger", JsonNode.stringNode(trigger));
        }
        if (paramError != null) {
            n.put("paramError", JsonNode.stringNode(paramError));
        }
        return n;
    }

    /** Reads an entry back from its JsonNode form. */
    public static CallLogEntry fromJson(JsonNode n) {
        Parent1 p1 = null;
        JsonNode p1n = JsonNodes.child(n, "parent1");
        if (p1n != null) {
            p1 = new Parent1(
                    JsonNodes.longOf(p1n, "aufrufId", 0),
                    JsonNodes.child(p1n, "score") == null ? null : JsonNodes.child(p1n, "score").asLong().intValue(),
                    Status.valueOf(JsonNodes.text(p1n, "status", "DONE")));
        }
        Long p2 = JsonNodes.child(n, "parent2") == null ? null : JsonNodes.child(n, "parent2").asLong();
        java.util.Map<String, String> payload = new java.util.LinkedHashMap<>();
        JsonNode pn = JsonNodes.child(n, "payload");
        if (pn != null && pn.isObject()) {
            for (var e : pn.asObjectValues().entrySet()) {
                payload.put(e.getKey(), e.getValue() == null || e.getValue().isNull() ? "" : e.getValue().asText());
            }
        }
        return new CallLogEntry(
                JsonNodes.longOf(n, "aufrufId", 0), JsonNodes.longOf(n, "taskId", 0), JsonNodes.text(n, "taskType", ""),
                Kind.valueOf(JsonNodes.text(n, "kind", "WORK")), JsonNodes.text(n, "slotId", ""),
                JsonNodes.text(n, "modelVersion", ""), (int) JsonNodes.longOf(n, "attempt", 0),
                JsonNodes.longOf(n, "dauerMs", 0), (int) JsonNodes.longOf(n, "tokensIn", 0), (int) JsonNodes.longOf(n, "tokensOut", 0),
                JsonNodes.child(n, "score") == null ? null : JsonNodes.child(n, "score").asLong().intValue(),
                Status.valueOf(JsonNodes.text(n, "status", "DONE")), JsonNodes.text(n, "decision", ""),
                JsonNodes.text(n, "message", ""), payload, p1, p2,
                JsonNodes.text(n, "loopId", null), JsonNodes.text(n, "loopItemId", null),
                JsonNodes.text(n, "trigger", null), JsonNodes.text(n, "paramError", null));
    }

}
