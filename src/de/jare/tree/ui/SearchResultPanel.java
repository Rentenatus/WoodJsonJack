/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.tree.control.JackMasterControl;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import de.jare.tree.control.model.JackTreeModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/**
 * Panel for displaying search results in a table with navigation history.
 */
public class SearchResultPanel extends JPanel implements TreeFocusListener, SearchToolbar.SearchListener {

    private final JackMasterControl master;
    private final JLabel searchLabel;
    private final JButton prevButton;
    private final JButton nextButton;
    private final JTable resultsTable;
    private final SearchResultTableModel tableModel;

    private SearchResults currentResults;
    private List<SearchResults> history = new ArrayList<>();
    private int historyIndex = -1;

    public SearchResultPanel(JackMasterControl master) {
        super(new BorderLayout());
        this.master = master;

        // Header panel with search label and navigation buttons
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        prevButton = new JButton("<");
        prevButton.setToolTipText("Previous search results");
        prevButton.addActionListener(e -> navigateHistory(-1));
        prevButton.setEnabled(false);

        nextButton = new JButton(">");
        nextButton.setToolTipText("Next search results");
        nextButton.addActionListener(e -> navigateHistory(1));
        nextButton.setEnabled(false);

        searchLabel = new JLabel("Search results: ");
        searchLabel.setHorizontalAlignment(SwingConstants.LEFT);

        headerPanel.add(prevButton);
        headerPanel.add(nextButton);
        headerPanel.add(searchLabel);

        // Table model and table
        tableModel = new SearchResultTableModel();
        resultsTable = new JTable(tableModel);
        resultsTable.setFillsViewportHeight(true);
        resultsTable.getTableHeader().setVisible(true);
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Add double-click listener to select node in tree
        resultsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = resultsTable.getSelectedRow();
                    if (row >= 0) {
                        DefaultMutableTreeNode node = tableModel.getNodeAt(row);
                        selectNodeInTree(node);
                    }
                }
            }
        });

        // Add components to panel
        add(headerPanel, BorderLayout.NORTH);
        add(new JScrollPane(resultsTable), BorderLayout.CENTER);

        // Initialize with empty results
        clearResults();
    }

    private void navigateHistory(int direction) {
        if (history.isEmpty()) return;

        historyIndex += direction;
        
        if (historyIndex < 0) {
            historyIndex = 0;
        } else if (historyIndex >= history.size()) {
            historyIndex = history.size() - 1;
        }

        if (historyIndex >= 0 && historyIndex < history.size()) {
            currentResults = history.get(historyIndex);
            searchLabel.setText(currentResults.getSearchText() + " (" + currentResults.getResultCount() + " results)");
            updateTable();
            updateNavigationButtons();
        }
    }

    private void updateNavigationButtons() {
        prevButton.setEnabled(historyIndex > 0);
        nextButton.setEnabled(historyIndex < history.size() - 1);
    }

    private void selectNodeInTree(DefaultMutableTreeNode node) {
        if (node == null) {
            return;
        }

        Object userObject = node.getUserObject();
        if (!(userObject instanceof EditNode)) {
            return;
        }

        EditNode editNode = (EditNode) userObject;
        long editId = editNode.getEditId();

        // Get the source component from current results
        TreeFocusComponent sourceComponent = currentResults != null ? currentResults.getSource() : null;
        
        if (sourceComponent == null) {
            // Fallback: use active editor from master
            sourceComponent = (TreeFocusComponent) master.getActiveEditor();
        }

        if (sourceComponent == null) {
            return;
        }

        // 1. Activate the source editor if it's not already active
        Object activeEditor = master.getActiveEditor();
        if (activeEditor != sourceComponent) {
            master.setActiveEditor(sourceComponent, this);
        }

        // 2. Select the node by editId in the tree
        JTree tree = sourceComponent.getTree();
        if (tree == null) {
            return;
        }

        JackTreeModel model = (JackTreeModel) tree.getModel();
        if (model == null) {
            return;
        }

        // Find the node by editId
        DefaultMutableTreeNode targetNode = model.findNodeById(editId);
        if (targetNode != null) {
            // Build the tree path manually
            TreePath path = buildTreePath(model, targetNode);
            if (path != null) {
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
            }
        }
    }

    @Override
    public void onSearch(SearchToolbar.SearchCriteria criteria, SearchResults results) {
        // Add to history
        history.add(results);
        historyIndex = history.size() - 1;
        
        // Update current results and display
        currentResults = results;
        String sourceName = "";
        TreeFocusComponent source = results.getSource();
        if (source != null) {
            sourceName = " [" + source.getDisplayName() + "]";
        }
        searchLabel.setText(results.getSearchText() + sourceName + " (" + results.getResultCount() + " results)");
        
        // Update the table
        updateTable();
        updateNavigationButtons();
    }

    private void updateTable() {
        if (currentResults != null) {
            tableModel.setResults(currentResults.getResults());
        } else {
            tableModel.setResults(new ArrayList<>());
        }
        tableModel.fireTableDataChanged();
    }

    public void clearResults() {
        currentResults = null;
        history.clear();
        historyIndex = -1;
        searchLabel.setText("Search results: ");
        tableModel.setResults(new ArrayList<>());
        tableModel.fireTableDataChanged();
        updateNavigationButtons();
    }

    @Override
    public void onNodeSelected(DefaultMutableTreeNode node, Object trigger, boolean rootSelected) {
        if (node == null || currentResults == null) {
            return;
        }

        // Check if the selected node is from the same source as the current search results
        TreeFocusComponent sourceComponent = currentResults.getSource();
        if (sourceComponent == null) {
            return;
        }

        // The trigger should be the TreeFocusComponent that contains the selected node
        if (trigger instanceof TreeFocusComponent) {
            TreeFocusComponent currentEditor = (TreeFocusComponent) trigger;
            if (currentEditor != sourceComponent) {
                return;
            }
        }

        // Find the index of the selected node in the current results
        List<DefaultMutableTreeNode> resultNodes = currentResults.getResults();
        int index = resultNodes.indexOf(node);
        
        if (index >= 0) {
            // Select the row in the table
            resultsTable.setRowSelectionInterval(index, index);
            resultsTable.scrollRectToVisible(resultsTable.getCellRect(index, 0, true));
        }
    }

    @Override
    public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
        // Could be used to highlight results in the active editor
    }

    /**
     * Builds a TreePath from the root to the given node.
     */
    private TreePath buildTreePath(JackTreeModel model, DefaultMutableTreeNode targetNode) {
        if (model == null || targetNode == null) {
            return null;
        }

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        if (root == null) {
            return null;
        }

        // Build path from root to target
        List<DefaultMutableTreeNode> pathNodes = new ArrayList<>();
        DefaultMutableTreeNode current = targetNode;
        
        while (current != null && current != root) {
            pathNodes.add(0, current);
            current = (DefaultMutableTreeNode) current.getParent();
        }
        
        if (current == root) {
            pathNodes.add(0, root);
        }
        
        if (pathNodes.isEmpty()) {
            return null;
        }
        
        return new TreePath(pathNodes.toArray());
    }

    /**
     * Table model for displaying search results.
     */
    private static class SearchResultTableModel extends AbstractTableModel {
        private List<DefaultMutableTreeNode> results = new ArrayList<>();
        private final String[] columnNames = {"Name", "Value", "Type", "Status", "Path"};

        public void setResults(List<DefaultMutableTreeNode> results) {
            this.results = new ArrayList<>(results);
        }

        @Override
        public int getRowCount() {
            return results.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex < 0 || rowIndex >= results.size()) {
                return null;
            }

            DefaultMutableTreeNode node = results.get(rowIndex);
            Object userObject = node.getUserObject();

            if (!(userObject instanceof EditNode)) {
                return null;
            }

            EditNode editNode = (EditNode) userObject;

            switch (columnIndex) {
                case 0: // Name
                    return editNode.getName();
                case 1: // Value
                    return editNode.getValue();
                case 2: // Type
                    return editNode.getTypeKey();
                case 3: // Status
                    return editNode.getEditStatus();
                case 4: // Path (simplified for now)
                    return buildPath(node);
                default:
                    return null;
            }
        }

        private String buildPath(DefaultMutableTreeNode node) {
            StringBuilder path = new StringBuilder();
            Object userObject = node.getUserObject();
            
            if (userObject instanceof EditNode) {
                EditNode editNode = (EditNode) userObject;
                path.insert(0, "/" + editNode.getName());
                
                // Walk up the parent chain
                DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
                while (parent != null) {
                    Object parentObj = parent.getUserObject();
                    if (parentObj instanceof EditNode) {
                        EditNode parentNode = (EditNode) parentObj;
                        path.insert(0, "/" + parentNode.getName());
                    }
                    parent = (DefaultMutableTreeNode) parent.getParent();
                }
            }
            
            return path.length() > 0 ? path.toString() : "/";
        }

        public DefaultMutableTreeNode getNodeAt(int row) {
            if (row >= 0 && row < results.size()) {
                return results.get(row);
            }
            return null;
        }
    }
}
