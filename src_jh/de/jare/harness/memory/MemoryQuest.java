/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.memory;

import de.jare.harness.TaskNode;
import de.jare.harness.model.Quest;

/**
 * The MemoryQuest - the MEMORY-side quest instance of one run
 * (docs/working/DESIGN_Quest_WorkOrder.md). Around the WorkOrder it carries the WorkCache
 * (intermediate results: RAG chunks, tool outputs, partials) and the WorkResponse (the live,
 * still-changing answer shown in the UI). It references the recipe-side {@link Quest}
 * (model.Quest, pure blueprint) but is never part of the recipe/lineage copies.
 *
 * Created in memory when the UI AskAi action fires; id = slot id, e.g. "askai.base".
 *
 * @author Janusch Rentenatus
 */
public class MemoryQuest {

    private final Quest recipe;         // the blueprint quest this run executes
    private final WorkOrder workOrder;  // memory of the current calculation
    private final WorkCache workCache = new WorkCache();
    private final WorkResponse workResponse = new WorkResponse();

    /**
     * Creates the memory quest AND its root TaskNode itself - the UI/host only passes the raw
     * user prompt and the task type, it knows nothing about TaskNode logic.
     *
     * @param idSupplier supplies the fresh task id (persisted counter of the instance)
     */
    public MemoryQuest(Quest recipe, String slotId, String userPrompt, String taskType,
                       java.util.function.LongSupplier idSupplier) {
        this.recipe = recipe;
        TaskNode root = new TaskNode(idSupplier.getAsLong(), taskType, userPrompt);
        this.workOrder = new WorkOrder(slotId, recipe == null ? null : recipe.getKind(), userPrompt, root);
    }

    /** The recipe-side quest (blueprint) this memory instance executes. */
    public Quest getRecipe() {
        return recipe;
    }

    /** Memory of the current calculation: id, userPrompt, task tree, call log, status. */
    public WorkOrder getWorkOrder() {
        return workOrder;
    }

    /** Intermediate results of the current run. */
    public WorkCache getWorkCache() {
        return workCache;
    }

    /** The live, changing answer of the current run - the UI attaches here. */
    public WorkResponse getWorkResponse() {
        return workResponse;
    }
}
