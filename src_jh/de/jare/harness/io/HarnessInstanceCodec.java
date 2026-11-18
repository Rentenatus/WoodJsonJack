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
                JsonNodes.text(root, "modelName", ""), JsonNodes.text(root, "modelVersion", ""));
        long maxTaskId = 0;
        long maxAufrufId = 0;
        JsonNode task = JsonNodes.child(root, "rootTask");
        if (task != null) {
            instance.adoptRoot(TaskData.fromJson(normalizeLegacyTask(task)));
            maxTaskId = deepestTaskId(instance.getRoot());
        }
        JsonNode log = JsonNodes.child(root, "callLog");
        if (log != null && log.isArray()) {
            for (JsonNode e : log.asArray()) {
                CallLogEntry entry = entryFromJson(e);
                instance.logCall(entry);
                maxAufrufId = Math.max(maxAufrufId, entry.getAufrufId());
                maxTaskId = Math.max(maxTaskId, entry.getTaskId());
            }
        }
        JsonNode ids = JsonNodes.child(root, "ids");
        long persistedTask = ids == null ? 1 : JsonNodes.longOf(ids, "nextTaskId", 1);
        long persistedAufruf = ids == null ? 1 : JsonNodes.longOf(ids, "nextAufrufId", 1);
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
     * Serializes a task into its JSON shape (member fields + "tasks" array of subtasks +
     * "calculatorLog"/"calls" arrays) - {@link TaskData#toJson()} is the single writer, so a
     * saved harness.json is directly re-readable via {@link TaskData#fromJson}.
     */
    private static JsonNode taskToJson(TaskData t) {
        return t.toJson();
    }

    /**
     * Converts the legacy persisted shape ("id"/"children", calculatorLog as string member)
     * into the current JSON shape (member names + "tasks"/"calculatorLog" arrays) so old
     * harness.json files still load.
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
                key = TaskData.M_TASKS;
            }
            JsonNode v = e.getValue();
            if (TaskData.M_TASKS.equals(key) && v != null && v.isArray()) {
                JsonNode arr = JsonNode.arrayNode();
                for (JsonNode c : v.asArray()) {
                    arr.add(normalizeLegacyTask(c));
                }
                out.put(key, arr);
            } else if (TaskData.M_LOG.equals(key) && v != null && v.getType() == de.jare.jsoncasted.lang.JsonNodeType.STRING) {
                // legacy stored the log as a JSON string member; the current shape stores it
                // as an array - without this conversion the log would be lost on load
                try {
                    out.put(key, JsonParserService.parse(v.asText(), JsonDebugLevel.SIMPLE).getRoot());
                } catch (Exception ex) {
                    throw new IllegalStateException("legacy calculatorLog is not parseable", ex);
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

}
