/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright> 
 */
package de.jare.tree.control.commands;

import de.jare.tree.control.TreeNodeUtils;
import de.jare.jsoncasted.editor.core.EditNode;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;

/**
 * Abstract base class for node movement commands (add, delete, move).
 * This class provides the foundation for commands that manipulate the tree structure.
 * 
 * <p>This class now supports integration with the editor's EditCommand system
 * through the EditCommandAdapter pattern.</p>
 */
public abstract class AbstractNodeMovementCommand implements WoodCommand {

    /**
     * Entry representing a node movement operation.
     * Contains the parent ID, index, and snapshot of the node to be moved.
     */
    protected static class MovementEntry {

        final long parentEditId;              // editId of the parent node
        final int index;                      // original index at parent
        final DefaultMutableTreeNode snapshot; // Snapshot of the subtree

        MovementEntry(long parentEditId, int index, DefaultMutableTreeNode snapshot) {
            this.parentEditId = parentEditId;
            this.index = index;
            this.snapshot = snapshot;
        }
    }
    
    private String status;
    private boolean skipped;
    protected String commandText;

    public AbstractNodeMovementCommand() {
        this.status = STATUS_ACTION_DONE;
        this.skipped = false;
    }

    @Override
    public String getStatus() {
        return status;
    }

    public void setCommandText(String commandText) {
        this.commandText = commandText;
    }

    public AbstractNodeMovementCommand resetCommandText(String commandText) {
        this.commandText = commandText;
        return this;
    }

    @Override
    public String getCommandText() {
        return commandText;
    }

    /**
     * Checks if nodes need to have their edit IDs regenerated due to duplicates.
     */
    protected void checkAddNodes(TreeModel model, MovementEntry[] entries, String newStatus) {
        DefaultTreeModel dtm = asDefaultModel(model);
        if (dtm == null) {
            return;
        }
        Object root = dtm.getRoot();
        if (!(root instanceof DefaultMutableTreeNode rootNode)) {
            return;
        }

        this.status = newStatus;
        boolean warwas = false;
        for (MovementEntry e : entries) {
            warwas = fixSnapshotEditIds(rootNode, e.snapshot) || warwas;
        }
        if (warwas) {
            this.status = newStatus + " (nodedata dupplicated)";

        } else {
            this.status = newStatus;
        }
    }

    /**
     * Fixes duplicate edit IDs in snapshots by regenerating them.
     */
    private boolean fixSnapshotEditIds(DefaultMutableTreeNode root,
            DefaultMutableTreeNode snapNode) {
        boolean warwas = false;
        Object uo = snapNode.getUserObject();
        if (uo instanceof EditNode data) {
            long id = data.getEditId();
            DefaultMutableTreeNode existing = TreeNodeUtils.findNodeByEditId(root, id);
            if (existing != null) {
                // editId collides -> regenerate
                EditNode newData = data.deepCopy(true); // regenerateEditId = true
                snapNode.setUserObject(newData);
                warwas = true;
            }
        }
        for (int i = 0; i < snapNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) snapNode.getChildAt(i);
            warwas = fixSnapshotEditIds(root, child) || warwas;
        }
        return warwas;
    }

    /**
     * Adds nodes to the tree model from the given entries.
     */
    public void addNodes(TreeModel model, MovementEntry[] entries, String newStatus) {
        DefaultTreeModel dtm = asDefaultModel(model);
        if (dtm == null) {
            return;
        }
        int done = 0;
        int failed = 0;
        for (MovementEntry e : entries) {
            DefaultMutableTreeNode parent = TreeNodeUtils.findNodeByEditId(model, e.parentEditId);
            if (parent == null) {
                failed++;
                continue;
            }

            int insertIndex = e.index;
            if (insertIndex < 0 || insertIndex > parent.getChildCount()) {
                insertIndex = parent.getChildCount();
            }

            DefaultMutableTreeNode copy = TreeNodeUtils.deepCopy(e.snapshot);
            dtm.insertNodeInto(copy, parent, insertIndex);
            done++;
        }

        if (failed == 0) {
            this.status = newStatus;
        } else if (done == 0) {
            this.status = "Failed: node not found";
        } else {
            this.status = done + " done, " + failed + " failed: node not found";
        }
    }

    /**
     * Deletes nodes from the tree model based on the given entries.
     */
    public void deleteNodes(TreeModel model, MovementEntry[] entries, String newStatus) {
        DefaultTreeModel dtm = asDefaultModel(model);
        if (dtm == null) {
            return;
        }

        int done = 0;
        int noParent = 0;
        int noChild = 0;
        int failed = 0;
        for (int i = entries.length - 1; i >= 0; i--) {
            MovementEntry e = entries[i];
            DefaultMutableTreeNode parent = TreeNodeUtils.findNodeByEditId(model, e.parentEditId);
            if (parent == null) {
                noParent++;
                failed++;
                continue;
            }

            Object snapUo = e.snapshot.getUserObject();
            if (!(snapUo instanceof EditNode snapData)) {
                failed++;
                continue;
            }
            long snapEditId = snapData.getEditId();

            DefaultMutableTreeNode toRemove = null;
            for (int c = 0; c < parent.getChildCount(); c++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(c);
                Object uo = child.getUserObject();
                if (uo instanceof EditNode data && data.getEditId() == snapEditId) {
                    toRemove = child;
                    break;
                }
            }
            if (toRemove != null) {
                dtm.removeNodeFromParent(toRemove);
                done++;
            } else {
                noChild++;
                failed++;
            }
        }

        if (failed == 0) {
            this.status = newStatus;
        } else if (done == 0) {
            this.status = "Failed: " + noParent + " parent not found, " + noChild + " node not found";
        } else {
            this.status = done + " done, " + failed + " failed: node or parent not found";
        }
    }

    /**
     * Checks and corrects node positions after undo/redo.
     */
    protected void checkNodesPos(TreeModel model, MovementEntry[] entries, String statusLabel) {
        DefaultTreeModel dtm = asDefaultModel(model);
        if (dtm == null) {
            return;
        }

        boolean anyReordered = false;
        boolean anyError = false;

        for (MovementEntry e : entries) {
            DefaultMutableTreeNode parent = TreeNodeUtils.findNodeByEditId(model, e.parentEditId);
            if (parent == null) {
                anyError = true;
                continue;
            }
            Object snapUo = e.snapshot.getUserObject();
            if (!(snapUo instanceof EditNode snapData)) {
                anyError = true;
                continue;
            }
            long snapEditId = snapData.getEditId();

            DefaultMutableTreeNode found = null;
            int foundIdx = -1;
            for (int i = 0; i < parent.getChildCount(); i++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
                Object uo = child.getUserObject();
                if (uo instanceof EditNode data && data.getEditId() == snapEditId) {
                    found = child;
                    foundIdx = i;
                    break;
                }
            }

            if (found == null) {
                anyError = true;
                continue;
            }

            if (foundIdx != e.index) {
                // Node needs to be repositioned
                parent.remove(foundIdx);
                int target = e.index;
                if (target < 0 || target > parent.getChildCount()) {
                    target = parent.getChildCount();
                }
                parent.insert(found, target);
                anyReordered = true;
            }
        }

        if (anyError) {
            this.status = statusLabel + " (pos error)";
        } else if (anyReordered) {
            this.status = statusLabel + " (repositioned)";
        } else {
            this.status = statusLabel;
        }
    }

    /**
     * Converts TreeModel to DefaultTreeModel if possible.
     */
    protected DefaultTreeModel asDefaultModel(TreeModel model) {
        return (model instanceof DefaultTreeModel dtm) ? dtm : null;
    }

    @Override
    public void execute(TreeModel model) {
        executeMovement(model);
    }

    @Override
    public void undo(TreeModel model) {
        if (skipped) {
            this.status = "";
            this.skipped = false;
            return;
        }
        undoMovement(model);
    }

    abstract void executeMovement(TreeModel model);

    abstract void undoMovement(TreeModel model);

    @Override
    public void skip(TreeModel model) {
        this.status = STATUS_SKIPPED;
        this.skipped = true;
    }
}
