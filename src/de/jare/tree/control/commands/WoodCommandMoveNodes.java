package de.jare.tree.control.commands;

import de.jare.tree.data.JsonTreeNodeData;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;

/**
 * Command that moves one or more nodes (and their subtrees) to a new parent.
 * <p>
 * Internally represented as a delete followed by an add with snapshots, so that
 * execute = delete + add, undo = delete(add-part) + add(delete-part).
 * </p>
 */
public class WoodCommandMoveNodes extends AbstractNodeMovementCommand {

    private final Entry[] deleteEntries;
    private final Entry[] addEntries;
    private final String description;

    /**
     * @param nodesToMove Knoten, die verschoben werden sollen (aktueller
     * Baumzustand)
     * @param parentNodes jeweilige Quell-Eltern der nodesToMove
     * @param trgNode Ziel-Elternknoten, unter den eingef?gt werden soll
     * @param startIdx Startindex beim Ziel (Index des ersten verschobenen
     * Kindes), -1 = am Ende anh?ngen
     */
    public WoodCommandMoveNodes(
            DefaultMutableTreeNode[] nodesToMove,
            DefaultMutableTreeNode[] parentNodes,
            DefaultMutableTreeNode trgNode,
            int startIdx) {
        this.commandText = "Move nodes";

        if (nodesToMove == null || parentNodes == null || nodesToMove.length == 0) {
            throw new IllegalArgumentException("parentNodes and nodesToMove must not be null/empty");
        }
        if (nodesToMove.length != parentNodes.length) {
            throw new IllegalArgumentException("nodesToMove and parentNodes length mismatch");
        }
        if (trgNode == null) {
            throw new IllegalArgumentException("trgNode must not be null");
        }

        Object trgData = trgNode.getUserObject();
        if (!(trgData instanceof JsonTreeNodeData trgJson)) {
            throw new IllegalArgumentException("trgNode userObject must be JsonTreeNodeData");
        }
        long trgParentEditId = trgJson.getEditId();

        int length = nodesToMove.length;
        this.deleteEntries = new Entry[length];
        this.addEntries = new Entry[length];

        // Ziel-Indizes fortlaufend ab startIdx; -1 => beim Einf?gen ans Ende
        int lastTrgIdx = startIdx;

        for (int i = 0; i < length; i++) {
            DefaultMutableTreeNode n = nodesToMove[i];
            DefaultMutableTreeNode p = parentNodes[i];

            if (n == null) {
                throw new IllegalArgumentException("nodesToMove[" + i + "] must not be null");
            }
            if (p == null) {
                throw new IllegalArgumentException("parentNodes[" + i + "] must not be null");
            }

            Object pData = p.getUserObject();
            if (!(pData instanceof JsonTreeNodeData srcJson)) {
                throw new IllegalArgumentException("parentNodes[" + i + "] userObject must be JsonTreeNodeData");
            }

            int srcIdx = p.getIndex(n);
            if (srcIdx < 0) {
                throw new IllegalArgumentException("nodesToMove[" + i + "] is not a child of parentNodes[" + i + "]");
            }
            //System.out.println(p + "." + n + " ::: " + srcIdx);

            // Delete-Entry: vom urspruenglichen Parent/Index entfernen
            deleteEntries[i] = new Entry(srcJson.getEditId(), srcIdx, deepCopy(n));

            // Add-Entry: unter Ziel-Parent einf?gen
            int trgIdx = lastTrgIdx;
            if (trgIdx >= 0) {
                trgIdx = lastTrgIdx;
                lastTrgIdx++;
            } else {
                trgIdx = -1; // beim Einf?gen ans Ende anh?ngen
            }
            addEntries[i] = new Entry(trgParentEditId, trgIdx, deepCopy(n));
        }

        if (nodesToMove.length == 1) {
            this.description = "Move '" + nodesToMove[0].getUserObject() + "'";
        } else {
            this.description = "Move " + nodesToMove.length + " nodes";
        }
    }

    @Override
    public void executeMovement(TreeModel model) {
        // Move = Delete (Quelle) + Add (Ziel)
        deleteNodes(model, deleteEntries, STATUS_REDO_DONE);
        addNodes(model, addEntries, getStatus());
    }

    @Override
    public void undoMovement(TreeModel model) {
        // Undo(Move) = Delete (Ziel) + Add (Quelle)
        deleteNodes(model, addEntries, STATUS_REVERTED);
        addNodes(model, deleteEntries, getStatus());
        checkNodesPos(model, deleteEntries, getStatus());
    }

    @Override
    public String getDescription() {
        return description;
    }

}
