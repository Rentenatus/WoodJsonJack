/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.swing;

import de.jare.jsoncasted.editor.command.AddNodeCommand;
import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.DeleteNodeCommand;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.command.SetValueCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.events.SelectionEvent;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.function.Consumer;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 * New version of WoodEditTree that uses TreeEditor and EditNode directly.
 * This replaces the old de.jare.tree.ui.WoodEditTree class.
 */
public class SwingEditTree extends JPanel {

    // Command IDs
    public static final String EDIT_ADD_NODE = "EDIT_ADD_NODE";
    public static final String EDIT_DELETE_NODE = "EDIT_DELETE_NODE";
    public static final String EDIT_RENAME_NODE = "EDIT_RENAME_NODE";
    public static final String EDIT_COPY = "EDIT_COPY";
    public static final String EDIT_CUT = "EDIT_CUT";
    public static final String EDIT_PASTE = "EDIT_PASTE";

    private final SwingMasterControl master;
    private final JTree tree;
    private final JPanel headerPanel;
    private final JLabel resourceLabel;
    private final JCheckBox linkCheckBox;

    private EditTree editTree;
    private EditNode rootNode;

    public SwingEditTree(String rootName, String... propNames) {
        this(null, rootName, propNames);
    }

    public SwingEditTree(SwingMasterControl master, String rootName, String... propNames) {
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

        // Root-Knoten und Baum initialisieren
        rootNode = new EditNodeObject("{" + rootName + "}");
        editTree = new EditTree(rootNode);
        
        DefaultMutableTreeNode swingRoot = new DefaultMutableTreeNode(rootNode);
        tree = new JTree(swingRoot);
        tree.setShowsRootHandles(true);
        tree.setEditable(true);

        // Selektionslistener für den Tree
        tree.addTreeSelectionListener(e -> {
            if (master != null && master.getActiveEditor() == SwingEditTree.this) {
                DefaultMutableTreeNode node
                        = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
                boolean rootSelected = node != null && node.getParent() == null;
                master.fireSelection(node, this, rootSelected);
            }
        });

        tree.setDragEnabled(true);
        tree.setDropMode(javax.swing.DropMode.ON_OR_INSERT);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);

        // Layout für das JPanel
        setLayout(new BorderLayout());
        add(headerPanel, BorderLayout.NORTH);
        add(new JScrollPane(tree), BorderLayout.CENTER);

        // Root-Knoten und optionale Demo-Properties
        for (String propName : propNames) {
            EditNode childData = rootNode.createChild(propName);
            if (childData instanceof EditNodeProperty) {
                ((EditNodeProperty) childData).setPrimValue("Value of " + propName);
            }
            swingRoot.add(new DefaultMutableTreeNode(childData));
        }

        // Beim MasterControl registrieren
        if (master != null) {
            master.registerEditor(tree.getModel());
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public SwingMasterControl getMaster() {
        return master;
    }

    public JTree getTree() {
        return tree;
    }

    public TreeModel getModel() {
        return tree.getModel();
    }

    public EditTree getEditTree() {
        return editTree;
    }

    public EditNode getRootNode() {
        return rootNode;
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

    JCheckBox getLinkCheckBox() {
        return linkCheckBox;
    }

    // -------------------------------------------------------------------------
    // Command handling
    // -------------------------------------------------------------------------

    /**
     * Handles a command execution.
     *
     * @param commandId the command identifier
     * @param trigger the trigger object
     */
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
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return;
        }
        DefaultMutableTreeNode selected = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object uo = selected.getUserObject();
        if (!(uo instanceof EditNode data)) {
            return; // Sicherheitsnetz
        }

        // neuen Kind-Knoten erzeugen
        EditNode childData = data.createChild("new");
        DefaultMutableTreeNode child = new DefaultMutableTreeNode(childData);
        
        // Zum Baum hinzufügen
        selected.add(child);
        ((DefaultTreeModel) tree.getModel()).reload(selected);
        
        // Command für Undo/Redo
        if (master != null) {
            AddNodeCommand cmd = new AddNodeCommand(data.getEditId(), childData);
            master.execute(cmd);
        }

        TreePath newPath = new TreePath(child.getPath());
        tree.setSelectionPath(newPath);
        tree.scrollPathToVisible(newPath);
    }

    private void deleteNode() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return;
        }
        DefaultMutableTreeNode selected = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (selected.getParent() == null) {
            return; // Cannot delete root
        }
        
        Object uo = selected.getUserObject();
        if (!(uo instanceof EditNode nodeData)) {
            return;
        }

        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selected.getParent();
        
        // Command für Undo/Redo
        if (master != null) {
            DeleteNodeCommand cmd = new DeleteNodeCommand(nodeData);
            master.execute(cmd);
        }
        
        // Physisch entfernen
        model.removeNodeFromParent(selected);

        // neue Selektion ermitteln
        DefaultMutableTreeNode newSelection = null;
        if (parent.getChildCount() > 0) {
            int newIdx = Math.min(parent.getIndex(selected), parent.getChildCount() - 1);
            newSelection = (DefaultMutableTreeNode) parent.getChildAt(newIdx);
            TreePath newPath = new TreePath(newSelection.getPath());
            tree.setSelectionPath(newPath);
            tree.scrollPathToVisible(newPath);
        } else {
            tree.clearSelection();
        }

        // Selektion melden
        if (master != null && master.getActiveEditor() == this) {
            boolean rootSelected = newSelection != null && newSelection.getParent() == null;
            master.fireSelection(newSelection, this, rootSelected);
        }
    }

    private void renameNode() {
        TreePath path = tree.getSelectionPath();
        if (path != null) {
            tree.startEditingAtPath(path);
        }
    }

    private void copySelection(boolean cut) {
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null || paths.length == 0 || master == null) {
            return;
        }

        master.copySelection(this, paths, cut);

        if (cut) {
            DefaultTreeModel srcModel = (DefaultTreeModel) tree.getModel();
            
            // Physisch entfernen (von unten nach oben)
            for (int i = paths.length - 1; i >= 0; i--) {
                DefaultMutableTreeNode n = (DefaultMutableTreeNode) paths[i].getLastPathComponent();
                if (n.getParent() != null) {
                    srcModel.removeNodeFromParent(n);
                }
            }
        }
    }

    private void pasteClipboard() {
        if (master == null) {
            return;
        }

        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return;
        }

        if (master.pasteClipboard(this, path)) {
            // Refresh selection
            if (master.getActiveEditor() == this) {
                DefaultMutableTreeNode sel
                        = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
                master.fireSelection(sel, this, false);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Selection handling
    // -------------------------------------------------------------------------

    /**
     * Handles selection change from master control.
     *
     * @param node the selected node
     * @param trigger the trigger object
     * @param rootSelected whether the root is selected
     */
    public void onNodeSelected(Object node, Object trigger, boolean rootSelected) {
        // Nur reagieren, wenn dieser Editor aktuell aktiv ist
        if (master != null && master.getActiveEditor() != this) {
            return;
        }

        if (!(node instanceof DefaultMutableTreeNode dmtn)) {
            return;
        }
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();

        TreePath path = findPath(root, dmtn);
        if (path != null) {
            tree.scrollPathToVisible(path);
            if (trigger == this) {
                return; // Selbst ausgelöst
            }
            tree.setSelectionPath(path);
        }
    }

    /**
     * Handles editor selection change.
     *
     * @param editor the selected editor
     * @param trigger the trigger object
     */
    public void onEditorSelected(Object editor, Object trigger) {
        if (master == null || editor != this) {
            return;
        }
        TreePath path = tree.getSelectionPath();
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

    // -------------------------------------------------------------------------
    // Value editing
    // -------------------------------------------------------------------------

    /**
     * Updates the value of a node after editing.
     * This is called by the cell editor when editing is complete.
     *
     * @param node the node that was edited
     * @param oldValue the old value
     * @param newValue the new value
     */
    public void updateNodeValue(DefaultMutableTreeNode node, String oldValue, String newValue) {
        Object uo = node.getUserObject();
        if (uo instanceof EditNode editNode) {
            // Create and execute set value command
            if (master != null) {
                SetValueCommand cmd = new SetValueCommand(editNode, newValue);
                master.execute(cmd);
            } else {
                // Direct update if no master
                editNode.setEditText(newValue);
            }
        }
    }
}
