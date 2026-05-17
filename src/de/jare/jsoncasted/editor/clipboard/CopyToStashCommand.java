/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.clipboard;

import de.jare.jsoncasted.editor.command.CommandAction;
import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;

public class CopyToStashCommand extends AbstractToStashCommand {

    public CopyToStashCommand(ClipboardManager clipboardManager, String stashName, long[] nodeIds) {
        super(CommandType.OTHER,
                "Copy nodes to stash '" + stashName + "'",
                clipboardManager,
                stashName,
                nodeIds);
    }

    public CopyToStashCommand(ClipboardManager clipboardManager, long[] nodeIds) {
        this(clipboardManager, clipboardManager.getActiveStashName(), nodeIds);
    }

    @Override
    protected String getAllowedMessageKey() {
        return "editor.command.copy.allowed";
    }

    @Override
    protected String getNodeMissingMessageKey() {
        return "editor.command.copy.nodeMissing";
    }

    @Override
    protected String getUnsupportedNodeTypeMessageKey() {
        return "editor.command.copy.unsupportedNodeType";
    }

    @Override
    protected String getMixedNodeTypesMessageKey() {
        return "editor.command.copy.mixedNodeTypes";
    }

    @Override
    public CommandResult execute(EditTree tree) {
        requireExecutable(tree);

        clipboardManager.copyToStash(stashName, tree, nodeIds);
        EditNode[] copiedNodes = collectNodes(tree);

        return new CommandResult(
                this,
                CommandAction.EXECUTE,
                copiedNodes,
                copiedNodes,
                null,
                null, null,
                NO_UPDATE_ACTIONS
        );
    }

    @Override
    public CommandResult doUndo(EditTree tree) {

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
                null, null,
                NO_UPDATE_ACTIONS
        );
    }

    @Override
    public String toString() {
        return "CopyToStashCommand[stash='" + stashName + "', nodeCount=" + nodeIds.length + "]";
    }
}
