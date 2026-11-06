/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The facade context every calculator receives (CONCEPT_Harness.md §14): facades only,
 * never raw engine access. This skeleton implements the pieces the deterministic
 * calculators need today - prompts, tools, rag, skills, mindset, lineage and ui are
 * added with their concept sections.
 *
 * @author Janusch Rentenatus
 */
public class CalcContext {

    private final HarnessEngine engine;
    private final HarnessInstance instance;
    private final HarnessModel model;
    private final TaskNode task;
    private final Kind kind;
    private final String slotId;
    private final CalculatorNode node;
    private final Limits limits;
    private final List<String> currentPrompts;

    public CalcContext(HarnessEngine engine, HarnessInstance instance, HarnessModel model,
                       TaskNode task, Kind kind, String slotId, CalculatorNode node,
                       Limits limits, List<String> currentPrompts) {
        this.engine = engine;
        this.instance = instance;
        this.model = model;
        this.task = task;
        this.kind = kind;
        this.slotId = slotId;
        this.node = node;
        this.limits = limits;
        this.currentPrompts = currentPrompts == null ? Collections.emptyList() : currentPrompts;
    }

    /** ctx.task - the task node of the current call. */
    public TaskNode task() {
        return task;
    }

    public Kind kind() {
        return kind;
    }

    public String slotId() {
        return slotId;
    }

    public CalculatorNode calculatorNode() {
        return node;
    }

    public HarnessModel model() {
        return model;
    }

    public HarnessInstance instance() {
        return instance;
    }

    /** ctx.limits - the ABORT counters (§14). */
    public Limits limits() {
        return limits;
    }

    /** ctx.prompts.current() - the already assembled prompt list of this call. */
    public List<String> prompts() {
        return new ArrayList<>(currentPrompts);
    }

    /** ctx.ids - the persisted internal counters (§6). */
    public long nextTaskId() {
        return instance.nextTaskId();
    }

    public long nextAufrufId() {
        return instance.nextAufrufId();
    }

    /** ctx.metrics.logCall - local raw log entry + M x 3 aggregate (§6, §8). */
    public void logCall(CallLogEntry entry) {
        instance.logCall(entry);
    }

    /** ctx.metrics - the aggregated metrics facade (§14). */
    public de.jare.harness.metrics.MetricsStore metrics() {
        return instance.getMetrics();
    }

    /** ctx.ui.notify - info to the harness window (§14). */
    public void notify(String text) {
        engine.notify(task, kind, text);
    }
}
