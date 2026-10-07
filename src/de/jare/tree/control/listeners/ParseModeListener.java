/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.listeners;

import de.jare.jsoncasted.editor.core.ParseMode;

/**
 * Listener interface for parse mode changes of an editor tree.
 * <p>
 * Implementations are notified when the parse mode of a tree changes, e.g. via the mode combo or the hard parse
 * fallback dialog, so menus that depend on the mode can rebuild even without a selection change.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public interface ParseModeListener {

    /**
     * Called when the parse mode of an editor tree changed.
     *
     * @param source the editor tree whose parse mode changed
     * @param newMode the new parse mode of the tree
     */
    void onParseModeChanged(TreeFocusComponent source, ParseMode newMode);
}
