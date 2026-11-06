/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import java.util.ArrayList;
import java.util.List;

/**
 * One node of the task tree - the single source of truth of a running harness
 * (CONCEPT_Harness.md §3, §14). State never lives in a prompt, only here:
 * id, taskType, title, status, score, attempt, children and the calculator log.
 *
 * @author Janusch Rentenatus
 */
public class TaskNode {

    private final long id;
    private String taskType;
    private String title;
    private Status status = Status.PENDING;
    private Integer score;
    private int attempt;
    private boolean summaryTask;            // SPLIT rewrote this task as a summarization task
    private String promptSupplement = "";  // CHECK retry supplement
    private String artifact;                // CLOSE writes the artifact
    private TaskNode parent;
    private final List<TaskNode> children = new ArrayList<>();
    private final List<CallLogEntry> calculatorLog = new ArrayList<>();

    public TaskNode(long id, String taskType, String title) {
        this.id = id;
        this.taskType = taskType;
        this.title = title;
    }

    public long getId() {
        return id;
    }

    public String getTaskType() {
        return taskType;
    }

    public String getTitle() {
        return title;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getAttempt() {
        return attempt;
    }

    public void incrementAttempt() {
        this.attempt++;
    }

    public boolean isSummaryTask() {
        return summaryTask;
    }

    /** SPLIT: this task becomes a summarization task over its children. */
    public void rewriteAsSummary() {
        this.summaryTask = true;
    }

    public String getPromptSupplement() {
        return promptSupplement;
    }

    /** CHECK: retry with an improved task statement. */
    public void appendPromptSupplement(String text) {
        this.promptSupplement = promptSupplement.isEmpty() ? text : promptSupplement + "\n" + text;
    }

    public String getArtifact() {
        return artifact;
    }

    /** CLOSE: finalize and write the artifact. */
    public void close(String artifact) {
        this.artifact = artifact;
        this.status = Status.DONE;
    }

    public TaskNode getParent() {
        return parent;
    }

    public List<TaskNode> getChildren() {
        return children;
    }

    /** SPLIT / COMPLETE: adds a subtask (reasoning subtask or split part). */
    public TaskNode addSubtask(long childId, String taskType, String title) {
        TaskNode child = new TaskNode(childId, taskType, title);
        child.parent = this;
        children.add(child);
        return child;
    }

    public List<CallLogEntry> getCalculatorLog() {
        return calculatorLog;
    }

    public void logCall(CallLogEntry entry) {
        calculatorLog.add(entry);
    }

    /** Depth in the task tree, root = 0. */
    public int depth() {
        int d = 0;
        TaskNode n = parent;
        while (n != null) {
            d++;
            n = n.getParent();
        }
        return d;
    }
}
