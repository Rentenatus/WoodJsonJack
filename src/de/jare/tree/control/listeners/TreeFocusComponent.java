/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.listeners;

import de.jare.tree.control.JackMasterControl;
import de.jare.tree.control.model.JackTreeModel;
import javax.swing.JTree;

/**
 * Interface for tree editor components that can receive focus and selection events.
 * <p>
 * Components implementing this interface represent editable tree views that can
 * be part of a tabbed interface or similar container. They provide access to
 * their underlying tree model, Swing tree component, and the master controller.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public interface TreeFocusComponent {

    /**
     * Returns the Swing JTree component used for displaying the tree structure.
     *
     * @return the JTree instance of this component
     */
    JTree getTree();

    /**
     * Returns the tree model associated with this component.
     *
     * @return the JackTreeModel instance
     */
    JackTreeModel getModel();

    /**
     * Returns the master controller that manages this component.
     *
     * @return the JackMasterControl instance, or null if not managed
     */
    JackMasterControl getJackMaster();

    /**
     * Indicates whether this component is in read-only mode.
     * <p>
     * When read-only, edit operations like adding, deleting, or renaming nodes
     * are disabled.
     * </p>
     *
     * @return true if this component is read-only, false otherwise
     */
    default boolean isReadonly() {
        return false;
    }

    /**
     * Returns a display name for this component.
     * <p>
     * This is typically the name of the resource or editor being displayed.
     * </p>
     *
     * @return the display name of this component
     */
    default String getDisplayName() {
        return "Unknown";
    }

}
