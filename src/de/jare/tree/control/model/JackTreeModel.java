package de.jare.tree.control.model;

import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.history.HistoryEvent;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

public class JackTreeModel extends DefaultTreeModel {

    private final EditTree editTree;
    private final Map<EditNode, DefaultMutableTreeNode> nodeMap = new IdentityHashMap<>();

    public JackTreeModel(String rootName) {
        this(new EditTree(new EditNodeObject("{" + rootName + "}")));
    }

    public JackTreeModel(EditTree editTree) {
        super(buildRoot(editTree, new IdentityHashMap<>()));
        this.editTree = editTree;
        rebuildNodeMap();
    }

    public EditTree getEditTree() {
        return editTree;
    }

    public void onHistoryEvent(HistoryEvent historyEvent) {
        if (historyEvent == null || historyEvent.getResult() == null) {
            return;
        }

        Runnable uiTask = () -> applyResult(historyEvent.getResult());
        if (SwingUtilities.isEventDispatchThread()) {
            uiTask.run();
        } else {
            SwingUtilities.invokeLater(uiTask);
        }
    }

    private void applyResult(CommandResult result) {
        boolean fallbackReload = false;

        for (EditNode node : result.getRemovedNodes()) {
            fallbackReload |= !handleRemovedNode(node);
        }

        for (EditNode node : result.getAddedNodes()) {
            fallbackReload |= !handleAddedNode(node);
        }

        for (EditNode node : result.getUpdatedNodes()) {
            fallbackReload |= !handleUpdatedNode(node);
        }

        if (fallbackReload) {
            rebuildFromDomain();
        }
    }

    private boolean handleUpdatedNode(EditNode editNode) {
        if (editNode == null) {
            return false;
        }

        DefaultMutableTreeNode swingNode = nodeMap.get(editNode);
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

        if (nodeMap.containsKey(editNode)) {
            return true;
        }

        EditNode parentEditNode = editNode.getParent();
        if (parentEditNode == null) {
            return false;
        }

        DefaultMutableTreeNode parentSwingNode = nodeMap.get(parentEditNode);
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

        DefaultMutableTreeNode swingNode = nodeMap.remove(editNode);
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
        nodeMap.clear();
        DefaultMutableTreeNode newRoot = buildSubtree(editTree.getRoot());
        setRoot(newRoot);
        reload();
    }

    private void rebuildNodeMap() {
        nodeMap.clear();
        DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) getRoot();
        rebuildNodeMapRecursive(rootNode);
    }

    private void rebuildNodeMapRecursive(DefaultMutableTreeNode swingNode) {
        if (swingNode == null) {
            return;
        }

        Object userObject = swingNode.getUserObject();
        if (userObject instanceof EditNode editNode) {
            nodeMap.put(editNode, swingNode);
        }

        for (int i = 0; i < swingNode.getChildCount(); i++) {
            rebuildNodeMapRecursive((DefaultMutableTreeNode) swingNode.getChildAt(i));
        }
    }

    private void removeSubtreeFromMap(DefaultMutableTreeNode swingNode) {
        Object userObject = swingNode.getUserObject();
        if (userObject instanceof EditNode editNode) {
            nodeMap.remove(editNode);
        }

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

    private static DefaultMutableTreeNode buildRoot(EditTree editTree, Map<EditNode, DefaultMutableTreeNode> ignored) {
        return buildStaticSubtree(editTree.getRoot());
    }

    private DefaultMutableTreeNode buildSubtree(EditNode editNode) {
        DefaultMutableTreeNode swingNode = new DefaultMutableTreeNode(editNode);
        nodeMap.put(editNode, swingNode);

        int childCount = getChildCountSafe(editNode);
        for (int i = 0; i < childCount; i++) {
            EditNode child = getChildSafe(editNode, i);
            if (child != null) {
                swingNode.add(buildSubtree(child));
            }
        }
        return swingNode;
    }

    private static DefaultMutableTreeNode buildStaticSubtree(EditNode editNode) {
        DefaultMutableTreeNode swingNode = new DefaultMutableTreeNode(editNode);

        int childCount = getChildCountSafe(editNode);
        for (int i = 0; i < childCount; i++) {
            EditNode child = getChildSafe(editNode, i);
            if (child != null) {
                swingNode.add(buildStaticSubtree(child));
            }
        }
        return swingNode;
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