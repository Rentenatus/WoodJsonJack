/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.impl.DefaultCalculatorFactory;
import de.jare.harness.mindset.Mindset;
import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Quest;
import de.jare.harness.model.Slot;
import de.jare.harness.rag.EmbeddingProvider;
import de.jare.harness.rag.RagChunk;
import de.jare.harness.rag.RagStore;
import de.jare.harness.skills.Skill;
import de.jare.harness.skills.SkillRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for skills (§12) and mindset (§16): N descriptions of one skill become N RAG
 * chunks with separate scopes, import writes docType=SKILL chunks into the project
 * RAG, and the global mindset stores categories with the 1024-char warning-only limit.
 *
 * @author Janusch Rentenatus
 */
public class HarnessSkillsMindsetNGTest {

    @Test
    public void testSkillWithNDescriptionsBecomesNChunksWithScopes() throws Exception {
        Path globalSkills = Files.createTempDirectory("skills-global").resolve("skill");
        Path mySkill = Files.createDirectories(globalSkills.resolve("code-review"));
        Files.writeString(mySkill.resolve("search.md"), "Greift wenn der User etwas sucht oder analysiert.");
        Files.writeString(mySkill.resolve("edit.md"), "Greift wenn der User Änderungen vornehmen will.");

        SkillRegistry registry = new SkillRegistry();
        registry.loadFrom(globalSkills, "global");
        Skill skill = registry.get("code-review");
        Assert.assertNotNull(skill);
        Assert.assertEquals(skill.getDescriptions().size(), 2);
        Assert.assertEquals(skill.getSource(), "global");

        RagStore rag = new RagStore(EmbeddingProvider.hashEmbedding());
        List<RagChunk> chunks = registry.importSkill(skill, rag);
        Assert.assertEquals(chunks.size(), 2, "N descriptions = N RAG chunks");
        for (RagChunk c : chunks) {
            Assert.assertEquals(c.getDocType(), "SKILL");
            Assert.assertEquals(c.getMeta().get("skillId"), "code-review");
            Assert.assertEquals(c.getMeta().get("source"), "global");
        }
        Assert.assertEquals(registry.skillChunks(rag, "code-review", "search").size(), 1);
        Assert.assertEquals(registry.skillChunks(rag, "code-review", "edit").size(), 1);
        Assert.assertEquals(registry.skillChunks(rag, "code-review", null).size(), 2);
        // retrieval matches per scope: a search query finds the search chunk
        List<RagStore.Hit> hits = rag.retrieve("User sucht etwas", 5, 0.0);
        Assert.assertTrue(hits.stream().anyMatch(h -> "search".equals(h.chunk().getMeta().get("scope"))));
    }

    @Test
    public void testMindsetCategoriesAndWarningOnlyLimit() throws Exception {
        Mindset m = new Mindset();
        Assert.assertTrue(m.set("answer", "Antworte wie ein Mathematiker"));
        Assert.assertEquals(m.get("answer"), "Antworte wie ein Mathematiker");

        String tooLong = "x".repeat(Mindset.MAX_CHARS + 1);
        Assert.assertFalse(m.set("analysis", tooLong), "over 1024 chars: warning only, not abort");
        Assert.assertTrue(m.getLastWarning().contains("1024"));
        Assert.assertEquals(m.get("analysis").length(), Mindset.MAX_CHARS + 1, "stored anyway");
    }

    @Test
    public void testMindsetPersistenceOneFilePerCategory() throws Exception {
        Path dir = Files.createTempDirectory("mindset");
        Mindset m = new Mindset();
        m.set("answer", "wie ein Mathematiker");
        m.set("analysis", "drei Rollen: technisch, kreativ, wirtschaftlich");
        m.save(dir);

        Mindset loaded = Mindset.load(dir);
        Assert.assertEquals(loaded.get("answer"), "wie ein Mathematiker");
        Assert.assertEquals(loaded.get("analysis"), "drei Rollen: technisch, kreativ, wirtschaftlich");
    }

    @Test
    public void testSkillAndMindsetLandInPromptList() {
        HarnessModel model = new HarnessModel().abortLimits(50, 5, 3, 10);
        model.getPromptBudgets().put("SYSTEM", 2);
        model.getPromptBudgets().put("SKILL", 1);
        model.getPromptBudgets().put("HISTORY", 3);
        model.getPromptBudgets().put("DATA", 2);
        model.getPromptBudgets().put("AUFTRAG", 1);
        for (Kind kind : Kind.values()) {
            model.addQuest(new Quest(kind).addSlot(new Slot(kind.name().toLowerCase() + ".base")
                    .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC))));
        }
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        instance.newRootTask(instance.nextTaskId(), "RESEARCH", "User sucht etwas analysieren");

        RagStore rag = instance.getRag(EmbeddingProvider.hashEmbedding());
        rag.setDefaults(new RagStore.RagDefaults(5, 0.0, 1000, 7L * 24 * 3600 * 1000));
        Map<String, String> meta = new HashMap<>();
        meta.put("docType", "SKILL");
        meta.put("skillId", "code-review");
        meta.put("scope", "search");
        rag.store("SKILL", "Greift wenn der User etwas sucht oder analysiert.", meta);
        rag.store("RESULT", "irrelevanter Kontext", Map.of("taskType", "RESEARCH"));

        List<List<String>> workPrompts = new ArrayList<>();
        DefaultCalculatorFactory base = new DefaultCalculatorFactory(model);
        CalculatorFactory capturing = (node, kind) -> {
            HarnessCalculator inner = base.createAtomic(node, kind);
            if (kind != Kind.WORK) {
                return inner;
            }
            return new HarnessCalculator() {
                @Override public Kind kind() { return Kind.WORK; }
                @Override public CalcResult calculate(CalcContext ctx) {
                    workPrompts.add(ctx.prompts());
                    return inner.calculate(ctx);
                }
            };
        };
        HarnessEngine engine = new HarnessEngine(model, instance, capturing);
        Mindset m = new Mindset();
        m.set("answer", "Antworte wie ein Mathematiker");
        engine.setMindset(m);
        engine.run();

        Assert.assertFalse(workPrompts.isEmpty(), "WORK must have run");
        List<String> prompts = workPrompts.get(0);
        Assert.assertTrue(prompts.stream().anyMatch(p -> p.startsWith("SKILL:skill:code-review")),
                "SKILL segment references the skill id, got: " + prompts);
        Assert.assertTrue(prompts.stream().anyMatch(p -> p.startsWith("SKILL:skill:code-review")
                && p.contains("see RAG chunk")), "reference only, never the full skill text (§12)");
        Assert.assertTrue(prompts.stream().noneMatch(p -> p.startsWith("DATA:")
                && p.contains("Greift wenn der User")), "skill full text must not land as DATA segment");
        Assert.assertTrue(prompts.stream().anyMatch(p -> p.startsWith("SYSTEM:mindset:answer")
                && p.contains("Antworte wie ein Mathematiker")), "mindset category as SYSTEM segment (§16)");
    }

    @Test
    public void testCalcContextFacadesPerConcept14() {
        HarnessModel model = new HarnessModel().abortLimits(50, 5, 3, 10);
        model.getPromptBudgets().put("SYSTEM", 2);
        model.getPromptBudgets().put("SKILL", 1);
        model.getPromptBudgets().put("HISTORY", 3);
        model.getPromptBudgets().put("DATA", 2);
        model.getPromptBudgets().put("AUFTRAG", 1);
        model.addModelclass(new de.jare.harness.model.Modelclass("ollama", "qwen2.5:7b", "edit", 0, 2.0, true));
        for (Kind kind : Kind.values()) {
            model.addQuest(new Quest(kind).addSlot(new Slot(kind.name().toLowerCase() + ".base")
                    .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC))));
        }
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        instance.newRootTask(instance.nextTaskId(), "RESEARCH", "facade check");

        List<CalcContext> seen = new ArrayList<>();
        DefaultCalculatorFactory base = new DefaultCalculatorFactory(model);
        CalculatorFactory capturing = (node, kind) -> {
            HarnessCalculator inner = base.createAtomic(node, kind);
            if (kind != Kind.WORK) {
                return inner;
            }
            return new HarnessCalculator() {
                @Override public Kind kind() { return Kind.WORK; }
                @Override public CalcResult calculate(CalcContext ctx) {
                    seen.add(ctx);
                    return inner.calculate(ctx);
                }
            };
        };
        HarnessEngine engine = new HarnessEngine(model, instance, capturing);
        engine.setSkillRegistry(new SkillRegistry());
        engine.run();

        Assert.assertFalse(seen.isEmpty());
        CalcContext ctx = seen.get(0);
        Assert.assertNotNull(ctx.skills(), "§14 ctx.skills");
        Assert.assertNotNull(ctx.relevance("RESEARCH", 0), "§14 ctx.prompts.relevance");
        Assert.assertFalse(ctx.relevance("RESEARCH", 0).isEmpty());
        Assert.assertNull(ctx.modelclass(), "deterministic slot has no modelclass bound");
        Assert.assertTrue(ctx.isProviderAvailable(), "no modelclass -> no outage");
        Assert.assertEquals(ctx.askUser("which?", List.of("a", "b")), "a", "§14 default askUser picks first option");
        engine.setUserAskHandler((q, opts) -> opts.get(1));
        Assert.assertEquals(ctx.askUser("which?", List.of("a", "b")), "b", "§14 ctx.ui.askUser via UI handler");
    }

    @Test
    public void testGlobalWoodPathsRedirectableAndMindsetRoundTrip() throws Exception {
        Path fakeHome = Files.createTempDirectory("fake-wood-home");
        String old = System.getProperty(GlobalWood.WOOD_HOME_PROPERTY);
        System.setProperty(GlobalWood.WOOD_HOME_PROPERTY, fakeHome.toString());
        try {
            Assert.assertEquals(GlobalWood.woodHome(), fakeHome.toFile());
            Assert.assertTrue(GlobalWood.metricsFile(GlobalWood.woodHome()).getPath()
                    .endsWith(".wood/harness/metrics.json".replace('/', java.io.File.separatorChar)));
            Assert.assertTrue(GlobalWood.lessonsFile(GlobalWood.woodHome()).getPath()
                    .endsWith(".wood/harness/lessons.json".replace('/', java.io.File.separatorChar)));
            Assert.assertTrue(GlobalWood.mindsetDir(GlobalWood.woodHome()).toString()
                    .endsWith(".wood/mindset".replace('/', java.io.File.separatorChar)));

            Mindset m = new Mindset();
            m.set("answer", "wie ein Mathematiker");
            m.save(GlobalWood.mindsetDir(GlobalWood.woodHome()));
            Mindset loaded = Mindset.load(GlobalWood.mindsetDir(GlobalWood.woodHome()));
            Assert.assertEquals(loaded.get("answer"), "wie ein Mathematiker",
                    "mindset persists under the global ~/.wood/mindset/ (§16)");
        } finally {
            if (old == null) {
                System.clearProperty(GlobalWood.WOOD_HOME_PROPERTY);
            } else {
                System.setProperty(GlobalWood.WOOD_HOME_PROPERTY, old);
            }
        }
    }
}
