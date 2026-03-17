/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.control.MasterControl;
import de.jare.tree.control.commands.WoodCommand;
import de.jare.tree.control.commands.WoodCommandAddNodes;
import de.jare.tree.control.commands.WoodCommandDeleteNodes;
import de.jare.tree.control.listeners.ContentListener;
import de.jare.tree.control.listeners.FocusListener;
import de.jare.tree.data.JsonObjectData;
import de.jare.tree.data.JsonTreeNodeData;

import javax.swing.*;
import javax.swing.tree.*;
import de.jare.tree.control.listeners.TreeSelectionListener;
import de.jare.tree.control.listeners.UndoRedoListener;

public class WoodEditTree extends JTree implements TreeSelectionListener, ContentListener, FocusListener, UndoRedoListener {

    private final MasterControl master;

    public WoodEditTree(String rootName, String... propNames) {
        this(null, rootName, propNames);
    }

    public WoodEditTree(MasterControl master, String rootName, String... propNames) {
        // Root als JsonObjectData
        super(new DefaultMutableTreeNode(new JsonObjectData("{" + rootName + "}")));
        this.master = master;

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) getModel().getRoot();

        // optionale Demo-Properties unter Root
        for (String propName : propNames) {
            JsonTreeNodeData childData = ((JsonTreeNodeData) root.getUserObject())
                    .createChild(propName);
            root.add(new DefaultMutableTreeNode(childData));
        }

        setShowsRootHandles(true);
        setCellRenderer(new JsonTreeCellRenderer());
        setEditable(true);
        setCellEditor(new JsonTreeCellEditor(master.getUndoManager()));

        // eigene Selektion an MasterControl melden, aber nur wenn dieser Tree aktiv ist
        addTreeSelectionListener(e -> {
            if (master.getActiveEditor() == WoodEditTree.this) {
                DefaultMutableTreeNode node
                        = (DefaultMutableTreeNode) getLastSelectedPathComponent();
                boolean rootSelected = node != null && node.getParent() == null;
                master.fireSelection(node, this, rootSelected);
            }
        });

        setDragEnabled(true);
        setDropMode(DropMode.ON_OR_INSERT);
        setTransferHandler(new TreeNodeTransferHandler());
        getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);

        master.addSelectionListener(1, this);
        master.addContentListener(1, this);
        master.addFocusListener(1, this);
    }

    MasterControl getMaster() {
        return master;
    }

    @Override
    public void onUndo(TreeModel model, WoodCommand cmd) {
        doRefreshIfModel(model);
    }

    @Override
    public void onRedo(TreeModel model, WoodCommand cmd) {
        doRefreshIfModel(model);
    }

    private void doRefreshIfModel(TreeModel model) {
        if (model != getModel()) {
            return;
        }
        ((DefaultTreeModel) getModel()).reload();
        revalidate();
        repaint();
    }

    @Override
    public void onFocusGained() {
        // aktuellen selektierten Knoten erneut melden
        if (master != null && master.getActiveEditor() == this) {
            DefaultMutableTreeNode node
                    = (DefaultMutableTreeNode) getLastSelectedPathComponent();
            master.fireSelection(node, this, false);
        }
    }

    @Override
    public void onFocusLost() {
        if (isEditing()) {
            cancelEditing();
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
        DefaultTreeModel model = (DefaultTreeModel) getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();

        TreePath path = findPath(root, dmtn);
        if (path != null) {
            scrollPathToVisible(path);
            if (trigger == this) {
                return; // Selbst ausgeloest
            }
            setSelectionPath(path);
        }
    }

    @Override
    public void onEditorSelected(JTree editor, Object trigger) {
        if (master == null || editor != this) {
            return;
        }
        TreePath path = getSelectionPath();
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
        TreePath path = getSelectionPath();
        if (path == null) {
            return;
        }
        DefaultMutableTreeNode selected = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object uo = selected.getUserObject();
        if (!(uo instanceof JsonTreeNodeData data)) {
            return; // Sicherheitsnetz
        }

        // neuen Kind-Knoten erzeugen
        JsonTreeNodeData childData = data.createChild("new");
        DefaultMutableTreeNode child = new DefaultMutableTreeNode(childData);
        selected.add(child);
        ((DefaultTreeModel) getModel()).reload(selected);
        master.getUndoManager().pushCommand(new WoodCommandAddNodes(
                new DefaultMutableTreeNode[]{child},
                new DefaultMutableTreeNode[]{selected},
                selected.getIndex(child)
        ));
    }

    private void deleteNode() {
        TreePath path = getSelectionPath();
        if (path == null) {
            return;
        }
        DefaultMutableTreeNode selected = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (selected.getParent() == null) {
            return;
        }
        DefaultTreeModel model = (DefaultTreeModel) getModel();
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selected.getParent();
        int idx = parent.getIndex(selected);

        if (selected.getUserObject() instanceof JsonTreeNodeData selectedData) {
            if (parent.getUserObject() instanceof JsonTreeNodeData parentData) {
                selectedData.sayOnRemoved(parentData);
            }
        }

        master.getUndoManager().pushCommand(new WoodCommandDeleteNodes(
                new DefaultMutableTreeNode[]{selected},
                new DefaultMutableTreeNode[]{parent}
        ));
        model.removeNodeFromParent(selected);

        // neue Selektion ermitteln: naechster/vorheriger Bruder oder nichts
        DefaultMutableTreeNode newSelection = null;
        if (parent.getChildCount() > 0) {
            int newIdx = Math.min(idx, parent.getChildCount() - 1);
            newSelection = (DefaultMutableTreeNode) parent.getChildAt(newIdx);
            TreePath newPath = new TreePath(newSelection.getPath());
            setSelectionPath(newPath);
            scrollPathToVisible(newPath);
        } else {
            // keine Selektion mehr
            clearSelection();
        }

        // explizit auch null melden, damit Properties sich leeren koennen
        if (master != null && master.getActiveEditor() == this) {
            boolean rootSelected = newSelection != null && newSelection.getParent() == null;
            master.fireSelection(newSelection, this, rootSelected);
        }

    }

    private void renameNode() {
        TreePath path = getSelectionPath();
        if (path != null) {
            startEditingAtPath(path);
        }
    }

    private void copySelection(boolean cut) {
        TreePath[] paths = getSelectionPaths();
        if (paths == null || paths.length == 0 || master == null) {
            return;
        }

        master.getClipboardTree().copySelection(this, paths, cut);

        // Bei Cut: Originale entfernen
        if (cut) {
            DefaultTreeModel srcModel = (DefaultTreeModel) getModel();
            for (int i = paths.length - 1; i >= 0; i--) {
                DefaultMutableTreeNode n = (DefaultMutableTreeNode) paths[i].getLastPathComponent();
                MutableTreeNode p = (MutableTreeNode) n.getParent();
                if (p != null) {
                    srcModel.removeNodeFromParent(n);
                }
            }
        }
    }

    private void pasteClipboard() {
        if (master == null) {
            return;
        }

        TreePath path = getSelectionPath();
        if (path == null) {
            return;
        }

        // Zielknoten (Elternkandidat)
        DefaultMutableTreeNode target = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object targetUo = target.getUserObject();
        if (!(targetUo instanceof JsonTreeNodeData targetData)) {
            // Ziel ist kein JSON-Knoten -> nichts einfuegen
            return;
        }

        if (!master.getClipboardTree().canPasteTo(targetData)) {
            UIManager.getLookAndFeel().provideErrorFeedback(this);
            return;
        }

        // Wenn Typ passt, regul?r einf?gen
        master.getClipboardTree().pasteClipboard(this, path);

        // Events (Properties etc.)
        if (master != null && master.getActiveEditor() == this) {
            DefaultMutableTreeNode sel
                    = (DefaultMutableTreeNode) getLastSelectedPathComponent();
            master.fireSelection(sel, this, false);
        }
    }

}
