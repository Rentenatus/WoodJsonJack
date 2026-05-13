/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.listeners;

import de.jare.tree.control.MasterControl;
import de.jare.jsoncasted.editor.core.EditTree;
import javax.swing.JTree;
import javax.swing.tree.TreeModel;

/**
 * Interface for tree components that can receive focus.
 * Extended to support both TreeModel (legacy) and EditTree (editor package).
 *
 * @author Janusch Rentenatus
 */
public interface TreeFocusComponent {

    /**
     * Gets the Swing JTree component.
     * @return the JTree
     */
    public JTree getTree();

    /**
     * Gets the TreeModel for this component (legacy Swing-based).
     * @return the TreeModel
     */
    public TreeModel getModel();

    /**
     * Gets the EditTree for this component (editor package-based).
     * May return null if the component doesn't use EditTree.
     * @return the EditTree, or null
     */
    default EditTree getEditTree() {
        return null;
    }

    /**
     * Gets the MasterControl associated with this component.
     * @return the MasterControl
     */
    public MasterControl getMaster();

}
