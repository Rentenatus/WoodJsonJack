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
import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Modelclass;
import de.jare.harness.model.Quest;
import de.jare.harness.model.Slot;
import java.io.File;
import java.io.IOException;

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

    private final File projectDir;

    public HarnessService(File projectDir) {
        this.projectDir = projectDir;
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

    /** Loads the project model, or falls back to the bundled default model. */
    public HarnessModel loadModel() throws IOException, de.jare.jsoncasted.io.JsonParseException {
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
        instance.setRoot(new TaskNode(instance.nextTaskId(), taskType, title));

        DefaultCalculatorFactory factory = new DefaultCalculatorFactory(model);
        for (var e : model.getModelclasses().entrySet()) {
            Modelclass mc = e.getValue();
            if ("ollama".equals(mc.getProvider())) {
                factory.registerProvider("ollama", new OllamaProvider(mc));
            } else if ("hosted".equals(mc.getProvider())) {
                String apiKey = de.jare.tree.settings.WoodSettings.INSTANCE.getAgentPreferences() != null
                        ? de.jare.tree.settings.WoodSettings.INSTANCE.getAgentPreferences().getApiKey()
                        : "";
                factory.registerProvider("hosted", new de.jare.harness.impl.hosted.HostedProvider(apiKey, mc));
            }
        }
        HarnessEngine engine = new HarnessEngine(model, instance, factory);
        engine.run();

        // feedback loop between runs (§7.1): threshold check against the metrics,
        // swap proposed as child version in the lineage, effective from the next run
        File lineageFile = new File(projectDir, ".wood/harness/lineage.json");
        HarnessLineage lineage = lineageFile.exists()
                ? HarnessLineage.load(lineageFile) : new HarnessLineage(model);
        engine.setLineage(lineage);
        engine.feedbackLoop(50.0, 1);
        lineage.save(lineageFile);

        HarnessInstanceCodec.save(instance, instanceFile());
        return instance;
    }

    /** The bundled default model: all twelve quests deterministic, modelclasses per §5 example. */
    public static HarnessModel defaultModel() {
        HarnessModel model = new HarnessModel().name("default-harness").version("1.0.0")
                .abortLimits(50, 5, 3, 10);
        model.getTaskTypes().add("RESEARCH");
        model.getTaskTypes().add("CODE_EDIT");
        model.getTaskTypes().add("TEST_RUN");
        model.getTaskTypes().add("SUMMARIZE");
        model.getPromptBudgets().put("SYSTEM", 2);
        model.getPromptBudgets().put("SKILL", 1);
        model.getPromptBudgets().put("HISTORY", 3);
        model.getPromptBudgets().put("DATA", 2);
        model.getPromptBudgets().put("AUFTRAG", 1);
        model.addModelclass("investigation", new Modelclass("ollama", "qwen2.5:3b", 1.0, true));
        model.addModelclass("edit", new Modelclass("ollama", "qwen2.5:7b", 2.0, true));
        model.addModelclass("plan", new Modelclass("ollama", "llama3.2:3b", 1.0, true));
        model.addModelclass("evaluation", new Modelclass("ollama", "llama3.2:3b", 0.5, false));
        model.addModelclass("rag", new Modelclass("ollama", "nomic-embed-text", 0.2, false));

        for (Kind kind : Kind.values()) {
            Quest quest = new Quest(kind).evolvable(kind == Kind.WORK);
            quest.addSlot(new Slot(kind.name().toLowerCase() + ".base")
                    .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC)));
            model.addQuest(quest);
        }
        return model;
    }
}
