package de.jare.tree.ui;

import de.jare.tree.control.MasterControl;
import de.jare.tree.control.commands.WoodCommandMoveNodes;
import de.jare.tree.data.JsonTreeNodeData;
import de.jare.tree.data.WoodUtils;

import java.awt.datatransfer.*;
import java.io.IOException;
import javax.swing.*;
import static javax.swing.TransferHandler.MOVE;
import javax.swing.tree.*;

/**
 * TransferHandler for moving tree nodes via drag & drop.
 * <p>
 * Performs a logical move by creating a WoodCommandMoveNodes and pushing it
 * into the UndoManager instead of directly modifying the model in exportDone.
 * </p>
 */
class TreeNodeTransferHandler extends TransferHandler {

    private final DataFlavor nodesFlavor;
    private DefaultMutableTreeNode[] nodesToMove;
    private DefaultMutableTreeNode[] sourceParents;

    TreeNodeTransferHandler() {
        try {
            String mimeType = DataFlavor.javaJVMLocalObjectMimeType
                    + ";class=\"" + DefaultMutableTreeNode[].class.getName() + "\"";
            nodesFlavor = new DataFlavor(mimeType);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getSourceActions(JComponent c) {
        return MOVE;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        JTree tree = (JTree) c;
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null || paths.length == 0) {
            return null;
        }
        DefaultMutableTreeNode[] nodes = new DefaultMutableTreeNode[paths.length];
        DefaultMutableTreeNode[] parents = new DefaultMutableTreeNode[paths.length];

        for (int i = 0; i < paths.length; i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) paths[i].getLastPathComponent();
            nodes[i] = node;
            parents[i] = (DefaultMutableTreeNode) node.getParent();
        }

        this.nodesToMove = WoodUtils.sortOnPos(nodes);
        this.sourceParents = parents;
        return new NodesTransferable(nodes);
    }

    @Override
    public boolean canImport(TransferSupport support) {
        if (!support.isDrop() || !support.isDataFlavorSupported(nodesFlavor)) {
            return false;
        }
        JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
        TreePath dest = dl.getPath();
        if (dest == null) {
            return false;
        }

        DefaultMutableTreeNode target = (DefaultMutableTreeNode) dest.getLastPathComponent();
        Object targetUo = target.getUserObject();
        if (!(targetUo instanceof JsonTreeNodeData targetData)) {
            return false;
        }

        try {
            DefaultMutableTreeNode[] dragged
                    = (DefaultMutableTreeNode[]) support.getTransferable().getTransferData(nodesFlavor);

            // 1. keine Zyklen
            for (DefaultMutableTreeNode node : dragged) {
                if (isNodeDescendant(target, node)) {
                    return false;
                }
            }

            // 2. JSON-Regeln: jeder Root des Teilbaums muss Kind von target sein dürfen
            for (DefaultMutableTreeNode node : dragged) {
                Object clipUo = node.getUserObject();
                if (!(clipUo instanceof JsonTreeNodeData clipData)) {
                    return false;
                }
                if (!clipData.canBeChildOf(targetData)) {
                    return false;
                }
            }
        } catch (UnsupportedFlavorException | IOException e) {
            return false;
        }
        return true;
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) {
            return false;
        }
        JTree tree = (JTree) support.getComponent();
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
        TreePath dest = dl.getPath();
        int childIndex = dl.getChildIndex();
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) dest.getLastPathComponent();

        try {
            DefaultMutableTreeNode[] nodes
                    = (DefaultMutableTreeNode[]) support.getTransferable().getTransferData(nodesFlavor);

            int index = (childIndex == -1) ? parent.getChildCount() : childIndex;
            int startIndex = index;

            // 1. Undo-Command registrieren (vor der physischen Änderung!)
            if (tree instanceof WoodEditTree editTree
                    && nodesToMove != null
                    && sourceParents != null) {
                MasterControl master = editTree.getMaster();
                if (master != null) {
                    WoodCommandMoveNodes cmd = new WoodCommandMoveNodes(
                            nodesToMove, // sortierte Originale (WoodUtils.sortOnPos)
                            sourceParents, // ursprüngliche Eltern
                            parent, // Ziel-Eltern
                            startIndex // Startindex im Ziel
                    );
                    master.getUndoManager().pushCommand(cmd);
                }
            }

            // 2. Physischer Move: neue Kopien einfügen
            DefaultMutableTreeNode lastCopy = null;
            for (DefaultMutableTreeNode node : nodes) {
                DefaultMutableTreeNode copy = deepCopy(node);
                model.insertNodeInto(copy, parent, index++);
                lastCopy = copy;
            }

            // 3. Auswahl setzen
            if (lastCopy != null) {
                TreePath newPath = new TreePath(lastCopy.getPath());
                tree.setSelectionPath(newPath);
                tree.scrollPathToVisible(newPath);
            }

        } catch (UnsupportedFlavorException | IOException e) {
            return false;
        }
        return true;
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
        if (action != MOVE || nodesToMove == null) {
            return;
        }
        JTree tree = (JTree) source;
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();

        // Originale an alter Stelle entfernen (von unten nach oben, um Indizes stabil zu halten)
        for (int i = nodesToMove.length - 1; i >= 0; i--) {
            DefaultMutableTreeNode node = nodesToMove[i];
            MutableTreeNode parent = (MutableTreeNode) node.getParent();
            if (parent != null) {
                model.removeNodeFromParent(node);
            }
        }
        nodesToMove = null;
    }

    private boolean isNodeDescendant(DefaultMutableTreeNode target, DefaultMutableTreeNode ancestor) {
        if (target == ancestor) {
            return true;
        }
        for (TreeNode n = target.getParent(); n != null; n = n.getParent()) {
            if (n == ancestor) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tiefkopie eines Knotens inkl. seiner Kinder (ohne editId-Änderung).
     */
    private DefaultMutableTreeNode deepCopy(DefaultMutableTreeNode original) {
        DefaultMutableTreeNode copy = new DefaultMutableTreeNode(original.getUserObject());
        for (int i = 0; i < original.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) original.getChildAt(i);
            copy.add(deepCopy(child));
        }
        return copy;
    }

    private class NodesTransferable implements Transferable {

        private final DefaultMutableTreeNode[] nodes;

        NodesTransferable(DefaultMutableTreeNode[] nodes) {
            this.nodes = nodes;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{nodesFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return nodesFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor)
                throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return nodes;
        }
    }
}
