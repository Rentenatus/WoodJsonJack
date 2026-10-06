/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.listeners;

import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Listener interface for parse problems detected in a tree editor.
 * <p>
 * Implementations are notified when nodes with an edit status other than
 * OKAY are collected, e.g. when a "hard parse" request is rejected. The
 * offending nodes are delivered so they can be presented to the user.
 * </p>
 */
public interface ParseProblemsListener {

    /**
     * Called when parse problems are collected in an editor tree.
     *
     * @param source the editor tree that contains the nodes
     * @param nodes the tree nodes whose edit status is not OKAY
     */
    void onParseProblems(TreeFocusComponent source, List<DefaultMutableTreeNode> nodes);
}
