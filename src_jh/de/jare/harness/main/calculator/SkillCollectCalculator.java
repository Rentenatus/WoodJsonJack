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
import de.jare.harness.impl.deterministic.DeterministicCalculators;

/**
 * The named calculator {@code "skill.collect"} (registered in the main tooling factory),
 * used by the SKILL quest's slot at the main loop's START (§12, §15.6): checks and collects
 * the current skills of the run's SkillRegistry (global {@code ~/.wood/skills/skill/} +
 * project-local {@code <projekt>/.wood/skills/skill/}, loaded by HarnessService) into the
 * WorkCache ({@code skills.count}, {@code skills.ids}, {@code skills.descriptions}) so the
 * following slots and prompts can reference them. The collection logic is shared with the
 * deterministic SKILL fallback ({@link DeterministicCalculators#collectSkills}).
 *
 * @author Janusch Rentenatus
 */
public class SkillCollectCalculator implements HarnessCalculator {

    private final Kind kind;

    public SkillCollectCalculator(Kind kind) {
        this.kind = kind;
    }

    @Override
    public Kind kind() {
        return kind;
    }

    @Override
    public CalcResult calculate(CalcContext ctx) {
        return DeterministicCalculators.collectSkills(ctx);
    }
}
