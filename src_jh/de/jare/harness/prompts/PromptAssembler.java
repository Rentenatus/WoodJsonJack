/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.prompts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles the prompt list for one calculator call and enforces the hard budgets
 * (CONCEPT_Harness.md §4): fixed category order, per-category segment counts and a
 * total token deckel. Whoever overflows is dropped deterministically - the driver
 * enforces this, it is not negotiable. Also injects the short sticky-facts block
 * (project path, language, schema; max ~150 tokens) into every SYSTEM segment run.
 *
 * @author Janusch Rentenatus
 */
public class PromptAssembler {

    private final Map<PromptSegment.Category, Integer> budgets;
    private final int maxTokensTotal;
    private final String stickyFacts;

    public PromptAssembler(Map<PromptSegment.Category, Integer> budgets, int maxTokensTotal, String stickyFacts) {
        this.budgets = new EnumMap<>(budgets);
        this.maxTokensTotal = maxTokensTotal;
        this.stickyFacts = stickyFacts == null ? "" : stickyFacts;
    }

    public List<PromptSegment> assemble(List<PromptSegment> requested) {
        List<PromptSegment> ordered = new ArrayList<>(requested);
        ordered.sort(Comparator.comparingInt(s -> s.cat().ordinal()));

        Map<PromptSegment.Category, Integer> used = new EnumMap<>(PromptSegment.Category.class);
        List<PromptSegment> result = new ArrayList<>();
        int tokens = 0;
        for (PromptSegment s : ordered) {
            int budget = budgets.getOrDefault(s.cat(), 0);
            int count = used.getOrDefault(s.cat(), 0);
            int segTokens = estimate(s);
            if (count >= budget || tokens + segTokens > maxTokensTotal) {
                continue; // hard drop - deterministic
            }
            used.put(s.cat(), count + 1);
            tokens += segTokens;
            result.add(s);
        }
        // sticky facts ride along in the first SYSTEM segment (anti-forgetting, §4)
        if (!stickyFacts.isEmpty() && !result.isEmpty()
                && result.get(0).cat() == PromptSegment.Category.SYSTEM) {
            PromptSegment first = result.get(0);
            result.set(0, new PromptSegment(first.cat(), first.ref(),
                    first.text() + "\n" + stickyFacts, first.maxTokens()));
        }
        return result;
    }

    /** Rough token estimate: 4 chars per token - deterministic, no tokenizer needed. */
    public static int estimate(PromptSegment s) {
        return Math.max(1, (s.text() == null ? 0 : s.text().length()) / 4);
    }

    public static List<String> toTexts(List<PromptSegment> segments) {
        List<String> out = new ArrayList<>();
        for (PromptSegment s : segments) {
            out.add(s.cat() + ":" + s.ref() + "\n" + s.text());
        }
        return out;
    }
}
