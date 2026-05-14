/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.listeners;

import javax.swing.tree.TreeModel;

public interface UndoRedoListener {

    void onUndo(TreeModel model);

    void onRedo(TreeModel model);

    default void onAddCommand(TreeModel model) {
        // NoOp
    }

    default void onClear(TreeModel model) {
        // NoOp
    }

}
