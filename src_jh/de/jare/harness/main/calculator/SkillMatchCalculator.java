/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.main.calculator;

import de.jare.harness.CalcContext;
import de.jare.harness.CalcResult;
import de.jare.harness.HarnessCalculator;
import de.jare.harness.Kind;
import de.jare.harness.Status;
import de.jare.harness.rag.EmbeddingProvider;
import de.jare.harness.rag.RagStore;
import de.jare.harness.skills.Skill;
import de.jare.harness.skills.SkillRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The named calculator {@code "skill.match"} (registered in the main tooling factory), the
 * matching half of the SKILL quest (CONCEPT_Harness.md §12, §15.6): matches the user prompt
 * against all skill descriptions in the similarity DB (the RAG store of the chosen rag
 * model) that pass the optional name {@code filter}, with the acceptance threshold
 * {@code minSimilarity} on the cosine similarity. All hits land in the running memory
 * (WorkCache): {@code skills.matched}, {@code skills.matched.count},
 * {@code skill.match.<skillId>} (best similarity) and {@code skills.matched.detail}.
 *
 * Params (all optional): {@code "filter"} (case-insensitive substring on the skillId,
 * comma-separated = OR; empty = all skills), {@code "minSimilarity"} (cosine acceptance,
 * default = the RAG defaults), {@code "topK"} (default = the RAG defaults),
 * {@code "embedModel"} (the rag modelclass by name, e.g.
 * {@code manutic/nomic-embed-code:latest}; fallback purpose {@code "rag"}; its model name
 * keys the RAG store, so two rag models get two separate stores).
 *
 * @author Janusch Rentenatus
 */
public class SkillMatchCalculator implements HarnessCalculator {

    private final Kind kind;
    private final EmbeddingProvider embedder;
    private final String filter;
    private final Double minSimilarity;   // null -> rag defaults
    private final Integer topK;           // null -> rag defaults

    public SkillMatchCalculator(Kind kind, EmbeddingProvider embedder, String filter,
                                Double minSimilarity, Integer topK) {
        this.kind = kind;
        this.embedder = embedder;
        this.filter = filter;
        this.minSimilarity = minSimilarity;
        this.topK = topK;
    }

    @Override
    public Kind kind() {
        return kind;
    }

    public EmbeddingProvider getEmbedder() {
        return embedder;
    }

    public String getFilter() {
        return filter;
    }

    public Double getMinSimilarity() {
        return minSimilarity;
    }

    public Integer getTopK() {
        return topK;
    }

    @Override
    public CalcResult calculate(CalcContext ctx) {
        RagStore rag = ctx.rag(embedder);
        // make sure every registry skill is in THIS store (idempotent via contentHash dedup)
        SkillRegistry registry = ctx.skills();
        if (registry != null) {
            for (Skill s : registry.all()) {
                registry.importSkill(s, rag);
            }
        }
        int topK = this.topK != null ? this.topK : rag.getDefaults().topK();
        double minSim = minSimilarity != null ? minSimilarity : rag.getDefaults().minScore();
        String query = ctx.task().getTitle()
                + (ctx.task().getPromptSupplement().isEmpty() ? "" : "\n" + ctx.task().getPromptSupplement());

        // docType filter BEFORE top-k, then the optional name filter on the skillId
        List<String> nameFilter = new ArrayList<>();
        if (filter != null && !filter.isBlank()) {
            for (String f : filter.split(",")) {
                if (!f.isBlank()) {
                    nameFilter.add(f.trim().toLowerCase());
                }
            }
        }
        List<RagStore.Hit> hits = rag.retrieve(query, SkillRegistry.DOC_TYPE_SKILL, topK, minSim);
        Map<String, Double> bestBySkill = new LinkedHashMap<>();
        List<String> detail = new ArrayList<>();
        for (RagStore.Hit h : hits) {
            String skillId = h.chunk().getMeta().get("skillId");
            if (skillId == null) {
                continue;
            }
            if (!nameFilter.isEmpty()
                    && nameFilter.stream().noneMatch(f -> skillId.toLowerCase().contains(f))) {
                continue;
            }
            bestBySkill.merge(skillId, h.similarity(), Math::max);
            detail.add(skillId + "#" + h.chunk().getMeta().get("descIndex")
                    + " sim=" + String.format(java.util.Locale.ROOT, "%.3f", h.similarity()));
        }

        // all hits into the running memory (WorkCache)
        if (ctx.workCache() != null) {
            ctx.workCache().put("skills.matched", String.join(",", bestBySkill.keySet()));
            ctx.workCache().put("skills.matched.count", String.valueOf(bestBySkill.size()));
            ctx.workCache().put("skills.matched.detail", String.join(" | ", detail));
            ctx.workCache().put("skills.matched.model", embedder.modelName());
            for (Map.Entry<String, Double> e : bestBySkill.entrySet()) {
                ctx.workCache().put("skill.match." + e.getKey(),
                        String.format(java.util.Locale.ROOT, "%.3f", e.getValue()));
            }
        }
        return CalcResult.of(kind, "matched").status(Status.DONE)
                .put("count", String.valueOf(bestBySkill.size()))
                .put("matched", String.join(",", bestBySkill.keySet()))
                .put("detail", String.join(" | ", detail))
                .put("model", embedder.modelName());
    }
}
