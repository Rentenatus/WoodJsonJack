/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.skills;

import java.util.ArrayList;
import java.util.List;

/**
 * One skill (CONCEPT_Harness.md §12): unlike usual harnesses a skill does not have to
 * hold its description in the window - it may carry N descriptions, each stored as its
 * own RAG chunk with its own scope (e.g. scope "search" vs "edit"), matched separately
 * against the user prompt, but still one skill.
 *
 * @author Janusch Rentenatus
 */
public class Skill {

    /** One description = one RAG chunk with its own matching scope. */
    public record Description(String text, String scope) {
    }

    private final String skillId;
    private String name;
    private final List<Description> descriptions = new ArrayList<>();
    private String source;      // "global" (~/.wood/skills/skill/) | "local" (<projekt>/.wood/skills/skill/)
    private String path;        // the skill file/folder on disk - user has full file-level control

    public Skill(String skillId, String name) {
        this.skillId = skillId;
        this.name = name;
    }

    public String getSkillId() { return skillId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Description> getDescriptions() { return descriptions; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public Skill addDescription(String text, String scope) {
        descriptions.add(new Description(text, scope));
        return this;
    }
}
