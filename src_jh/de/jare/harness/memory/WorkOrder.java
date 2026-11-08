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
import de.jare.harness.TaskNode;

import java.util.ArrayList;
import java.util.List;

/**
 * The WorkOrder (Bearbeitungsauftrag) - the MEMORY of the current calculation
 * (docs/working/DESIGN_Quest_WorkOrder.md): created in memory when the UI AskAi action fires,
 * with the slot id (e.g. "askai.base") and at least the raw user prompt as a field, plus the
 * task tree and call log that grow during the run. The HarnessInstance stays the recipe.
 *
 * @author Janusch Rentenatus
 */
public class WorkOrder {

    private final String id;            // slot id, e.g. "askai.base"
    private final Kind questKind;
    private final String userPrompt;    // the raw user input of this run
    private final TaskNode root;        // task tree of this run
    private final List<CallLogEntry> callLog = new ArrayList<>();
    private Status status = Status.PENDING;
    private final long createdTs = System.currentTimeMillis();

    public WorkOrder(String id, Kind questKind, String userPrompt, TaskNode root) {
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

    public TaskNode getRoot() {
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
