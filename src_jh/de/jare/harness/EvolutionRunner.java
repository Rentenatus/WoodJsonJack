/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.metrics.MetricBucket;
import de.jare.harness.metrics.MetricsStore;
import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Quest;
import de.jare.harness.model.Slot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One evolution step (CONCEPT_Harness.md §7.2): at an evolvable quest, three alternative
 * slot implementations run in parallel over a measuring window (N sample tasks). After
 * the window, two are discarded as soon as one alternative beats the reliability
 * threshold significantly (avg score +15 AND fail rate halved vs. the base). The winner
 * becomes the new base slot in a child version of the lineage; the losers land in the
 * archive (their stats stay in the metrics). Candidates come from the fixed candidate
 * list (main source), rarely two random mutations, optionally ollama-proposed.
 *
 * @author Janusch Rentenatus
 */
public class EvolutionRunner {

    /** Reliability threshold (§7.2): winner must beat base by this score delta … */
    public static final double SCORE_DELTA = 15.0;
    /** … and at most half the base's fail rate. */
    public static final double FAIL_RATE_FACTOR = 0.5;

    public record CandidateResult(Slot slot, double avgScore, double failRate, long samples) {
    }

    public record Selection(CandidateResult winner, List<CandidateResult> losers, boolean swapped) {
    }

    private final HarnessModel model;
    private final CalculatorFactory factory;

    public EvolutionRunner(HarnessModel model, CalculatorFactory factory) {
        this.model = model;
        this.factory = factory;
    }

    /**
     * Runs the three candidates of an evolvable quest over the sample tasks and selects
     * the winner. Returns null when the quest is not evolvable or has no 3 candidates.
     */
    public Selection run(Kind kind, List<String[]> sampleTasks) {
        Quest quest = model.questFor(kind);
        if (quest == null || !quest.isEvolvable() || quest.getSlots().size() != 3) {
            return null;
        }
        List<CandidateResult> results = new ArrayList<>();
        for (Slot slot : quest.getSlots()) {
            results.add(measure(kind, slot, sampleTasks));
        }
        CandidateResult baseResult = results.stream()
                .filter(r -> r.slot().isBase()).findFirst().orElse(results.get(0));
        CandidateResult winner = baseResult;
        for (CandidateResult r : results) {
            if (r.slot().isBase()) {
                continue;
            }
            boolean scoreOk = r.avgScore() >= baseResult.avgScore() + SCORE_DELTA;
            boolean failOk = r.failRate() <= baseResult.failRate() * FAIL_RATE_FACTOR + 1e-9;
            if (scoreOk && failOk && r.avgScore() > winner.avgScore()) {
                winner = r;
            }
        }
        List<CandidateResult> losers = new ArrayList<>(results);
        losers.remove(winner);
        return new Selection(winner, losers, !winner.slot().isBase());
    }

    /** Measures one candidate over the sample window, each sample as a fresh run. */
    private CandidateResult measure(Kind kind, Slot slot, List<String[]> sampleTasks) {
        HarnessModel measurementModel = model.copy();
        Quest mq = measurementModel.questFor(kind);
        mq.getSlots().clear();
        mq.addSlot(slot);

        MetricsStore aggregate = new MetricsStore();
        for (String[] spec : sampleTasks) {
            HarnessInstance single = new HarnessInstance(model.getName(), model.getVersion());
            single.setRoot(new TaskNode(single.nextTaskId(), spec[0], spec[1]));
            new HarnessEngine(measurementModel, single, factory).run();
            for (CallLogEntry entry : single.getCallLog()) {
                aggregate.recordCall(entry);
            }
        }
        Map<MetricsStore.Category, MetricBucket> stats = aggregate.stats(slot.getId(), 1);
        MetricBucket score = stats.get(MetricsStore.Category.SCORE);
        MetricBucket fail = stats.get(MetricsStore.Category.FAIL_RATE);
        double avg = score == null ? 0 : score.getAvg();
        double failRate = fail == null || fail.getCount() == 0 ? 0 : fail.getAvg();
        return new CandidateResult(slot, avg, failRate, sampleTasks.size());
    }

    /**
     * Commits the selection into the lineage: the winner becomes the base slot of a child
     * version; the losers stay archived (their stats remain in the metrics).
     */
    public HarnessLineage.Version commit(HarnessLineage lineage, Kind kind, Selection selection) {
        Slot winnerSlot = selection.winner().slot();
        Slot winnerBase = new Slot(winnerSlot.isBase() ? winnerSlot.getId()
                : winnerSlot.getId().replaceAll("\\.m\\d+$", ".base"))
                .calculator(winnerSlot.getCalculator());
        return lineage.requestEvolution(kind, List.of(
                winnerBase,
                new Slot("loser.1").mutation("archived")
                        .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC)),
                new Slot("loser.2").mutation("archived")
                        .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC))),
                "evolution winner " + winnerSlot.getId() + " avg=" + selection.winner().avgScore());
    }
}
