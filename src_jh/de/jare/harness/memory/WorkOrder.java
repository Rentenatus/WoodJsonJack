/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.memory;

import de.jare.harness.CallLogEntry;
import de.jare.harness.Kind;
import de.jare.harness.Status;
import de.jare.harness.TaskData;
import java.util.ArrayList;
import java.util.List;

/**
 * The WorkOrder (Bearbeitungsauftrag) - the MEMORY of the current calculation and part of the
 * RUNTIME TRUTH of a run (CONCEPT_Harness.md §3 layering, docs/working/DESIGN_Quest_WorkOrder.md):
 * created in memory when the UI AskAi action fires, with the slot id (e.g. "askai.base") and at
 * least the raw user prompt as a field, plus the call log and status that grow during the run.
 * The {@link TaskData} root referenced here is only the PERSISTED PROJECTION of this
 * state (structure + overwritable status cell + append-only call log + projected calculator
 * calls) - the engine logic runs on this runtime layer, the task tree receives the projection.
 * The HarnessInstance stays the recipe-side run container.
 *
 * @author Janusch Rentenatus
 */
public class WorkOrder {

    private final String id;            // slot id, e.g. "askai.base"
    private final Kind questKind;
    private final String userPrompt;    // the raw user input of this run
    private final TaskData root;        // root task of this run (the persisted projection tree)
    private final List<CallLogEntry> callLog = new ArrayList<>();
    private Status status = Status.PENDING;
    private final long createdTs = System.currentTimeMillis();

    public WorkOrder(String id, Kind questKind, String userPrompt, TaskData root) {
        this.id = id;
        this.questKind = questKind;
        this.userPrompt = userPrompt == null ? "" : userPrompt;
        this.root = root;
    }

    public String getId() {
        return id;
    }

    public Kind getQuestKind() {
        return questKind;
    }

    /** The raw user prompt of this run - always carried as its own field. */
    public String getUserPrompt() {
        return userPrompt;
    }

    /** The root task of this run's task tree (the persisted projection). */
    public TaskData getRoot() {
        return root;
    }

    public List<CallLogEntry> getCallLog() {
        return callLog;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public long getCreatedTs() {
        return createdTs;
    }
}
