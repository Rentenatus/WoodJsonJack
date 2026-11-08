/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.io.AtomicFileWrite;
import de.jare.harness.io.HarnessModelCodec;
import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Quest;
import de.jare.harness.model.Slot;
import de.jare.jsoncasted.io.JsonNodeWriter;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.lang.JsonNode;
import de.jare.debug.JsonDebugLevel;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The persisted lineage / family tree of a harness (CONCEPT_Harness.md §7):
 * every model change (swap, evolution, manual edit) creates a new version with a
 * parent reference, stored project-local in {@code <projekt>/.wood/harness/lineage.json}
 * (versionId, parentId, created, changeDescription, slots). Rollback points the
 * instance at an older version; writing on continues as a child of the current one.
 * Swap is only proposed between runs (feedback loop after CLOSE), never mid-run.
 *
 * @author Janusch Rentenatus
 */
public class HarnessLineage {

    /** One lineage entry: a model version with its parent and a change description. */
    public static class Version {
        public final String versionId;
        public final String parentId;      // null for the root version
        public final long created;
        public final String changeDescription;
        public final HarnessModel model;

        public Version(String versionId, String parentId, long created, String changeDescription, HarnessModel model) {
            this.versionId = versionId;
            this.parentId = parentId;
            this.created = created;
            this.changeDescription = changeDescription;
            this.model = model;
        }
    }

    private final Map<String, Version> versions = new LinkedHashMap<>();
    private String currentVersionId;
    private long versionCounter = 0;

    public HarnessLineage(HarnessModel initial) {
        String id = newVersionId();
        versions.put(id, new Version(id, null, System.currentTimeMillis(), "initial version", initial));
        currentVersionId = id;
    }

    public Version current() {
        return versions.get(currentVersionId);
    }

    public HarnessModel currentModel() {
        return current().model;
    }

    public List<Version> getVersions() {
        return new ArrayList<>(versions.values());
    }

    /**
     * Feedback-loop swap (§7.1): proposes a child version with the slot's calculator
     * replaced - effective from the next run, the old version stays in the tree.
     */
    public Version proposeSwap(String slotId, CalculatorNode newCalculator, String reason) {
        HarnessModel child = currentModel().copy();
        bumpVersion(child);
        for (Quest q : child.getQuests()) {
            for (Slot s : q.getSlots()) {
                if (s.getId().equals(slotId)) {
                    s.calculator(newCalculator);
                }
            }
        }
        return commitChild("swap slot " + slotId + ": " + reason, child);
    }

    /**
     * Evolution step (§7.2): at an evolvable quest, three alternative slots run in
     * parallel - the base plus the mutation-labelled candidates (or fresh mutations).
     */
    public Version requestEvolution(Kind kind, List<Slot> candidates, String reason) {
        if (candidates.size() != 3) {
            throw new IllegalArgumentException("evolution needs exactly 3 candidates, got " + candidates.size());
        }
        HarnessModel child = currentModel().copy();
        bumpVersion(child);
        Quest quest = child.questFor(kind);
        if (quest == null) {
            throw new IllegalArgumentException("no quest for kind " + kind);
        }
        quest.getSlots().clear();
        for (Slot s : candidates) {
            quest.addSlot(s);
        }
        return commitChild("evolution at " + kind + ": " + reason, child);
    }

    /** Rollback: point the lineage at an older version (§7.3). */
    public Version rollbackTo(String versionId) {
        if (!versions.containsKey(versionId)) {
            throw new IllegalArgumentException("unknown version " + versionId);
        }
        currentVersionId = versionId;
        return current();
    }

    private Version commitChild(String description, HarnessModel model) {
        String id = newVersionId();
        Version v = new Version(id, currentVersionId, System.currentTimeMillis(), description, model);
        versions.put(id, v);
        currentVersionId = id;
        return v;
    }

    private String newVersionId() {
        return "v" + (++versionCounter);
    }

    private static void bumpVersion(HarnessModel model) {
        model.version(model.getVersion() + "+local");
    }

    public void save(File file) throws IOException, JsonParseException {
        JsonNode root = JsonNode.objectNode();
        root.put("currentVersionId", JsonNode.stringNode(currentVersionId));
        JsonNode arr = JsonNode.arrayNode();
        for (Version v : versions.values()) {
            JsonNode n = JsonNode.objectNode();
            n.put("versionId", JsonNode.stringNode(v.versionId));
            n.put("parentId", v.parentId == null ? JsonNode.nullNode() : JsonNode.stringNode(v.parentId));
            n.put("created", JsonNode.longNode(v.created));
            n.put("changeDescription", JsonNode.stringNode(v.changeDescription));
            n.put("model", HarnessModelCodec.toJson(v.model));
            arr.add(n);
        }
        root.put("versions", arr);
        AtomicFileWrite.writeString(file.toPath(), JsonNodeWriter.writeToString(root));
    }

    public static HarnessLineage load(File file) throws IOException, JsonParseException {
        JsonNode root = JsonParserService.parse(file, JsonDebugLevel.SIMPLE).getRoot();
        HarnessLineage lineage = null;
        JsonNode arr = root.asObjectValues().get("versions");
        if (arr != null && arr.isArray() && !arr.asArray().isEmpty()) {
            JsonNode first = arr.asArray().get(0);
            lineage = new HarnessLineage(HarnessModelCodec.fromJson(first.asObjectValues().get("model")));
            lineage.versions.clear();
            for (JsonNode n : arr.asArray()) {
                String vid = n.asObjectValues().get("versionId").asText();
                JsonNode pid = n.asObjectValues().get("parentId");
                lineage.versions.put(vid, new Version(vid, pid == null || pid.isNull() ? null : pid.asText(),
                        n.asObjectValues().get("created").asLong(),
                        n.asObjectValues().get("changeDescription").asText(),
                        HarnessModelCodec.fromJson(n.asObjectValues().get("model"))));
                lineage.versionCounter = Math.max(lineage.versionCounter,
                        Long.parseLong(vid.substring(1)));
            }
            lineage.currentVersionId = root.asObjectValues().get("currentVersionId").asText();
        }
        return lineage;
    }
}
