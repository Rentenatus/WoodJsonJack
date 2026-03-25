/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.listeners;

import javax.swing.JTree;

public interface TreeFocusListener {

    void onNodeSelected(Object node, Object trigger, boolean rootSelected);

    void onEditorSelected(JTree editor, Object trigger);
}
