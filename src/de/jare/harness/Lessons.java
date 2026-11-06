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
import de.jare.jsoncasted.io.JsonNodeWriter;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.lang.JsonNode;
import de.jare.debug.JsonDebugLevel;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Global lessons learned (CONCEPT_Harness.md §8): when a slot config significantly beats
 * the global prior, the winning slot config - not prompts, not task data - is exported
 * as a lesson to {@code ~/.wood/harness/lessons.json}. Other projects import opt-in.
 * The coupling unit is the aggregated metric, never a harness merge.
 *
 * @author Janusch Rentenatus
 */
public class Lessons {

    /** One lesson: the winning slot configuration plus its measured advantage. */
    public record Lesson(String kind, String slotId, CalculatorNode calculator,
                         double scoreAdvantage, String sourceProjectHash, long ts) {
    }

    private final List<Lesson> lessons = new ArrayList<>();

    public void add(Lesson lesson) {
        lessons.add(lesson);
    }

    public List<Lesson> getLessons() {
        return lessons;
    }

    public void save(File file) throws IOException, JsonParseException {
        JsonNode root = JsonNode.objectNode();
        JsonNode arr = JsonNode.arrayNode();
        for (Lesson l : lessons) {
            JsonNode n = JsonNode.objectNode();
            n.put("kind", JsonNode.stringNode(l.kind()));
            n.put("slotId", JsonNode.stringNode(l.slotId()));
            n.put("calculator", calculatorToJson(l.calculator()));
            n.put("scoreAdvantage", JsonNode.numberNode(l.scoreAdvantage()));
            n.put("sourceProjectHash", JsonNode.stringNode(l.sourceProjectHash()));
            n.put("ts", JsonNode.longNode(l.ts()));
            arr.add(n);
        }
        root.put("lessons", arr);
        AtomicFileWrite.writeString(file.toPath(), JsonNodeWriter.writeToString(root));
    }

    public static Lessons load(File file) throws IOException, JsonParseException {
        Lessons out = new Lessons();
        if (!file.exists()) {
            return out;
        }
        JsonNode root = JsonParserService.parse(file, JsonDebugLevel.SIMPLE).getRoot();
        JsonNode arr = root.asObjectValues().get("lessons");
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr.asArray()) {
                out.add(new Lesson(
                        n.asObjectValues().get("kind").asText(),
                        n.asObjectValues().get("slotId").asText(),
                        calculatorFromJson(n.asObjectValues().get("calculator")),
                        n.asObjectValues().get("scoreAdvantage").asNumber(),
                        n.asObjectValues().get("sourceProjectHash").asText(),
                        n.asObjectValues().get("ts").asLong()));
            }
        }
        return out;
    }

    private static JsonNode calculatorToJson(CalculatorNode c) {
        JsonNode node = JsonNode.objectNode();
        node.put("type", JsonNode.stringNode(c.getType()));
        if (c.isPuzzle()) {
            node.put("strategy", JsonNode.stringNode(c.getStrategy()));
            JsonNode children = JsonNode.arrayNode();
            for (CalculatorNode child : c.getChildren()) {
                children.add(calculatorToJson(child));
            }
            node.put("children", children);
        } else {
            node.put("impl", JsonNode.stringNode(c.getImpl()));
            if (c.getModelclass() != null) {
                node.put("modelclass", JsonNode.stringNode(c.getModelclass()));
            }
            if (c.getTool() != null) {
                node.put("tool", JsonNode.stringNode(c.getTool()));
            }
        }
        return node;
    }

    private static CalculatorNode calculatorFromJson(JsonNode n) {
        // parse through the model codec via a synthetic one-quest wrapper
        JsonNode wrap = JsonNode.objectNode();
        JsonNode quests = JsonNode.arrayNode();
        JsonNode qn = JsonNode.objectNode();
        qn.put("kind", JsonNode.stringNode("WORK"));
        JsonNode slots = JsonNode.arrayNode();
        JsonNode sn = JsonNode.objectNode();
        sn.put("id", JsonNode.stringNode("tmp"));
        sn.put("calculator", n);
        slots.add(sn);
        qn.put("slots", slots);
        quests.add(qn);
        wrap.put("quests", quests);
        HarnessModel parsed = HarnessModelCodec.fromJson(wrap);
        return parsed.questFor(Kind.WORK).baseSlot().getCalculator();
    }
}
