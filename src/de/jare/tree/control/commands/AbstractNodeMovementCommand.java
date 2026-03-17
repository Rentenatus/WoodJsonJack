package de.jare.tree.control.commands;

import de.jare.tree.data.JsonTreeNodeData;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;

public abstract class AbstractNodeMovementCommand implements WoodCommand {

    protected static class Entry {

        final long parentEditId;              // editId des Elternknotens
        final int index;                      // ursprünglicher Index beim Parent
        final DefaultMutableTreeNode snapshot; // Snapshot des gelöschten Teilbaums

        Entry(long parentEditId, int index, DefaultMutableTreeNode snapshot) {
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

    public void addNodes(TreeModel model, Entry[] entries, String newStatus) {
        DefaultTreeModel dtm = asDefaultModel(model);
        if (dtm == null) {
            return;
        }
        int done = 0;
        int failed = 0;
        for (Entry e : entries) {
            DefaultMutableTreeNode parent = findNodeByEditId(model, e.parentEditId);
            if (parent == null) {
                failed++;
                continue;
            }

            int insertIndex = e.index;
            if (insertIndex < 0 || insertIndex > parent.getChildCount()) {
                insertIndex = parent.getChildCount();
            }

            DefaultMutableTreeNode copy = deepCopy(e.snapshot);
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

    public void deleteNodes(TreeModel model, Entry[] entries, String newStatus) {
        DefaultTreeModel dtm = asDefaultModel(model);
        if (dtm == null) {
            return;
        }

        int done = 0;
        int noParent = 0;
        int noChild = 0;
        int failed = 0;
        for (int i = entries.length - 1; i >= 0; i--) {
            Entry e = entries[i];
            DefaultMutableTreeNode parent = findNodeByEditId(model, e.parentEditId);
            if (parent == null) {
                noParent++;
                failed++;
                continue;
            }

            Object snapUo = e.snapshot.getUserObject();
            if (!(snapUo instanceof JsonTreeNodeData snapData)) {
                failed++;
                continue;
            }
            long snapEditId = snapData.getEditId();

            DefaultMutableTreeNode toRemove = null;
            for (int c = 0; c < parent.getChildCount(); c++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(c);
                Object uo = child.getUserObject();
                if (uo instanceof JsonTreeNodeData data && data.getEditId() == snapEditId) {
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

    protected void checkNodesPos(TreeModel model, Entry[] entries, String statusLabel) {
        DefaultTreeModel dtm = asDefaultModel(model);
        if (dtm == null) {
            return;
        }

        boolean anyReordered = false;
        boolean anyError = false;

        for (Entry e : entries) {
            DefaultMutableTreeNode parent = findNodeByEditId(model, e.parentEditId);
            if (parent == null) {
                anyError = true;
                continue;
            }
            Object snapUo = e.snapshot.getUserObject();
            if (!(snapUo instanceof JsonTreeNodeData snapData)) {
                anyError = true;
                continue;
            }
            long snapEditId = snapData.getEditId();

            DefaultMutableTreeNode found = null;
            int foundIdx = -1;
            for (int i = 0; i < parent.getChildCount(); i++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
                Object uo = child.getUserObject();
                if (uo instanceof JsonTreeNodeData data && data.getEditId() == snapEditId) {
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
                //System.out.println(foundIdx + " != " + e.index);
                anyReordered = true;
                // Node an Zielposition verschieben
                parent.remove(foundIdx);
                // entfernt, Kinder bleiben intakt
                int target = e.index;
                if (target < 0 || target > parent.getChildCount()) {
                    target = parent.getChildCount();
                }
                parent.insert(found, target);
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
