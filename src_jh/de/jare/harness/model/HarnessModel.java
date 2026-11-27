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
    private final List<String> kinddefs = new ArrayList<>();   // custom kind literals declared in the root, §2

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

    /**
     * The custom kind literals declared in the model root ({@code kinddefs[]}): usable in
     * quests and loop items without a built-in {@link Kind} constant (Kind is a registry,
     * §2). The codec validates every kind literal against built-ins ∪ these.
     */
    public List<String> getKinddefs() {
        return kinddefs;
    }

    public HarnessModel addKinddef(String kinddef) {
        if (kinddef != null && !kinddef.isBlank() && !kinddefs.contains(kinddef)) {
            kinddefs.add(kinddef);
        }
        return this;
    }

    /** Typo-safety at load: a kind literal must be a built-in constant or a declared kinddef. */
    public boolean isKnownKind(String name) {
        return Kind.isBuiltin(name) || kinddefs.contains(name);
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

    public void setMaxTokensTotal(int maxTokensTotal) {
        this.maxTokensTotal = maxTokensTotal;
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

    /**
     * Adds a named loop. §18.5: loop item ids must be unique PER LOOP (the history origin
     * triple is {@code loopId/loopItemId/trigger}) - a duplicate is a PARAMETER error.
     */
    public HarnessModel addLoop(Loop loop) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (LoopItem item : loop.getItems()) {
            if (!ids.add(item.getId())) {
                throw new de.jare.harness.HarnessException(de.jare.harness.HarnessException.Type.PARAMETER,
                        "duplicate loop item id \"" + item.getId() + "\" in loop \"" + loop.getId()
                                + "\" (§18.5: ids must be unique per loop)", "loopItemId");
            }
        }
        loops.put(loop.getId(), loop);
        return this;
    }

    public Loop getLoop(String id) {
        return loops.get(id);
    }

    /**
     * The default main loop: the previously hard-coded processLoop order of the engine,
     * plus GUARD + SKILL at START (§15.6 — entry guard and skill collection fire once per
     * run, only in "main"; backward compat when the JSON has no {@code loops[]},
     * DESIGN_Loops.md §2). All EXECUTE items except SPLIT are tagged
     * {@code skipAfter:["split"]} - after a split the slot's own summarize child and the
     * subloops handle the rest of the iteration.
     */
    public static Loop defaultMainLoop() {
        return defaultLoop(Loop.MAIN, true);
    }

    /**
     * The default subloop: same legacy order as the main loop, but under the id
     * {@link Loop#SUB} and with the plain ABORT START (backward compat when the JSON
     * configures {@code loops[]} without a {@code "sub"} entry — split children still get
     * a full loop, §15.6; GUARD/SKILL START live only in "main").
     */
    public static Loop defaultSubLoop() {
        return defaultLoop(Loop.SUB, false);
    }

    private static Loop defaultLoop(String id, boolean guardSkillStart) {
        Loop loop = new Loop(id);
        if (guardSkillStart) {
            // main only (§15.6): entry guard + skill collection fire once at START
            loop.addItem(new LoopItem("guard", Kind.GUARD).trigger(Trigger.START));
            loop.addItem(new LoopItem("skill", Kind.SKILL).trigger(Trigger.START));
        } else {
            loop.addItem(new LoopItem("abort", Kind.ABORT).trigger(Trigger.START));
        }
        for (Kind k : new Kind[]{Kind.SPLIT, Kind.COMPLETE, Kind.WORK, Kind.EVALUATE,
                Kind.RAG_STORE, Kind.CHECK, Kind.SUMMARIZE}) {
            LoopItem it = new LoopItem(k.name().toLowerCase(), k).trigger(Trigger.EXECUTE);
            if (k != Kind.SPLIT) {
                it.skipAfter(java.util.List.of("split"));   // after a split the slot/subloops handle the rest
            }
            loop.addItem(it);
        }
        loop.addItem(new LoopItem("close", Kind.CLOSE).trigger(Trigger.CLOSE));
        loop.addItem(new LoopItem("rag_prune", Kind.RAG_PRUNE).trigger(Trigger.CLOSE)); // prune after CLOSE (§11)
        return loop;
    }

    /** Deep copy for lineage snapshots and evolution clones (§7). */
    public HarnessModel copy() {
        HarnessModel c = new HarnessModel().name(name).version(version)
                .abortLimits(maxIterations, maxDepth, maxRetries, maxToolCalls);
        c.maxTokensTotal = maxTokensTotal;
        c.taskTypes.addAll(taskTypes);
        c.kinddefs.addAll(kinddefs);
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
