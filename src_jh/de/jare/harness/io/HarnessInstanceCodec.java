/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.io;

import de.jare.harness.CallLogEntry;
import de.jare.harness.HarnessInstance;
import de.jare.harness.Kind;
import de.jare.harness.Status;
import de.jare.harness.TaskNode;
import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.io.JsonNodeWriter;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.lang.JsonNode;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Codec for the running instance (CONCEPT_Harness.md §5, §6): task tree + persisted
 * nextId counters + metrics-only call log, saved as {@code <projekt>/.wood/harness.json}
 * via write-temp-rename. On load the defensive id reconciliation runs:
 * nextId = max(persisted nextId, highest id in tree + log).
 *
 * @author Janusch Rentenatus
 */
public final class HarnessInstanceCodec {

    private HarnessInstanceCodec() {
    }

    public static HarnessInstance load(File file) throws IOException, JsonParseException {
        JsonNode root = JsonParserService.parse(file, JsonDebugLevel.SIMPLE).getRoot();
        return fromJson(root);
    }

    public static void save(HarnessInstance instance, File file) throws IOException, JsonParseException {
        AtomicFileWrite.writeString(file.toPath(), JsonNodeWriter.writeToString(toJson(instance)));
    }

    public static HarnessInstance fromJson(JsonNode root) {
        HarnessInstance instance = new HarnessInstance(
                text(root, "modelName", ""), text(root, "modelVersion", ""));
        long maxTaskId = 0;
        long maxAufrufId = 0;
        JsonNode task = child(root, "rootTask");
        if (task != null) {
            instance.setRoot(taskFromJson(task));
            maxTaskId = deepestTaskId(instance.getRoot());
        }
        JsonNode log = child(root, "callLog");
        if (log != null && log.isArray()) {
            for (JsonNode e : log.asArray()) {
                CallLogEntry entry = entryFromJson(e);
                instance.logCall(entry);
                maxAufrufId = Math.max(maxAufrufId, entry.getAufrufId());
                maxTaskId = Math.max(maxTaskId, entry.getTaskId());
            }
        }
        JsonNode ids = child(root, "ids");
        long persistedTask = ids == null ? 1 : longOf(ids, "nextTaskId", 1);
        long persistedAufruf = ids == null ? 1 : longOf(ids, "nextAufrufId", 1);
        instance.reconcileIds(Math.max(maxTaskId, persistedTask - 1), Math.max(maxAufrufId, persistedAufruf - 1));
        return instance;
    }

    public static JsonNode toJson(HarnessInstance instance) {
        JsonNode root = JsonNode.objectNode();
        root.put("modelName", JsonNode.stringNode(instance.getModelName()));
        root.put("modelVersion", JsonNode.stringNode(instance.getModelVersion()));
        if (instance.getRoot() != null) {
            root.put("rootTask", taskToJson(instance.getRoot()));
        }
        JsonNode log = JsonNode.arrayNode();
        for (CallLogEntry e : instance.getCallLog()) {
            log.add(entryToJson(e));
        }
        root.put("callLog", log);
        JsonNode ids = JsonNode.objectNode();
        ids.put("nextTaskId", JsonNode.longNode(instance.getNextTaskId()));
        ids.put("nextAufrufId", JsonNode.longNode(instance.getNextAufrufId()));
        root.put("ids", ids);
        return root;
    }

    private static JsonNode taskToJson(TaskNode t) {
        JsonNode n = JsonNode.objectNode();
        n.put("id", JsonNode.longNode(t.getId()));
        n.put("taskType", JsonNode.stringNode(t.getTaskType()));
        n.put("title", JsonNode.stringNode(t.getTitle()));
        n.put("status", JsonNode.stringNode(t.getStatus().name()));
        if (t.getScore() != null) {
            n.put("score", JsonNode.longNode(t.getScore()));
        }
        n.put("attempt", JsonNode.longNode(t.getAttempt()));
        n.put("summaryTask", JsonNode.booleanNode(t.isSummaryTask()));
        n.put("promptSupplement", JsonNode.stringNode(t.getPromptSupplement()));
        if (t.getArtifact() != null) {
            n.put("artifact", JsonNode.stringNode(t.getArtifact()));
        }
        JsonNode children = JsonNode.arrayNode();
        for (TaskNode c : t.getChildren()) {
            children.add(taskToJson(c));
        }
        n.put("children", children);
        JsonNode log = JsonNode.arrayNode();
        for (CallLogEntry e : t.getCalculatorLog()) {
            log.add(entryToJson(e));
        }
        n.put("calculatorLog", log);
        return n;
    }

    private static TaskNode taskFromJson(JsonNode n) {
        TaskNode t = new TaskNode(longOf(n, "id", 0), text(n, "taskType", ""), text(n, "title", ""));
        t.setStatus(Status.valueOf(text(n, "status", "PENDING")));
        if (child(n, "score") != null) {
            t.setScore(child(n, "score").asLong().intValue());
        }
        for (int i = 0; i < longOf(n, "attempt", 0); i++) {
            t.incrementAttempt();
        }
        if (child(n, "summaryTask") != null && child(n, "summaryTask").asBoolean()) {
            t.rewriteAsSummary();
        }
        t.appendPromptSupplement(text(n, "promptSupplement", ""));
        if (child(n, "artifact") != null) {
            t.close(child(n, "artifact").asText());
            t.setStatus(Status.valueOf(text(n, "status", "DONE")));
        }
        JsonNode children = child(n, "children");
        if (children != null && children.isArray()) {
            for (JsonNode c : children.asArray()) {
                TaskNode child = taskFromJson(c);
                // parent link is rebuilt via addSubtask semantics
                t.getChildren().add(child);
                setParent(child, t);
            }
        }
        JsonNode log = child(n, "calculatorLog");
        if (log != null && log.isArray()) {
            for (JsonNode e : log.asArray()) {
                t.logCall(entryFromJson(e));
            }
        }
        return t;
    }

    private static void setParent(TaskNode node, TaskNode parent) {
        node.setParentInternal(parent);
    }

    private static JsonNode entryToJson(CallLogEntry e) {
        JsonNode n = JsonNode.objectNode();
        n.put("aufrufId", JsonNode.longNode(e.getAufrufId()));
        n.put("taskId", JsonNode.longNode(e.getTaskId()));
        n.put("taskType", JsonNode.stringNode(e.getTaskType()));
        n.put("kind", JsonNode.stringNode(e.getKind().name()));
        n.put("slotId", JsonNode.stringNode(e.getSlotId()));
        n.put("modelVersion", JsonNode.stringNode(e.getModelVersion()));
        n.put("attempt", JsonNode.longNode(e.getAttempt()));
        n.put("dauerMs", JsonNode.longNode(e.getDauerMs()));
        n.put("tokensIn", JsonNode.longNode(e.getTokensIn()));
        n.put("tokensOut", JsonNode.longNode(e.getTokensOut()));
        if (e.getScore() != null) {
            n.put("score", JsonNode.longNode(e.getScore()));
        }
        n.put("status", JsonNode.stringNode(e.getStatus().name()));
        n.put("decision", JsonNode.stringNode(e.getDecision()));
        if (e.getParent1() != null) {
            JsonNode p1 = JsonNode.objectNode();
            p1.put("aufrufId", JsonNode.longNode(e.getParent1().aufrufId));
            if (e.getParent1().score != null) {
                p1.put("score", JsonNode.longNode(e.getParent1().score));
            }
            p1.put("status", JsonNode.stringNode(e.getParent1().status.name()));
            n.put("parent1", p1);
        }
        if (e.getParent2() != null) {
            n.put("parent2", JsonNode.longNode(e.getParent2()));
        }
        return n;
    }

    private static CallLogEntry entryFromJson(JsonNode n) {
        CallLogEntry.Parent1 p1 = null;
        JsonNode p1n = child(n, "parent1");
        if (p1n != null) {
            p1 = new CallLogEntry.Parent1(
                    longOf(p1n, "aufrufId", 0),
                    child(p1n, "score") == null ? null : child(p1n, "score").asLong().intValue(),
                    Status.valueOf(text(p1n, "status", "DONE")));
        }
        Long p2 = child(n, "parent2") == null ? null : child(n, "parent2").asLong();
        return new CallLogEntry(
                longOf(n, "aufrufId", 0), longOf(n, "taskId", 0), text(n, "taskType", ""),
                Kind.valueOf(text(n, "kind", "WORK")), text(n, "slotId", ""),
                text(n, "modelVersion", ""), (int) longOf(n, "attempt", 0),
                longOf(n, "dauerMs", 0), (int) longOf(n, "tokensIn", 0), (int) longOf(n, "tokensOut", 0),
                child(n, "score") == null ? null : child(n, "score").asLong().intValue(),
                Status.valueOf(text(n, "status", "DONE")), text(n, "decision", ""), p1, p2);
    }

    private static long deepestTaskId(TaskNode t) {
        long max = t.getId();
        for (TaskNode c : t.getChildren()) {
            max = Math.max(max, deepestTaskId(c));
        }
        return max;
    }

    private static JsonNode child(JsonNode node, String key) {
        if (node == null || !node.isObject()) {
            return null;
        }
        return node.asObjectValues().get(key);
    }

    private static String text(JsonNode node, String key, String def) {
        JsonNode v = child(node, key);
        return v == null || v.isNull() ? def : v.asText();
    }

    private static long longOf(JsonNode node, String key, long def) {
        JsonNode v = child(node, key);
        return v == null || v.isNull() ? def : v.asLong();
    }
}
