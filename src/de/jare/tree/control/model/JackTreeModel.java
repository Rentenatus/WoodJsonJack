package de.jare.tree.control.model;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

public class JackTreeModel extends DefaultTreeModel {

    private final EditTree editTree;

    public JackTreeModel(String rootName) {
        this(new EditTree("{" + rootName + "}"));
    }

    public JackTreeModel(EditTree editTree) {
        super(buildRoot(editTree));
        this.editTree = editTree;
    }

    public EditTree getEditTree() {
        return editTree;
    }

    public DefaultMutableTreeNode findNodeById(long id) {
        DefaultMutableTreeNode DMroot = (DefaultMutableTreeNode) getRoot();
        if (DMroot == null) {
            return null;
        }

        java.util.ArrayDeque<DefaultMutableTreeNode> stack = new java.util.ArrayDeque<>();
        stack.push(DMroot);

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

    public void buildSubtreeStructureChanged(DefaultMutableTreeNode mutableTreeNode, EditNode editNode) {
        buildSubtree(mutableTreeNode, editNode);
        nodeStructureChanged(mutableTreeNode);
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

    private static DefaultMutableTreeNode buildRoot(EditTree editTree) {
        return buildSubtree(editTree.getRoot());
    }

    public static DefaultMutableTreeNode buildSubtree(EditNode editNode) {
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

    public void rebuildFromDomain() {
        DefaultMutableTreeNode newRoot = buildSubtree(editTree.getRoot());
        setRoot(newRoot);
        reload();
    }

    public int resolveChildIndex(EditNode parent, EditNode child) {
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
