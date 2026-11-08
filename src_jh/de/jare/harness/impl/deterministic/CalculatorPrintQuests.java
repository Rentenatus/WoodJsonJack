/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.impl.deterministic;

import de.jare.harness.CalcContext;
import de.jare.harness.CalcResult;
import de.jare.harness.HarnessCalculator;
import de.jare.harness.Kind;
import de.jare.harness.TaskNode;

/**
 * Deterministic named calculator, registered as {@code "printig"}
 * (class name {@code CalculatorPrintQuests}): appends to the harness output the N quests
 * (subtasks) just produced, each with its freshly generated single prompt (the subtask title).
 *
 * @author Janusch Rentenatus
 */
public class CalculatorPrintQuests implements HarnessCalculator {

    private final Kind kind;

    public CalculatorPrintQuests(Kind kind) {
        this.kind = kind;
    }

    @Override
    public Kind kind() {
        return kind;
    }

    @Override
    public CalcResult calculate(CalcContext ctx) {
        StringBuilder out = new StringBuilder();
        var children = ctx.task().getChildren();
        out.append("Quests (").append(children.size()).append("):");
        int i = 1;
        for (TaskNode child : children) {
            out.append("\n  ").append(i++).append(". [").append(child.getTaskType()).append("] ")
                    .append(child.getTitle());
        }
        String text = out.toString();
        ctx.notify(text);   // lands in the harness window / host listeners
        return CalcResult.of(kind, "printed " + children.size() + " quests")
                .put("printedQuests", text)
                .status(ctx.task().getStatus());
    }
}
