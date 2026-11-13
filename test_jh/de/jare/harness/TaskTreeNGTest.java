/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.io.HarnessInstanceCodec;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.lang.JsonNode;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * The task tree as a real EditTree (CONCEPT_Harness.md §3, decided): TaskData is a facade with a
 * back-reference to its EditNodeObject - the same instance a JTree would show as userObject.
 * Payload lives as primitive member properties, subtasks in the "tasks" array member, changes
 * fire the tree's change listeners, and the codec persists/loads through the EditTree shape.
 *
 * @author Janusch Rentenatus
 */
public class TaskTreeNGTest {

    /**
     * The facade wraps the EditNode: the tree root object IS the root task, the same EditNode
     * instance is shared (no parallel world), and the payload is readable from the node members.
     */
    @Test
    public void testRootTaskIsTheTreeRootObject() {
        TaskTree tree = new TaskTree();
        TaskData root = tree.newRoot(1, "SPLIT", "root prompt");
        assertSame(root.getNode(), tree.getEditTree().getRoot(), "the root task is the tree root object");
        assertSame(tree.getRootTask(), root);
        assertEquals(root.getId(), 1);
        assertEquals(root.getTaskType(), "SPLIT");
        assertEquals(root.getTitle(), "root prompt");
        assertEquals(root.getStatus(), Status.PENDING);
        assertNull(root.getParent(), "the root task has no parent task");
        assertEquals(root.depth(), 0);
    }

    /**
     * Subtasks hang as object elements of the "tasks" array member; parent/children navigate
     * through the registry, and every mutation fires a change event (the sync bridge).
     */
    @Test
    public void testSubtasksAndChangeEvents() {
        TaskTree tree = new TaskTree();
        TaskData root = tree.newRoot(1, "SPLIT", "root");
        List<de.jare.jsoncasted.editor.core.EditNodeAbstract> events = new ArrayList<>();
        tree.addChangeListener(events::add);

        TaskData child = root.addSubtask(2, "WORK", "part one");
        assertEquals(root.getChildren().size(), 1);
        assertSame(root.getChildren().get(0), child);
        assertSame(child.getParent(), root, "parent navigates through the registry");
        assertEquals(child.depth(), 1);
        assertFalse(events.isEmpty(), "addSubtask fired a change event");

        events.clear();
        child.setStatus(Status.RUNNING);
        child.setScore(7);
        child.incrementAttempt();
        assertEquals(child.getStatus(), Status.RUNNING);
        assertEquals(child.getScore(), Integer.valueOf(7));
        assertEquals(child.getAttempt(), 1);
        assertEquals(events.size(), 3, "every setter fired its own change event");
        // the payload is really in the node members (persisted with the tree)
        assertEquals(((EditNodeObject) child.getNode()).getChildren().size() > 0, true);
    }

    /**
     * The codec roundtrip runs through the EditTree shape: save → load restores the tree with
     * children, status, score and the calculator log, and the loaded nodes are EditNodes again.
     */
    @Test
    public void testCodecRoundtripThroughEditTreeShape() {
        HarnessInstance instance = new HarnessInstance("m", "1");
        TaskData root = instance.newRootTask(instance.nextTaskId(), "AskAi", "do research AND code");
        TaskData child = root.addSubtask(instance.nextTaskId(), "WORK", "research part");
        child.setStatus(Status.DONE);
        child.setScore(9);
        child.logCall(new CallLogEntry(1, child.getId(), "WORK", Kind.WORK, "work.m1", "m",
                1, 12, 10, 20, 9, Status.DONE, "ok", null, null));
        root.close("final answer");

        JsonNode json = HarnessInstanceCodec.toJson(instance);
        HarnessInstance loaded = HarnessInstanceCodec.fromJson(json);

        TaskData loadedRoot = loaded.getRoot();
        assertNotNull(loadedRoot);
        assertSame(loadedRoot.getNode(), loaded.getTaskTree().getEditTree().getRoot());
        assertEquals(loadedRoot.getTitle(), "do research AND code");
        assertEquals(loadedRoot.getStatus(), Status.DONE, "close() set DONE and it persisted");
        assertEquals(loadedRoot.getArtifact(), "final answer");
        assertEquals(loadedRoot.getChildren().size(), 1);
        TaskData loadedChild = loadedRoot.getChildren().get(0);
        assertEquals(loadedChild.getStatus(), Status.DONE);
        assertEquals(loadedChild.getScore(), Integer.valueOf(9));
        assertEquals(loadedChild.getCalculatorLog().size(), 1, "the log member round-trips");
        assertEquals(loadedChild.getCalculatorLog().get(0).getScore(), Integer.valueOf(9));
        assertSame(loadedChild.getParent(), loadedRoot);
    }

    /**
     * Legacy files (old "id"/"children" shape) still load: the codec normalizes them into the
     * EditTree shape ("taskId"/"tasks") before the import.
     */
    @Test
    public void testLegacyRootTaskShapeStillLoads() {
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
        assertEquals(loaded.getRoot().getChildren().size(), 1);
        assertEquals(loaded.getRoot().getChildren().get(0).getTitle(), "old child");
        assertEquals(loaded.getRoot().getChildren().get(0).getStatus(), Status.DONE);
        assertEquals(loaded.getRoot().getChildren().get(0).getCalculatorLog().size(), 1,
                "the legacy calculatorLog array is converted to the string member, not lost");
        assertEquals(loaded.getRoot().getChildren().get(0).getCalculatorLog().get(0).getScore(),
                Integer.valueOf(8));
    }

    /**
     * Event ordering: the constructor initializes muted, so a change listener never sees an
     * unregistered node - dataOf resolves inside the event, and exactly one event fires per
     * structural add (the addChild notification is separate).
     */
    @Test
    public void testChangeEventFiresAfterRegister() {
        TaskTree tree = new TaskTree();
        List<TaskData> seen = new ArrayList<>();
        tree.addChangeListener(n -> seen.add(tree.dataOf(n)));
        TaskData root = tree.newRoot(1, "SPLIT", "root");
        assertEquals(seen.size(), 1, "exactly one event for the root creation");
        assertSame(seen.get(0), root, "the node is already registered when the event fires");
        seen.clear();
        TaskData child = root.addSubtask(2, "WORK", "part");
        assertEquals(seen.size(), 2, "one event for the tasks-array creation, one for the child");
        assertSame(seen.get(0), root);
        assertSame(seen.get(1), child, "both events resolve through the registry - nothing unregistered");
    }
}
