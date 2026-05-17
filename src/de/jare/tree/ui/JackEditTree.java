/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
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
import javax.swing.*;
import javax.swing.tree.*;

public class JackEditTree extends JPanel implements TreeFocusComponent, TreeFocusListener, ContentListener, FocusListener, UndoRedoListener {

    private final JackMasterControl master;
    private final JTree jtree;
    private final JPanel headerPanel;
    private final JLabel resourceLabel;
    private final JCheckBox linkCheckBox;

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

        if (master != null) {
            master.addSelectionListener(1, this);
            master.addContentListener(1, this);
            master.addFocusListener(1, this);
        }
    }

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

    @Override
    public void onUndo(TreeModel model) {
        doRefreshIfModel(model);
    }

    @Override
    public void onExecute(TreeModel model) {
        doRefreshIfModel(model);
    }

    @Override
    public void onSkipped(TreeModel model) {
        doRefreshIfModel(model);
    }

    private void doRefreshIfModel(TreeModel model) {
        if (model != jtree.getModel()) {
            return;
        }
        ((DefaultTreeModel) jtree.getModel()).reload();
        revalidate();
        repaint();
    }

    @Override
    public void onFocusGained() {
        // aktuellen selektierten Knoten erneut melden
        if (master != null && master.getActiveEditor() == this) {
            DefaultMutableTreeNode node
                    = (DefaultMutableTreeNode) jtree.getLastSelectedPathComponent();
            master.fireSelection(node, this, false);
        }
    }

    @Override
    public void onFocusLost() {
        if (jtree.isEditing()) {
            jtree.cancelEditing();
        }
    }

    @Override
    public void onNodeSelected(Object node, Object trigger, boolean rootSelected) {
        // Nur reagieren, wenn dieser Editor aktuell aktiv ist
        if (master != null && master.getActiveEditor() != this) {
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
            if (trigger == this) {
                return; // Selbst ausgeloest
            }
            jtree.setSelectionPath(path);
        }
    }

    @Override
    public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
        if (master == null || editor != this) {
            return;
        }
        TreePath path = jtree.getSelectionPath();
        master.fireSelection(path == null ? null : path.getLastPathComponent(), this, false);
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

    @Override
    public void onCommand(String commandId, Object trigger) {
        if (master != null && master.getActiveEditor() != this) {
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

    private void addNode() {
//        TreePath path = tree.getSelectionPath();
//        if (path == null) {
//            return;
//        }
//        DefaultMutableTreeNode selected = (DefaultMutableTreeNode) path.getLastPathComponent();
//        Object uo = selected.getUserObject();
//        if (!(uo instanceof EditNode data)) {
//            return; // Sicherheitsnetz
//        }
//
//        // neuen Kind-Knoten erzeugen
//        EditNode childData = data.createChild("new");
//        DefaultMutableTreeNode child = new DefaultMutableTreeNode(childData);
//        selected.add(child);
//        ((DefaultTreeModel) tree.getModel()).reload(selected);
//        master.getUndoManager().pushCommand(new WoodCommandAddNodes(
//                new DefaultMutableTreeNode[]{child},
//                new DefaultMutableTreeNode[]{selected},
//                selected.getIndex(child)
//        ));
//
//        TreePath newPath = new TreePath(child.getPath());
//        tree.setSelectionPath(newPath);
//        tree.scrollPathToVisible(newPath);
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
//        TreePath[] paths = tree.getSelectionPaths();
//        if (paths == null || paths.length == 0 || master == null) {
//            return;
//        }
//
//        master.getClipboardTree().copySelection(this, paths, cut);
//
//        if (cut) {
//            DefaultTreeModel srcModel = (DefaultTreeModel) tree.getModel();
//            DefaultMutableTreeNode[] nodes = new DefaultMutableTreeNode[paths.length];
//            DefaultMutableTreeNode[] parents = new DefaultMutableTreeNode[paths.length];
//
//            for (int i = 0; i < paths.length; i++) {
//                DefaultMutableTreeNode n = (DefaultMutableTreeNode) paths[i].getLastPathComponent();
//                nodes[i] = n;
//                parents[i] = (DefaultMutableTreeNode) n.getParent();
//            }
//
//            // Undo-Command f�r Cut
//            master.getUndoManager().pushCommand(
//                    new WoodCommandDeleteNodes(nodes, parents)
//            );
//
//            // physisch entfernen (von unten nach oben)
//            for (int i = paths.length - 1; i >= 0; i--) {
//                DefaultMutableTreeNode n = nodes[i];
//                MutableTreeNode p = (MutableTreeNode) n.getParent();
//                if (p != null) {
//                    srcModel.removeNodeFromParent(n);
//                }
//            }
//        }
    }

    private void pasteClipboard() {
//        if (master == null) {
//            return;
//        }
//
//        TreePath path = tree.getSelectionPath();
//        if (path == null) {
//            return;
//        }
//
//        DefaultMutableTreeNode target = (DefaultMutableTreeNode) path.getLastPathComponent();
//        Object targetUo = target.getUserObject();
//        if (!(targetUo instanceof EditNode targetData)) {
//            return;
//        }
//
//        if (!master.getClipboardTree().canPasteTo(targetData)) {
//            UIManager.getLookAndFeel().provideErrorFeedback(this);
//            return;
//        }
//
//        // Wenn Typ passt, regul?r einf?gen
//        master.getClipboardTree().pasteClipboard(this, path);
//
//        // Events (Properties etc.)
//        if (master != null && master.getActiveEditor() == this) {
//            DefaultMutableTreeNode sel
//                    = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
//            master.fireSelection(sel, this, false);
//        }
    }

}
