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

    /** The main quest: the UI's Ask-AI entry - checks which quest(s) the user actually entered. */
    public static final String QUEST_ASK_AI = "AskAi";

    private String name = "default-harness";
    private String version = "1.0.0";
    private final List<String> taskTypes = new ArrayList<>();
    private final Map<String, Integer> promptBudgets = new LinkedHashMap<>();
    private int maxTokensTotal = 4000;
    private int maxIterations = 50;     // abortLimits - final defaults, decided in CONCEPT_Harness.md §15
    private int maxDepth = 5;
    private int maxRetries = 3;
    private int maxToolCalls = 10;
    private final List<Modelclass> modelclasses = new ArrayList<>();
    private final List<Quest> quests = new ArrayList<>();
    private final Map<String, Loop> loops = new LinkedHashMap<>();   // named loops, §15.6; "main" = entry

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

    /** The modelclasses list - entries carry their purpose, the former map key is gone. */
    public List<Modelclass> getModelclasses() {
        return modelclasses;
    }

    public HarnessModel addModelclass(Modelclass mc) {
        modelclasses.add(mc);
        return this;
    }

    /** First modelclass with this purpose (slots reference a purpose, not a model name). */
    public Modelclass modelclass(String purpose) {
        return modelclasses.stream().filter(m -> m.getPurpose().equals(purpose)).findFirst().orElse(null);
    }

    /** All modelclasses of one purpose - several may share a purpose, pick by size. */
    public List<Modelclass> modelclassesForPurpose(String purpose) {
        return modelclasses.stream().filter(m -> m.getPurpose().equals(purpose)).toList();
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

    /** The named loops of the model (CONCEPT_Harness.md §15.6); empty until the codec fills them. */
    public Map<String, Loop> getLoops() {
        return loops;
    }

    public HarnessModel addLoop(Loop loop) {
        loops.put(loop.getId(), loop);
        return this;
    }

    public Loop getLoop(String id) {
        return loops.get(id);
    }

    /**
     * The legacy main loop: the previously hard-coded processLoop order of the engine
     * (backward compat when the JSON has no {@code loops[]}, DESIGN_Loops.md §2).
     */
    public static Loop defaultMainLoop() {
        Loop main = new Loop(Loop.MAIN);
        main.addItem(new LoopItem("abort", Kind.ABORT).trigger(Trigger.START));
        for (Kind k : new Kind[]{Kind.SPLIT, Kind.COMPLETE, Kind.WORK, Kind.EVALUATE,
                Kind.RAG_STORE, Kind.CHECK, Kind.SUMMARIZE}) {
            main.addItem(new LoopItem(k.name().toLowerCase(), k).trigger(Trigger.EXECUTE));
        }
        main.addItem(new LoopItem("close", Kind.CLOSE).trigger(Trigger.CLOSE));
        main.addItem(new LoopItem("rag_prune", Kind.RAG_PRUNE).trigger(Trigger.CLOSE)); // prune after CLOSE (§11)
        return main;
    }

    /** Deep copy for lineage snapshots and evolution clones (§7). */
    public HarnessModel copy() {
        HarnessModel c = new HarnessModel().name(name).version(version)
                .abortLimits(maxIterations, maxDepth, maxRetries, maxToolCalls);
        c.maxTokensTotal = maxTokensTotal;
        c.taskTypes.addAll(taskTypes);
        c.promptBudgets.putAll(promptBudgets);
        for (Modelclass mc : modelclasses) {
            Modelclass mcCopy = new Modelclass(mc.getProvider(), mc.getModel(), mc.getPurpose(),
                    mc.getSize(), mc.getCostFactor(), mc.isToolCapable());
            if (!mc.isAvailable()) {
                mcCopy.markUnavailable();
            }
            c.modelclasses.add(mcCopy);
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
        for (Loop l : loops.values()) {
            c.loops.put(l.getId(), l.copy());
        }
        return c;
    }

    private static CalculatorNode copyNode(CalculatorNode n) {
        return n.copy();
    }
}
