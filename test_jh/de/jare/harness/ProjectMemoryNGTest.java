/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.memory.ProjectMemory;
import de.jare.harness.memory.ProjectMemory.Kind;
import de.jare.harness.memory.ProjectMemory.Nugget;
import java.io.File;
import java.nio.file.Files;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * The ProjectMemory (CONCEPT_Harness.md §17): project-local chat history + nuggets with
 * TTL expiry, explicit forget/clear, the uses/hits feedback fields (constructed gradient
 * for scoring every calculator) and the atomic save/load roundtrip.
 *
 * @author Janusch Rentenatus
 */
public class ProjectMemoryNGTest {

    @Test
    public void testAddChatAndNugget() {
        ProjectMemory mem = new ProjectMemory();
        Nugget chat = mem.addChat("user", "wie viel ist 2*4?");
        Nugget nug = mem.addNugget("User rechnet gern in Deutsch", "nugget.extract");
        assertEquals(chat.kind, Kind.CHAT);
        assertEquals(chat.role, "user");
        assertEquals(chat.ttlMs, ProjectMemory.DEFAULT_TTL_CHAT_MS);
        assertEquals(nug.kind, Kind.NUGGET);
        assertEquals(nug.source, "nugget.extract");
        assertEquals(nug.ttlMs, ProjectMemory.DEFAULT_TTL_NUGGET_MS);
        assertNotEquals(chat.id, nug.id, "ids are unique and increasing");
        assertEquals(mem.size(), 2);
    }

    @Test
    public void testAddDecision() {
        ProjectMemory mem = new ProjectMemory();
        Nugget d = mem.addDecision("nicht mehr commiten", "user:/decide");
        assertEquals(d.kind, Kind.DECISION);
        assertEquals(d.text, "nicht mehr commiten");
        assertEquals(d.source, "user:/decide");
        assertEquals(d.ttlMs, ProjectMemory.DEFAULT_TTL_DECISION_MS, "decisions live longer than chat");
        assertTrue(ProjectMemory.DEFAULT_TTL_DECISION_MS > ProjectMemory.DEFAULT_TTL_CHAT_MS);
        assertFalse(d.isExpired(System.currentTimeMillis() + 89L * 24 * 3600 * 1000));
        assertTrue(d.isExpired(System.currentTimeMillis() + 91L * 24 * 3600 * 1000));
    }

    @Test
    public void testForgetAndClear() {
        ProjectMemory mem = new ProjectMemory();
        Nugget a = mem.addChat("user", "eins");
        mem.addChat("assistant", "zwei");
        assertTrue(mem.forget(a.id));
        assertFalse(mem.forget(a.id), "second forget of the same id fails");
        assertEquals(mem.size(), 1);
        mem.clear();
        assertEquals(mem.size(), 0);
    }

    @Test
    public void testTtlExpiry() {
        long now = System.currentTimeMillis();
        ProjectMemory mem = new ProjectMemory();
        Nugget old = mem.add(Kind.CHAT, "user", "alt", null, 1000);
        Nugget fresh = mem.add(Kind.CHAT, "user", "neu", null, 60_000);
        Nugget forever = mem.add(Kind.NUGGET, null, "ewig", "src", ProjectMemory.NEVER_EXPIRES);
        // age the old entry beyond its ttl
        assertTrue(old.isExpired(now + 2000));
        assertFalse(fresh.isExpired(now + 2000));
        assertFalse(forever.isExpired(now + 2000));
        assertEquals(mem.live(now + 2000).size(), 2, "expired entry is not live");
        assertEquals(mem.pruneExpired(now + 2000), 1, "prune removes exactly the expired one");
        assertEquals(mem.size(), 2);
    }

    @Test
    public void testUsesHitsFeedback() {
        ProjectMemory mem = new ProjectMemory();
        Nugget n = mem.addNugget("Fakt", "nugget.extract");
        mem.recordUse(n.id);
        mem.recordUse(n.id);
        mem.recordHit(n.id);
        mem.recordUse(999); // unknown id: no-op, no exception
        assertEquals(n.uses, 2);
        assertEquals(n.hits, 1);
        assertEquals(n.hitRatio(), 0.5, 1e-9);
        assertEquals(mem.addNugget("anderer", "src").hitRatio(), 0.0, "no uses -> ratio 0");
    }

    @Test
    public void testSaveLoadRoundtrip() throws Exception {
        File dir = Files.createTempDirectory("projectmemory").toFile();
        File file = new File(dir, ".wood/harness/memory.json");
        ProjectMemory mem = new ProjectMemory();
        Nugget chat = mem.addChat("user", "hallo");
        Nugget nug = mem.addNugget("User mag kurze Antworten", "nugget.extract");
        mem.recordUse(nug.id);
        mem.recordHit(nug.id);
        mem.save(file);
        assertTrue(file.exists(), "atomic write created the file (incl. parent dirs)");

        ProjectMemory loaded = ProjectMemory.load(file);
        assertEquals(loaded.size(), 2);
        Nugget lc = loaded.get(chat.id);
        assertEquals(lc.kind, Kind.CHAT);
        assertEquals(lc.role, "user");
        assertEquals(lc.text, "hallo");
        Nugget ln = loaded.get(nug.id);
        assertEquals(ln.source, "nugget.extract");
        assertEquals(ln.uses, 1);
        assertEquals(ln.hits, 1);
        // id counter survives the roundtrip: new entries do not collide
        Nugget after = loaded.addChat("assistant", "weiter");
        assertTrue(after.id > nug.id);
    }

    @Test
    public void testLoadMissingFileIsEmpty() throws Exception {
        File dir = Files.createTempDirectory("projectmemory-empty").toFile();
        ProjectMemory mem = ProjectMemory.load(new File(dir, "does/not/exist.json"));
        assertEquals(mem.size(), 0);
    }
}
