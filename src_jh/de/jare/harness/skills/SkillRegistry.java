/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.skills;

import de.jare.harness.rag.RagChunk;
import de.jare.harness.rag.RagStore;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The skill registry (CONCEPT_Harness.md §12): skills live as normal files/folders -
 * global under {@code ~/.wood/skills/skill/}, project-local under
 * {@code <projekt>/.wood/skills/skill/} - the user has full file-level control.
 * Importing/approving a skill writes each of its N descriptions as its own RAG chunk
 * with {@code docType = SKILL} and meta {skillId, descIndex, scope, source}.
 *
 * @author Janusch Rentenatus
 */
public class SkillRegistry {

    public static final String DOC_TYPE_SKILL = "SKILL";
    public static final String GLOBAL_SKILL_DIR = ".wood/skills/skill";

    private final Map<String, Skill> skills = new LinkedHashMap<>();

    /** Loads all skills from a skill folder: one subfolder per skill, description files inside. */
    public void loadFrom(Path skillDir, String source) throws IOException {
        if (skillDir == null || !Files.isDirectory(skillDir)) {
            return;
        }
        try (Stream<Path> dirs = Files.list(skillDir)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                Skill skill = new Skill(dir.getFileName().toString(), dir.getFileName().toString());
                skill.setSource(source);
                skill.setPath(dir.toString());
                try (Stream<Path> files = Files.list(dir)) {
                    int idx = 0;
                    for (Path f : files.filter(Files::isRegularFile).sorted().toList()) {
                        String text = Files.readString(f);
                        String scope = f.getFileName().toString().contains("edit") ? "edit"
                                : f.getFileName().toString().contains("search") ? "search" : "general";
                        skill.addDescription(text, scope);
                        idx++;
                    }
                }
                skills.put(skill.getSkillId(), skill);
            }
        }
    }

    public Skill get(String skillId) {
        return skills.get(skillId);
    }

    public List<Skill> all() {
        return new ArrayList<>(skills.values());
    }

    /**
     * Import/approve: writes every description as its own SKILL chunk into the
     * project RAG (N descriptions = N chunks, §12).
     *
     * @return the stored chunks
     */
    public List<RagChunk> importSkill(Skill skill, RagStore rag) {
        List<RagChunk> out = new ArrayList<>();
        int descIndex = 0;
        for (Skill.Description d : skill.getDescriptions()) {
            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("skillId", skill.getSkillId());
            meta.put("docType", DOC_TYPE_SKILL);
            meta.put("descIndex", String.valueOf(descIndex));
            meta.put("scope", d.scope());
            meta.put("source", skill.getSource() == null ? "local" : skill.getSource());
            out.add(rag.store(DOC_TYPE_SKILL, d.text(), meta));
            descIndex++;
        }
        return out;
    }

    /** All SKILL chunks of one skill, optionally filtered by scope (§14 ctx.skills). */
    public List<RagChunk> skillChunks(RagStore rag, String skillId, String scope) {
        List<RagChunk> out = new ArrayList<>();
        for (RagChunk c : rag.getChunks()) {
            if (DOC_TYPE_SKILL.equals(c.getDocType()) && skillId.equals(c.getMeta().get("skillId"))) {
                if (scope == null || scope.equals(c.getMeta().get("scope"))) {
                    out.add(c);
                }
            }
        }
        return out;
    }
}
