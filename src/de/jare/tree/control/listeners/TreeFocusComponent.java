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
 *
 * @author Janusch Rentenatus
 */
public interface TreeFocusComponent {

    public JTree getTree();

    public JackTreeModel getModel();

    public JackMasterControl getJackMaster();

}
