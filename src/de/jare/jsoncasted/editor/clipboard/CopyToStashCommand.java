/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
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
 * Command that copies nodes to a clipboard stash.
 *
 * <p>This command only affects clipboard state, not the tree itself.</p>
 *
 * <p>In the revised clipboard model, the stash stores neutral subtree snapshots.
 * It does not remember whether content came from copy or cut, and it does not
 * encode move semantics. ID reuse vs. regeneration is decided later during paste.</p>
 */
public class CopyToStashCommand extends AbstractEditCommand {

    private final ClipboardManager clipboardManager;
    private final String stashName;
    private final long[] nodeIds;
    private final EditNode[] originalStashContent;

    /**
     * Creates a command to copy nodes to a stash.
     *
     * @param clipboardManager the clipboard manager
     * @param stashName the name of the target stash
     * @param nodeIds the IDs of the nodes to copy
     */
    public CopyToStashCommand(ClipboardManager clipboardManager, String stashName, long[] nodeIds) {
        super(EditCommand.CommandType.OTHER, "Copy nodes to stash '" + stashName + "'");

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

        ClipboardStash stash = clipboardManager.getStash(stashName);
        this.originalStashContent = stash != null ? stash.getNodes() : new EditNode[0];
    }

    /**
     * Creates a command to copy nodes to the active stash.
     *
     * @param clipboardManager the clipboard manager
     * @param nodeIds the IDs of the nodes to copy
     */
    public CopyToStashCommand(ClipboardManager clipboardManager, long[] nodeIds) {
        this(clipboardManager, clipboardManager.getActiveStashName(), nodeIds);
    }

    @Override
    public CommandResult execute(EditTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }

        clipboardManager.copyToStash(stashName, tree, nodeIds);

        EditNode[] copiedNodes = new EditNode[nodeIds.length];
        for (int i = 0; i < nodeIds.length; i++) {
            EditNode node = tree.findNodeById(nodeIds[i]);
            if (node != null) {
                copiedNodes[i] = node;
            }
        }

        return new CommandResult(
                this,
                CommandAction.EXECUTE,
                copiedNodes,
                copiedNodes,
                null,
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

        return new CommandResult(
                this,
                CommandAction.UNDO,
                originalStashContent,
                null,
                originalStashContent,
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
     * Returns the node IDs to copy.
     *
     * @return a copy of the node IDs array
     */
    public long[] getNodeIds() {
        return nodeIds.clone();
    }

    @Override
    public String toString() {
        return "CopyToStashCommand["
                + "stash='" + stashName + '\''
                + ", nodeCount=" + nodeIds.length
                + "]";
    }
}