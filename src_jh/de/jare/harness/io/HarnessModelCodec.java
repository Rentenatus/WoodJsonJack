/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.io;

import de.jare.harness.Kind;
import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Modelclass;
import de.jare.harness.model.Quest;
import de.jare.harness.model.Slot;
import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.io.JsonNodeWriter;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.lang.JsonNode;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Codec between the {@link HarnessModel} and its JSON form ({@code harness-model.json},
 * CONCEPT_Harness.md §5): parse via jsonCasted's plain {@link JsonParserService}, write
 * via {@link JsonNodeWriter} with write-temp-rename. The model file stays a normal
 * parseable/editable JSON - the JsonItemDefinition binding for the WJJ tree follows.
 *
 * @author Janusch Rentenatus
 */
public final class HarnessModelCodec {

    private HarnessModelCodec() {
    }

    public static HarnessModel load(File file) throws IOException, JsonParseException {
        JsonNode root = JsonParserService.parse(file, JsonDebugLevel.SIMPLE).getRoot();
        return fromJson(root);
    }

    public static void save(HarnessModel model, File file) throws IOException, JsonParseException {
        AtomicFileWrite.writeString(file.toPath(), JsonNodeWriter.writeToString(toJson(model)));
    }

    public static HarnessModel fromJson(JsonNode root) {
        HarnessModel model = new HarnessModel()
                .name(text(root, "name", "default-harness"))
                .version(text(root, "version", "1.0.0"));
        JsonNode taskTypes = child(root, "taskTypes");
        if (taskTypes != null && taskTypes.isArray()) {
            for (JsonNode t : taskTypes.asArray()) {
                model.getTaskTypes().add(t.asText());
            }
        }
        JsonNode budgets = child(root, "promptBudgets");
        if (budgets != null && budgets.isObject()) {
            for (Map.Entry<String, JsonNode> e : budgets.asObjectValues().entrySet()) {
                model.getPromptBudgets().put(e.getKey(), e.getValue().asLong().intValue());
            }
        }
        JsonNode limits = child(root, "abortLimits");
        if (limits != null && limits.isObject()) {
            model.abortLimits(
                    (int) longOf(limits, "maxIterations", model.getMaxIterations()),
                    (int) longOf(limits, "maxDepth", model.getMaxDepth()),
                    (int) longOf(limits, "maxRetries", model.getMaxRetries()),
                    (int) longOf(limits, "maxToolCalls", model.getMaxToolCalls()));
        }
        JsonNode mcs = child(root, "modelclasses");
        if (mcs != null && mcs.isArray()) {
            for (JsonNode mc : mcs.asArray()) {
                model.addModelclass(new Modelclass(
                        text(mc, "provider", "ollama"),
                        text(mc, "model", ""),
                        text(mc, "purpose", ""),
                        (int) longOf(mc, "size", 0),
                        doubleOf(mc, "costFactor", 1.0),
                        child(mc, "toolCapable") != null && child(mc, "toolCapable").asBoolean()));
            }
        }
        JsonNode quests = child(root, "quests");
        if (quests != null && quests.isArray()) {
            for (JsonNode q : quests.asArray()) {
                model.addQuest(questFromJson(q));
            }
        }
        return model;
    }

    private static Quest questFromJson(JsonNode q) {
        Quest quest = new Quest(Kind.valueOf(text(q, "kind", "WORK")))
                .evolvable(child(q, "evolvable") != null && child(q, "evolvable").asBoolean());
        JsonNode slots = child(q, "slots");
        if (slots != null && slots.isArray()) {
            for (JsonNode s : slots.asArray()) {
                Slot slot = new Slot(text(s, "id", "base"));
                if (child(s, "mutation") != null && !child(s, "mutation").isNull()) {
                    slot.mutation(child(s, "mutation").asText());
                }
                if (child(s, "calculator") != null) {
                    slot.calculator(calculatorFromJson(child(s, "calculator")));
                }
                quest.addSlot(slot);
            }
        }
        return quest;
    }

    private static CalculatorNode calculatorFromJson(JsonNode c) {
        String type = text(c, "type", CalculatorNode.TYPE_ATOMIC);
        CalculatorNode node;
        if (CalculatorNode.TYPE_PUZZLE.equals(type)) {
            node = CalculatorNode.puzzle(text(c, "strategy", "sequence"));
            JsonNode children = child(c, "children");
            if (children != null && children.isArray()) {
                for (JsonNode child : children.asArray()) {
                    node.addChild(calculatorFromJson(child));
                }
            }
        } else {
            node = CalculatorNode.atomic(text(c, "impl", CalculatorNode.IMPL_DETERMINISTIC));
            if (child(c, "calculator") != null) {
                node.calculator(child(c, "calculator").asText());
            }
            JsonNode params = child(c, "params");
            if (params != null && params.isObject()) {
                Map<String, String> pm = new LinkedHashMap<>();
                for (Map.Entry<String, JsonNode> pe : params.asObjectValues().entrySet()) {
                    pm.put(pe.getKey(), pe.getValue().asText());
                }
                node.params(pm);
            }
            if (child(c, "modelclass") != null) {
                node.modelclass(child(c, "modelclass").asText());
            }
            if (child(c, "tool") != null) {
                node.tool(child(c, "tool").asText());
            }
        }
        JsonNode on = child(c, "on");
        if (on != null && on.isObject()) {
            for (Map.Entry<String, JsonNode> e : on.asObjectValues().entrySet()) {
                node.on(de.jare.harness.signals.Signal.valueOf(e.getKey()), calculatorFromJson(e.getValue()));
            }
        }
        JsonNode cat = child(c, "catch");
        if (cat != null && cat.isObject()) {
            for (Map.Entry<String, JsonNode> e : cat.asObjectValues().entrySet()) {
                node.catchWith(de.jare.harness.HarnessException.Type.valueOf(e.getKey()), calculatorFromJson(e.getValue()));
            }
        }
        return node;
    }

    public static JsonNode toJson(HarnessModel model) {
        JsonNode root = JsonNode.objectNode();
        root.put("name", JsonNode.stringNode(model.getName()));
        root.put("version", JsonNode.stringNode(model.getVersion()));
        JsonNode taskTypes = JsonNode.arrayNode();
        for (String t : model.getTaskTypes()) {
            taskTypes.add(JsonNode.stringNode(t));
        }
        root.put("taskTypes", taskTypes);
        JsonNode budgets = JsonNode.objectNode();
        for (Map.Entry<String, Integer> e : model.getPromptBudgets().entrySet()) {
            budgets.put(e.getKey(), JsonNode.longNode(e.getValue()));
        }
        root.put("promptBudgets", budgets);
        JsonNode limits = JsonNode.objectNode();
        limits.put("maxIterations", JsonNode.longNode(model.getMaxIterations()));
        limits.put("maxDepth", JsonNode.longNode(model.getMaxDepth()));
        limits.put("maxRetries", JsonNode.longNode(model.getMaxRetries()));
        limits.put("maxToolCalls", JsonNode.longNode(model.getMaxToolCalls()));
        root.put("abortLimits", limits);
        JsonNode mcs = JsonNode.arrayNode();
        for (Modelclass mc : model.getModelclasses()) {
            JsonNode m = JsonNode.objectNode();
            m.put("provider", JsonNode.stringNode(mc.getProvider()));
            m.put("model", JsonNode.stringNode(mc.getModel()));
            m.put("purpose", JsonNode.stringNode(mc.getPurpose()));
            m.put("size", JsonNode.longNode(mc.getSize()));
            m.put("costFactor", JsonNode.numberNode(mc.getCostFactor()));
            m.put("toolCapable", JsonNode.booleanNode(mc.isToolCapable()));
            mcs.add(m);
        }
        root.put("modelclasses", mcs);
        JsonNode quests = JsonNode.arrayNode();
        for (Quest q : model.getQuests()) {
            quests.add(questToJson(q));
        }
        root.put("quests", quests);
        return root;
    }

    private static JsonNode questToJson(Quest q) {
        JsonNode node = JsonNode.objectNode();
        node.put("kind", JsonNode.stringNode(q.getKind().name()));
        node.put("evolvable", JsonNode.booleanNode(q.isEvolvable()));
        JsonNode slots = JsonNode.arrayNode();
        for (Slot s : q.getSlots()) {
            JsonNode sn = JsonNode.objectNode();
            sn.put("id", JsonNode.stringNode(s.getId()));
            sn.put("mutation", s.getMutation() == null ? JsonNode.nullNode() : JsonNode.stringNode(s.getMutation()));
            if (s.getCalculator() != null) {
                sn.put("calculator", calculatorToJson(s.getCalculator()));
            }
            slots.add(sn);
        }
        node.put("slots", slots);
        return node;
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
            if (c.getCalculator() != null) {
                node.put("calculator", JsonNode.stringNode(c.getCalculator()));
            }
            if (!c.getParams().isEmpty()) {
                JsonNode params = JsonNode.objectNode();
                for (Map.Entry<String, String> pe : c.getParams().entrySet()) {
                    params.put(pe.getKey(), JsonNode.stringNode(pe.getValue()));
                }
                node.put("params", params);
            }
            if (c.getModelclass() != null) {
                node.put("modelclass", JsonNode.stringNode(c.getModelclass()));
            }
            if (c.getTool() != null) {
                node.put("tool", JsonNode.stringNode(c.getTool()));
            }
        }
        if (!c.getOn().isEmpty()) {
            JsonNode on = JsonNode.objectNode();
            for (Map.Entry<de.jare.harness.signals.Signal, CalculatorNode> e : c.getOn().entrySet()) {
                on.put(e.getKey().name(), calculatorToJson(e.getValue()));
            }
            node.put("on", on);
        }
        if (!c.getCatchMapping().isEmpty()) {
            JsonNode cat = JsonNode.objectNode();
            for (Map.Entry<de.jare.harness.HarnessException.Type, CalculatorNode> e : c.getCatchMapping().entrySet()) {
                cat.put(e.getKey().name(), calculatorToJson(e.getValue()));
            }
            node.put("catch", cat);
        }
        return node;
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

    private static double doubleOf(JsonNode node, String key, double def) {
        JsonNode v = child(node, key);
        if (v == null || v.isNull()) {
            return def;
        }
        Double d = v.asNumber();
        return d != null ? d : v.asLong();
    }
}
