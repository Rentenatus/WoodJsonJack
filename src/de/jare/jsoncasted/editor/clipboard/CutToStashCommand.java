/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.clipboard;

import de.jare.jsoncasted.editor.command.CommandAction;
import de.jare.jsoncasted.editor.command.CommandAvailability;
import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;

public class CutToStashCommand extends AbstractToStashCommand {

    private final long[] parentIds;
    private final int[] indices;
    private EditNode[] removedSnapshots = new EditNode[0];

    public CutToStashCommand(ClipboardManager clipboardManager, String stashName, long[] nodeIds) {
        super(CommandType.OTHER,
                "Cut nodes to stash '" + stashName + "'",
                clipboardManager,
                stashName,
                nodeIds);

        this.parentIds = new long[nodeIds.length];
        this.indices = new int[nodeIds.length];
    }

    public CutToStashCommand(ClipboardManager clipboardManager, long[] nodeIds) {
        this(clipboardManager, clipboardManager.getActiveStashName(), nodeIds);
    }

    @Override
    protected String getAllowedMessageKey() {
        return "editor.command.cut.allowed";
    }

    @Override
    protected String getNodeMissingMessageKey() {
        return "editor.command.cut.nodeMissing";
    }

    @Override
    protected String getUnsupportedNodeTypeMessageKey() {
        return "editor.command.cut.unsupportedNodeType";
    }

    @Override
    protected String getMixedNodeTypesMessageKey() {
        return "editor.command.cut.mixedNodeTypes";
    }

    @Override
    protected CommandAvailability validateFurther(EditTree tree) {
        EditNode[] nodes = new EditNode[nodeIds.length];

        for (int i = 0; i < nodeIds.length; i++) {
            EditNode node = tree.findNodeById(nodeIds[i]);
            EditNode parent = node.getParent();
            if (parent == null) {
                return CommandAvailability.disallowed(
                        "editor.command.cut.parentMissing",
                        Long.toString(nodeIds[i]),
                        Integer.toString(i));
            }
            if (parent.getChildIndex(node) < 0) {
                return CommandAvailability.disallowed(
                        "editor.command.cut.nodeNotChildOfParent",
                        Long.toString(nodeIds[i]),
                        Long.toString(parent.getEditId()),
                        Integer.toString(i));
            }
            nodes[i] = node;
        }

        for (int i = 0; i < nodes.length; i++) {
            for (int j = 0; j < nodes.length; j++) {
                if (i != j && isAncestorOf(nodes[i], nodes[j])) {
                    return CommandAvailability.disallowed(
                            "editor.command.cut.containsAncestorAndDescendant",
                            Long.toString(nodes[i].getEditId()),
                            Long.toString(nodes[j].getEditId()),
                            Integer.toString(i),
                            Integer.toString(j));
                }
            }
        }

        return super.validateFurther(tree);
    }

    @Override
    public CommandResult execute(EditTree tree) {
        requireExecutable(tree);
        captureState(tree);

        ClipboardStash stash = clipboardManager.getStash(stashName);
        if (stash == null) {
            throw new IllegalStateException("Stash with name " + stashName + " does not exist");
        }

        stash.setNodes(removedSnapshots);
        tree.removeNodes(nodeIds);

        return new CommandResult(
                this,
                CommandAction.EXECUTE,
                removedSnapshots,
                null,
                removedSnapshots,
                null
        );
    }

    @Override
    public CommandResult undo(EditTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }

        ClipboardStash stash = clipboardManager.getStash(stashName);
        if (stash != null) {
            stash.setNodes(originalStashContent);
        }

        EditNode[] restoredNodes = new EditNode[nodeIds.length];
        for (int i = 0; i < nodeIds.length; i++) {
            EditNode snapshot = i < removedSnapshots.length ? removedSnapshots[i] : null;
            if (snapshot != null && parentIds[i] >= 0 && indices[i] >= 0) {
                tree.addNode(parentIds[i], snapshot, indices[i]);
                restoredNodes[i] = snapshot;
            }
        }

        return new CommandResult(
                this,
                CommandAction.UNDO,
                restoredNodes,
                restoredNodes,
                null,
                null
        );
    }

    @Override
    public String toString() {
        return "CutToStashCommand[stash='" + stashName + "', nodeCount=" + nodeIds.length + "]";
    }

    private void captureState(EditTree tree) {
        removedSnapshots = new EditNode[nodeIds.length];
        for (int i = 0; i < nodeIds.length; i++) {
            EditNode node = tree.findNodeById(nodeIds[i]);
            EditNode parent = node.getParent();
            parentIds[i] = parent.getEditId();
            indices[i] = parent.getChildIndex(node);
            removedSnapshots[i] = node.deepCopy(false);
        }
    }

    private static boolean isAncestorOf(EditNode ancestor, EditNode node) {
        for (EditNode current = node.getParent(); current != null; current = current.getParent()) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }
}
