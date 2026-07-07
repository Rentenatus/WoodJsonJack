/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control;

import de.jare.jsoncasted.editor.TreeEditorAbstract;
import de.jare.jsoncasted.editor.events.HistoryManager;
import de.jare.tree.control.model.JackTreeModel;
import java.lang.ref.WeakReference;

/**
 * Simple undo/redo manager for the tree editor.
 *
 *
 * @author Jansuch Rentenatus
 */
public class JackUndoManagerModel extends TreeEditorAbstract {

    private final WeakReference<JackTreeModel> weakTreeModel;

    public JackUndoManagerModel(JackTreeModel treeModel) {
        super(new HistoryManager(treeModel.getEditTree()));
        this.weakTreeModel = new WeakReference<>(treeModel);
    }

    @Override
    public boolean hasTreeModel() {
        return weakTreeModel.get() != null;
    }

    @Override
    public boolean missTreeModel() {
        return weakTreeModel.get() == null;
    }

    public JackTreeModel getTreeModel() {
        return weakTreeModel.get();
    }

    boolean containsHistory(HistoryManager source) {
        return historyManager == source;
    }

}
