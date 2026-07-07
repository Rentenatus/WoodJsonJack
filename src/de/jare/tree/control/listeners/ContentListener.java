/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.listeners;

/**
 * Listener interface for content-related commands in the tree editor.
 * <p>
 * Implementations are notified when content modification commands such as copy, paste,
 * cut, delete, rename, or add node are triggered. This allows components to react
 * to content changes and update their state accordingly.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public interface ContentListener {

    /**
     * Command identifier for pasting content into the selected node.
     */
    String EDIT_PASTE = "edit.paste";

    /**
     * Command identifier for pasting content underneath the selected node.
     * The pasted content will be inserted as a sibling after the selected node.
     */
    String EDIT_PASTE_UNDERNEATH = "edit.pasteUnderneath";

    /**
     * Command identifier for cutting (copy and remove) the selected nodes.
     */
    String EDIT_CUT = "edit.cut";

    /**
     * Command identifier for copying the selected nodes to clipboard.
     */
    String EDIT_COPY = "edit.copy";

    /**
     * Command identifier for renaming the selected node.
     */
    String EDIT_RENAME_NODE = "edit.renameNode";

    /**
     * Command identifier for deleting the selected nodes.
     */
    String EDIT_DELETE_NODE = "edit.deleteNode";

    /**
     * Command identifier for adding a new child node to the selected node.
     */
    String EDIT_ADD_NODE = "edit.addNode";

    /**
     * Called when a content command is triggered.
     *
     * @param commandId the identifier of the command being executed.
     *                  Use the constants defined in this interface (e.g., {@link #EDIT_PASTE},
     *                  {@link #EDIT_COPY}, etc.)
     * @param trigger the object that triggered this command (e.g., a UI component or menu item)
     */
    void onCommand(String commandId, Object trigger);
}
