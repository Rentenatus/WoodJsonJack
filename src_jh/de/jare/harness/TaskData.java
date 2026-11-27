/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.io.JsonNodes;
import de.jare.jsoncasted.lang.JsonNode;
import de.jare.tree.control.Orator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One task of the harness - a plain data object (decided: the EditTree coupling is removed, the
 * task tree is pure data and JSON is its persistence shape directly, CONCEPT_Harness.md §3).
 * The runtime truth of a run lives in {@code de.jare.harness.memory} (MemoryQuest/WorkOrder);
 * this object is the persisted PROJECTION (trace) of the run: structure (children), the
 * overwritable "current" cell (status/score/attempt/summaryTask/artifact/promptSupplement/
 * prompt - the model prompt text, defaults to the title; a reasoning subtask keeps the ORIGINAL
 * prompt here and only its label "reasoning about …" in the title),
 * the append-only {@code calculatorLog[]} and the projected calculator steps {@code calls[]}
 * ({@link CalcCall} - input prompts, payload, loop action = the data movement of the run).
 *
 * <p>Mutations never happen directly (CONCEPT_Harness.md §18): every change is a
 * {@link TaskCommand} (see {@link TaskCommands}) executed through the command dispatcher
 * ({@code de.jare.harness.memory.CommandHistory}), which fires the change listeners exactly
 * once per command - the host repaints its view from there; subtask events bubble up to
 * the root, so one listener on the root sees the whole tree. The mutators below are
 * package-private on purpose: only commands (same package) can call them. Calculators see
 * the read-only {@link TaskView} facade via {@code ctx.task()}.</p>
 *
 * @author Janusch Rentenatus
 */
public class TaskData implements TaskView {

    public static final String M_ID = "taskId";
    public static final String M_TYPE = "taskType";
    public static final String M_TITLE = "title";
    public static final String M_PROMPT = "prompt";   // the prompt text - defaults to the title, but a reasoning subtask keeps the ORIGINAL prompt here and only its label in the title
    public static final String M_STATUS = "status";
    public static final String M_SCORE = "score";
    public static final String M_ATTEMPT = "attempt";
    public static final String M_SUMMARY = "summaryTask";
    public static final String M_SUPPLEMENT = "promptSupplement";
    public static final String M_ARTIFACT = "artifact";
    public static final String M_LOG = "calculatorLog";
    public static final String M_CALLS = "calls";
    public static final String M_TASKS = "tasks";

    /**
     * The projected step of one executed atomic calculator (§3 data movement): the input
     * prompts, the {@link CalcResult} (status/decision/score/message, the payload map) and the
     * {@link LoopAction} (type/flow plus its detail, e.g. the split prompts). Serialized as one
     * "call" object of the task's "calls" array.
     */
    public static class CalcCall {

        private final String calc;
        private final String kind;
        private final String slot;
        private final String status;
        private final String decision;
        private final Integer score;
        private final String message;
        private final Map<String, String> payload = new LinkedHashMap<>();
        private final String loopActionType;
        private final String loopActionFlow;
        private final JsonNode loopActionDetail;   // extra carried data, may be null
        private final List<String> input = new ArrayList<>();

        private CalcCall(String calc, String kind, String slot, Status status, String decision,
                         Integer score, String message, Map<String, String> payload,
                         LoopAction loopAction, List<String> input) {
            this.calc = calc == null ? "" : calc;
            this.kind = kind == null ? "" : kind;
            this.slot = slot == null ? "" : slot;
            this.status = status == null ? "" : status.name();
            this.decision = decision == null ? "" : decision;
            this.score = score;
            this.message = message == null ? "" : message;
            if (payload != null) {
                this.payload.putAll(payload);
            }
            this.loopActionType = loopAction == null ? "" : loopAction.type();
            this.loopActionFlow = loopAction == null ? "" : loopAction.flow().name();
            this.loopActionDetail = loopAction == null ? null : loopAction.detail();
            if (input != null) {
                this.input.addAll(input);
            }
        }

        static CalcCall of(String calc, Kind kind, String slot, CalcResult res, List<String> input) {
            return new CalcCall(calc, kind == null ? "" : kind.name(), slot,
                    res.getStatus(), res.getDecision(), res.getScore(), res.getMessage(),
                    res.getPayload(), res.getLoopAction(), input);
        }

        public String getCalc() { return calc; }
        public String getKind() { return kind; }
        public String getSlot() { return slot; }
        public String getStatus() { return status; }
        public String getDecision() { return decision; }
        public Integer getScore() { return score; }
        public String getMessage() { return message; }
        public Map<String, String> getPayload() { return payload; }
        public String getLoopActionType() { return loopActionType; }
        public String getLoopActionFlow() { return loopActionFlow; }
        public List<String> getInput() { return input; }

        JsonNode toJson() {
            JsonNode n = JsonNode.objectNode();
            n.put("calc", JsonNode.stringNode(calc));
            n.put("kind", JsonNode.stringNode(kind));
            n.put("slot", JsonNode.stringNode(slot));
            n.put("status", JsonNode.stringNode(status));
            n.put("decision", JsonNode.stringNode(decision));
            if (score != null) {
                n.put("score", JsonNode.longNode(score));
            }
            if (!message.isEmpty()) {
                n.put("message", JsonNode.stringNode(message));
            }
            JsonNode la = JsonNode.objectNode();
            la.put("type", JsonNode.stringNode(loopActionType));
            la.put("flow", JsonNode.stringNode(loopActionFlow));
            if (loopActionDetail != null && loopActionDetail.isObject()) {
                for (var e : loopActionDetail.asObjectValues().entrySet()) {
                    la.put(e.getKey(), e.getValue());
                }
            }
            n.put("loopAction", la);
            JsonNode p = JsonNode.objectNode();
            for (var e : payload.entrySet()) {
                p.put(e.getKey(), JsonNode.stringNode(abbrev(e.getValue(), 500)));
            }
            n.put("payload", p);
            JsonNode in = JsonNode.arrayNode();
            for (String s : input) {
                // persisted projection only: full prompts (up to the token budget each) would
                // blow up the instance file - and the save - on long runs (OOM in the writer)
                in.add(JsonNode.stringNode(abbrev(s, 500)));
            }
            n.put("input", in);
            return n;
        }

    /** Truncate for persistence/debug display; null-safe. */
    private static String abbrev(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…(" + s.length() + " chars)";
    }

        static CalcCall fromJson(JsonNode n) {
            Map<String, String> payload = new LinkedHashMap<>();
            JsonNode pn = JsonNodes.child(n, "payload");
            if (pn != null && pn.isObject()) {
                for (var e : pn.asObjectValues().entrySet()) {
                    payload.put(e.getKey(), e.getValue() == null || e.getValue().isNull() ? "" : e.getValue().asText());
                }
            }
            JsonNode lan = JsonNodes.child(n, "loopAction");
            List<String> input = new ArrayList<>();
            JsonNode inn = JsonNodes.child(n, "input");
            if (inn != null && inn.isArray()) {
                for (JsonNode s : inn.asArray()) {
                    input.add(s == null || s.isNull() ? "" : s.asText());
                }
            }
            String laType = JsonNodes.text(lan, "type", "");
            String laFlow = JsonNodes.text(lan, "flow", "");
            JsonNode detailNode = null;
            if (lan != null && lan.isObject()) {
                JsonNode d = JsonNode.objectNode();
                for (var e : lan.asObjectValues().entrySet()) {
                    if (!"type".equals(e.getKey()) && !"flow".equals(e.getKey())) {
                        d.put(e.getKey(), e.getValue());
                    }
                }
                if (d.asObjectValues() != null && !d.asObjectValues().isEmpty()) {
                    detailNode = d;
                }
            }
            final JsonNode detail = detailNode;
            return new CalcCall(JsonNodes.text(n, "calc", ""), JsonNodes.text(n, "kind", ""),
                    JsonNodes.text(n, "slot", ""),
                    Status.valueOf(JsonNodes.text(n, "status", "PENDING")),
                    JsonNodes.text(n, "decision", ""),
                    JsonNodes.child(n, "score") == null ? null : JsonNodes.child(n, "score").asLong().intValue(),
                    JsonNodes.text(n, "message", ""), payload,
                    new LoopAction() {
                        @Override public String type() { return laType; }
                        @Override public Flow flow() { return Flow.valueOf(laFlow.isEmpty() ? "CONTINUE" : laFlow); }
                        @Override public JsonNode detail() { return detail; }
                    }, input);
        }
    }

    private final long id;
    private String taskType;
    private String title;
    private String prompt;                 // prompt text for the models; defaults to title
    private Status status = Status.PENDING;
    private Integer score;
    private int attempt;
    private boolean summaryTask;
    private String promptSupplement = "";
    private String artifact;
    private TaskData parent;
    private final List<TaskData> children = new ArrayList<>();
    private final List<CallLogEntry> calculatorLog = new ArrayList<>();
    private final List<CalcCall> calls = new ArrayList<>();
    // weak orator (jsonCasted Orator, §18.6): subscribers hold their listener in a field;
    // the bubble forwarder below is such a field so it is never GC'd while the parent lives
    private final Orator<Consumer<TaskData>> changeListeners = new Orator<>();
    private final Consumer<TaskData> childEventForwarder = this::notify;

    public TaskData(long id, String taskType, String title) {
        this.id = id;
        this.taskType = taskType == null ? "" : taskType;
        this.title = title == null ? "" : title;
        this.prompt = this.title;   // default: title IS the prompt
    }

    /** Standalone task (tests, ad-hoc runs). */
    public static TaskData create(long id, String taskType, String title) {
        return new TaskData(id, taskType, title);
    }

    //
    // change events (bubble up: a subtask forwards its events to its parent)
    //

    /** Orator registration (§18.6): held weakly - keep a field reference on the subscriber side. */
    public void addChangeListener(Consumer<TaskData> listener) {
        changeListeners.addListener(listener);
    }

    /** Package-private: fired exactly once per executed {@link TaskCommand} (§18). */
    void fire() {
        notify(this);
    }

    /** Fires the listeners with the originating task (bubble-up keeps the origin). */
    private void notify(TaskData origin) {
        changeListeners.say((level, l) -> l.accept(origin));
    }

    //
    // the task API
    //

    public long getId() { return id; }

    public String getTaskType() { return taskType; }

    public String getTitle() { return title; }

    /** The prompt text sent to the models - NOT necessarily the title (a reasoning subtask's title is just a label). */
    public String getPrompt() { return prompt == null ? title : prompt; }

    public Status getStatus() { return status; }

    void setStatus(Status status) {
        this.status = status;
    }

    public Integer getScore() { return score; }

    void setScore(int score) {
        this.score = score;
    }

    void clearScore() {
        this.score = null;
    }

    public int getAttempt() { return attempt; }

    void incrementAttempt() {
        attempt++;
    }

    public boolean isSummaryTask() { return summaryTask; }

    /** SPLIT: this task becomes a summarization task over its children. */
    void rewriteAsSummary() {
        summaryTask = true;
    }

    public String getPromptSupplement() { return promptSupplement; }

    /** CHECK: retry with an improved task statement. */
    void appendPromptSupplement(String text) {
        promptSupplement = promptSupplement.isEmpty() ? (text == null ? "" : text)
                : promptSupplement + "\n" + text;
    }

    public String getArtifact() { return artifact; }

    /** CLOSE: finalize and write the artifact. */
    void close(String artifact) {
        this.artifact = artifact == null ? "" : artifact;
        // CLOSE fires in the loop's finally (§15.6) - it finalizes the artifact but must
        // never resurrect a terminal failure status (ABORTED/FAILED stay)
        if (status != Status.ABORTED && status != Status.FAILED) {
            status = Status.DONE;
        }
    }

    /** The parent task (null for the root). */
    public TaskData getParent() { return parent; }

    /** The subtasks. */
    public List<TaskData> getChildren() { return children; }

    /** SPLIT / COMPLETE: adds a subtask (reasoning subtask or split part). */
    TaskData addSubtask(long childId, String taskType, String title) {
        return addSubtask(childId, taskType, title, null);
    }

    /** @param prompt the prompt text of the child; null = title is the prompt */
    TaskData addSubtask(long childId, String taskType, String title, String prompt) {
        TaskData child = new TaskData(childId, taskType, title);
        if (prompt != null && !prompt.isBlank()) {
            child.prompt = prompt;
        }
        child.parent = this;
        children.add(child);
        child.addChangeListener(childEventForwarder);   // bubble: the root's listeners see every descendant, with origin
        return child;
    }

    public List<CallLogEntry> getCalculatorLog() { return calculatorLog; }

    void logCall(CallLogEntry entry) {
        calculatorLog.add(entry);
    }

    /**
     * Projects one executed atomic calculator call (§3 data movement): appends a {@link CalcCall}
     * with the input prompts, the {@link CalcResult} payload and the {@link LoopAction} - the
     * data the calculator read and wrote, visible in the task tree and persisted with it.
     */
    void appendCalcCall(String calcName, Kind kind, String slotId, CalcResult res, List<String> input) {
        calls.add(CalcCall.of(calcName, kind, slotId, res, input));
    }

    /** The projected calculator steps of this task (the "calls" array). */
    public List<CalcCall> getCalls() { return calls; }

    /** Depth in the task tree, root = 0. */
    public int depth() {
        int d = 0;
        TaskData n = parent;
        while (n != null) {
            d++;
            n = n.parent;
        }
        return d;
    }

    //
    // JSON (the persistence shape - no EditNode detour)
    //

    public JsonNode toJson() {
        JsonNode n = JsonNode.objectNode();
        n.put(M_ID, JsonNode.longNode(id));
        n.put(M_TYPE, JsonNode.stringNode(taskType));
        n.put(M_TITLE, JsonNode.stringNode(title));
        n.put(M_PROMPT, JsonNode.stringNode(getPrompt()));
        n.put(M_STATUS, JsonNode.stringNode(status.name()));
        if (score != null) {
            n.put(M_SCORE, JsonNode.longNode(score));
        }
        n.put(M_ATTEMPT, JsonNode.longNode(attempt));
        n.put(M_SUMMARY, JsonNode.booleanNode(summaryTask));
        n.put(M_SUPPLEMENT, JsonNode.stringNode(promptSupplement));
        if (artifact != null) {
            n.put(M_ARTIFACT, JsonNode.stringNode(artifact));
        }
        JsonNode tasks = JsonNode.arrayNode();
        for (TaskData c : children) {
            tasks.add(c.toJson());
        }
        n.put(M_TASKS, tasks);
        JsonNode log = JsonNode.arrayNode();
        for (CallLogEntry e : calculatorLog) {
            log.add(e.toJson());
        }
        n.put(M_LOG, log);
        JsonNode callsArr = JsonNode.arrayNode();
        for (CalcCall c : calls) {
            callsArr.add(c.toJson());
        }
        n.put(M_CALLS, callsArr);
        return n;
    }

    public static TaskData fromJson(JsonNode n) {
        TaskData t = new TaskData(JsonNodes.longOf(n, M_ID, 0),
                JsonNodes.text(n, M_TYPE, ""), JsonNodes.text(n, M_TITLE, ""));
        t.prompt = JsonNodes.text(n, M_PROMPT, t.title);   // old files without prompt fall back to the title
        t.status = Status.valueOf(JsonNodes.text(n, M_STATUS, "PENDING"));
        t.score = JsonNodes.child(n, M_SCORE) == null ? null : JsonNodes.child(n, M_SCORE).asLong().intValue();
        t.attempt = (int) JsonNodes.longOf(n, M_ATTEMPT, 0);
        JsonNode sum = JsonNodes.child(n, M_SUMMARY);
        t.summaryTask = sum != null && "true".equals(sum.asText() == null ? String.valueOf(sum.asBoolean()) : sum.asText());
        t.promptSupplement = JsonNodes.text(n, M_SUPPLEMENT, "");
        t.artifact = JsonNodes.child(n, M_ARTIFACT) == null ? null : JsonNodes.text(n, M_ARTIFACT, null);
        JsonNode tasks = JsonNodes.child(n, M_TASKS);
        if (tasks != null && tasks.isArray()) {
            for (JsonNode c : tasks.asArray()) {
                TaskData child = fromJson(c);
                child.parent = t;
                t.children.add(child);
            }
        }
        JsonNode log = JsonNodes.child(n, M_LOG);
        if (log != null && log.isArray()) {
            for (JsonNode e : log.asArray()) {
                t.calculatorLog.add(CallLogEntry.fromJson(e));
            }
        }
        JsonNode callsArr = JsonNodes.child(n, M_CALLS);
        if (callsArr != null && callsArr.isArray()) {
            for (JsonNode c : callsArr.asArray()) {
                t.calls.add(CalcCall.fromJson(c));
            }
        }
        return t;
    }
}
