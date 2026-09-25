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
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Toolbar for searching nodes in the tree by name, value, type key, or edit
 * status.
 */
public class SearchToolbar extends JPanel implements TreeFocusListener {

    private final JackMasterControl master;
    private final JTextField nameField;
    private final JTextField valueField;
    private final JComboBox<String> typeKeyComboBox;
    private final JComboBox<EditStatus> statusComboBox;
    private final JButton searchButton;
    private final JButton clearButton;

    private TreeFocusComponent currentEditor;

    private List<SearchListener> searchListeners = new ArrayList<>();

    /**
     * Converts a wildcard pattern to a regex pattern. ? matches any single
     * character, * matches any sequence of characters.
     */
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

    /**
     * Checks if a string matches a wildcard pattern. Supports * (any sequence)
     * and ? (any single character).
     */
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

    /**
     * Pre-processes a search pattern: if it contains only uppercase letters and
     * digits (and no wildcards or lowercase letters), insert * after each
     * character. Example: "WJJ" becomes "W*J*J*", "B2B" becomes "B*2*B*"
     */
    private static String preprocessSearchPattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return pattern;
        }

        // Check if pattern contains only uppercase letters and digits
        // and does NOT contain wildcards (* or ?) or lowercase letters
        boolean onlyUppercaseAndDigits = true;
        for (char c : pattern.toCharArray()) {
            if (Character.isLowerCase(c) || c == '*' || c == '?') {
                onlyUppercaseAndDigits = false;
                break;
            }
            if (!Character.isUpperCase(c) && !Character.isDigit(c)) {
                onlyUppercaseAndDigits = false;
                break;
            }
        }

        if (onlyUppercaseAndDigits) {
            StringBuilder result = new StringBuilder();
            for (char c : pattern.toCharArray()) {
                result.append(c).append('*');
            }
            return result.toString();
        }

        return pattern;
    }

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
    private static final EditStatus[] EDIT_STATUSES = {
        null,
        EditStatus.STATELESS,
        EditStatus.OKAY,
        EditStatus.WARNING,
        EditStatus.ERROR
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
        private final String valueText;
        private final String typeKey;
        private final String editStatus;

        public SearchCriteria(String nameText, String valueText, String typeKey, String editStatus) {
            this.nameText = nameText;
            this.valueText = valueText;
            this.typeKey = typeKey;
            this.editStatus = editStatus;
        }

        public String getNameText() {
            return nameText;
        }

        public String getValueText() {
            return valueText;
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

        public boolean hasValueFilter() {
            return valueText != null && !valueText.trim().isEmpty();
        }

        public boolean hasTypeKeyFilter() {
            return typeKey != null && !typeKey.trim().isEmpty();
        }

        public boolean hasEditStatusFilter() {
            return editStatus != null && !editStatus.trim().isEmpty();
        }

        public boolean hasAnyFilter() {
            return hasNameFilter() || hasValueFilter() || hasTypeKeyFilter() || hasEditStatusFilter();
        }
    }

    public SearchToolbar(JackMasterControl master) {
        super(new FlowLayout(FlowLayout.LEFT));
        this.master = master;

        // Name search field
        nameField = new JTextField(15);
        nameField.setToolTipText("Search by node name (getName()). Supports wildcards: * (any sequence), ? (any single char)");

        // Value search field
        valueField = new JTextField(15);
        valueField.setToolTipText("Search by node value (getValue()). Supports wildcards: * (any sequence), ? (any single char)");

        // Type key combo box
        typeKeyComboBox = new JComboBox<>(TYPE_KEYS);
        typeKeyComboBox.setToolTipText("Search by type key (e.g., 'fore.object', 'fore.property')");
        typeKeyComboBox.setEditable(true);

        // Edit status combo box
        statusComboBox = new JComboBox<>(EDIT_STATUSES);
        statusComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof EditStatus) {
                    setText(((EditStatus) value).getName());
                } else if (value == null) {
                    setText(" "); // Ein Leerzeichen für bessere Darstellung (keine Auswahl)
                }
                return this;
            }
        });
        statusComboBox.setToolTipText("Filter by edit status (empty = no filter)");

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
        add(new JLabel("Value:"));
        add(valueField);
        add(new JLabel("Type:"));
        add(typeKeyComboBox);
        add(new JLabel("Status:"));
        add(statusComboBox);
        add(searchButton);
        add(clearButton);

        // Register as TreeFocusListener to track active editor
        master.addSelectionListener(5, this);
    }

    private void performSearch() {
        String nameText = nameField.getText().trim();
        String valueText = valueField.getText().trim();
        String typeKey = (String) typeKeyComboBox.getSelectedItem();
        if (typeKey != null) {
            typeKey = typeKey.trim();
        }
        EditStatus editStatus = (EditStatus) statusComboBox.getSelectedItem();

        // Pre-process name and value patterns for uppercase+digits only
        nameText = preprocessSearchPattern(nameText);
        valueText = preprocessSearchPattern(valueText);

        String editStatusString = editStatus != null ? editStatus.getLiteral() : null;
        SearchCriteria criteria = new SearchCriteria(nameText, valueText, typeKey, editStatusString);

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
            EditStatus status = EditStatus.getByName(criteria.getEditStatus());
            String statusDisplay = status != null ? status.getLiteral() : criteria.getEditStatus();
            searchText.append("Status='").append(statusDisplay).append("'");
            hasFilter = true;
        }

        if (!hasFilter) {
            return "Results:";
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
            if (matches && criteria.hasNameFilter()) {
                String nodeName = editNode.getName();
                if (nodeName == null || !matchesWildcard(nodeName, criteria.getNameText())) {
                    matches = false;
                }
            }

            // Check value filter
            if (matches && criteria.hasValueFilter()) {
                String nodeValue = editNode.getValue();
                if (nodeValue == null || !matchesWildcard(nodeValue, criteria.getValueText())) {
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

        // Recursively search children
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            searchInNode(child, criteria, results);
        }
    }

    private void clearSearch() {
        nameField.setText("");
        valueField.setText("");
        typeKeyComboBox.setSelectedIndex(0);
        statusComboBox.setSelectedIndex(0);
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
     *
     * @param editor
     */
    public void setCurrentEditor(TreeFocusComponent editor) {
        this.currentEditor = editor;
    }

    /**
     * Returns the name search field.
     *
     * @return
     */
    public JTextField getNameField() {
        return nameField;
    }

    /**
     * Returns the value search field.
     *
     * @return
     */
    public JTextField getValueField() {
        return valueField;
    }

    /**
     * Returns the type key combo box.
     *
     * @return
     */
    public JComboBox<String> getTypeKeyComboBox() {
        return typeKeyComboBox;
    }

    /**
     * Returns the edit status combo box.
     *
     * @return
     */
    public JComboBox<EditStatus> getStatusComboBox() {
        return statusComboBox;
    }
}
