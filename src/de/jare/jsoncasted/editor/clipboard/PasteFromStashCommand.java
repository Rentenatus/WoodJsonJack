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
 * Command that pastes nodes from a clipboard stash into the tree.
 *
 * <p>The stash contains neutral subtree snapshots. This command does not
 * interpret cut vs. copy semantics; it only inserts copies of the stash
 * content into the target tree.</p>
 *
 * <p>On execute, the command asks the {@link ClipboardManager} to perform
 * the paste and records the IDs of the newly inserted nodes. On undo,
 * those nodes are removed again.</p>
 */
public class PasteFromStashCommand extends AbstractEditCommand {

    private final ClipboardManager clipboardManager;
    private final String stashName;
    private final long parentId;
    private final int index;

    /**
     * IDs of the nodes that were inserted by the last execute() call.
     * Used by undo() to remove them again.
     */
    private long[] pastedNodeIds;

    /**
     * Creates a command to paste from a stash to a parent node.
     *
     * @param clipboardManager the clipboard manager
     * @param stashName the name of the source stash
     * @param parentId the ID of the parent node where nodes will be inserted
     * @param index the index at which to insert the nodes, or -1 to append
     */
    public PasteFromStashCommand(ClipboardManager clipboardManager,
                                 String stashName,
                                 long parentId,
                                 int index) {
        super(EditCommand.CommandType.ADD_NODE,
              "Paste from stash '" + stashName + "' to parent " + parentId
                      + (index >= 0 ? " at index " + index : " (append)"));

        if (clipboardManager == null) {
            throw new IllegalArgumentException("ClipboardManager cannot be null");
        }
        if (stashName == null || stashName.trim().isEmpty()) {
            throw new IllegalArgumentException("Stash name cannot be null or empty");
        }
        if (parentId < 0) {
            throw new IllegalArgumentException("Parent ID cannot be negative");
        }

        this.clipboardManager = clipboardManager;
        this.stashName = stashName;
        this.parentId = parentId;
        this.index = index;
        this.pastedNodeIds = new long[0];
    }

    /**
     * Creates a command to paste from the active stash.
     *
     * @param clipboardManager the clipboard manager
     * @param parentId the ID of the parent node where nodes will be inserted
     * @param index the index at which to insert the nodes, or -1 to append
     */
    public PasteFromStashCommand(ClipboardManager clipboardManager,
                                 long parentId,
                                 int index) {
        this(clipboardManager, clipboardManager.getActiveStashName(), parentId, index);
    }

    /**
     * Creates a command to paste from a stash to a parent node (append).
     *
     * @param clipboardManager the clipboard manager
     * @param stashName the name of the source stash
     * @param parentId the ID of the parent node where nodes will be inserted
     */
    public PasteFromStashCommand(ClipboardManager clipboardManager,
                                 String stashName,
                                 long parentId) {
        this(clipboardManager, stashName, parentId, -1);
    }

    /**
     * Creates a command to paste from the active stash (append).
     *
     * @param clipboardManager the clipboard manager
     * @param parentId the ID of the parent node where nodes will be inserted
     */
    public PasteFromStashCommand(ClipboardManager clipboardManager,
                                 long parentId) {
        this(clipboardManager, clipboardManager.getActiveStashName(), parentId, -1);
    }

    @Override
    public CommandResult execute(EditTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }

        // Perform the paste operation via ClipboardManager.
        // ClipboardManager decides whether to reuse or regenerate edit IDs.
        pastedNodeIds = clipboardManager.pasteFromStash(stashName, tree, parentId, index);

        // Resolve the inserted nodes for the CommandResult.
        EditNode[] pastedNodes = new EditNode[pastedNodeIds.length];
        for (int i = 0; i < pastedNodeIds.length; i++) {
            EditNode node = tree.findNodeById(pastedNodeIds[i]);
            if (node != null) {
                pastedNodes[i] = node;
            }
        }

        return new CommandResult(
                this,
                CommandAction.EXECUTE,
                pastedNodes,   // affected nodes
                pastedNodes,   // added nodes
                null,          // removed nodes
                null           // moved nodes
        );
    }

    @Override
    public CommandResult undo(EditTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("Tree cannot be null");
        }

        // Remove all nodes that were inserted by the last execute().
        EditNode[] removedNodes = new EditNode[pastedNodeIds.length];
        for (int i = 0; i < pastedNodeIds.length; i++) {
            EditNode node = tree.findNodeById(pastedNodeIds[i]);
            if (node != null) {
                tree.removeNode(pastedNodeIds[i]);
                removedNodes[i] = node;
            }
        }

        return new CommandResult(
                this,
                CommandAction.UNDO,
                removedNodes,  // affected nodes
                null,          // added nodes
                removedNodes,  // removed nodes
                null           // moved nodes
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
     * Returns the parent ID where nodes will be pasted.
     *
     * @return the parent ID
     */
    public long getParentId() {
        return parentId;
    }

    /**
     * Returns the insertion index.
     *
     * @return the index, or -1 for append
     */
    public int getIndex() {
        return index;
    }

    /**
     * Returns the IDs of the pasted nodes.
     * This is only available after execute() has been called.
     *
     * @return a copy of the pasted node IDs array
     */
    public long[] getPastedNodeIds() {
        return pastedNodeIds.clone();
    }

    @Override
    public String toString() {
        return "PasteFromStashCommand[" +
                "stash='" + stashName + '\'' +
                ", parentId=" + parentId +
                ", index=" + (index >= 0 ? index : "append") +
                "]";
    }
}