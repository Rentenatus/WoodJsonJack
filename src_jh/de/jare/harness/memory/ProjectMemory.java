/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.memory;

import de.jare.harness.io.AtomicFileWrite;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.lang.JsonNode;
import de.jare.debug.JsonDebugLevel;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ProjectMemory (Projektgedächtnis, CONCEPT_Harness.md §17): the project-local memory that
 * survives runs - the chat history (CHAT entries) and extracted facts (NUGGET entries, produced
 * later by dedicated calculators in a loop slot). Persisted project-local at
 * {@code <projekt>/.wood/harness/memory.json} via write-temp-rename, NEVER global (§8 principle).
 *
 * <p>Every entry carries the feedback fields {@code uses} / {@code hits} - how often the nugget
 * was fed into a follow-up prompt and how many of those runs ended with score >= threshold. That
 * is the constructed first derivative of the cost function (§9 analogy): it allows scoring every
 * calculator, including nugget producers and consumers, over the same metric pipeline as §7/§8.</p>
 *
 * <p>Expiry ("yesterday must not disturb today"): per-entry {@code ttlMs} (0 = never expires),
 * pruned lazily on access and on save; explicit control via {@link #forget(long)} and
 * {@link #clear()} (the {@code /clear} chat command).</p>
 *
 * @author Janusch Rentenatus
 */
public class ProjectMemory {

    /** Entry kind: a chat message, an extracted fact, or a standing user decision. */
    public enum Kind {
        CHAT, NUGGET, DECISION
    }

    /** Default TTLs: chat 7 days, nuggets 30 days, decisions 90 days, 0 = never expires. */
    public static final long DEFAULT_TTL_CHAT_MS = 7L * 24 * 3600 * 1000;
    public static final long DEFAULT_TTL_NUGGET_MS = 30L * 24 * 3600 * 1000;
    public static final long DEFAULT_TTL_DECISION_MS = 90L * 24 * 3600 * 1000;
    public static final long NEVER_EXPIRES = 0L;

    /**
     * Hard cap for CHAT entries (the chat history must not grow without bound - a long
     * session would otherwise blow up memory.json and the save). On save, the OLDEST
     * chat entries beyond the cap are evicted FIFO. NUGGET/DECISION entries are not
     * affected (they have their own TTLs and feedback fields).
     */
    public static final int MAX_CHAT_ENTRIES = 200;

    /** One memory entry (chat message or nugget). */
    public static class Nugget {
        public final long id;
        public final long ts;
        public final Kind kind;
        public final String role;      // CHAT only: "user" | "assistant"
        public final String text;
        public final String source;    // NUGGET only: producing calculator name
        public final long ttlMs;
        public int uses;               // feedback: fed into a follow-up prompt this often
        public int hits;               // feedback: of those, runs with score >= threshold

        public Nugget(long id, long ts, Kind kind, String role, String text, String source, long ttlMs) {
            this.id = id;
            this.ts = ts;
            this.kind = kind;
            this.role = role;
            this.text = text;
            this.source = source;
            this.ttlMs = ttlMs;
        }

        public boolean isExpired(long now) {
            return ttlMs > 0 && ts + ttlMs < now;
        }

        /** Feedback ratio hits/uses - the constructed gradient signal for the source calculator. */
        public double hitRatio() {
            return uses == 0 ? 0.0 : (double) hits / uses;
        }
    }

    private final Map<Long, Nugget> entries = new LinkedHashMap<>();
    private long nextId = 1;

    /** Adds a chat message (role "user"/"assistant") with the default chat TTL. */
    public Nugget addChat(String role, String text) {
        return addChat(role, text, null);
    }

    /** Chat message with a source marker (e.g. "askai.decompose" for split sub-prompts). */
    public Nugget addChat(String role, String text, String source) {
        return add(Kind.CHAT, role, text, source, DEFAULT_TTL_CHAT_MS);
    }

    /** Adds a nugget (extracted fact) with the default nugget TTL; source = producing calculator. */
    public Nugget addNugget(String text, String source) {
        return add(Kind.NUGGET, null, text, source, DEFAULT_TTL_NUGGET_MS);
    }

    /**
     * Adds a standing user decision (e.g. "nicht mehr commiten", "nach jedem Schritt commiten")
     * with the long decision TTL. Decisions are fed into prompts as SYSTEM segments, so they
     * steer every following run until they expire or are forgotten.
     */
    public Nugget addDecision(String text, String source) {
        return add(Kind.DECISION, null, text, source, DEFAULT_TTL_DECISION_MS);
    }

    /** Adds an entry with an explicit TTL (0 = never expires). */
    public Nugget add(Kind kind, String role, String text, String source, long ttlMs) {
        Nugget n = new Nugget(nextId++, System.currentTimeMillis(), kind, role, text, source, ttlMs);
        entries.put(n.id, n);
        return n;
    }

    /** Explicit forget (the /forget &lt;id&gt; chat command). Returns false when the id is unknown. */
    public boolean forget(long id) {
        return entries.remove(id) != null;
    }

    /** /clear: drops everything. */
    public void clear() {
        entries.clear();
    }

    /** Removes all expired entries (lazy prune on access/save). Returns the removed count. */
    public int pruneExpired(long now) {
        int before = entries.size();
        entries.values().removeIf(n -> n.isExpired(now));
        return before - entries.size();
    }

    /** Feedback: the nugget was fed into a follow-up prompt. */
    public void recordUse(long id) {
        Nugget n = entries.get(id);
        if (n != null) {
            n.uses++;
        }
    }

    /** Feedback: a run that used the nugget ended with score >= threshold. */
    public void recordHit(long id) {
        Nugget n = entries.get(id);
        if (n != null) {
            n.hits++;
        }
    }

    public Nugget get(long id) {
        return entries.get(id);
    }

    /** All entries (insertion order), including not-yet-pruned expired ones. */
    public List<Nugget> getEntries() {
        return new ArrayList<>(entries.values());
    }

    /** All live (not expired) entries at the given time. */
    public List<Nugget> live(long now) {
        List<Nugget> out = new ArrayList<>();
        for (Nugget n : entries.values()) {
            if (!n.isExpired(now)) {
                out.add(n);
            }
        }
        return out;
    }

    public int size() {
        return entries.size();
    }

    /** Persists to {@code <projekt>/.wood/harness/memory.json} shape: { nextId, entries[] }.
     *  Streams to a temp file (no full JSON string in the heap), then atomic rename. */
    public void save(File file) throws IOException, JsonParseException {
        pruneExpired(System.currentTimeMillis());
        evictChatCap();
        JsonNode root = JsonNode.objectNode();
        root.put("nextId", JsonNode.longNode(nextId));
        JsonNode arr = JsonNode.arrayNode();
        for (Nugget n : entries.values()) {
            JsonNode e = JsonNode.objectNode();
            e.put("id", JsonNode.longNode(n.id));
            e.put("ts", JsonNode.longNode(n.ts));
            e.put("kind", JsonNode.stringNode(n.kind.name()));
            e.put("role", n.role == null ? JsonNode.nullNode() : JsonNode.stringNode(n.role));
            e.put("text", JsonNode.stringNode(n.text));
            e.put("source", n.source == null ? JsonNode.nullNode() : JsonNode.stringNode(n.source));
            e.put("ttlMs", JsonNode.longNode(n.ttlMs));
            e.put("uses", JsonNode.longNode(n.uses));
            e.put("hits", JsonNode.longNode(n.hits));
            arr.add(e);
        }
        root.put("entries", arr);
        AtomicFileWrite.writeNode(file.toPath(), root);
    }

    /** FIFO eviction: drops the oldest CHAT entries beyond {@link #MAX_CHAT_ENTRIES}
     *  (LinkedHashMap keeps insertion order = id order, so iteration hits the oldest first). */
    private void evictChatCap() {
        long chatCount = entries.values().stream().filter(n -> n.kind == Kind.CHAT).count();
        long toRemove = chatCount - MAX_CHAT_ENTRIES;
        if (toRemove <= 0) {
            return;
        }
        java.util.Iterator<Nugget> it = entries.values().iterator();
        while (it.hasNext() && toRemove > 0) {
            Nugget n = it.next();
            if (n.kind == Kind.CHAT) {
                it.remove();
                toRemove--;
            }
        }
    }

    /** Loads a persisted memory; a missing file yields an empty memory. */
    public static ProjectMemory load(File file) throws IOException, JsonParseException {
        ProjectMemory mem = new ProjectMemory();
        if (file == null || !file.exists()) {
            return mem;
        }
        JsonNode root = JsonParserService.parse(file, JsonDebugLevel.SIMPLE).getRoot();
        JsonNode nextIdNode = root.asObjectValues().get("nextId");
        if (nextIdNode != null) {
            mem.nextId = nextIdNode.asLong();
        }
        JsonNode arr = root.asObjectValues().get("entries");
        if (arr != null && arr.isArray()) {
            for (JsonNode e : arr.asArray()) {
                Map<String, JsonNode> v = e.asObjectValues();
                JsonNode role = v.get("role");
                JsonNode source = v.get("source");
                Nugget n = new Nugget(v.get("id").asLong(), v.get("ts").asLong(),
                        Kind.valueOf(v.get("kind").asText()),
                        role == null || role.isNull() ? null : role.asText(),
                        v.get("text").asText(),
                        source == null || source.isNull() ? null : source.asText(),
                        v.get("ttlMs").asLong());
                n.uses = (int) v.get("uses").asLong().longValue();
                n.hits = (int) v.get("hits").asLong().longValue();
                mem.entries.put(n.id, n);
            }
        }
        return mem;
    }
}
