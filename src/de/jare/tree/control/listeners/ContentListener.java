/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.listeners;

public interface ContentListener {

    public static final String EDIT_PASTE = "edit.paste";
    public static final String EDIT_CUT = "edit.cut";
    public static final String EDIT_COPY = "edit.copy";
    public static final String EDIT_RENAME_NODE = "edit.renameNode";
    public static final String EDIT_DELETE_NODE = "edit.deleteNode";
    public static final String EDIT_ADD_NODE = "edit.addNode";

    void onCommand(String commandId, Object trigger);
}
