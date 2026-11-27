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
import de.jare.harness.TaskData;

/**
 * The named deterministic calculator {@code "evaluate.aggregate"}: last child of the
 * EVALUATE slot's puzzle sequence. Takes the MINIMUM of the EVALUATE-kind call scores of
 * the CURRENT loop iteration only (calls from {@code ctx.getIterationCallBase()} on - scores
 * of a previous, restarted iteration must not leak into the min; the chained AI evaluators'
 * results are already recorded as CalcCalls), so one weak dimension
 * (completeness/correctness/readability) cannot be hidden by the others. No scores at all ->
 * the old deterministic default 80. The engine applies the result score via SetScore;
 * CHECK then gates on it (score >= 50).
 *
 * @author Janusch Rentenatus
 */
public class EvaluateAggregateCalculator implements HarnessCalculator {

    /** Default when no evaluator produced a score. */
    public static final int DEFAULT_SCORE = 80;

    private final Kind kind;

    public EvaluateAggregateCalculator(Kind kind) {
        this.kind = kind;
    }

    @Override
    public Kind kind() {
        return kind;
    }

    @Override
    public CalcResult calculate(CalcContext ctx) {
        Integer min = null;
        int count = 0;
        // only the EVALUATE calls of the CURRENT loop iteration (ctx.iterationCallBase) -
        // scores of a previous, restarted iteration must not leak into the min
        java.util.List<TaskData.CalcCall> calls = ctx.task().getCalls();
        int from = Math.max(0, Math.min(ctx.getIterationCallBase(), calls.size()));
        for (int i = from; i < calls.size(); i++) {
            TaskData.CalcCall c = calls.get(i);
            if ("EVALUATE".equalsIgnoreCase(c.getKind()) && c.getScore() != null) {
                min = min == null ? c.getScore() : Math.min(min, c.getScore());
                count++;
            }
        }
        int score = min == null ? DEFAULT_SCORE : min;
        return CalcResult.of(kind, "aggregated")
                .status(Status.DONE)
                .score(score)
                .put("evaluators", String.valueOf(count))
                .put("minScore", String.valueOf(score));
    }
}
