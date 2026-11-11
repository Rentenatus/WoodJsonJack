/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.impl.DefaultCalculatorFactory;
import de.jare.harness.impl.ollama.OllamaProvider;
import de.jare.harness.io.HarnessInstanceCodec;
import de.jare.harness.io.HarnessModelCodec;
import de.jare.harness.metrics.MetricBucket;
import de.jare.harness.metrics.MetricsStore;
import de.jare.harness.mindset.Mindset;
import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Modelclass;
import de.jare.harness.memory.MemoryQuest;
import de.jare.harness.model.Quest;
import de.jare.harness.model.Slot;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The entry point of the harness in WJJ (CONCEPT_Harness.md §3, §5): the model lives at
 * {@code <projekt>/.wood/harness-model.json} (fallback: the bundled default), the running
 * instance at {@code <projekt>/.wood/harness.json}. Both are normal WJJ-editable JSON.
 * Entry points: a tool call, or an editor selection that is turned into a prompt and
 * continued in the prompt window.
 *
 * @author Janusch Rentenatus
 */
public class HarnessService {

    public static final String MODEL_FILE = ".wood/harness-model.json";
    public static final String INSTANCE_FILE = ".wood/harness.json";
    public static final String MEMORY_FILE = ".wood/harness/memory.json";   // §17 project-local memory

    /** Score advantage over the global prior that makes a slot config a lesson (§8). */
    public static final double LESSON_ADVANTAGE = 10.0;

    private final File projectDir;
    private final de.jare.harness.spi.HarnessHost host;   // embedding application (WJJ/TUI); never a host class import
    private File woodHome;   // null -> GlobalWood default (user.home); settable for tests
    private de.jare.harness.memory.MemoryQuest lastMemoryQuest;   // the memory-side quest instance of the last run
    private de.jare.harness.memory.ProjectMemory projectMemory;   // §17 the project-local memory across runs

    public HarnessService(File projectDir) {
        this(projectDir, null);
    }

    /** The host SPI: provides api key, model handover, editor open, askUser, run listeners. */
    public HarnessService(File projectDir, de.jare.harness.spi.HarnessHost host) {
        this.projectDir = projectDir;
        this.host = host;
    }

    /** Redirects the global wood level (tests); null restores {@code user.home}. */
    public void setWoodHome(File woodHome) {
        this.woodHome = woodHome;
    }

    /** The memory-side quest instance (WorkOrder + WorkCache + WorkResponse) of the last run. */
    public de.jare.harness.memory.MemoryQuest getLastMemoryQuest() {
        return lastMemoryQuest;
    }

    public File getProjectDir() {
        return projectDir;
    }

    public File modelFile() {
        return new File(projectDir, MODEL_FILE);
    }

    public File instanceFile() {
        return new File(projectDir, INSTANCE_FILE);
    }

    /** §17: the project-local memory file {@code <projekt>/.wood/harness/memory.json}. */
    public File memoryFile() {
        return new File(projectDir, MEMORY_FILE);
    }

    /** §17: the project-local memory (chat history + nuggets) of the last load/run. */
    public de.jare.harness.memory.ProjectMemory getProjectMemory() {
        return projectMemory;
    }

    /**
     * Loads the project model: first the host's handed-over JsonEdit structure
     * ({@link de.jare.harness.spi.HarnessHost#harnessModelJson(File)}), then the project file,
     * else the bundled default model.
     */
    public HarnessModel loadModel() throws IOException, de.jare.jsoncasted.io.JsonParseException {
        if (host != null) {
            de.jare.jsoncasted.lang.JsonNode handedOver = host.harnessModelJson(projectDir);
            if (handedOver != null) {
                return HarnessModelCodec.fromJson(handedOver);
            }
        }
        if (modelFile().exists()) {
            return HarnessModelCodec.load(modelFile());
        }
        return defaultModel();
    }

    /** Runs one root task end-to-end and persists the instance. */
    public HarnessInstance runTask(String taskType, String title) throws Exception {
        HarnessModel model = loadModel();
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        instance.setProjectRoot(projectDir);

        // §17: load the project-local memory; the user prompt becomes a CHAT entry, the run's
        // outcome an assistant entry - saved back after the run
        projectMemory = de.jare.harness.memory.ProjectMemory.load(memoryFile());
        projectMemory.addChat("user", title);

        // runtime model (DESIGN_Quest_WorkOrder.md): the MemoryQuest creates the WorkOrder AND the
        // root TaskData itself in memory - id = slot id (e.g. "askai.base"), user prompt as its own
        // field; the host only passes taskType + prompt text, no TaskData logic
        Kind questKind = de.jare.harness.model.HarnessModel.QUEST_ASK_AI.equals(taskType) ? Kind.SPLIT : Kind.WORK;
        Quest recipeQuest = model.questFor(questKind);
        if (recipeQuest != null && recipeQuest.baseSlot() != null) {
            lastMemoryQuest = new de.jare.harness.memory.MemoryQuest(recipeQuest, recipeQuest.baseSlot().getId(),
                    title, taskType, instance::nextTaskId);
            instance.setRoot(lastMemoryQuest.getWorkOrder().getRoot());
        } else {
            instance.newRootTask(instance.nextTaskId(), taskType, title);
        }

        DefaultCalculatorFactory factory = new DefaultCalculatorFactory(model);
        for (Modelclass mc : model.getModelclasses()) {
            if ("ollama".equals(mc.getProvider())) {
                factory.registerProvider("ollama", new OllamaProvider(mc));
            } else if ("hosted".equals(mc.getProvider())) {
                String apiKey = host != null ? host.hostedApiKey() : "";
                factory.registerProvider("hosted", new de.jare.harness.impl.hosted.HostedProvider(apiKey, mc));
            }
        }
        HarnessEngine engine = new HarnessEngine(model, instance, factory);
        // result channel (§3 layering): the runtime WorkCache of the MemoryQuest is handed to
        // every CalcContext, so calculators of one puzzle sequence read predecessor results
        if (lastMemoryQuest != null) {
            engine.setWorkCache(lastMemoryQuest.getWorkCache());
        }
        engine.setProjectMemory(projectMemory);   // §17: fed as HISTORY/DATA segments + via ctx.projectMemory()
        if (host != null) {
            host.wireInto(engine);   // askUser, editor.open, run listeners - via SPI only
        }

        // global wood level (§8, §16): load ~/.wood/harness + ~/.wood/mindset before the run,
        // write them back after - the coupling unit is the aggregated metric, never a harness
        File wood = woodHome != null ? woodHome : GlobalWood.woodHome();
        MetricsStore globalMetrics = MetricsStore.load(GlobalWood.metricsFile(wood));
        instance.setGlobalMetrics(globalMetrics);
        Lessons lessons = Lessons.load(GlobalWood.lessonsFile(wood));
        engine.setMindset(Mindset.load(GlobalWood.mindsetDir(wood)));

        // §12 skills: global ~/.wood/skills/skill (browse/import) + project-local .wood/skills/skill
        de.jare.harness.skills.SkillRegistry skills = new de.jare.harness.skills.SkillRegistry();
        Path globalSkills = new File(wood, ".wood/skills/skill").toPath();
        if (Files.isDirectory(globalSkills)) {
            skills.loadFrom(globalSkills, "global");
        }
        Path localSkills = new File(projectDir, ".wood/skills/skill").toPath();
        if (Files.isDirectory(localSkills)) {
            skills.loadFrom(localSkills, "local");
        }
        engine.setSkillRegistry(skills);

        engine.run();

        // feedback loop between runs (§7.1): threshold check against the metrics,
        // swap proposed as child version in the lineage, effective from the next run
        File lineageFile = new File(projectDir, ".wood/harness/lineage.json");
        HarnessLineage lineage = lineageFile.exists()
                ? HarnessLineage.load(lineageFile) : new HarnessLineage(model);
        engine.setLineage(lineage);
        engine.feedbackLoop(50.0, 1);
        lineage.save(lineageFile);

        exportLessons(lessons, engine, LESSON_ADVANTAGE, 1);
        globalMetrics.save(GlobalWood.metricsFile(wood));
        lessons.save(GlobalWood.lessonsFile(wood));
        engine.getMindset().save(GlobalWood.mindsetDir(wood));

        // the live answer of the run lands in the de.jare.harness.memory.MemoryQuest's WorkResponse
        if (lastMemoryQuest != null) {
            lastMemoryQuest.getWorkResponse().update("done: " + instance.getRoot().getStatus(),
                    instance.getRoot().getScore(), instance.getRoot().getStatus());
            lastMemoryQuest.getWorkOrder().setStatus(instance.getRoot().getStatus());
        }

        // §17: the run's outcome as assistant CHAT entry, then persist the project memory
        if (projectMemory != null) {
            String answer = lastMemoryQuest != null ? lastMemoryQuest.getWorkResponse().getText()
                    : "done: " + instance.getRoot().getStatus();
            projectMemory.addChat("assistant", answer);
            projectMemory.save(memoryFile());
        }

        HarnessInstanceCodec.save(instance, instanceFile());
        return instance;
    }

    /**
     * Lesson export (§8): when a base slot's average score beats the global prior by at
     * least {@code advantage} over enough samples, the winning slot config - not prompts,
     * not task data - is exported as a lesson to {@code ~/.wood/harness/lessons.json}.
     */
    private void exportLessons(Lessons lessons, HarnessEngine engine, double advantage, int minSamples) {
        String projectHash = Integer.toHexString(projectDir.getAbsolutePath().hashCode());
        for (Quest quest : engine.getModel().getQuests()) {
            for (Slot slot : quest.getSlots()) {
                if (!slot.isBase() || slot.getCalculator() == null) {
                    continue;
                }
                MetricBucket local = engine.getInstance().getMetrics()
                        .stats(slot.getId(), 1).get(MetricsStore.Category.SCORE);
                if (local == null || local.getCount() < minSamples) {
                    continue;
                }
                MetricBucket prior = engine.getInstance().getGlobalMetrics() != null
                        ? engine.getInstance().getGlobalMetrics()
                                .stats(slot.getId(), 1).get(MetricsStore.Category.SCORE)
                        : null;
                if (prior == null || prior.getCount() == 0) {
                    continue;   // no global baseline yet - nothing to beat
                }
                double lead = local.getAvg() - prior.getAvg();
                if (lead >= advantage) {
                    lessons.add(new Lessons.Lesson(quest.getKind().name(), slot.getId(),
                            slot.getCalculator(), lead, projectHash, System.currentTimeMillis()));
                }
            }
        }
    }

    /** The bundled default model: all twelve quests deterministic, modelclasses per §5 example. */
    public static HarnessModel defaultModel() {
        HarnessModel model = new HarnessModel().name("default-harness").version("1.0.0")
                .abortLimits(50, 5, 3, 10);
        model.getTaskTypes().add(de.jare.harness.model.HarnessModel.QUEST_ASK_AI);
        model.getTaskTypes().add("RESEARCH");
        model.getTaskTypes().add("CODE_EDIT");
        model.getTaskTypes().add("TEST_RUN");
        model.getTaskTypes().add("SUMMARIZE");
        model.getPromptBudgets().put("SYSTEM", 2);
        model.getPromptBudgets().put("SKILL", 1);
        model.getPromptBudgets().put("HISTORY", 3);
        model.getPromptBudgets().put("DATA", 2);
        model.getPromptBudgets().put("AUFTRAG", 1);
        model.addModelclass(new Modelclass("ollama", "qwen3.8:27b", "investigation", 27, 1.0, true));
        model.addModelclass(new Modelclass("ollama", "nemotron3:33b", "investigation", 33, 1.5, true));
        model.addModelclass(new Modelclass("ollama", "muse-glimmer:30b", "investigation", 30, 1.5, true));
        model.addModelclass(new Modelclass("ollama", "devstral-small-2:24b", "edit", 24, 2.0, true));
        model.addModelclass(new Modelclass("ollama", "gemma4:26b", "edit", 26, 1.5, true));
        model.addModelclass(new Modelclass("ollama", "glm-4.7-flash:latest", "plan", 30, 1.0, true));
        model.addModelclass(new Modelclass("ollama", "gpt-oss:20b", "plan", 20, 1.0, true));
        model.addModelclass(new Modelclass("ollama", "granite3.3:8b", "evaluation", 8, 0.5, false));
        model.addModelclass(new Modelclass("ollama", "gemma3:27b", "evaluation", 27, 1.5, false));
        model.addModelclass(new Modelclass("ollama", "manutic/nomic-embed-code:latest", "rag", 0, 0.2, false));
        model.addModelclass(new Modelclass("ollama", "phi4-mini:latest", "scoring", 4, 0.3, false));

        for (Kind kind : Kind.values()) {
            Quest quest = new Quest(kind).evolvable(kind == Kind.WORK);
            quest.addSlot(new Slot(kind.name().toLowerCase() + ".base")
                    .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC)));
            model.addQuest(quest);
        }
        // the main AskAi quest (SPLIT): scoring counts the quests, investigation decomposes, printig prints
        CalculatorNode askAiCalc = CalculatorNode.puzzle("sequence")
                .addChild(CalculatorNode.atomic(CalculatorNode.IMPL_AI).calculator("scoring.countQuests")
                        .params(java.util.Map.of(
                                "model", "phi4-mini:latest",
                                "systemPrompt", de.jare.harness.main.calculator.ScoringCountQuestsCalculator.DEFAULT_SYSTEM_PROMPT,
                                "emphasisPrompt", de.jare.harness.main.calculator.ScoringCountQuestsCalculator.DEFAULT_EMPHASIS_PROMPT,
                                "temperature", "0.0")))
                .addChild(CalculatorNode.atomic(CalculatorNode.IMPL_AI).calculator("askai.decompose")
                        .params(java.util.Map.of(
                                "model", "nemotron3:33b",
                                "systemPrompt", askAiDecomposeSystemPrompt(),
                                "temperature", "0.3")))
                .addChild(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC).calculator("printig"));
        model.questFor(Kind.SPLIT).baseSlot().calculator(askAiCalc);
        // the entry loop (§15.6): legacy order + a CATCH handler on the ABORT quest
        de.jare.harness.model.Loop main = HarnessModel.defaultMainLoop();
        main.addItem(new de.jare.harness.model.LoopItem("err", Kind.ABORT)
                .trigger(de.jare.harness.model.Trigger.CATCH));
        model.addLoop(main);
        return model;
    }

    /**
     * The system prompt of the askai.decompose call - handed to the builder via the config
     * params, so mutations can be tried purely at the config level (asset harness-model.json).
     */
    private static String askAiDecomposeSystemPrompt() {
        return de.jare.harness.main.calculator.AskAiDecomposeCalculator.DEFAULT_SYSTEM_PROMPT;
    }
}
