/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.command.CommandAvailability;
import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.MoveNodeCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.tree.control.JackUndoManager;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

class JackTreeNodeTransferHandler extends TransferHandler {

    private final DataFlavor nodesFlavor;
    private final JackUndoManager undoMan;
    private boolean readonly = false;

    JackTreeNodeTransferHandler(JackUndoManager undoMan) {
        this.undoMan = undoMan;
        try {
            String mimeType = DataFlavor.javaJVMLocalObjectMimeType
                    + ";class=\"" + DefaultMutableTreeNode[].class.getName() + "\"";
            this.nodesFlavor = new DataFlavor(mimeType);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot initialize node transfer flavor", e);
        }
    }

    @Override
    public int getSourceActions(JComponent c) {
        if (readonly) {
            return NONE;
        }
        return MOVE;
    }

    public void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }

    public boolean isReadonly() {
        return readonly;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        if (!(c instanceof JTree tree)) {
            return null;
        }

        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null || paths.length == 0) {
            return null;
        }

        DefaultMutableTreeNode[] selectedNodes = Arrays.stream(paths)
                .map(path -> (DefaultMutableTreeNode) path.getLastPathComponent())
                .toArray(DefaultMutableTreeNode[]::new);

        DefaultMutableTreeNode[] topLevelNodes = reduceToTopLevelSelections(selectedNodes);
        if (topLevelNodes.length == 0) {
            return null;
        }

        return new NodesTransferable(topLevelNodes);
    }

    @Override
    public boolean canImport(TransferSupport support) {
        if (readonly) {
            return false;
        }
        if (!support.isDrop() || !support.isDataFlavorSupported(nodesFlavor)) {
            return false;
        }
        if (!(support.getComponent() instanceof JTree)) {
            return false;
        }

        JTree.DropLocation dropLocation = (JTree.DropLocation) support.getDropLocation();
        TreePath destinationPath = dropLocation.getPath();
        if (destinationPath == null) {
            return false;
        }

        DefaultMutableTreeNode targetTreeNode
                = (DefaultMutableTreeNode) destinationPath.getLastPathComponent();
        EditNode targetEditNode = toEditNode(targetTreeNode);
        if (targetEditNode == null) {
            return false;
        }

        try {
            DefaultMutableTreeNode[] draggedTreeNodes
                    = (DefaultMutableTreeNode[]) support.getTransferable().getTransferData(nodesFlavor);

            DefaultMutableTreeNode[] topLevelDraggedTreeNodes
                    = reduceToTopLevelSelections(draggedTreeNodes);
            if (topLevelDraggedTreeNodes.length == 0) {
                return false;
            }

            EditNode[] draggedEditNodes = toEditNodes(topLevelDraggedTreeNodes);
            if (draggedEditNodes == null || draggedEditNodes.length == 0) {
                return false;
            }

            for (DefaultMutableTreeNode draggedNode : topLevelDraggedTreeNodes) {
                if (draggedNode == targetTreeNode || isDescendant(targetTreeNode, draggedNode)) {
                    return false;
                }
            }

            for (EditNode draggedEditNode : draggedEditNodes) {
                if (!draggedEditNode.canBeChildOf(targetEditNode)) {
                    return false;
                }
            }

            return true;
        } catch (UnsupportedFlavorException | IOException e) {
            return false;
        }
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) {
            return false;
        }

        if (!(support.getComponent() instanceof JTree tree)) {
            return false;
        }

        if (undoMan == null) {
            return false;
        }

        JTree.DropLocation dropLocation = (JTree.DropLocation) support.getDropLocation();
        TreePath destinationPath = dropLocation.getPath();
        if (destinationPath == null) {
            return false;
        }

        DefaultMutableTreeNode targetTreeNode
                = (DefaultMutableTreeNode) destinationPath.getLastPathComponent();
        EditNodeAbstract targetParent = toEditNode(targetTreeNode);
        if (targetParent == null) {
            return false;
        }

        int childIndex = dropLocation.getChildIndex();
        int targetIndex = childIndex < 0 ? -1 : childIndex;

        try {
            DefaultMutableTreeNode[] draggedTreeNodes
                    = (DefaultMutableTreeNode[]) support.getTransferable().getTransferData(nodesFlavor);

            DefaultMutableTreeNode[] topLevelDraggedTreeNodes
                    = reduceToTopLevelSelections(draggedTreeNodes);
            if (topLevelDraggedTreeNodes.length == 0) {
                return false;
            }

            EditNodeAbstract[] draggedEditNodes = toEditNodes(topLevelDraggedTreeNodes);
            if (draggedEditNodes == null || draggedEditNodes.length == 0) {
                return false;
            }

            EditNodeAbstract[] normalizedNodes = normalizeForMove(draggedEditNodes);

            MoveNodeCommand command
                    = new MoveNodeCommand(normalizedNodes, targetParent, targetIndex);

            CommandAvailability check = command.check(undoMan.getActiveManager().getTreeModel().getEditTree());
            if (check.isUseless()) {
                return false;
            }
            if (check.isDisallowed()) {
                return false;
            }

            CommandResult result = undoMan.executeCommand(command);
            return result != null;
        } catch (UnsupportedFlavorException | IOException e) {
            return false;
        }
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
        // Keine direkte Mutation mehr im Swing-Handler.
        // Der Move wird vollständig durch MoveNodeCommand ausgeführt.
    }

    /**
     * Reduziert eine Selektion auf ihre Top-Level-Knoten.
     *
     * <p>
     * Wenn sowohl ein Parent als auch dessen Nachfahre selektiert sind, bleibt
     * nur der Parent erhalten. Dadurch wird verhindert, dass bei einem Move
     * dieselbe Teilbaumstruktur mehrfach oder widersprüchlich verarbeitet wird.
     * </p>
     *
     * @param selectedNodes selektierte Tree-Nodes
     * @return nur die obersten selektierten Knoten
     */
    private static DefaultMutableTreeNode[] reduceToTopLevelSelections(
            DefaultMutableTreeNode[] selectedNodes) {

        if (selectedNodes == null || selectedNodes.length == 0) {
            return new DefaultMutableTreeNode[0];
        }

        Set<DefaultMutableTreeNode> selectedSet
                = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        selectedSet.addAll(Arrays.asList(selectedNodes));

        List<DefaultMutableTreeNode> result = new ArrayList<>();
        for (DefaultMutableTreeNode node : selectedNodes) {
            if (node == null) {
                continue;
            }
            if (!hasSelectedAncestor(node, selectedSet)) {
                result.add(node);
            }
        }

        return result.toArray(DefaultMutableTreeNode[]::new);
    }

    /**
     * Prüft, ob ein selektierter Vorfahr existiert.
     *
     * @param node der zu prüfende Knoten
     * @param selectedSet Menge aller selektierten Knoten
     * @return true, wenn ein Vorfahr ebenfalls selektiert ist
     */
    private static boolean hasSelectedAncestor(
            DefaultMutableTreeNode node,
            Set<DefaultMutableTreeNode> selectedSet) {

        TreeNode current = node.getParent();
        while (current instanceof DefaultMutableTreeNode parent) {
            if (selectedSet.contains(parent)) {
                return true;
            }
            current = parent.getParent();
        }
        return false;
    }

    private static EditNodeAbstract[] toEditNodes(DefaultMutableTreeNode[] treeNodes) {
        EditNodeAbstract[] result = new EditNodeAbstract[treeNodes.length];
        for (int i = 0; i < treeNodes.length; i++) {
            EditNodeAbstract node = toEditNode(treeNodes[i]);
            if (node == null) {
                return null;
            }
            result[i] = node;
        }
        return result;
    }

    private static EditNodeAbstract toEditNode(DefaultMutableTreeNode treeNode) {
        if (treeNode == null) {
            return null;
        }
        Object userObject = treeNode.getUserObject();
        return (userObject instanceof EditNodeAbstract editNode) ? editNode : null;
    }

    /**
     * Prüft, ob possibleAncestor ein Vorfahr von candidateTarget ist
     * (einschließlich Gleichheit).
     *
     * @param candidateTarget mögliches Ziel
     * @param possibleAncestor möglicher Vorfahr
     * @return true, wenn candidateTarget im Teilbaum von possibleAncestor liegt
     */
    private static boolean isDescendant(
            DefaultMutableTreeNode candidateTarget,
            DefaultMutableTreeNode possibleAncestor) {

        for (DefaultMutableTreeNode current = candidateTarget;
                current != null;
                current = (DefaultMutableTreeNode) current.getParent()) {
            if (current == possibleAncestor) {
                return true;
            }
        }
        return false;
    }

    /**
     * Normalisiert die Move-Reihenfolge stabil nach source-parent und
     * source-index.
     *
     * @param nodes die zu bewegenden EditNodes
     * @return sortierte Kopie
     */
    private static EditNodeAbstract[] normalizeForMove(EditNodeAbstract[] nodes) {
        EditNodeAbstract[] copy = Arrays.copyOf(nodes, nodes.length);
        Arrays.sort(copy, Comparator
                .comparingLong((EditNode n) -> n.getParent() != null ? n.getParent().getEditId() : -1L)
                .thenComparingInt(n -> n.getParent() != null ? n.getParent().getChildIndex(n) : -1));
        return copy;
    }

    private final class NodesTransferable implements Transferable {

        private final DefaultMutableTreeNode[] nodes;

        private NodesTransferable(DefaultMutableTreeNode[] nodes) {
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
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return nodes;
        }
    }
}
