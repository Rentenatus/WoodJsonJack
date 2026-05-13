/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import de.jare.jsoncasted.editor.core.EditNode;

/**
 * DEPRECATED: This interface has been replaced by {@link TreeNodeUtils}.
 * 
 * <p>This interface provided utility methods for tree node operations.
 * All functionality has been moved to the concrete utility class TreeNodeUtils,
 * which provides the same methods as static utility methods.</p>
 * 
 * <p>Existing code that implements WoodUtils should continue to work, but new code
 * should use TreeNodeUtils directly instead.</p>
 * 
 * @deprecated Use {@link TreeNodeUtils} instead
 */
public interface WoodUtils {

    /**
     * @deprecated Use {@link TreeNodeUtils#findNodeByEditId(TreeModel, long)} instead
     */
    @Deprecated
    default DefaultMutableTreeNode findNodeByEditId(TreeModel model, long id) {
        return TreeNodeUtils.findNodeByEditId(model, id);
    }

    /**
     * @deprecated Use {@link TreeNodeUtils#findNodeByEditId(DefaultMutableTreeNode, long)} instead
     */
    @Deprecated
    default DefaultMutableTreeNode findNodeByEditId(DefaultMutableTreeNode node, long id) {
        return TreeNodeUtils.findNodeByEditId(node, id);
    }

    /**
     * @deprecated Use {@link TreeNodeUtils#deepCopy(DefaultMutableTreeNode)} instead
     */
    @Deprecated
    default DefaultMutableTreeNode deepCopy(DefaultMutableTreeNode original) {
        return TreeNodeUtils.deepCopy(original);
    }

    /**
     * @deprecated Use {@link TreeNodeUtils#deepCopy(DefaultMutableTreeNode, boolean)} instead
     */
    @Deprecated
    default DefaultMutableTreeNode deepCopy(DefaultMutableTreeNode original, boolean regenerateEditId) {
        return TreeNodeUtils.deepCopy(original, regenerateEditId);
    }

}
