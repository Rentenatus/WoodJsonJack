/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.prompts;

import de.jare.harness.Kind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The default deterministic implementation of the RELEVANCE calculator
 * (CONCEPT_Harness.md §4): a versioned registry table addressed by
 * taskType × attempt × kind × modelVersion → segment refs. Cheap, reproducible,
 * versionable in the repo. An Ollama implementation is optional; its proposal is
 * validated by the driver against budgets + allowed refs and hard-truncated.
 *
 * @author Janusch Rentenatus
 */
public class RelevanceRegistry {

    private record Key(String taskType, int attempt, Kind kind, String modelVersion) {
    }

    private final Map<Key, List<String>> table = new HashMap<>();
    private final List<String> defaultRefs;

    public RelevanceRegistry() {
        this(List.of("prompts/system/orchestrator.v1.md"));
    }

    public RelevanceRegistry(List<String> defaultRefs) {
        this.defaultRefs = defaultRefs;
    }

    public void register(String taskType, int attempt, Kind kind, String modelVersion, List<String> refs) {
        table.put(new Key(taskType, attempt, kind, modelVersion), refs);
    }

    /**
     * Resolves the segment refs for one call: exact match first, then wildcard fallbacks
     * (taskType×kind any attempt, any taskType same kind, then the default).
     */
    public List<PromptSegment> resolve(String taskType, int attempt, Kind kind, String modelVersion,
                                       String auftragText) {
        List<String> refs = table.getOrDefault(new Key(taskType, attempt, kind, modelVersion), null);
        if (refs == null) {
            refs = table.getOrDefault(new Key(taskType, -1, kind, modelVersion), null);
        }
        if (refs == null) {
            refs = table.getOrDefault(new Key(taskType, -1, kind, "*"), null);
        }
        if (refs == null) {
            refs = table.getOrDefault(new Key("*", -1, kind, "*"), null);
        }
        if (refs == null) {
            refs = defaultRefs;
        }
        List<PromptSegment> segments = new ArrayList<>();
        for (String ref : refs) {
            segments.add(PromptSegment.of(PromptSegment.Category.SYSTEM, ref, "@" + ref));
        }
        segments.add(PromptSegment.of(PromptSegment.Category.AUFTRAG, "inline:task", auftragText));
        return segments;
    }
}
