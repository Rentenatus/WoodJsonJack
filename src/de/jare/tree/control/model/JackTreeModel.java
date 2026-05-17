package de.jare.tree.control.model;

import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.UpdateAction;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.events.HistoryEvent;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

public class JackTreeModel extends DefaultTreeModel {

    private final EditTree editTree;

    public JackTreeModel(String rootName) {
        this(new EditTree(new EditNodeObject("{" + rootName + "}")));
    }

    public JackTreeModel(EditTree editTree) {
        super(buildRoot(editTree, new IdentityHashMap<>()));
        this.editTree = editTree;
    }

    public EditTree getEditTree() {
        return editTree;
    }

    public DefaultMutableTreeNode findNodeById(long id) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) getRoot();
        if (root == null) {
            return null;
        }

        java.util.ArrayDeque<DefaultMutableTreeNode> stack = new java.util.ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            DefaultMutableTreeNode node = stack.pop();

            EditNode edNode = (EditNode) node.getUserObject();
            if (edNode == null) {
                continue;
            }

            if (edNode.getEditId() == id) {
                return node;
            }

            for (int i = node.getChildCount() - 1; i >= 0; i--) {
                TreeNode child = node.getChildAt(i);
                if (child != null) {
                    stack.push((DefaultMutableTreeNode) child);
                }
            }
        }

        return null;
    }

    public void onHistoryEvent(HistoryEvent historyEvent) {
        if (historyEvent == null || historyEvent.getResult() == null) {
            return;
        }

        Runnable uiTask = () -> applyResult(historyEvent.getResult());
        SwingUtilities.invokeLater(uiTask);
    }

    private void applyResult(CommandResult result) {
        boolean fallbackReload = false;

        for (UpdateAction update : result.getUpdateActions()) {
            switch (update) {
                case REBUILD_AFFECTED -> {
                    fallbackReload = handleRebuildAffected(result);
                    break;
                }
                case SELECT_UPDATED -> {
                    // Selection handling is managed by the view, so we can ignore this action in the model.
                    break;
                }
                default -> {
                    // Unknown update action, consider fallback reload to ensure consistency.
                    fallbackReload = true;
                }
            }
            if (fallbackReload) {
                break;
            }
        }

        if (fallbackReload) {
            rebuildFromDomain();
        }

    }

    private boolean handleRebuildAffected(CommandResult result) {
        boolean fallbackReload = false;
        for (EditNode editNode : result.getUpdatedNodes()) {
            fallbackReload |= !handleRebuildNode(editNode);
        }
        return fallbackReload;
    }

    private boolean handleRebuildNode(EditNode editNode) {
        if (editNode == null) {
            return false;
        }
        DefaultMutableTreeNode mutableTreeNode = findNodeById(editNode.getEditId());
        if (mutableTreeNode == null) {
            return true;
        }
        mutableTreeNode.removeAllChildren();
        buildSubtree(mutableTreeNode, editNode);
        this.nodeStructureChanged(mutableTreeNode);
        return false;
    }

    private DefaultMutableTreeNode buildSubtree(DefaultMutableTreeNode rootNode, EditNode editNode) {
        rootNode.setUserObject(editNode);

        int childCount = getChildCountSafe(editNode);
        for (int i = 0; i < childCount; i++) {
            EditNode childEditNode = getChildSafe(editNode, i);
            if (childEditNode != null) {
                DefaultMutableTreeNode newChildSwingNode = buildSubtree(childEditNode);
                rootNode.add(newChildSwingNode);
            }
        }
        return rootNode;
    }

    private static DefaultMutableTreeNode buildRoot(EditTree editTree, Map<EditNode, DefaultMutableTreeNode> ignored) {
        return buildSubtree(editTree.getRoot());
    }

    private static DefaultMutableTreeNode buildSubtree(EditNode editNode) {
        DefaultMutableTreeNode swingNode = new DefaultMutableTreeNode(editNode);

        int childCount = getChildCountSafe(editNode);
        for (int i = 0; i < childCount; i++) {
            EditNode child = getChildSafe(editNode, i);
            if (child != null) {
                swingNode.add(buildSubtree(child));
            }
        }
        return swingNode;
    }

//            for (EditNode node : result.getRemovedNodes()) {
//                fallbackReload |= !handleRemovedNode(node);
//            }
//
//            for (EditNode node : result.getAddedNodes()) {
//                fallbackReload |= !handleAddedNode(node);
//            }
//
//            for (EditNode node : result.getUpdatedNodes()) {
//                fallbackReload |= !handleUpdatedNode(node);
//            }
    private boolean handleUpdatedNode(EditNode editNode) {
        if (editNode == null) {
            return false;
        }

        DefaultMutableTreeNode swingNode = findNodeById(editNode.getEditId());
        if (swingNode == null) {
            return false;
        }

        swingNode.setUserObject(editNode);
        nodeChanged(swingNode);
        return true;
    }

    private boolean handleAddedNode(EditNode editNode) {
        if (editNode == null) {
            return false;
        }

        DefaultMutableTreeNode swingNode = findNodeById(editNode.getEditId());
        if (swingNode != null) {
            return true;
        }

        EditNode parentEditNode = editNode.getParent();
        if (parentEditNode == null) {
            return false;
        }

        DefaultMutableTreeNode parentSwingNode = findNodeById(parentEditNode.getEditId());
        if (parentSwingNode == null) {
            return false;
        }

        DefaultMutableTreeNode newSwingNode = buildSubtree(editNode);
        int index = resolveChildIndex(parentEditNode, editNode);
        if (index < 0 || index > parentSwingNode.getChildCount()) {
            index = parentSwingNode.getChildCount();
        }

        insertNodeInto(newSwingNode, parentSwingNode, index);
        return true;
    }

    private boolean handleRemovedNode(EditNode editNode) {
        if (editNode == null) {
            return false;
        }

        DefaultMutableTreeNode swingNode = findNodeById(editNode.getEditId());
        if (swingNode == null) {
            return false;
        }

        removeSubtreeFromMap(swingNode);

        if (swingNode.getParent() != null) {
            removeNodeFromParent(swingNode);
        } else {
            return false;
        }

        return true;
    }

    private void rebuildFromDomain() {
        DefaultMutableTreeNode newRoot = buildSubtree(editTree.getRoot());
        setRoot(newRoot);
        reload();
    }

    private void rebuildNodeMapRecursive(DefaultMutableTreeNode swingNode) {
        if (swingNode == null) {
            return;
        }

        for (int i = 0; i < swingNode.getChildCount(); i++) {
            rebuildNodeMapRecursive((DefaultMutableTreeNode) swingNode.getChildAt(i));
        }
    }

    private void removeSubtreeFromMap(DefaultMutableTreeNode swingNode) {

        for (int i = 0; i < swingNode.getChildCount(); i++) {
            removeSubtreeFromMap((DefaultMutableTreeNode) swingNode.getChildAt(i));
        }
    }

    private int resolveChildIndex(EditNode parent, EditNode child) {
        int count = getChildCountSafe(parent);
        for (int i = 0; i < count; i++) {
            EditNode current = getChildSafe(parent, i);
            if (current == child) {
                return i;
            }
        }
        return -1;
    }

    private static int getChildCountSafe(EditNode node) {
        try {
            return node.getChildCount();
        } catch (Exception ex) {
            return 0;
        }
    }

    private static EditNode getChildSafe(EditNode node, int index) {
        try {
            return node.getChildAt(index);
        } catch (Exception ex) {
            return null;
        }
    }

}
