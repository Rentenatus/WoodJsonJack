/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.prompts;

/**
 * One segment of the prompt list (CONCEPT_Harness.md §4): a single prompt is never enough
 * for the small local models - every call gets an ordered list of segments with a fixed
 * category order SYSTEM → SKILL → HISTORY → DATA → AUFTRAG and hard per-category budgets.
 *
 * @author Janusch Rentenatus
 */
public record PromptSegment(Category cat, String ref, String text, int maxTokens) {

    /** Fixed order of the categories - enforced by the assembler, not negotiable. */
    public enum Category {
        SYSTEM, SKILL, HISTORY, DATA, AUFTRAG
    }

    public static PromptSegment of(Category cat, String ref, String text) {
        return new PromptSegment(cat, ref, text, 0);
    }
}
