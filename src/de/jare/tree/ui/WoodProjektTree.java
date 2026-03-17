/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import javax.swing.*;
import javax.swing.tree.*;

public class WoodProjektTree extends JTree {

    private final TreeModel model;

    public WoodProjektTree(String rootText, String... children) {
        super();
        model = createModel(rootText, children);
        setModel(model);
        setEditable(true);
        setShowsRootHandles(true);
    }

    private TreeModel createModel(String rootText, String... children) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(rootText);
        for (String child : children) {
            root.add(new DefaultMutableTreeNode(child));
        }
        return new DefaultTreeModel(root);
    }
}
