/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.io.JsonNodeWriter;
import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.lang.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * One task of the harness - the PERSISTED PROJECTION (trace) of the run, not its runtime state
 * (concept CONCEPT_Harness.md §3 layering): the runtime truth lives in
 * {@code de.jare.harness.memory} (MemoryQuest/WorkOrder) and {@link HarnessInstance}; this
 * facade only receives the projected values. It is a FACADE with a back-reference to its
 * {@link EditNodeObject}: the tree structure (parent/children) lives in the EditNode world,
 * the payload (id, taskType, title, ...) lives as primitive member properties of the node, so
 * persistence runs through the JsonNode root of the EditTree and the JTree shows the very same
 * node instances. Every setter writes through to the node and fires the change event - the
 * attach is internal, there is no second mutation path.
 *
 * <p>Two kinds of payload, deliberately different semantics: the "current" cell
 * ({@code status}/{@code score}/{@code attempt}/{@code summaryTask}/{@code artifact}/
 * {@code promptSupplement}) is OVERWRITTEN on each change - progress-bar semantics, the UI
 * repaints it in place. The {@code calculatorLog[]} is APPEND-ONLY - the history of calculator
 * calls, never overwritten; it is the memory for HISTORY segments and the feedback loop.</p>
 *
 * @author Janusch Rentenatus
 */
public class TaskData {

    public static final String M_ID = "taskId";
    public static final String M_TYPE = "taskType";
    public static final String M_TITLE = "title";
    public static final String M_STATUS = "status";
    public static final String M_SCORE = "score";
    public static final String M_ATTEMPT = "attempt";
    public static final String M_SUMMARY = "summaryTask";
    public static final String M_SUPPLEMENT = "promptSupplement";
    public static final String M_ARTIFACT = "artifact";
    public static final String M_LOG = "calculatorLog";

    private final EditNodeObject node;
    private final TaskTree tree;
    private final List<CallLogEntry> calculatorLog = new ArrayList<>();
    private boolean muted;   // true while the constructor initializes - no events before register

    /** Standalone task on its own fresh tree (tests, ad-hoc runs). */
    public static TaskData create(long id, String taskType, String title) {
        return new TaskTree().newRoot(id, taskType, title);
    }

    /** Creates a new task on a fresh node (runtime creation). */
    TaskData(EditNodeObject node, TaskTree tree, long id, String taskType, String title) {
        this.node = node;
        this.tree = tree;
        muted = true;
        setMember(M_ID, Long.toString(id));
        setMember(M_TYPE, taskType == null ? "" : taskType);
        setMember(M_TITLE, title == null ? "" : title);
        setMember(M_STATUS, Status.PENDING.name());
        setMember(M_ATTEMPT, "0");
        setMember(M_SUMMARY, "false");
        setMember(M_SUPPLEMENT, "");
        syncLogMember();
        muted = false;
    }

    /** Wraps an existing node (load) - reads the persisted payload from the members. */
    TaskData(EditNodeObject node, TaskTree tree) {
        this.node = node;
        this.tree = tree;
        String logJson = getMember(M_LOG, "");
        if (!logJson.isEmpty()) {
            try {
                JsonNode arr = JsonParserService.parse(logJson, JsonDebugLevel.SIMPLE).getRoot();
                if (arr != null && arr.isArray()) {
                    for (JsonNode e : arr.asArray()) {
                        calculatorLog.add(CallLogEntry.fromJson(e));
                    }
                }
            } catch (Exception broken) {
                // a broken log member must not break the load - start with an empty log
            }
        }
    }

    /** The backing EditNode (the same instance the JTree shows). */
    public EditNodeObject getNode() {
        return node;
    }

    TaskTree tree() {
        return tree;
    }

    //
    // member access (payload = primitive member properties of the node)
    //

    private EditNodeProperty member(String name) {
        for (EditNode c : node.getChildren()) {
            if (name.equals(c.getName()) && c instanceof EditNodeProperty p
                    && !TaskTree.TASK_ARRAY.equals(name)) {
                return p;
            }
        }
        return null;
    }

    String getMember(String name, String def) {
        EditNodeProperty p = member(name);
        String v = p == null ? null : p.getValue();
        return v == null || v.isEmpty() ? def : v;
    }

    void setMember(String name, String value) {
        EditNodeProperty p = member(name);
        if (p == null) {
            p = node.createChild(name);
            tree.getEditTree().addChild(node, p);
        }
        p.setValue(value == null ? "" : value);
        if (!muted) {
            tree.fireChanged(node);
        }
    }

    /** The "tasks" array member holding the subtask objects (created on demand). */
    EditNodeProperty tasksArray(boolean create) {
        for (EditNode c : node.getChildren()) {
            if (TaskTree.TASK_ARRAY.equals(c.getName()) && c instanceof EditNodeProperty p) {
                return p;
            }
        }
        if (create) {
            EditNodeProperty p = node.createArrChild(TaskTree.TASK_ARRAY);
            tree.getEditTree().addChild(node, p);
            if (!muted) {
                tree.fireChanged(node);
            }
            return p;
        }
        return null;
    }

    //
    // the task API (same surface as the former TaskData)
    //

    public long getId() {
        try {
            return Long.parseLong(getMember(M_ID, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String getTaskType() {
        return getMember(M_TYPE, "");
    }

    public String getTitle() {
        return getMember(M_TITLE, "");
    }

    public Status getStatus() {
        return Status.valueOf(getMember(M_STATUS, Status.PENDING.name()));
    }

    public void setStatus(Status status) {
        setMember(M_STATUS, status.name());
    }

    public Integer getScore() {
        String v = getMember(M_SCORE, "");
        return v.isEmpty() ? null : Integer.valueOf(v);
    }

    public void setScore(int score) {
        setMember(M_SCORE, Integer.toString(score));
    }

    public int getAttempt() {
        try {
            return Integer.parseInt(getMember(M_ATTEMPT, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void incrementAttempt() {
        setMember(M_ATTEMPT, Integer.toString(getAttempt() + 1));
    }

    public boolean isSummaryTask() {
        return "true".equals(getMember(M_SUMMARY, "false"));
    }

    /** SPLIT: this task becomes a summarization task over its children. */
    public void rewriteAsSummary() {
        setMember(M_SUMMARY, "true");
    }

    public String getPromptSupplement() {
        return getMember(M_SUPPLEMENT, "");
    }

    /** CHECK: retry with an improved task statement. */
    public void appendPromptSupplement(String text) {
        String current = getPromptSupplement();
        setMember(M_SUPPLEMENT, current.isEmpty() ? text : current + "\n" + text);
    }

    public String getArtifact() {
        String v = getMember(M_ARTIFACT, "");
        return v.isEmpty() ? null : v;
    }

    /** CLOSE: finalize and write the artifact. */
    public void close(String artifact) {
        setMember(M_ARTIFACT, artifact == null ? "" : artifact);
        // CLOSE fires in the loop's finally (§15.6) - it finalizes the artifact but must
        // never resurrect a terminal failure status (ABORTED/FAILED stay)
        if (getStatus() != Status.ABORTED && getStatus() != Status.FAILED) {
            setStatus(Status.DONE);
        }
    }

    /** The parent task - the task object above the "tasks" array (null for the tree root). */
    public TaskData getParent() {
        if (node.getParent() == null) {
            return null; // the tree root object is the root task
        }
        EditNode p = node.getParent();
        if (p instanceof EditNodeProperty) {
            p = p.getParent();
        }
        return p instanceof EditNodeObject && TaskTree.isTaskObject(p) ? tree.dataOf(p) : null;
    }

    /** The subtasks - the task objects of the "tasks" array member. */
    public List<TaskData> getChildren() {
        List<TaskData> result = new ArrayList<>();
        EditNodeProperty arr = tasksArray(false);
        if (arr != null) {
            for (EditNode c : arr.getChildren()) {
                if (c instanceof EditNodeObject obj) {
                    TaskData data = tree.dataOf(obj);
                    if (data == null) {
                        data = tree.register(obj, new TaskData(obj, tree));
                    }
                    result.add(data);
                }
            }
        }
        return result;
    }

    /** SPLIT / COMPLETE: adds a subtask (reasoning subtask or split part). */
    public TaskData addSubtask(long childId, String taskType, String title) {
        return tree.addSubtask(this, childId, taskType, title);
    }

    public List<CallLogEntry> getCalculatorLog() {
        return calculatorLog;
    }

    public void logCall(CallLogEntry entry) {
        calculatorLog.add(entry);
        syncLogMember();
    }

    /** Serializes the in-memory log into the node's log member (persisted with the tree). */
    private void syncLogMember() {
        JsonNode arr = JsonNode.arrayNode();
        for (CallLogEntry e : calculatorLog) {
            arr.add(e.toJson());
        }
        try {
            setMember(M_LOG, JsonNodeWriter.writeToString(arr));
        } catch (Exception e) {
            throw new IllegalStateException("calculatorLog is not serializable: " + e.getMessage(), e);
        }
    }

    /** Depth in the task tree, root = 0. */
    public int depth() {
        int d = 0;
        TaskData n = getParent();
        while (n != null) {
            d++;
            n = n.getParent();
        }
        return d;
    }
}
