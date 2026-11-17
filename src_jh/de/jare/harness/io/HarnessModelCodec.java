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
import de.jare.harness.model.Loop;
import de.jare.harness.model.LoopItem;
import de.jare.harness.model.Modelclass;
import de.jare.harness.model.Quest;
import de.jare.harness.model.Slot;
import de.jare.harness.model.Trigger;
import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.io.JsonNodeWriter;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.lang.JsonNode;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
                .name(JsonNodes.text(root, "name", "default-harness"))
                .version(JsonNodes.text(root, "version", "1.0.0"));
        JsonNode taskTypes = JsonNodes.child(root, "taskTypes");
        if (taskTypes != null && taskTypes.isArray()) {
            for (JsonNode t : taskTypes.asArray()) {
                model.getTaskTypes().add(t.asText());
            }
        }
        // kinddefs[] come first: every quest/loop-item kind literal is validated against
        // built-in Kind constants ∪ these custom literals (typo-safety, §2)
        JsonNode kinddefs = JsonNodes.child(root, "kinddefs");
        if (kinddefs != null && kinddefs.isArray()) {
            for (JsonNode k : kinddefs.asArray()) {
                model.addKinddef(k.asText());
            }
        }
        JsonNode budgets = JsonNodes.child(root, "promptBudgets");
        if (budgets != null && budgets.isObject()) {
            for (Map.Entry<String, JsonNode> e : budgets.asObjectValues().entrySet()) {
                model.getPromptBudgets().put(e.getKey(), e.getValue().asLong().intValue());
            }
        }
        JsonNode limits = JsonNodes.child(root, "abortLimits");
        if (limits != null && limits.isObject()) {
            model.abortLimits(
                    (int) JsonNodes.longOf(limits, "maxIterations", model.getMaxIterations()),
                    (int) JsonNodes.longOf(limits, "maxDepth", model.getMaxDepth()),
                    (int) JsonNodes.longOf(limits, "maxRetries", model.getMaxRetries()),
                    (int) JsonNodes.longOf(limits, "maxToolCalls", model.getMaxToolCalls()));
        }
        JsonNode mcs = JsonNodes.child(root, "modelclasses");
        if (mcs != null && mcs.isArray()) {
            for (JsonNode mc : mcs.asArray()) {
                model.addModelclass(new Modelclass(
                        JsonNodes.text(mc, "provider", "ollama"),
                        JsonNodes.text(mc, "model", ""),
                        JsonNodes.text(mc, "purpose", ""),
                        (int) JsonNodes.longOf(mc, "size", 0),
                        JsonNodes.doubleOf(mc, "costFactor", 1.0),
                        JsonNodes.child(mc, "toolCapable") != null && JsonNodes.child(mc, "toolCapable").asBoolean()));
            }
        }
        JsonNode quests = JsonNodes.child(root, "quests");
        if (quests != null && quests.isArray()) {
            for (JsonNode q : quests.asArray()) {
                model.addQuest(questFromJson(model, q));
            }
        }
        JsonNode loops = JsonNodes.child(root, "loops");
        if (loops != null && loops.isArray() && !loops.asArray().isEmpty()) {
            Set<String> loopIds = new HashSet<>();
            for (JsonNode l : loops.asArray()) {
                String loopId = JsonNodes.text(l, "id", null);
                if (loopId == null || loopId.isBlank()) {
                    throw new IllegalArgumentException("loops[]: every loop needs an \"id\"");
                }
                if (!loopIds.add(loopId)) {
                    throw new IllegalArgumentException("loops[]: duplicate loop id \"" + loopId + "\"");
                }
                model.addLoop(loopFromJson(model, loopId, l));
            }
            validateLoopRefs(model, loopIds);
        } else {
            // backward compat: no loops[] in the JSON -> the legacy hard-coded order as default "main"
            model.addLoop(HarnessModel.defaultMainLoop());
        }
        return model;
    }

    private static Loop loopFromJson(HarnessModel model, String loopId, JsonNode l) {
        Loop loop = new Loop(loopId);
        Set<String> itemIds = new HashSet<>();
        JsonNode items = JsonNodes.child(l, "items");
        if (items != null && items.isArray()) {
            for (JsonNode it : items.asArray()) {
                String itemId = JsonNodes.text(it, "id", "");
                if (!itemIds.add(itemId)) {
                    throw new IllegalArgumentException("loop \"" + loopId + "\": duplicate item id \"" + itemId + "\"");
                }
                String kindName = JsonNodes.text(it, "kind", "");
                if (!model.isKnownKind(kindName)) {
                    throw new IllegalArgumentException("loop \"" + loopId + "\" item \"" + itemId
                            + "\": unknown kind \"" + kindName + "\" (not a built-in, not in kinddefs[])");
                }
                Kind kind = Kind.valueOf(kindName);
                LoopItem item = new LoopItem(itemId, kind);
                if (JsonNodes.child(it, "loop") != null && !JsonNodes.child(it, "loop").isNull()) {
                    item.loop(JsonNodes.child(it, "loop").asText());
                }
                String trig = JsonNodes.text(it, "trigger", null);
                if (trig != null) {
                    try {
                        item.trigger(Trigger.valueOf(trig));
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("loop \"" + loopId + "\" item \"" + itemId
                                + "\": unknown trigger \"" + trig + "\" (START|EXECUTE|CATCH|CLOSE)", e);
                    }
                }
                loop.addItem(item);
            }
        }
        return loop;
    }

    private static void validateLoopRefs(HarnessModel model, Set<String> loopIds) {
        for (Loop loop : model.getLoops().values()) {
            for (LoopItem item : loop.getItems()) {
                if (item.isSubLoop() && !loopIds.contains(item.getLoop())) {
                    throw new IllegalArgumentException("loop \"" + loop.getId() + "\" item \"" + item.getId()
                            + "\": unknown loop ref \"" + item.getLoop() + "\"");
                }
            }
        }
    }

    private static Quest questFromJson(HarnessModel model, JsonNode q) {
        String kindName = JsonNodes.text(q, "kind", "WORK");
        if (!model.isKnownKind(kindName)) {
            throw new IllegalArgumentException("quest: unknown kind \"" + kindName
                    + "\" (not a built-in, not in kinddefs[])");
        }
        Quest quest = new Quest(Kind.valueOf(kindName))
                .evolvable(JsonNodes.child(q, "evolvable") != null && JsonNodes.child(q, "evolvable").asBoolean());
        JsonNode slots = JsonNodes.child(q, "slots");
        if (slots != null && slots.isArray()) {
            for (JsonNode s : slots.asArray()) {
                Slot slot = new Slot(JsonNodes.text(s, "id", "base"));
                if (JsonNodes.child(s, "mutation") != null && !JsonNodes.child(s, "mutation").isNull()) {
                    slot.mutation(JsonNodes.child(s, "mutation").asText());
                }
                if (JsonNodes.child(s, "calculator") != null) {
                    slot.calculator(calculatorFromJson(JsonNodes.child(s, "calculator")));
                }
                quest.addSlot(slot);
            }
        }
        return quest;
    }

    private static CalculatorNode calculatorFromJson(JsonNode c) {
        String type = JsonNodes.text(c, "type", CalculatorNode.TYPE_ATOMIC);
        CalculatorNode node;
        if (CalculatorNode.TYPE_PUZZLE.equals(type)) {
            node = CalculatorNode.puzzle(JsonNodes.text(c, "strategy", "sequence"));
            JsonNode children = JsonNodes.child(c, "children");
            if (children != null && children.isArray()) {
                for (JsonNode child : children.asArray()) {
                    node.addChild(calculatorFromJson(child));
                }
            }
        } else {
            node = CalculatorNode.atomic(JsonNodes.text(c, "impl", CalculatorNode.IMPL_DETERMINISTIC));
            if (JsonNodes.child(c, "calculator") != null) {
                node.calculator(JsonNodes.child(c, "calculator").asText());
            }
            JsonNode params = JsonNodes.child(c, "params");
            if (params != null && params.isObject()) {
                Map<String, String> pm = new LinkedHashMap<>();
                for (Map.Entry<String, JsonNode> pe : params.asObjectValues().entrySet()) {
                    pm.put(pe.getKey(), pe.getValue().asText());
                }
                node.params(pm);
            }
            if (JsonNodes.child(c, "modelclass") != null) {
                node.modelclass(JsonNodes.child(c, "modelclass").asText());
            }
            if (JsonNodes.child(c, "tool") != null) {
                node.tool(JsonNodes.child(c, "tool").asText());
            }
        }
        JsonNode on = JsonNodes.child(c, "on");
        if (on != null && on.isObject()) {
            for (Map.Entry<String, JsonNode> e : on.asObjectValues().entrySet()) {
                node.on(de.jare.harness.signals.Signal.valueOf(e.getKey()), calculatorFromJson(e.getValue()));
            }
        }
        JsonNode cat = JsonNodes.child(c, "catch");
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
        if (!model.getKinddefs().isEmpty()) {
            JsonNode kinddefs = JsonNode.arrayNode();
            for (String k : model.getKinddefs()) {
                kinddefs.add(JsonNode.stringNode(k));
            }
            root.put("kinddefs", kinddefs);
        }
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
        JsonNode loops = JsonNode.arrayNode();
        for (Loop l : model.getLoops().values()) {
            loops.add(loopToJson(l));
        }
        root.put("loops", loops);
        return root;
    }

    private static JsonNode loopToJson(Loop l) {
        JsonNode node = JsonNode.objectNode();
        node.put("id", JsonNode.stringNode(l.getId()));
        JsonNode items = JsonNode.arrayNode();
        for (LoopItem item : l.getItems()) {
            JsonNode in = JsonNode.objectNode();
            in.put("id", JsonNode.stringNode(item.getId()));
            in.put("kind", JsonNode.stringNode(item.getKind().name()));
            in.put("loop", item.getLoop() == null ? JsonNode.nullNode() : JsonNode.stringNode(item.getLoop()));
            in.put("trigger", JsonNode.stringNode(item.getTrigger().name()));
            items.add(in);
        }
        node.put("items", items);
        return node;
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

}
