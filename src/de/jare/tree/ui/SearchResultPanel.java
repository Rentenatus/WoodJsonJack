/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditStatus;
import de.jare.tree.control.JackMasterControl;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import de.jare.tree.control.model.JackTreeModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
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
    private final JButton clearButton;
    private final JButton researchButton;
    private final JTable resultsTable;
    private final SearchResultTableModel tableModel;

    private SearchResults currentResults;
    private SearchToolbar.SearchCriteria currentCriteria;
    private List<SearchResults> history = new ArrayList<>();
    private List<SearchToolbar.SearchCriteria> historyCriteria = new ArrayList<>();
    private int historyIndex = -1;

    public SearchResultPanel(JackMasterControl master) {
        super(new BorderLayout());
        this.master = master;

        // Header panel with search label and navigation buttons
        JPanel headerPanel = new JPanel(new BorderLayout());

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

        // Left panel for navigation and label
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftPanel.add(prevButton);
        leftPanel.add(nextButton);
        leftPanel.add(searchLabel);

        // Clear button on the right
        clearButton = new JButton("Clear");
        clearButton.setToolTipText("Clear search results");
        clearButton.addActionListener(e -> clearResults());

        // Research button on the right
        researchButton = new JButton("Research");
        researchButton.setToolTipText("Re-search with the same filter");
        researchButton.addActionListener(e -> performResearch());
        researchButton.setEnabled(false);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightPanel.add(researchButton);
        rightPanel.add(clearButton);

        headerPanel.add(leftPanel, BorderLayout.WEST);
        headerPanel.add(rightPanel, BorderLayout.EAST);

        // Table model and table
        tableModel = new SearchResultTableModel();
        resultsTable = new JTable(tableModel);
        resultsTable.setFillsViewportHeight(true);
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Set header alignment to left
        JTableHeader header = resultsTable.getTableHeader();
        DefaultTableCellRenderer headerRenderer = (DefaultTableCellRenderer) header.getDefaultRenderer();
        headerRenderer.setHorizontalAlignment(SwingConstants.LEFT);

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
        if (history.isEmpty()) {
            return;
        }

        historyIndex += direction;

        if (historyIndex < 0) {
            historyIndex = 0;
        } else if (historyIndex >= history.size()) {
            historyIndex = history.size() - 1;
        }

        if (historyIndex >= 0 && historyIndex < history.size()) {
            currentResults = history.get(historyIndex);
            currentCriteria = historyCriteria.get(historyIndex);
            searchLabetSetText(currentResults);
            updateTable();
            updateNavigationButtons();
        }
    }

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public void searchLabetSetText(SearchResults results) {
        StringBuilder searchText = new StringBuilder();

        // Add time at the beginning
        LocalDateTime dateTime = LocalDateTime.ofInstant(results.getTimestamp(), ZoneId.systemDefault());
        searchText.append(TIME_FORMATTER.format(dateTime))
                .append(" ");

        TreeFocusComponent source = results.getSource();
        if (source != null) {
            searchText.append(" [")
                    .append(source.getDisplayName())
                    .append("] ");
        }
        searchText.append(results.getSearchText());
        searchText.append(" (");
        searchText.append(results.getResultCount());
        searchText.append(" results)");
        searchLabel.setText(searchText.toString());
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
        // Store the criteria with the results in history
        history.add(results);
        historyCriteria.add(criteria);
        historyIndex = history.size() - 1;

        // Update current results and criteria
        currentResults = results;
        currentCriteria = criteria;
        researchButton.setEnabled(true);

        // Update current results and display 
        searchLabetSetText(currentResults);

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
        currentCriteria = null;
        history.clear();
        historyCriteria.clear();
        historyIndex = -1;
        searchLabel.setText("Search results: ");
        tableModel.setResults(new ArrayList<>());
        tableModel.fireTableDataChanged();
        updateNavigationButtons();
        researchButton.setEnabled(false);
    }

    private void performResearch() {
        if (currentCriteria == null) {
            return;
        }

        TreeFocusComponent sourceComponent = currentResults != null ? currentResults.getSource() : null;
        if (sourceComponent == null) {
            sourceComponent = (TreeFocusComponent) master.getActiveEditor();
        }

        if (sourceComponent == null) {
            JOptionPane.showMessageDialog(this, "No source editor available for research",
                    "Research Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JackTreeModel model = sourceComponent.getModel();
        if (model == null) {
            JOptionPane.showMessageDialog(this, "No tree model available",
                    "Research Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<DefaultMutableTreeNode> results = searchTree(model, currentCriteria);

        // Check if results are the same as current results (by comparing edit IDs)
        if (currentResults != null && haveSameResults(currentResults.getResults(), results)) {
            JOptionPane.showMessageDialog(this, "No changes detected - same results",
                    "Research", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String searchText = buildSearchText(currentCriteria);
        SearchResults searchResults = new SearchResults(searchText, results, sourceComponent);

        // Fire the search event to notify listeners (including ourselves via onSearch)
        // We need to manually update as if onSearch was called
        history.add(searchResults);
        historyCriteria.add(currentCriteria);
        historyIndex = history.size() - 1;
        searchLabetSetText(currentResults = searchResults);
        updateTable();
        updateNavigationButtons();
    }

    private boolean haveSameResults(List<DefaultMutableTreeNode> results1, List<DefaultMutableTreeNode> results2) {
        if (results1.size() != results2.size()) {
            return false;
        }

        List<Long> ids1 = extractEditIds(results1);
        List<Long> ids2 = extractEditIds(results2);

        return ids1.equals(ids2);
    }

    private List<Long> extractEditIds(List<DefaultMutableTreeNode> nodes) {
        List<Long> ids = new ArrayList<>(nodes.size());
        for (DefaultMutableTreeNode node : nodes) {
            Object userObject = node.getUserObject();
            if (userObject instanceof EditNode) {
                ids.add(((EditNode) userObject).getEditId());
            }
        }
        return ids;
    }

    private String buildSearchText(SearchToolbar.SearchCriteria criteria) {
        StringBuilder searchText = new StringBuilder("Search: ");
        boolean hasFilter = false;

        if (criteria.hasNameFilter()) {
            searchText.append("Name='").append(criteria.getNameText()).append("'");
            hasFilter = true;
        }

        if (criteria.hasValueFilter()) {
            if (hasFilter) {
                searchText.append(", ");
            }
            searchText.append("Value='").append(criteria.getValueText()).append("'");
            hasFilter = true;
        }

        if (criteria.hasTypeKeyFilter()) {
            if (hasFilter) {
                searchText.append(", ");
            }
            searchText.append("Type='").append(criteria.getTypeKey()).append("'");
            hasFilter = true;
        }

        if (criteria.hasEditStatusFilter()) {
            if (hasFilter) {
                searchText.append(", ");
            }
            searchText.append("Status='").append(criteria.getEditStatus()).append("'");
            hasFilter = true;
        }

        if (!hasFilter) {
            return "Results:";
        }

        return searchText.toString();
    }

    private List<DefaultMutableTreeNode> searchTree(JackTreeModel model, SearchToolbar.SearchCriteria criteria) {
        List<DefaultMutableTreeNode> results = new ArrayList<>();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();

        if (root != null) {
            searchInNode(root, criteria, results);
        }

        return results;
    }

    private void searchInNode(DefaultMutableTreeNode node, SearchToolbar.SearchCriteria criteria, List<DefaultMutableTreeNode> results) {
        Object userObject = node.getUserObject();

        if (userObject instanceof EditNode editNode) {
            boolean matches = true;

            if (matches && criteria.hasNameFilter()) {
                String nodeName = editNode.getName();
                if (nodeName == null || !matchesWildcard(nodeName, criteria.getNameText())) {
                    matches = false;
                }
            }

            if (matches && criteria.hasValueFilter()) {
                String nodeValue = editNode.getValue();
                if (nodeValue == null || !matchesWildcard(nodeValue, criteria.getValueText())) {
                    matches = false;
                }
            }

            if (matches && criteria.hasTypeKeyFilter()) {
                String nodeTypeKey = editNode.getTypeKey();
                if (nodeTypeKey == null || !nodeTypeKey.equals(criteria.getTypeKey())) {
                    matches = false;
                }
            }

            if (matches && criteria.hasEditStatusFilter()) {
                EditStatus nodeStatus = editNode.getEditStatus();
                String filterStatusStr = criteria.getEditStatus();
                if (nodeStatus == null || filterStatusStr == null
                        || !nodeStatus.getLiteral().equals(filterStatusStr)) {
                    matches = false;
                }
            }

            if (matches) {
                results.add(node);
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            searchInNode(child, criteria, results);
        }
    }

    private static boolean matchesWildcard(String text, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return true;
        }
        if (text == null) {
            return false;
        }
        Pattern p = createWildcardPattern(pattern);
        return p.matcher(text).matches();
    }

    private static Pattern createWildcardPattern(String pattern) {
        StringBuilder regex = new StringBuilder();
        regex.append('^');
        for (char c : pattern.toCharArray()) {
            switch (c) {
                case '*':
                    regex.append(".*");
                    break;
                case '?':
                    regex.append('.');
                    break;
                case '.':
                case '^':
                case '$':
                case '\\':
                case '|':
                case '(':
                case ')':
                case '[':
                case ']':
                case '{':
                case '}':
                    regex.append('\\').append(c);
                    break;
                default:
                    regex.append(c);
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
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
