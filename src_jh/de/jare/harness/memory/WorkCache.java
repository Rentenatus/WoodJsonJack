/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.memory;

import de.jare.harness.CalcResult;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The WorkCache (BearbeitungsCache) - intermediate state of the current run, owned by the Quest: retrieved RAG chunks,
 * tool outputs, partial results. Never persisted as recipe data; lives and dies with the WorkOrder
 * (docs/working/DESIGN_Quest_WorkOrder.md).
 *
 * <p>
 * Also the RESULT CHANNEL between calculators of one puzzle sequence (CONCEPT_Harness.md §3 layering - the WorkCache is
 * runtime truth, so results are threaded through here): the engine stores every atomic calculator's {@link CalcResult}
 * under the calculator name and as the {@code lastResult}; a following calculator (e.g. askai.decompose after
 * scoring.countQuests) reads the predecessor's payload from here via the CalcContext.</p>
 *
 * @author Janusch Rentenatus
 */
public class WorkCache {

    private final Map<String, String> entries = new LinkedHashMap<>();
    private final Map<String, CalcResult> results = new LinkedHashMap<>();
    private CalcResult lastResult;
    private CalcResult previousResult;   // quick access: the result before lastResult

    public WorkCache put(String key, String value) {
        entries.put(key, value);
        return this;
    }

    public String get(String key) {
        return entries.get(key);
    }

    public boolean has(String key) {
        return entries.containsKey(key);
    }

    public Map<String, String> getEntries() {
        return entries;
    }

    /**
     * Stores a calculator result under its calculator name (result channel, runtime truth).
     *
     * @param calculatorName
     * @param result
     * @return
     */
    public WorkCache putResult(String calculatorName, CalcResult result) {
        if (calculatorName != null && result != null) {
            results.put(calculatorName, result);
        }
        return this;
    }

    /**
     * The result of the named calculator of the current puzzle sequence (null if none).
     *
     * @param calculatorName
     * @return
     */
    public CalcResult getResult(String calculatorName) {
        return results.get(calculatorName);
    }

    /**
     * The result of the most recently executed atomic calculator (quick access).
     *
     * @return
     */
    public CalcResult getLastResult() {
        return lastResult;
    }

    /**
     * The result of the atomic calculator executed just before the last one (quick access).
     *
     * @return
     */
    public CalcResult getPreviousResult() {
        return previousResult;
    }

    /**
     * Sets the new lastResult and shifts the old one to previousResult.
     *
     * @param lastResult
     * @return
     */
    public WorkCache setLastResult(CalcResult lastResult) {
        if (this.lastResult != lastResult) {
            this.previousResult = this.lastResult;
        }
        this.lastResult = lastResult;
        return this;
    }
}
