/*
 * Copyright (c) 2025, Janusch Rentenatus.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.clipboard;

import de.jare.jsoncasted.editor.command.AbstractEditCommand;
import de.jare.jsoncasted.editor.command.CommandAction;
import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;

/**
 * Command that cuts nodes to a clipboard stash.
 *
 * <p>In the revised clipboard model, cutting means:
 * <ul>
 *   <li>Store neutral snapshots of the selected nodes in the stash (deep copy with existing IDs)</li>
 *   <li>Remove the original nodes from the tree immediately</li>
 *   <li>Undo restores the originals at their previous positions</li>
 * </ul>
 * There is no special coordination with paste; paste operates only on the
 * snapshot content stored in the stash.</p>
 */
public class CutToStashCommand extends AbstractEditCommand {

    private final ClipboardManager clipboardManager;
    private final String stashName;
    private final long[] nodeIds;

    /**
     * Original parent IDs and indices of the nodes, used for undo().
     */
    private final long[] parentIds;
    private final int[] indices;

    /**
     * Snapshots of the nodes as sie zum Zeitpunkt des Cuts im Tree sind.
     * Diese werden beim Undo wieder eingesetzt.
     */
    private EditNode[] removedSnapshots;

    /**
     * Speichert den ursprünglichen Inhalt des Stash, um ihn bei Undo
     * wiederherstellen zu können.
     */
    private final EditNode[] originalStashContent;

    /**
     * Creates a command to cut nodes to a stash.
     *
     * @param clipboardManager the clipboard manager
     * @param stashName the name of the target stash
     * @param nodeIds the IDs of the nodes to cut
     */
    public CutToStashCommand(ClipboardManager clipboardManager,
                             String stashName,
                             long[] nodeIds) {
        super(EditCommand.CommandType.OTHER, "Cut nodes to stash '" + stashName + "'");

        if (clipboardManager == null) {
            throw new IllegalArgumentException("ClipboardManager cannot be null");
        }
        if (stashName == null || stashName.trim().isEmpty()) {
            throw new IllegalArgumentException("Stash name cannot be null or empty");
        }
        if (nodeIds == null || nodeIds.length == 0) {
            throw new IllegalArgumentException("Node IDs cannot be null or empty");
        }

        this.clipboardManager = clipboardManager;
        this.stashName = stashName;
        this.nodeIds = nodeIds.clone();
        this.parentIds = new long[nodeIds.length];
        this.indices = new int[nodeIds.length];

        ClipboardStash stash = clipboardManager.getStash(stashName);
        this.originalStashContent = stash != null ? stash.getNodes() : new EditNode[0];
        this.removedSnapshots = new EditNode[0];
    }

    /**
     * Creates a command to cut nodes to the active stash.
     *
     * @param clipboardManager the clipboard manager
     * @param nodeIds the IDs of the nodes to cut
     */
    public CutToStashCommand(ClipboardManager clipboardManager, long[] nodeIds) {
        this(clipboardManager, clipboardManager.getActiveStashName(), nodeIds);
    }

    @Override
    public CommandResult execute(EditTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }

        removedSnapshots = new EditNode[nodeIds.length];

        // 1. Positionen und Snapshots sichern
        for (int i = 0; i < nodeIds.length; i++) {
            EditNode node = tree.findNodeById(nodeIds[i]);
            if (node != null) {
                EditNode parent = node.getParent();
                parentIds[i] = parent != null ? parent.getEditId() : -1;
                indices[i] = parent != null ? parent.getChildIndex(node) : -1;
                // Snapshot mit bestehenden IDs
                removedSnapshots[i] = node.deepCopy(false);
            } else {
                parentIds[i] = -1;
                indices[i] = -1;
                removedSnapshots[i] = null;
            }
        }

        // 2. Snapshot in den Stash schreiben
        ClipboardStash stash = clipboardManager.getStash(stashName);
        if (stash == null) {
            throw new IllegalStateException("Stash with name " + stashName + " does not exist");
        }
        stash.setNodes(removedSnapshots);

        // 3. Originalknoten aus dem Tree entfernen
        //    removeNodes() entfernt rückwärts, damit Indices stabil sind.
        tree.removeNodes(nodeIds);

        EditNode[] cutNodes = new EditNode[nodeIds.length];
        for (int i = 0; i < nodeIds.length; i++) {
            // Nach removeNodes() sind die Originale nicht mehr im Baum;
            // für das CommandResult verwenden wir die Snapshots.
            cutNodes[i] = removedSnapshots[i];
        }

        return new CommandResult(
                this,
                CommandAction.EXECUTE,
                cutNodes,
                null,
                cutNodes,
                null
        );
    }

    @Override
    public CommandResult undo(EditTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }

        // 1. Ursprünglichen Stash-Inhalt wiederherstellen
        ClipboardStash stash = clipboardManager.getStash(stashName);
        if (stash != null) {
            stash.setNodes(originalStashContent);
        }

        // 2. Entfernte Snapshots wieder an ihre ursprünglichen Positionen einfügen
        EditNode[] restoredNodes = new EditNode[nodeIds.length];

        for (int i = 0; i < nodeIds.length; i++) {
            EditNode snapshot = removedSnapshots != null && i < removedSnapshots.length
                    ? removedSnapshots[i]
                    : null;
            long parentId = parentIds[i];
            int index = indices[i];

            if (snapshot != null && parentId >= 0 && index >= 0) {
                // Wir fügen den Snapshot mit seinen ursprünglichen IDs ein.
                // Da der Cut die Originale entfernt hat, sollte es keine Kollisionen geben.
                tree.addNode(parentId, snapshot, index);
                restoredNodes[i] = snapshot;
            } else {
                restoredNodes[i] = null;
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

    /**
     * Returns the stash name.
     *
     * @return the stash name
     */
    public String getStashName() {
        return stashName;
    }

    /**
     * Returns the node IDs to cut.
     *
     * @return a copy of the node IDs array
     */
    public long[] getNodeIds() {
        return nodeIds.clone();
    }

    @Override
    public String toString() {
        return "CutToStashCommand[" +
               "stash='" + stashName + '\'' +
               ", nodeCount=" + nodeIds.length +
               "]";
    }
}