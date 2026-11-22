/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.io.HarnessInstanceCodec;
import de.jare.harness.main.actions.LoopSplit;
import de.jare.jsoncasted.lang.JsonNode;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * The task as plain data (CONCEPT_Harness.md §3, decided: no EditTree coupling): TaskData is a
 * POJO whose JSON form is the persistence shape directly - payload fields, subtasks in
 * "tasks", the append-only calculator log, and the projected calculator steps in "calls".
 * Mutations fire change listeners; subtask events bubble up to the root.
 *
 * @author Janusch Rentenatus
 */
public class TaskDataNGTest {

    /**
     * A root task is a plain data object: id/type/title/status, no parent, depth 0.
     */
    @Test
    public void testRootTaskIsPlainData() {
        TaskData root = TaskData.create(1, "SPLIT", "root prompt");
        assertEquals(root.getId(), 1);
        assertEquals(root.getTaskType(), "SPLIT");
        assertEquals(root.getTitle(), "root prompt");
        assertEquals(root.getStatus(), Status.PENDING);
        assertNull(root.getParent(), "the root task has no parent");
        assertEquals(root.depth(), 0);
    }

    /**
     * §18: mutations never happen directly - every change is a {@link TaskCommand} through the
     * {@link CommandHistory}, which fires exactly ONE change event per command; subtask events
     * bubble up so a listener on the root sees every descendant.
     */
    @Test
    public void testSubtasksAndChangeEventsBubbleUp() {
        TaskData root = TaskData.create(1, "SPLIT", "root");
        de.jare.harness.memory.CommandHistory history = new de.jare.harness.memory.CommandHistory();
        List<TaskData> events = new ArrayList<>();
        java.util.function.Consumer<TaskData> eventsListener = events::add; // §18.6: field/local held, orator is weak
        root.addChangeListener(eventsListener);

        TaskCommands.AddSubtask add = new TaskCommands.AddSubtask(2, "WORK", "part one");
        history.apply(add, root, "main", "split", "EXECUTE");
        TaskData child = add.created();
        assertEquals(root.getChildren().size(), 1);
        assertSame(root.getChildren().get(0), child);
        assertSame(child.getParent(), root);
        assertEquals(child.depth(), 1);
        assertEquals(events.size(), 1, "exactly one event for the AddSubtask command");
        assertEquals(history.size(), 1, "the command is recorded in the history");
        assertEquals(history.entries().get(0).loopItemId, "split", "origin triple is recorded (§18.5)");
        assertTrue(history.entries().get(0).invertible, "commands start invertible (§18.5)");

        events.clear();
        history.apply(new TaskCommands.SetStatus(Status.RUNNING), child, null, null, null);
        history.apply(new TaskCommands.SetScore(7), child, null, null, null);
        history.apply(new TaskCommands.IncrementAttempt(), child, null, null, null);
        assertEquals(child.getStatus(), Status.RUNNING);
        assertEquals(child.getScore(), Integer.valueOf(7));
        assertEquals(child.getAttempt(), 1);
        assertEquals(events.size(), 3, "one event per command, and the child's events bubbled to the root listener");
        assertSame(events.get(0), child, "the event carries the changed task");
    }

    /**
     * §18 orator: the CommandHistory fires its listeners with each new entry right after
     * recording (the UI subscribes here - the controller never holds a UI reference).
     */
    @Test
    public void testCommandHistoryFiresListenersPerEntry() {
        TaskData root = TaskData.create(1, "WORK", "root");
        de.jare.harness.memory.CommandHistory history = new de.jare.harness.memory.CommandHistory();
        List<de.jare.harness.memory.CommandHistory.Entry> seen = new ArrayList<>();
        java.util.function.Consumer<de.jare.harness.memory.CommandHistory.Entry> seenListener = seen::add; // §18.6 held
        history.addListener(seenListener);

        history.apply(new TaskCommands.SetStatus(Status.RUNNING), root, "main", "work", "EXECUTE");
        history.apply(new TaskCommands.SetScore(42), root, "main", "evaluate", "EXECUTE");

        assertEquals(seen.size(), 2, "one listener call per applied command");
        assertEquals(seen.get(0).seq, 1);
        assertEquals(seen.get(1).description, "setScore(42)");
        assertEquals(seen.get(0).loopItemId, "work", "origin triple is on the entry");
    }

    /**
     * §18.2: the composite SplitInto is ONE history entry with ONE change event, and the
     * created children come back from the command.
     */
    @Test
    public void testSplitIntoCompositeIsOneEntryOneEvent() {
        TaskData root = TaskData.create(1, "SPLIT", "2*4 und 7*8");
        de.jare.harness.memory.CommandHistory history = new de.jare.harness.memory.CommandHistory();
        List<TaskData> events = new ArrayList<>();
        java.util.function.Consumer<TaskData> eventsListener = events::add; // §18.6: field/local held, orator is weak
        root.addChangeListener(eventsListener);

        long[] ids = {10};
        TaskCommands.SplitInto split = new TaskCommands.SplitInto("sub",
                List.of("rechne 2*4", "rechne 7*8"), "SPLIT", () -> ids[0]++);
        history.apply(split, root, "main", "split", "EXECUTE");

        assertTrue(root.isSummaryTask(), "rewriteAsSummary was applied");
        assertEquals(root.getChildren().size(), 2, "N subtasks created");
        assertEquals(split.children().size(), 2, "the command exposes the created children");
        assertEquals(history.size(), 1, "the whole composite is ONE history entry");
        assertEquals(events.size(), 1, "the whole composite fired ONE change event");
        assertEquals(history.entries().get(0).description, "splitInto(loop=\"sub\", parts=2)");
    }

    /**
     * The data-movement projection (§3): one atomic calculator call appends a CalcCall with the
     * input prompts, the CalcResult (payload) and the LoopAction (type/flow + carried detail).
     */
    @Test
    public void testAppendCalcCallProjectsResultLoopActionAndInput() {
        TaskData root = TaskData.create(1, "SPLIT", "2*4 und 7*8");
        List<TaskData> events = new ArrayList<>();
        java.util.function.Consumer<TaskData> eventsListener = events::add; // §18.6: field/local held, orator is weak
        root.addChangeListener(eventsListener);

        CalcResult res = CalcResult.of(Kind.SPLIT, "split").status(Status.DONE).score(100)
                .put("quests", "rechne 2*4 | rechne 7*8")
                .loopAction(new LoopSplit(List.of("rechne 2*4", "rechne 7*8")));
        new de.jare.harness.memory.CommandHistory().apply(
                new TaskCommands.AppendCalcCall("askai.decompose", Kind.SPLIT, "askai.base", res,
                        List.of("AUFTRAG:inline:task\n2*4 und 7*8")), root, "main", "split", "EXECUTE");

        assertEquals(root.getCalls().size(), 1, "one call per calculator step");
        TaskData.CalcCall call = root.getCalls().get(0);
        assertEquals(call.getCalc(), "askai.decompose");
        assertEquals(call.getKind(), "SPLIT");
        assertEquals(call.getSlot(), "askai.base");
        assertEquals(call.getStatus(), "DONE");
        assertEquals(call.getDecision(), "split");
        assertEquals(call.getScore(), Integer.valueOf(100));
        assertEquals(call.getPayload().get("quests"), "rechne 2*4 | rechne 7*8");
        assertEquals(call.getLoopActionType(), "split");
        assertEquals(call.getLoopActionFlow(), "SPLIT");
        assertEquals(call.getInput().size(), 1, "the input side of the data movement is projected");
        assertFalse(events.isEmpty(), "the projection fired a change event");
    }

    /**
     * The codec roundtrip runs through the plain JSON shape: save → load restores fields,
     * children, status, score, the calculator log AND the projected calls.
     */
    @Test
    public void testCodecRoundtripThroughPlainJsonShape() {
        HarnessInstance instance = new HarnessInstance("m", "1");
        TaskData root = instance.newRootTask(instance.nextTaskId(), "AskAi", "do research AND code");
        TaskData child = root.addSubtask(instance.nextTaskId(), "WORK", "research part");
        child.setStatus(Status.DONE);
        child.setScore(9);
        child.logCall(new CallLogEntry(1, child.getId(), "WORK", Kind.WORK, "work.m1", "m",
                1, 12, 10, 20, 9, Status.DONE, "ok", null, null));
        root.appendCalcCall("skill.match", Kind.SKILL, "skill.base",
                CalcResult.of(Kind.SKILL, "matched").put("skills.matched", "jsoncasted-ecosystem"),
                List.of("prompt"));
        root.close("final answer");

        JsonNode json = HarnessInstanceCodec.toJson(instance);
        HarnessInstance loaded = HarnessInstanceCodec.fromJson(json);

        TaskData loadedRoot = loaded.getRoot();
        assertNotNull(loadedRoot);
        assertEquals(loadedRoot.getTitle(), "do research AND code");
        assertEquals(loadedRoot.getStatus(), Status.DONE, "close() set DONE and it persisted");
        assertEquals(loadedRoot.getArtifact(), "final answer");
        assertEquals(loadedRoot.getChildren().size(), 1);
        TaskData loadedChild = loadedRoot.getChildren().get(0);
        assertEquals(loadedChild.getStatus(), Status.DONE);
        assertEquals(loadedChild.getScore(), Integer.valueOf(9));
        assertEquals(loadedChild.getCalculatorLog().size(), 1, "the log round-trips");
        assertEquals(loadedChild.getCalculatorLog().get(0).getScore(), Integer.valueOf(9));
        assertSame(loadedChild.getParent(), loadedRoot);
        assertEquals(loadedRoot.getCalls().size(), 1, "the projected calls persist");
        assertEquals(loadedRoot.getCalls().get(0).getCalc(), "skill.match");
        assertEquals(loadedRoot.getCalls().get(0).getPayload().get("skills.matched"), "jsoncasted-ecosystem");
        assertEquals(loadedRoot.getCalls().get(0).getLoopActionType(), "continue");
    }

    /**
     * Legacy files (old "id"/"children" shape, calculatorLog as array OR as string member)
     * still load: the codec normalizes them into the current shape before the parse.
     */
    @Test
    public void testLegacyShapeStillLoads() throws Exception {
        JsonNode legacyChild = JsonNode.objectNode();
        legacyChild.put("id", JsonNode.longNode(2));
        legacyChild.put("taskType", JsonNode.stringNode("WORK"));
        legacyChild.put("title", JsonNode.stringNode("old child"));
        legacyChild.put("status", JsonNode.stringNode("DONE"));
        JsonNode legacyLog = JsonNode.arrayNode();
        legacyLog.add(new CallLogEntry(5, 2, "WORK", Kind.WORK, "work.m1", "m",
                1, 3, 4, 5, 8, Status.DONE, "ok", null, null).toJson());
        legacyChild.put("calculatorLog", legacyLog);
        legacyChild.put("children", JsonNode.arrayNode());
        JsonNode legacyRoot = JsonNode.objectNode();
        legacyRoot.put("id", JsonNode.longNode(1));
        legacyRoot.put("taskType", JsonNode.stringNode("AskAi"));
        legacyRoot.put("title", JsonNode.stringNode("legacy"));
        legacyRoot.put("status", JsonNode.stringNode("PENDING"));
        // string-member log variant (the EditTree-era shape)
        legacyRoot.put("calculatorLog", JsonNode.stringNode(
                de.jare.jsoncasted.io.JsonNodeWriter.writeToString(legacyLog)));
        JsonNode children = JsonNode.arrayNode();
        children.add(legacyChild);
        legacyRoot.put("children", children);

        JsonNode doc = JsonNode.objectNode();
        doc.put("modelName", JsonNode.stringNode("m"));
        doc.put("modelVersion", JsonNode.stringNode("1"));
        doc.put("rootTask", legacyRoot);

        HarnessInstance loaded = HarnessInstanceCodec.fromJson(doc);
        assertNotNull(loaded.getRoot());
        assertEquals(loaded.getRoot().getId(), 1);
        assertEquals(loaded.getRoot().getCalculatorLog().size(), 1,
                "the string-member calculatorLog is converted to an array, not lost");
        assertEquals(loaded.getRoot().getCalculatorLog().get(0).getScore(), Integer.valueOf(8));
        assertEquals(loaded.getRoot().getChildren().size(), 1);
        TaskData loadedChild = loaded.getRoot().getChildren().get(0);
        assertEquals(loadedChild.getTitle(), "old child");
        assertEquals(loadedChild.getStatus(), Status.DONE);
        assertEquals(loadedChild.getCalculatorLog().size(), 1, "the legacy array log loads");
        assertEquals(loadedChild.getCalculatorLog().get(0).getScore(), Integer.valueOf(8));
    }
}
