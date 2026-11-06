/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.mindset.Mindset;
import de.jare.harness.rag.EmbeddingProvider;
import de.jare.harness.rag.RagChunk;
import de.jare.harness.rag.RagStore;
import de.jare.harness.skills.Skill;
import de.jare.harness.skills.SkillRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
