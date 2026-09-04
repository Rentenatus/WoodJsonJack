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
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Toolbar for searching nodes in the tree by name, type key, or edit status.
 */
public class SearchToolbar extends JPanel implements TreeFocusListener {

    private final JackMasterControl master;
    private final JTextField nameField;
    private final JComboBox<String> typeKeyComboBox;
    private final JComboBox<String> statusComboBox;
    private final JButton searchButton;
    private final JButton clearButton;

    private TreeFocusComponent currentEditor;

    private List<SearchListener> searchListeners = new ArrayList<>();

    /**
     * Possible type key values for filtering.
     */
    private static final String[] TYPE_KEYS = {
        "",
        "fore.object",
        "fore.property",
        "fore.array"
    };

    /**
     * Possible edit status values for filtering.
     */
    private static final String[] EDIT_STATUSES = {
        "",
        EditNode.EDIT_STATELESS,
        EditNode.EDIT_OKAY,
        EditNode.EDIT_WARNING,
        EditNode.EDIT_ERROR
    };

    /**
     * Listener interface for search events.
     */
    public interface SearchListener {
        void onSearch(SearchCriteria criteria, SearchResults results);
    }

    /**
     * Criteria for node search.
     */
    public static class SearchCriteria {
        private final String nameText;
        private final String typeKey;
        private final String editStatus;

        public SearchCriteria(String nameText, String typeKey, String editStatus) {
            this.nameText = nameText;
            this.typeKey = typeKey;
            this.editStatus = editStatus;
        }

        public String getNameText() {
            return nameText;
        }

        public String getTypeKey() {
            return typeKey;
        }

        public String getEditStatus() {
            return editStatus;
        }

        public boolean hasNameFilter() {
            return nameText != null && !nameText.trim().isEmpty();
        }

        public boolean hasTypeKeyFilter() {
            return typeKey != null && !typeKey.trim().isEmpty();
        }

        public boolean hasEditStatusFilter() {
            return editStatus != null && !editStatus.trim().isEmpty();
        }

        public boolean hasAnyFilter() {
            return hasNameFilter() || hasTypeKeyFilter() || hasEditStatusFilter();
        }
    }

    public SearchToolbar(JackMasterControl master) {
        super(new FlowLayout(FlowLayout.LEFT));
        this.master = master;

        // Name search field
        nameField = new JTextField(15);
        nameField.setToolTipText("Search by node name (getName())");

        // Type key combo box
        typeKeyComboBox = new JComboBox<>(TYPE_KEYS);
        typeKeyComboBox.setToolTipText("Search by type key (e.g., 'fore.object', 'fore.property')");
        typeKeyComboBox.setEditable(true);

        // Edit status combo box
        statusComboBox = new JComboBox<>(EDIT_STATUSES);
        statusComboBox.setToolTipText("Filter by edit status");

        // Search button
        searchButton = new JButton("Search");
        searchButton.addActionListener(e -> performSearch());
        searchButton.setToolTipText("Perform search with current criteria");

        // Clear button
        clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> clearSearch());
        clearButton.setToolTipText("Clear all search fields");

        // Add components to toolbar
        add(new JLabel("Name:"));
        add(nameField);
        add(new JLabel("Type:"));
        add(typeKeyComboBox);
        add(new JLabel("Status:"));
        add(statusComboBox);
        add(searchButton);
        add(clearButton);

        // Register as TreeFocusListener to track active editor
        master.addSelectionListener(5, this);

        // Add Enter key listener for name field
        nameField.addActionListener(e -> performSearch());
        typeKeyComboBox.addActionListener(e -> performSearch());
    }

    private void performSearch() {
        String nameText = nameField.getText().trim();
        String typeKey = (String) typeKeyComboBox.getSelectedItem();
        if (typeKey != null) {
            typeKey = typeKey.trim();
        }
        String editStatus = (String) statusComboBox.getSelectedItem();

        if (editStatus != null) {
            editStatus = editStatus.trim();
        }

        SearchCriteria criteria = new SearchCriteria(nameText, typeKey, editStatus);

        if (!criteria.hasAnyFilter()) {
            JOptionPane.showMessageDialog(this, "Please enter at least one search criterion", 
                "Search Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (currentEditor == null) {
            JOptionPane.showMessageDialog(this, "No active editor selected", 
                "Search Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JackTreeModel model = getActiveTreeModel();
        if (model == null) {
            JOptionPane.showMessageDialog(this, "No tree model available", 
                "Search Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<DefaultMutableTreeNode> results = searchTree(model, criteria);
        fireSearchEvent(criteria, results, buildSearchText(criteria));
    }

    private String buildSearchText(SearchCriteria criteria) {
        StringBuilder searchText = new StringBuilder("Search: ");
        boolean hasFilter = false;
        
        if (criteria.hasNameFilter()) {
            searchText.append("Name='").append(criteria.getNameText()).append("'");
            hasFilter = true;
        }
        
        if (criteria.hasTypeKeyFilter()) {
            if (hasFilter) searchText.append(", ");
            searchText.append("Type='").append(criteria.getTypeKey()).append("'");
            hasFilter = true;
        }
        
        if (criteria.hasEditStatusFilter()) {
            if (hasFilter) searchText.append(", ");
            searchText.append("Status='").append(criteria.getEditStatus()).append("'");
        }
        
        if (!hasFilter) {
            return "Search results";
        }
        
        return searchText.toString();
    }

    private JackTreeModel getActiveTreeModel() {
        if (currentEditor != null) {
            return currentEditor.getModel();
        }
        return null;
    }

    private List<DefaultMutableTreeNode> searchTree(JackTreeModel model, SearchCriteria criteria) {
        List<DefaultMutableTreeNode> results = new ArrayList<>();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        
        if (root != null) {
            searchInNode(root, criteria, results);
        }
        
        return results;
    }

    private void searchInNode(DefaultMutableTreeNode node, SearchCriteria criteria, List<DefaultMutableTreeNode> results) {
        Object userObject = node.getUserObject();
        
        if (userObject instanceof EditNode editNode) {
            boolean matches = true;
            
            // Check name filter
            if (criteria.hasNameFilter()) {
                String nodeName = editNode.getName();
                if (nodeName == null || !nodeName.contains(criteria.getNameText())) {
                    matches = false;
                }
            }
            
            // Check type key filter
            if (matches && criteria.hasTypeKeyFilter()) {
                String nodeTypeKey = editNode.getTypeKey();
                if (nodeTypeKey == null || !nodeTypeKey.equals(criteria.getTypeKey())) {
                    matches = false;
                }
            }
            
            // Check edit status filter
            if (matches && criteria.hasEditStatusFilter()) {
                Object nodeStatus = editNode.getEditStatus();
                if (nodeStatus == null || !nodeStatus.toString().equals(criteria.getEditStatus())) {
                    matches = false;
                }
            }
            
            if (matches) {
                results.add(node);
            }
        }
        
        // Recursively search children
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            searchInNode(child, criteria, results);
        }
    }

    private void clearSearch() {
        nameField.setText("");
        typeKeyComboBox.setSelectedIndex(0);
        statusComboBox.setSelectedIndex(0);
        
        // Fire empty search event
        SearchCriteria emptyCriteria = new SearchCriteria("", "", "");
        fireSearchEvent(emptyCriteria, new ArrayList<>(), "");
    }

    private void fireSearchEvent(SearchCriteria criteria, List<DefaultMutableTreeNode> results, String searchText) {
        TreeFocusComponent source = getActiveSource();
        SearchResults searchResults = new SearchResults(searchText, results, source);
        for (SearchListener listener : searchListeners) {
            listener.onSearch(criteria, searchResults);
        }
    }

    private TreeFocusComponent getActiveSource() {
        return currentEditor;
    }

    public void addSearchListener(SearchListener listener) {
        searchListeners.add(listener);
    }

    public void removeSearchListener(SearchListener listener) {
        searchListeners.remove(listener);
    }

    @Override
    public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
        this.currentEditor = editor;
    }

    /**
     * Sets the current active editor for searching.
     */
    public void setCurrentEditor(TreeFocusComponent editor) {
        this.currentEditor = editor;
    }

    /**
     * Returns the name search field.
     */
    public JTextField getNameField() {
        return nameField;
    }

    /**
     * Returns the type key combo box.
     */
    public JComboBox<String> getTypeKeyComboBox() {
        return typeKeyComboBox;
    }

    /**
     * Returns the edit status combo box.
     */
    public JComboBox<String> getStatusComboBox() {
        return statusComboBox;
    }
}
