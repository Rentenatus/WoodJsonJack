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
import de.jare.harness.TaskData;
import de.jare.harness.TaskTree;
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
            try {
                instance.adoptTaskTree(TaskTree.fromJson(normalizeLegacyTask(task)));
                maxTaskId = deepestTaskId(instance.getRoot());
            } catch (JsonParseException e) {
                throw new IllegalStateException("rootTask is no parseable task tree: " + e.getMessage(), e);
            }
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

    /**
     * Serializes a task into the EditTree-shaped JsonNode (member properties + "tasks"
     * array of subtasks) - the same shape the EditTree writer produces, so a saved
     * harness.json is directly re-importable via {@link TaskTree#fromJson}.
     */
    private static JsonNode taskToJson(TaskData t) {
        JsonNode n = JsonNode.objectNode();
        n.put(TaskData.M_ID, JsonNode.longNode(t.getId()));
        n.put(TaskData.M_TYPE, JsonNode.stringNode(t.getTaskType()));
        n.put(TaskData.M_TITLE, JsonNode.stringNode(t.getTitle()));
        n.put(TaskData.M_STATUS, JsonNode.stringNode(t.getStatus().name()));
        if (t.getScore() != null) {
            n.put(TaskData.M_SCORE, JsonNode.longNode(t.getScore()));
        }
        n.put(TaskData.M_ATTEMPT, JsonNode.longNode(t.getAttempt()));
        n.put(TaskData.M_SUMMARY, JsonNode.booleanNode(t.isSummaryTask()));
        n.put(TaskData.M_SUPPLEMENT, JsonNode.stringNode(t.getPromptSupplement()));
        if (t.getArtifact() != null) {
            n.put(TaskData.M_ARTIFACT, JsonNode.stringNode(t.getArtifact()));
        }
        JsonNode children = JsonNode.arrayNode();
        for (TaskData c : t.getChildren()) {
            children.add(taskToJson(c));
        }
        n.put(TaskTree.TASK_ARRAY, children);
        JsonNode log = JsonNode.arrayNode();
        for (CallLogEntry e : t.getCalculatorLog()) {
            log.add(e.toJson());
        }
        try {
            n.put(TaskData.M_LOG, JsonNode.stringNode(JsonNodeWriter.writeToString(log)));
        } catch (Exception e) {
            throw new IllegalStateException("calculatorLog is not serializable: " + e.getMessage(), e);
        }
        return n;
    }

    /**
     * Converts the legacy persisted shape ("id"/"children") into the EditTree shape
     * (member names + "tasks" array) so old harness.json files still load.
     */
    private static JsonNode normalizeLegacyTask(JsonNode n) {
        if (n == null || !n.isObject()) {
            return n;
        }
        JsonNode out = JsonNode.objectNode();
        for (Map.Entry<String, JsonNode> e : n.asObjectValues().entrySet()) {
            String key = e.getKey();
            if ("id".equals(key)) {
                key = TaskData.M_ID;
            } else if ("children".equals(key)) {
                key = TaskTree.TASK_ARRAY;
            }
            JsonNode v = e.getValue();
            if (TaskTree.TASK_ARRAY.equals(key) && v != null && v.isArray()) {
                JsonNode arr = JsonNode.arrayNode();
                for (JsonNode c : v.asArray()) {
                    arr.add(normalizeLegacyTask(c));
                }
                out.put(key, arr);
            } else if (TaskData.M_LOG.equals(key) && v != null && v.isArray()) {
                // legacy stored the log as a JSON array; the EditTree shape stores it as a
                // string member - without this conversion the log would be lost on load
                try {
                    out.put(key, JsonNode.stringNode(JsonNodeWriter.writeToString(v)));
                } catch (Exception ex) {
                    throw new IllegalStateException("legacy calculatorLog is not serializable", ex);
                }
            } else {
                out.put(key, v);
            }
        }
        return out;
    }

    private static JsonNode entryToJson(CallLogEntry e) {
        return e.toJson();
    }

    private static CallLogEntry entryFromJson(JsonNode n) {
        return CallLogEntry.fromJson(n);
    }

    private static long deepestTaskId(TaskData t) {
        long max = t.getId();
        for (TaskData c : t.getChildren()) {
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
