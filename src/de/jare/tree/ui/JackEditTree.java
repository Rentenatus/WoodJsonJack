/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.clipboard.ClipboardManager;
import de.jare.jsoncasted.editor.clipboard.ClipboardStash;
import de.jare.jsoncasted.editor.clipboard.CopyToStashCommand;
import de.jare.jsoncasted.editor.clipboard.CutToStashCommand;
import de.jare.jsoncasted.editor.clipboard.PasteFromStashCommand;
import de.jare.jsoncasted.editor.command.AddNodeCommand;
import java.util.Set;
import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.command.UpdateAction;
import static de.jare.jsoncasted.editor.command.UpdateAction.REBUILD_AFFECTED;
import static de.jare.jsoncasted.editor.command.UpdateAction.SELECT_ADDED;
import static de.jare.jsoncasted.editor.command.UpdateAction.SELECT_UPDATED;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.tree.control.JackMasterControl;
import de.jare.tree.control.JackUndoManager;
import de.jare.tree.control.MasterControl;
import de.jare.tree.control.listeners.ContentListener;
import de.jare.tree.control.listeners.FocusListener;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import de.jare.tree.control.listeners.UndoRedoListener;
import de.jare.tree.control.model.JackTreeModel;
import java.awt.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.swing.*;
import javax.swing.tree.*;

public class JackEditTree extends JPanel implements TreeFocusComponent {

    private final JackMasterControl master;
    private final JTree jtree;
    private final JPanel headerPanel;
    private final JLabel resourceLabel;
    private final JCheckBox linkCheckBox;

    private final TreeFocusListenerImpl treeFocusListener;
    private final ContentListenerImpl contentListener;
    private final FocusListenerImpl focusListener;
    private final UndoRedoListenerImpl undoRedoListener;

    public JackEditTree(String rootName, String... propNames) {
        this(null, rootName, propNames);
    }

    public JackEditTree(JackMasterControl master, String rootName, String... propNames) {
        this.master = master;

        // Header-Panel für Labels und Icons
        headerPanel = new JPanel();
        headerPanel.setLayout(new BorderLayout());

        // Linkes Panel für das Label
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        headerPanel.add(leftPanel, BorderLayout.WEST);

        // Rechtes Panel für die Checkbox
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        headerPanel.add(rightPanel, BorderLayout.EAST);

        // Label für Ressourceninfo
        resourceLabel = new JLabel("this = noname");
        leftPanel.add(resourceLabel);

        // Checkbox für Link-Ansicht
        linkCheckBox = new JCheckBox();
        linkCheckBox.setSelectedIcon(new ImageIcon(getClass().getResource("/icons/link_view.png")));
        linkCheckBox.setIcon(new ImageIcon(getClass().getResource("/icons/no_link.png")));
        rightPanel.add(linkCheckBox);

        // JTree initialisieren 
        final JackTreeModel jackTreeModel = new JackTreeModel(rootName);
        jtree = new JTree(jackTreeModel);
        jtree.setShowsRootHandles(true);
        jtree.setCellRenderer(new JsonTreeCellRenderer());
        jtree.setEditable(true);
        final JackUndoManager undoMan = master != null ? master.getUndoManager() : null;
        jtree.setCellEditor(new JackJsonTreeCellEditor(undoMan));

        // Selektionslistener für den Tree
        jtree.addTreeSelectionListener(e -> {
            if (master != null && master.getActiveEditor() == JackEditTree.this) {
                DefaultMutableTreeNode node
                        = (DefaultMutableTreeNode) jtree.getLastSelectedPathComponent();
                boolean rootSelected = node != null && node.getParent() == null;
                master.fireSelection(node, this, rootSelected);
            }
        });

        jtree.setDragEnabled(true);
        jtree.setDropMode(DropMode.ON_OR_INSERT);
        if (undoMan != null) {
            jtree.setTransferHandler(new JackTreeNodeTransferHandler(undoMan));
        }
        jtree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);

        // Layout für das JPanel
        setLayout(new BorderLayout());
        add(headerPanel, BorderLayout.NORTH);
        add(new JScrollPane(jtree), BorderLayout.CENTER);

        // Root-Knoten und optionale Demo-Properties
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) jtree.getModel().getRoot();
        for (String propName : propNames) {
            EditNodeAbstract childData = jackTreeModel.getEditTree().addNewChild(
                    ((EditNodeAbstract) root.getUserObject()),
                    propName
            );
            ((EditNodeProperty) childData).setValue("Value of " + propName);
            root.add(new DefaultMutableTreeNode(childData));
        }

        // Initialize listener implementations
        treeFocusListener = new TreeFocusListenerImpl();
        contentListener = new ContentListenerImpl();
        focusListener = new FocusListenerImpl();
        undoRedoListener = new UndoRedoListenerImpl();

        if (master != null) {
            master.addSelectionListener(1, treeFocusListener);
            master.addContentListener(1, contentListener);
            master.addFocusListener(1, focusListener);
            master.addUndoRedoListener(8, undoRedoListener);
        }
    }

    // ========== TreeFocusListener Implementation ==========
    private class TreeFocusListenerImpl implements TreeFocusListener {

        @Override
        public void onNodeSelected(Object node, Object trigger, boolean rootSelected) {
            // Nur reagieren, wenn dieser Editor aktuell aktiv ist
            if (master != null && master.getActiveEditor() != JackEditTree.this) {
                return;
            }

            if (!(node instanceof DefaultMutableTreeNode dmtn)) {
                return;
            }
            DefaultTreeModel model = (DefaultTreeModel) jtree.getModel();
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();

            TreePath path = findPath(root, dmtn);
            if (path != null) {
                jtree.scrollPathToVisible(path);
                if (trigger == JackEditTree.this) {
                    return; // Selbst ausgeloest
                }
                jtree.setSelectionPath(path);
            }
        }

        @Override
        public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
            if (master == null || editor != JackEditTree.this) {
                return;
            }
            TreePath path = jtree.getSelectionPath();
            master.fireSelection(path == null ? null : path.getLastPathComponent(), JackEditTree.this, false);
        }
    }

    // ========== ContentListener Implementation ==========
    private class ContentListenerImpl implements ContentListener {

        @Override
        public void onCommand(String commandId, Object trigger) {
            if (master != null && master.getActiveEditor() != JackEditTree.this) {
                return;
            }
            switch (commandId) {
                case EDIT_ADD_NODE ->
                    addNode();
                case EDIT_DELETE_NODE ->
                    deleteNode();
                case EDIT_RENAME_NODE ->
                    renameNode();
                case EDIT_COPY ->
                    copySelection(false);
                case EDIT_CUT ->
                    copySelection(true);
                case EDIT_PASTE ->
                    pasteClipboard();
            }
        }
    }

    // ========== FocusListener Implementation ==========
    private class FocusListenerImpl implements FocusListener {

        @Override
        public void onFocusGained() {
            // aktuellen selektierten Knoten erneut melden
            if (master != null && master.getActiveEditor() == JackEditTree.this) {
                DefaultMutableTreeNode node
                        = (DefaultMutableTreeNode) jtree.getLastSelectedPathComponent();
                master.fireSelection(node, JackEditTree.this, false);
            }
        }

        @Override
        public void onFocusLost() {
            if (jtree.isEditing()) {
                jtree.cancelEditing();
            }
        }
    }

    // ========== UndoRedoListener Implementation ==========
    private class UndoRedoListenerImpl implements UndoRedoListener {

        @Override
        public void onUndo(TreeModel model, CommandResult cmdResult) {

            doRefreshIfModel(model, cmdResult);
        }

        @Override
        public void onExecute(TreeModel model, CommandResult cmdResult) {
            doRefreshIfModel(model, cmdResult);
        }

        @Override
        public void onSkipped(TreeModel model, EditCommand command) {
            // NoOp here
        }

        private void doRefreshIfModel(TreeModel model, CommandResult cmdResult) {
            if (model != jtree.getModel()) {
                return;
            }
            Runnable uiTask = () -> applyUndoRedoResult(cmdResult);
            SwingUtilities.invokeLater(uiTask);
        }

        private void applyUndoRedoResult(CommandResult result) {
            boolean fallbackReload = false;

            for (UpdateAction update : result.getUpdateActions()) {
                switch (update) {
                    case REBUILD_AFFECTED -> {
                        fallbackReload = handleRebuildAffected(result);
                        break;
                    }
                    case SELECT_ADDED -> {
                        selectEditNodes(result.getAddedNodes());
                        break;
                    }
                    case SELECT_UPDATED -> {
                        selectEditNodes(result.getUpdatedNodes());
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
                getModel().rebuildFromDomain();
                revalidate();
                repaint();
            }
        }

        private boolean handleRebuildAffected(CommandResult result) {
            boolean fallbackReload = false;
            for (EditNode editNode : result.getAffectedNodes()) {
                fallbackReload |= !handleRebuildNode(editNode);
            }
            return fallbackReload;
        }

        private boolean handleRebuildNode(EditNode editNode) {
            if (editNode == null) {
                return false;
            }
            JackTreeModel model = getModel();
            DefaultMutableTreeNode mutableTreeNode = model.findNodeByIdAndRange(
                    editNode.getEditId(), editNode.getLeftRange(), editNode.getTimesRange());
            if (mutableTreeNode == null) {
                return true;
            }
            TreePath rootPath = new TreePath(mutableTreeNode.getPath());
            Map<Long, LongPairLeftTimes> expandedEditIds = saveExpandedEditIds(rootPath);
            mutableTreeNode.removeAllChildren();
            model.buildSubtreeStructureChanged(mutableTreeNode, editNode);

            restoreExpandedPaths(expandedEditIds);
            return true;
        }

        private boolean handleUpdatedNode(EditNode editNode) {
            if (editNode == null) {
                return false;
            }

            JackTreeModel model = getModel();
            DefaultMutableTreeNode swingNode = model.findNodeByIdAndRange(
                    editNode.getEditId(), editNode.getLeftRange(), editNode.getTimesRange());
            if (swingNode == null) {
                return false;
            }

            swingNode.setUserObject(editNode);
            model.nodeChanged(swingNode);
            return true;
        }

        private boolean handleAddedNode(EditNode editNode) {
            if (editNode == null) {
                return false;
            }

            JackTreeModel model = getModel();
            DefaultMutableTreeNode swingNode = model.findNodeByIdAndRange(
                    editNode.getEditId(), editNode.getLeftRange(), editNode.getTimesRange());
            if (swingNode != null) {
                return true;
            }

            EditNode parentEditNode = editNode.getParent();
            if (parentEditNode == null) {
                return false;
            }

            DefaultMutableTreeNode parentSwingNode = model.findNodeById(parentEditNode.getEditId());
            if (parentSwingNode == null) {
                return false;
            }

            DefaultMutableTreeNode newSwingNode = JackTreeModel.buildSubtree(editNode);
            int index = model.resolveChildIndex(parentEditNode, editNode);
            if (index < 0 || index > parentSwingNode.getChildCount()) {
                index = parentSwingNode.getChildCount();
            }

            model.insertNodeInto(newSwingNode, parentSwingNode, index);
            return true;
        }

        private boolean handleRemovedNode(EditNode editNode) {
            if (editNode == null) {
                return false;
            }

            JackTreeModel model = getModel();
            DefaultMutableTreeNode swingNode = model.findNodeById(editNode.getEditId());
            if (swingNode == null) {
                return false;
            }

            if (swingNode.getParent() != null) {
                model.removeNodeFromParent(swingNode);
            } else {
                return false;
            }

            return true;
        }
    }

    // ========== JackEditTree itself ==========
    @Override
    public JackMasterControl getJackMaster() {
        return master;
    }

    @Override
    public MasterControl getMaster() {
        return null;
    }

    protected JCheckBox getLinkCheckBox() {
        return linkCheckBox;
    }

    @Override
    public JTree getTree() {
        return jtree;
    }

    @Override
    public JackTreeModel getModel() {
        return (JackTreeModel) jtree.getModel();
    }

    JPanel getHeaderPanel() {
        return headerPanel;
    }

    JLabel getResourceLabel() {
        return resourceLabel;
    }

    void setResourceInfo(String text) {
        resourceLabel.setText(text);
    }

    private TreePath findPath(DefaultMutableTreeNode root, DefaultMutableTreeNode target) {
        if (root == target) {
            return new TreePath(root.getPath());
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            TreePath path = findPath(child, target);
            if (path != null) {
                return path;
            }
        }
        return null;
    }

    /**
     * Selects the given EditNodeAbstract nodes in the tree.Finds the
     * corresponding tree nodes and sets the selection paths.
     *
     * @param nodes
     */
    public void selectEditNodes(EditNodeAbstract[] nodes) {
        if (nodes == null || nodes.length == 0) {
            return;
        }

        TreePath[] paths = new TreePath[nodes.length];
        int pathCount = 0;

        for (EditNodeAbstract node : nodes) {
            DefaultMutableTreeNode treeNode = getModel().findNodeByIdAndRange(
                    node.getEditId(), node.getLeftRange(), node.getTimesRange());
            if (treeNode != null) {
                paths[pathCount++] = new TreePath(treeNode.getPath());
            }
        }

        if (pathCount > 0) {
            TreePath[] selectedPaths = new TreePath[pathCount];
            System.arraycopy(paths, 0, selectedPaths, 0, pathCount);
            jtree.setSelectionPaths(selectedPaths);
            if (selectedPaths.length > 0) {
                jtree.scrollPathToVisible(selectedPaths[0]);
            }
        }
    }

    public record LongPairLeftTimes(long left, long times) {

    }

    private Map<Long, LongPairLeftTimes> saveExpandedEditIds(TreePath rootPath) {
        Map<Long, LongPairLeftTimes> editIds = new HashMap<>();
        Enumeration<TreePath> e = jtree.getExpandedDescendants(rootPath);
        if (e != null) {
            while (e.hasMoreElements()) {
                TreePath path = e.nextElement();
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                Object userObject = node.getUserObject();
                if (userObject instanceof EditNodeAbstract editNode) {
                    editIds.put(editNode.getEditId(), new LongPairLeftTimes(editNode.getLeftRange(), editNode.getTimesRange()));
                }
            }
        }
        return editIds;
    }

    private void restoreExpandedPaths(Map<Long, LongPairLeftTimes> editIds) {
        if (editIds == null || editIds.isEmpty()) {
            return;
        }
        JackTreeModel model = getModel();
        for (Long editId : editIds.keySet()) {
            LongPairLeftTimes pair = editIds.get(editId);
            DefaultMutableTreeNode node = model.findNodeByIdAndRange(editId, pair.left(), pair.times());
            if (node != null) {
                TreePath path = new TreePath(node.getPath());
                jtree.expandPath(path);
            }
        }
    }

    private void addNode() {
        TreePath path = jtree.getSelectionPath();
        if (path == null) {
            return;
        }
        DefaultMutableTreeNode selected = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object uo = selected.getUserObject();
        if (!(uo instanceof EditNodeAbstract selectedData)) {
            return; // Secure
        }

        long selEditId = selectedData.getEditId();
        EditNodeAbstract newNode = selectedData.createChild("new");
        AddNodeCommand command = new AddNodeCommand(selEditId, newNode);

        if (master != null) {
            master.getUndoManager().executeCommand(command);
        }
    }

    private void deleteNode() {
//        TreePath path = tree.getSelectionPath();
//        if (path == null) {
//            return;
//        }
//        DefaultMutableTreeNode selected = (DefaultMutableTreeNode) path.getLastPathComponent();
//        if (selected.getParent() == null) {
//            return;
//        }
//        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
//        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selected.getParent();
//        int idx = parent.getIndex(selected);
//
//        if (selected.getUserObject() instanceof EditNode selectedData) {
//            if (parent.getUserObject() instanceof EditNode parentData) {
//                selectedData.sayOnRemoved(parentData);
//            }
//        }
//
//        master.getUndoManager().pushCommand(new WoodCommandDeleteNodes(
//                new DefaultMutableTreeNode[]{selected},
//                new DefaultMutableTreeNode[]{parent}
//        ));
//        model.removeNodeFromParent(selected);
//
//        // neue Selektion ermitteln: naechster/vorheriger Bruder oder nichts
//        DefaultMutableTreeNode newSelection = null;
//        if (parent.getChildCount() > 0) {
//            int newIdx = Math.min(idx, parent.getChildCount() - 1);
//            newSelection = (DefaultMutableTreeNode) parent.getChildAt(newIdx);
//            TreePath newPath = new TreePath(newSelection.getPath());
//            tree.setSelectionPath(newPath);
//            tree.scrollPathToVisible(newPath);
//        } else {
//            // keine Selektion mehr
//            tree.clearSelection();
//        }
//
//        // explizit auch null melden, damit Properties sich leeren koennen
//        if (master != null && master.getActiveEditor() == this) {
//            boolean rootSelected = newSelection != null && newSelection.getParent() == null;
//            master.fireSelection(newSelection, this, rootSelected);
//        }

    }

    private void renameNode() {
        TreePath path = jtree.getSelectionPath();
        if (path != null) {
            jtree.startEditingAtPath(path);
        }
    }

    private void copySelection(boolean cut) {
        TreePath[] paths = jtree.getSelectionPaths();
        if (paths == null || paths.length == 0 || master == null) {
            return;
        }

        // Extract EditNode IDs from selected tree paths
        long[] nodeIds = new long[paths.length];
        for (int i = 0; i < paths.length; i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) paths[i].getLastPathComponent();
            Object uo = node.getUserObject();
            if (uo instanceof EditNodeAbstract editNode) {
                nodeIds[i] = editNode.getEditId();
            } else {
                // Cannot copy non-EditNode objects
                return;
            }
        }

        ClipboardManager clipboardManager = master.getClipboardManager();
        String stashName = clipboardManager.getActiveStashName();

        // Sichere die Expansionszustände der ausgewählten Knoten
        java.util.Set<Long> expandedNodeIds = saveExpandedNodeIdsForPaths(paths);

        EditCommand command;
        if (cut) {
            command = new CutToStashCommand(
                    clipboardManager,
                    stashName,
                    nodeIds
            );
        } else {
            command = new CopyToStashCommand(
                    clipboardManager,
                    stashName,
                    nodeIds
            );
        }

        master.getUndoManager().executeCommand(command);

        // Speichere die Expansionszustände im Stash
        ClipboardStash stash = clipboardManager.getStash(stashName);
        if (stash != null && expandedNodeIds != null && !expandedNodeIds.isEmpty()) {
            stash.setExpandedNodeIds(expandedNodeIds);
        }
    }

    /**
     * Speichert die Expansionszustände für die gegebenen Pfade.
     * Nur Knoten, deren Pfad tatsächlich expanded ist, werden gespeichert.
     *
     * @param paths die TreePath-Array
     * @return Set der expandierten Node-IDs
     */
    private java.util.Set<Long> saveExpandedNodeIdsForPaths(TreePath[] paths) {
        java.util.Set<Long> expandedIds = new java.util.HashSet<>();
        for (TreePath path : paths) {
            collectExpandedNodeIds(path, expandedIds);
        }
        return expandedIds;
    }

    /**
     * Rekursiv alle expandierten Knoten unter einem Pfad sammeln.
     * Nur Knoten, deren Pfad expanded ist, werden zur Liste hinzugefügt.
     */
    private void collectExpandedNodeIds(TreePath path, java.util.Set<Long> expandedIds) {
        if (path == null) {
            return;
        }
        
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        
        // Nur hinzufügen, wenn dieser Pfad expanded ist
        if (jtree.isExpanded(path)) {
            Object uo = node.getUserObject();
            if (uo instanceof EditNodeAbstract editNode) {
                expandedIds.add(editNode.getEditId());
            }
            
            // Rekursiv alle Kinder durchgehen
            for (int i = 0; i < node.getChildCount(); i++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
                TreePath childPath = path.pathByAddingChild(child);
                collectExpandedNodeIds(childPath, expandedIds);
            }
        }
    }

    private void pasteClipboard() {
        if (master == null) {
            return;
        }

        TreePath path = jtree.getSelectionPath();
        if (path == null) {
            return;
        }

        DefaultMutableTreeNode target = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object targetUo = target.getUserObject();
        if (!(targetUo instanceof EditNodeAbstract targetData)) {
            return;
        }

        ClipboardManager clipboardManager = master.getClipboardManager();

        // Use the currently active stash from ClipboardManager
        String stashName = clipboardManager.getActiveStashName();

        // Check if there's content in the active stash
        ClipboardStash stash = clipboardManager.getStash(stashName);
        if (stash == null || stash.isEmpty()) {
            UIManager.getLookAndFeel().provideErrorFeedback(jtree);
            return;
        }

        // Create and execute paste command
        long parentId = targetData.getEditId();
        PasteFromStashCommand command = new PasteFromStashCommand(
                clipboardManager,
                stashName,
                parentId
        );

        master.getUndoManager().executeCommand(command);

        // Select the pasted nodes - fire selection to update properties
        if (master != null && master.getActiveEditor() == this) {
            DefaultMutableTreeNode sel = (DefaultMutableTreeNode) jtree.getLastSelectedPathComponent();
            master.fireSelection(sel, this, false);
        }
    }

}
