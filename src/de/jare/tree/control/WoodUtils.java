/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control;

import de.jare.tree.control.commands.*;
import de.jare.tree.data.JsonTreeNodeData;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;

/**
 * Represents a single undoable command in the tree editor.
 * <p>
 * Implementations encapsulate a single logical change (add node, delete node,
 * rename, value change, move, ...) and know how to execute and undo that
 * change.
 * </p>
 */
public interface WoodUtils {

    default DefaultMutableTreeNode findNodeByEditId(TreeModel model, long id) {
        Object root = model.getRoot();
        if (!(root instanceof DefaultMutableTreeNode dmtn)) {
            return null;
        }
        return findNodeByEditId(dmtn, id);
    }

    default DefaultMutableTreeNode findNodeByEditId(DefaultMutableTreeNode node, long id) {
        Object uo = node.getUserObject();
        if (uo instanceof JsonTreeNodeData data && data.getEditId() == id) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            DefaultMutableTreeNode found = findNodeByEditId(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    default DefaultMutableTreeNode deepCopy(DefaultMutableTreeNode original) {
        Object uo = original.getUserObject();
        if (uo instanceof JsonTreeNodeData data) {
            uo = data.deepCopy(false);
        } else {
            uo = String.valueOf(uo);
        }
        DefaultMutableTreeNode copy = new DefaultMutableTreeNode(uo);
        for (int i = 0; i < original.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) original.getChildAt(i);
            copy.add(deepCopy(child));
        }
        return copy;
    }
}
