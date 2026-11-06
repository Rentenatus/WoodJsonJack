/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.model;

import de.jare.harness.Kind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The declarative blueprint of a harness (CONCEPT_Harness.md §5): task types, prompt
 * budgets, abort limits, the {@code modelclasses} JsonMap and the quests with their
 * slots. The model file ({@code harness-model.json}) is a JsonItemDefinition-described
 * JSON - parseable, validatable, editable in the WJJ tree like any bundle.
 *
 * @author Janusch Rentenatus
 */
public class HarnessModel {

    private String name = "default-harness";
    private String version = "1.0.0";
    private final List<String> taskTypes = new ArrayList<>();
    private final Map<String, Integer> promptBudgets = new LinkedHashMap<>();
    private int maxTokensTotal = 4000;
    private int maxIterations = 50;     // abortLimits - values ILLUSTRATIVE until §15 is decided
    private int maxDepth = 5;
    private int maxRetries = 3;
    private int maxToolCalls = 10;
    private final Map<String, Modelclass> modelclasses = new LinkedHashMap<>();
    private final List<Quest> quests = new ArrayList<>();

    public String getName() {
        return name;
    }

    public HarnessModel name(String name) {
        this.name = name;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public HarnessModel version(String version) {
        this.version = version;
        return this;
    }

    public List<String> getTaskTypes() {
        return taskTypes;
    }

    public Map<String, Integer> getPromptBudgets() {
        return promptBudgets;
    }

    public int getMaxTokensTotal() {
        return maxTokensTotal;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public HarnessModel abortLimits(int maxIterations, int maxDepth, int maxRetries, int maxToolCalls) {
        this.maxIterations = maxIterations;
        this.maxDepth = maxDepth;
        this.maxRetries = maxRetries;
        this.maxToolCalls = maxToolCalls;
        return this;
    }

    /** The modelclasses JsonMap: key = modelclass name. Never referenced from slots by model name. */
    public Map<String, Modelclass> getModelclasses() {
        return modelclasses;
    }

    public HarnessModel addModelclass(String name, Modelclass mc) {
        modelclasses.put(name, mc);
        return this;
    }

    public Modelclass modelclass(String name) {
        return modelclasses.get(name);
    }

    public List<Quest> getQuests() {
        return quests;
    }

    public HarnessModel addQuest(Quest quest) {
        quests.add(quest);
        return this;
    }

    public Quest questFor(Kind kind) {
        return quests.stream().filter(q -> q.getKind() == kind).findFirst().orElse(null);
    }

    /** Deep copy for lineage snapshots and evolution clones (§7). */
    public HarnessModel copy() {
        HarnessModel c = new HarnessModel().name(name).version(version)
                .abortLimits(maxIterations, maxDepth, maxRetries, maxToolCalls);
        c.maxTokensTotal = maxTokensTotal;
        c.taskTypes.addAll(taskTypes);
        c.promptBudgets.putAll(promptBudgets);
        for (Map.Entry<String, Modelclass> e : modelclasses.entrySet()) {
            Modelclass mc = e.getValue();
            Modelclass mcCopy = new Modelclass(mc.getProvider(), mc.getModel(), mc.getCostFactor(), mc.isToolCapable());
            if (!mc.isAvailable()) {
                mcCopy.markUnavailable();
            }
            c.modelclasses.put(e.getKey(), mcCopy);
        }
        for (Quest q : quests) {
            Quest qCopy = new Quest(q.getKind()).evolvable(q.isEvolvable());
            for (Slot s : q.getSlots()) {
                Slot sCopy = new Slot(s.getId());
                if (s.getMutation() != null) {
                    sCopy.mutation(s.getMutation());
                }
                if (s.getCalculator() != null) {
                    sCopy.calculator(copyNode(s.getCalculator()));
                }
                qCopy.addSlot(sCopy);
            }
            c.quests.add(qCopy);
        }
        return c;
    }

    private static CalculatorNode copyNode(CalculatorNode n) {
        if (n.isPuzzle()) {
            CalculatorNode p = CalculatorNode.puzzle(n.getStrategy());
            for (CalculatorNode child : n.getChildren()) {
                p.addChild(copyNode(child));
            }
            return p;
        }
        CalculatorNode a = CalculatorNode.atomic(n.getImpl());
        if (n.getModelclass() != null) {
            a.modelclass(n.getModelclass());
        }
        if (n.getTool() != null) {
            a.tool(n.getTool());
        }
        return a;
    }
}
