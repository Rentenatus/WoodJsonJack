/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.impl.DefaultCalculatorFactory;
import de.jare.harness.impl.ModelProvider;
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

    /** §17: a run with root score >= this threshold counts as a "hit" for the used nuggets. */
    public static final int NUGGET_HIT_SCORE_THRESHOLD = 50;

    private final File projectDir;
    private final de.jare.harness.spi.HarnessHost host;   // embedding application (WJJ/TUI); never a host class import
    private File woodHome;   // null -> GlobalWood default (user.home); settable for tests
    private de.jare.harness.memory.MemoryQuest lastMemoryQuest;   // the memory-side quest instance of the last run
    // orator registrations (§18): forwarded to every new MemoryQuest's CommandHistory/WorkResponse,
    // so the UI subscribes on the memory objects - the service/controller never hold UI references
    private final java.util.List<java.util.function.Consumer<de.jare.harness.memory.CommandHistory.Entry>> commandListeners = new java.util.ArrayList<>();
    private final java.util.List<java.util.function.Consumer<de.jare.harness.memory.WorkResponse>> responseListeners = new java.util.ArrayList<>();

    /** Registers a listener on the CommandHistory of EVERY run's MemoryQuest (fired per applied command). */
    public void addCommandListener(java.util.function.Consumer<de.jare.harness.memory.CommandHistory.Entry> l) {
        if (l != null) {
            commandListeners.add(l);
        }
    }

    /** Registers a listener on the WorkResponse of EVERY run's MemoryQuest (fired per update). */
    public void addResponseListener(java.util.function.Consumer<de.jare.harness.memory.WorkResponse> l) {
        if (l != null) {
            responseListeners.add(l);
        }
    }
    /** Optional hook fired with (instance, engine) right before {@code engine.run()} - live views bind here. */
    private java.util.function.BiConsumer<HarnessInstance, HarnessEngine> runStartHook;

    public void setRunStartHook(java.util.function.BiConsumer<HarnessInstance, HarnessEngine> runStartHook) {
        this.runStartHook = runStartHook;
    }

    private de.jare.harness.memory.ProjectMemory projectMemory;   // §17 the project-local memory across runs

    public HarnessService(File projectDir) {
        this(projectDir, null);
    }

    /** The host SPI: provides api key, model handover, editor open, askUser, run listeners. */
    public HarnessService(File projectDir, de.jare.harness.spi.HarnessHost host) {
        this.projectDir = projectDir;
        this.host = host;
    }

    /**
     * Creates the provider for one modelclass - extracted as an overridable seam so tests can
     * inject a fake (canned answers, no live Ollama; the suite must stay stable offline).
     * Returns null for provider "system" (built-in deterministic fallback, no provider needed).
     */
    protected ModelProvider createProvider(Modelclass mc) {
        if ("ollama".equals(mc.getProvider())) {
            return new OllamaProvider(mc);
        }
        if ("hosted".equals(mc.getProvider())) {
            String apiKey = host != null ? host.hostedApiKey() : "";
            return new de.jare.harness.impl.hosted.HostedProvider(apiKey, mc);
        }
        return null;
    }

    /** Redirects the global wood level (tests, same package); null restores {@code user.home}. */
    void setWoodHome(File woodHome) {
        this.woodHome = woodHome;
    }

    /** The memory-side quest instance (WorkOrder + WorkCache + WorkResponse) of the last run. */
    public de.jare.harness.memory.MemoryQuest getLastMemoryQuest() {
        return lastMemoryQuest;
    }

    File getProjectDir() {
        return projectDir;
    }

    File modelFile() {
        return new File(projectDir, MODEL_FILE);
    }

    File instanceFile() {
        return new File(projectDir, INSTANCE_FILE);
    }

    /**
     * §17: the project-local memory file {@code <projekt>/.wood/harness/memory.json}.
     * Public because the chat commands (de.jare.harness.main.chat) save through it.
     */
    public File memoryFile() {
        return new File(projectDir, MEMORY_FILE);
    }

    /** §17: the project-local memory (chat history + nuggets) of the last load/run. */
    de.jare.harness.memory.ProjectMemory getProjectMemory() {
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

        // §17: load the project-local memory. The user prompt becomes a CHAT entry only AFTER
        // the run when it was not guard-blocked (ABORTED) - injection test prompts must not
        // persist in memory.json
        projectMemory = de.jare.harness.memory.ProjectMemory.load(memoryFile());

        // runtime model (DESIGN_Quest_WorkOrder.md): the MemoryQuest creates the WorkOrder AND the
        // root TaskData itself in memory - id = slot id (e.g. "askai.base"), user prompt as its own
        // field; the host only passes taskType + prompt text, no TaskData logic
        Kind questKind = de.jare.harness.model.HarnessModel.QUEST_ASK_AI.equals(taskType) ? Kind.SPLIT : Kind.WORK;
        Quest recipeQuest = model.questFor(questKind);
        System.out.println("[DIAG." + de.jare.harness.Diag.runNo() + "] HarnessService.runTask: type=" + taskType + " model=" + model.getName()
                + " v" + model.getVersion() + " questKind=" + questKind + " recipeQuest=" + (recipeQuest != null)
                + " commandListeners=" + commandListeners.size() + " responseListeners=" + responseListeners.size());
        if (recipeQuest != null && recipeQuest.baseSlot() != null) {
            lastMemoryQuest = new de.jare.harness.memory.MemoryQuest(recipeQuest, recipeQuest.baseSlot().getId(),
                    title, taskType, instance::nextTaskId);
            for (var l : commandListeners) {
                lastMemoryQuest.getCommandHistory().addListener(l);
            }
            for (var l : responseListeners) {
                lastMemoryQuest.getWorkResponse().addListener(l);
            }
            instance.setRoot(lastMemoryQuest.getWorkOrder().getRoot());
        } else {
            instance.newRootTask(instance.nextTaskId(), taskType, title);
        }

        DefaultCalculatorFactory factory = new DefaultCalculatorFactory(model);
        for (Modelclass mc : model.getModelclasses()) {
            ModelProvider p = createProvider(mc);
            if (p != null) {
                factory.registerProvider(mc.getProvider() == null ? "ollama" : mc.getProvider(), p);
            }
            // provider "system" needs no registered provider - it is the built-in deterministic fallback
        }
        HarnessEngine engine = new HarnessEngine(model, instance, factory);
        // result channel (§3 layering): the runtime WorkCache of the MemoryQuest is handed to
        // every CalcContext, so calculators of one puzzle sequence read predecessor results
        if (lastMemoryQuest != null) {
            engine.setWorkCache(lastMemoryQuest.getWorkCache());
            engine.setCommandHistory(lastMemoryQuest.getCommandHistory());   // §18: task mutations via commands
            // live partial answers of split children -> WorkResponse as RUNNING update (the pane
            // renders non-terminal updates as normal-color answer lines)
            engine.setAnswerSink(text -> lastMemoryQuest.getWorkResponse()
                    .update(text, null, Status.RUNNING));
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

        // §11: load the per-model RAG stores (one file per rag model name under
        // .wood/harness/rag/) - two rag models = two separate stores
        File ragDir = new File(projectDir, ".wood/harness/rag");
        for (Modelclass mc : model.getModelclasses()) {
            if ("rag".equals(mc.getPurpose())) {
                File f = new File(ragDir, sanitizeFileName(mc.getModel()) + ".json");
                if (f.exists()) {
                    instance.setRag(mc.getModel(),
                            de.jare.harness.rag.RagStore.load(f, factory.embeddingProviderFor(mc),
                                    de.jare.harness.rag.RagStore.RagDefaults.standard()));
                }
            }
        }

        // run-start hook: the host can bind a live view to instance+engine BEFORE the run starts
        if (runStartHook != null) {
            runStartHook.accept(instance, engine);
        }
        engine.run();

        // feedback loop between runs (§7.1): threshold check against the metrics,
        // swap proposed as child version in the lineage, effective from the next run
        File lineageFile = new File(projectDir, ".wood/harness/lineage.json");
        HarnessLineage lineage = lineageFile.exists()
                ? HarnessLineage.load(lineageFile) : new HarnessLineage(model);
        engine.setLineage(lineage);
        engine.feedbackLoop(50.0, 1);
        System.out.println("[DIAG." + de.jare.harness.Diag.runNo() + "] save: lineage");
        lineage.save(lineageFile);

        // §17 feedback hook: the nuggets fed into prompts this run get uses (+hits when the
        // run scored >= threshold) and a per-source-calculator SCORE into the global metrics -
        // the constructed first derivative of the cost function, scoreable for every calculator
        if (projectMemory != null && !engine.getUsedMemoryIds().isEmpty()) {
            boolean hit = instance.getRoot().getScore() != null
                    && instance.getRoot().getScore() >= NUGGET_HIT_SCORE_THRESHOLD;
            for (long id : engine.getUsedMemoryIds()) {
                projectMemory.recordUse(id);
                if (hit) {
                    projectMemory.recordHit(id);
                }
                de.jare.harness.memory.ProjectMemory.Nugget n = projectMemory.get(id);
                if (n != null && n.source != null) {
                    globalMetrics.recordValue("nugget:" + n.source,
                            de.jare.harness.metrics.MetricsStore.Category.SCORE, hit ? 100 : 0);
                }
            }
        }

        exportLessons(lessons, engine, LESSON_ADVANTAGE, 1);
        globalMetrics.save(GlobalWood.metricsFile(wood));
        lessons.save(GlobalWood.lessonsFile(wood));
        engine.getMindset().save(GlobalWood.mindsetDir(wood));

        // the live answer of the run lands in the de.jare.harness.memory.MemoryQuest's WorkResponse
        if (lastMemoryQuest != null) {
            lastMemoryQuest.getWorkResponse().update(composeAnswer(instance.getRoot()),
                    instance.getRoot().getScore(), instance.getRoot().getStatus());
            lastMemoryQuest.getWorkOrder().setStatus(instance.getRoot().getStatus());
        }

        // §17: the run's outcome as assistant CHAT entry, then persist the project memory
        if (projectMemory != null) {
            // user prompt lands in the chat trace only when the run was not aborted by the guard
            if (instance.getRoot().getStatus() != de.jare.harness.Status.ABORTED) {
                projectMemory.addChat("user", title);
            }
            // the real answer is the root's summary artifact (written by SUMMARIZE/CLOSE),
            // not the WorkResponse status line
            String artifact = instance.getRoot().getArtifact();
            String answer = artifact != null && !artifact.isBlank() ? artifact
                    : lastMemoryQuest != null ? lastMemoryQuest.getWorkResponse().getText()
                    : "done: " + instance.getRoot().getStatus();
            if (instance.getRoot().getStatus() != de.jare.harness.Status.ABORTED) {
                projectMemory.addChat("assistant", answer, "run:" + instance.getRoot().getTaskType());
            }
            System.out.println("[DIAG." + de.jare.harness.Diag.runNo() + "] save: memory");
            projectMemory.save(memoryFile());
        }

        // §11: persist every rag store under its own file (per rag model)
        File ragDirOut = new File(projectDir, ".wood/harness/rag");
        for (java.util.Map.Entry<String, de.jare.harness.rag.RagStore> e : instance.getRags().entrySet()) {
            e.getValue().save(new File(ragDirOut, sanitizeFileName(e.getKey()) + ".json"));
        }

        System.out.println("[DIAG." + de.jare.harness.Diag.runNo() + "] before instance save: tasks=" + countTasks(instance.getRoot())
                + " calls=" + countCalls(instance.getRoot()) + " logEntries=" + instance.getRoot().getCalculatorLog().size());
        try {
            HarnessInstanceCodec.save(instance, instanceFile());
        } catch (Throwable t) {   // persistence must never kill the run (incl. OOM in the writer)
            System.out.println("[DIAG." + de.jare.harness.Diag.runNo() + "] instance save failed (run result stays in memory/UI): " + t);
            t.printStackTrace(System.out);
        }
        return instance;
    }

    private static int countTasks(de.jare.harness.TaskData t) {
        if (t == null) {
            return 0;
        }
        int n = 1;
        for (var c : t.getChildren()) {
            n += countTasks(c);
        }
        return n;
    }

    private static int countCalls(de.jare.harness.TaskData t) {
        if (t == null) {
            return 0;
        }
        int n = t.getCalls().size();
        for (var c : t.getChildren()) {
            n += countCalls(c);
        }
        return n;
    }

    private static String sanitizeFileName(String model) {
        return model == null ? "default" : model.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    /**
     * Lesson export (§8): when a base slot's average score beats the global prior by at
     * least {@code advantage} over enough samples, the winning slot config - not prompts,
     * not task data - is exported as a lesson to {@code ~/.wood/harness/lessons.json}.
     */
    /**
     * The answer text for the WorkResponse: the status line plus the run's real content -
     * the SUMMARIZE payload ("summary") after a split, else the WORK artifact, else the
     * CLOSE artifact - so the pane shows the answer, not only "done: DONE".
     */
    private static String composeAnswer(TaskData root) {
        String statusLine = "done: " + root.getStatus();
        String summary = null;
        String workArtifact = null;
        String closeArtifact = null;
        for (TaskData.CalcCall c : root.getCalls()) {
            String s = c.getPayload().get("summary");
            if (s != null && !s.isBlank()) {
                summary = s;
            }
            if ("WORK".equalsIgnoreCase(c.getKind())) {
                String a = c.getPayload().get("artifact");
                if (a != null && !a.isBlank()) {
                    workArtifact = a;
                }
            }
            if ("CLOSE".equalsIgnoreCase(c.getKind())) {
                String a = c.getPayload().get("artifact");
                if (a != null && !a.isBlank()) {
                    closeArtifact = a;
                }
            }
        }
        String body = summary != null ? summary : (workArtifact != null ? workArtifact : closeArtifact);
        return body == null || body.isBlank() ? statusLine : statusLine + "\n" + body;
    }

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

    /**
     * The bundled default model - loaded from the config asset
     * {@code assets/harness/harness-model.json}. RULE: prompts live ONLY in this config JSON,
     * never in code, so there is no programmatic fallback with hardcoded prompts anymore.
     *
     * <p>Lookup order (the working directory of the running app is irrelevant, e.g. the IDE
     * starts with {@code test_assets} as cwd - exactly like the editor icons):
     * 1. classpath resource {@code /harness/harness-model.json} - {@code assets/} is a
     *    resource root of the app module (that is why {@code /icons/...} resolves), so the
     *    asset is on the classpath as {@code /harness/harness-model.json},
     * 2. classpath resource {@code /assets/harness/harness-model.json} (assets/ itself on the cp),
     * 3. file relative to the working directory (dev run from the repo root),
     * 4. file found by walking up from this class's code source.</p>
     */
    public static HarnessModel defaultModel() {
        String assetPath = "assets/harness/harness-model.json";
        try {
            for (String resPath : new String[]{"/harness/harness-model.json", "/" + assetPath}) {
                java.net.URL res = HarnessService.class.getResource(resPath);
                if (res != null) {
                    return completeModel(HarnessModelCodec.fromJson(
                            de.jare.jsoncasted.io.parserservice.JsonParserService
                                    .parse(res, de.jare.debug.JsonDebugLevel.SIMPLE).getRoot()));
                }
            }
            File asset = new File(assetPath);
            if (!asset.exists()) {
                // IDE run (working dir is NOT the repo root, e.g. test_assets): walk up from this
                // class's code source (out/, out/production/..., jar dir) until the repo root with
                // assets/harness/harness-model.json is found
                java.net.URL codeSource = HarnessService.class.getProtectionDomain()
                        .getCodeSource().getLocation();
                File dir = new File(codeSource.toURI());
                if (dir.isFile()) {
                    dir = dir.getParentFile();   // jar file -> its directory
                }
                for (int i = 0; i < 6 && dir != null; i++) {
                    File candidate = new File(dir, assetPath);
                    if (candidate.exists()) {
                        asset = candidate;
                        break;
                    }
                    dir = dir.getParentFile();
                }
            }
            if (!asset.exists()) {
                throw new IllegalStateException("bundled harness model asset missing: " + assetPath
                        + " (tried classpath, working dir and code-source walk-up; prompts live only in"
                        + " the config JSON, there is no code fallback)");
            }
            return completeModel(HarnessModelCodec.load(asset));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("bundled harness model asset is not parseable: " + e.getMessage(), e);
        }
    }

    /** Completes a loaded model: every kind NOT modelled gets a deterministic fallback quest (no prompts in code). */
    private static HarnessModel completeModel(HarnessModel model) {
        for (Kind kind : Kind.values()) {
            if (model.questFor(kind) == null) {
                model.addQuest(new Quest(kind).addSlot(new Slot(kind.name().toLowerCase() + ".base")
                        .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC))));
            }
        }
        return model;
    }
}
