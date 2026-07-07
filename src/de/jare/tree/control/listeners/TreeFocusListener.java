/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.listeners;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Listener interface for tree node selection and editor activation events.
 * <p>
 * Implementations are notified when nodes are selected in a tree or when
 * a different tree editor becomes active. This allows components to
 * synchronize their state with the currently selected node and editor.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public interface TreeFocusListener {

    /**
     * Called when a node is selected in a tree.
     * <p>
     * Implementations can use this to update their state based on the selected
     * node. The default implementation does nothing.
     * </p>
     *
     * @param node the selected tree node, may be null if no node is selected
     * @param trigger the object that triggered this selection (e.g., the tree component)
     * @param rootSelected true if the root node is selected, false otherwise
     */
    default void onNodeSelected(DefaultMutableTreeNode node, Object trigger, boolean rootSelected) {
        // NoOp by default
    }

    /**
     * Called when a different tree editor becomes active.
     * <p>
     * This is typically triggered when switching between tabs in a tabbed interface.
     * Implementations should update their state to reflect the newly active editor.
     * </p>
     *
     * @param editor the editor component that has become active, may be null
     * @param trigger the object that triggered this editor selection
     */
    void onEditorSelected(TreeFocusComponent editor, Object trigger);
}
