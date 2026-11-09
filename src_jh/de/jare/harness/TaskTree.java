/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditTimes;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import de.jare.jsoncasted.editor.core.TypeParserListener;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.lang.JsonNode;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The task tree of one harness run/instance, held as a real {@link EditTree} (concept
 * CONCEPT_Harness.md §3, decided): NOT a parallel world - the same EditNode instances the
 * JTree shows as {@code DefaultMutableTreeNode.userObject}. Each task is an
 * {@link EditNodeObject} named "task" whose member properties carry the {@link TaskData}
 * payload (primitive values), and whose "tasks" array member holds the subtask objects.
 *
 * <p>
 * The registry maps EditNode → TaskData (the facade holds the back-reference; the registry
 * answers the reverse direction for children/parent navigation). Structural changes go
 * through {@link EditTree#addChild} so the tree's own {@link TypeParserListener} fires;
 * value changes go through {@link #fireChanged(EditNodeAbstract)} which additionally
 * notifies the harness-side change listeners (the host repaints from there).
 * </p>
 *
 * @author Janusch Rentenatus
 */
public class TaskTree {

    /** Name of the array member of a task object that holds its subtask objects. */
    public static final String TASK_ARRAY = "tasks";

    /** Name of a task object node. */
    public static final String TASK_NAME = "task";

    private final EditTree editTree;
    private final Map<EditNode, TaskData> registry = new IdentityHashMap<>();
    private final List<Consumer<EditNodeAbstract>> changeListeners = new ArrayList<>();
    private TaskData rootTask;

    /** A fresh empty task tree - the tree root object IS the root task. */
    public TaskTree() {
        this(new EditTree(new EditNodeObject(TASK_NAME)));
    }

    public TaskTree(EditTree editTree) {
        this.editTree = editTree;
    }

    public EditTree getEditTree() {
        return editTree;
    }

    /** The root task of this tree (null until {@link #newRoot} or a load). */
    public TaskData getRootTask() {
        return rootTask;
    }

    /** Creates the root task on the tree root object itself. */
    public TaskData newRoot(long id, String taskType, String title) {
        EditNodeObject node = (EditNodeObject) editTree.getRoot();
        // the constructor initializes muted (no events); after register one event fires,
        // so listeners never see an unregistered node
        TaskData data = register(node, new TaskData(node, this, id, taskType, title));
        this.rootTask = data;
        fireChanged(node);
        return data;
    }

    /** Adds a subtask object to the parent's "tasks" array member. */
    public TaskData addSubtask(TaskData parent, long id, String taskType, String title) {
        EditNodeProperty arr = parent.tasksArray(true);
        EditNodeObject child = new EditNodeObject(TASK_NAME);
        editTree.addChild(arr, child);
        TaskData data = register(child, new TaskData(child, this, id, taskType, title));
        fireChanged(child);
        return data;
    }

    public TaskData register(EditNodeObject node, TaskData data) {
        registry.put(node, data);
        return data;
    }

    /** The TaskData facade of a node (null when the node is no registered task). */
    public TaskData dataOf(EditNode node) {
        return registry.get(node);
    }

    /** Harness-side change listener - the host repaints its view from here. */
    public void addChangeListener(Consumer<EditNodeAbstract> listener) {
        if (listener != null) {
            changeListeners.add(listener);
        }
    }

    /** Fires a change for a node: harness listeners plus the EditTree's own parser listener. */
    public void fireChanged(EditNodeAbstract node) {
        for (Consumer<EditNodeAbstract> l : new ArrayList<>(changeListeners)) {
            l.accept(node);
        }
        TypeParserListener p = editTree.getParserListener();
        if (p != null && node != null) {
            p.onNodeValueChanged(node, null, null);
        }
    }

    /**
     * Builds a TaskTree from a persisted root-task JsonNode (the task object itself).
     * The node structure is imported via the editor's own converter, then every task
     * object is wrapped into the registry.
     */
    public static TaskTree fromJson(JsonNode rootTaskJson) throws JsonParseException {
        EditNodeAbstract root = JsonTreeConverter.importFromJsonNode(rootTaskJson, TASK_NAME, new EditTimes());
        EditTree tree = new EditTree(root);
        TaskTree taskTree = new TaskTree(tree);
        taskTree.wrapAll();
        taskTree.rootTask = taskTree.dataOf(root);
        return taskTree;
    }

    /** Wraps every task object of the tree into the registry (after a load). */
    public void wrapAll() {
        wrapNode(editTree.getRoot());
    }

    private TaskData wrapNode(EditNodeAbstract node) {
        if (!(node instanceof EditNodeObject obj)) {
            return null;
        }
        TaskData data = registry.get(obj);
        if (data == null) {
            data = register(obj, new TaskData(obj, this));
        }
        for (EditNode c : obj.getChildren()) {
            if (c instanceof EditNodeAbstract a) {
                wrapNode(a);
            }
        }
        return data;
    }

    /** True when the node is a task object (the tree root or an element of a "tasks" array). */
    static boolean isTaskObject(EditNode node) {
        if (!(node instanceof EditNodeObject)) {
            return false;
        }
        EditNode parent = node.getParent();
        return parent == null || (parent instanceof EditNodeProperty p && TASK_ARRAY.equals(p.getName()));
    }
}
