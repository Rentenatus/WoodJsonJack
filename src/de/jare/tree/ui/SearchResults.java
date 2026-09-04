/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.tree.ui;

import de.jare.tree.control.listeners.TreeFocusComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Immutable container for search results, including the search text/description
 * and the matching tree nodes.
 */
public class SearchResults {

    private final String searchText;
    private final List<DefaultMutableTreeNode> results;
    private final Instant timestamp;
    private final TreeFocusComponent source;

    /**
     * Creates a new SearchResults instance.
     *
     * @param searchText descriptive text of the search criteria
     * @param results the list of matching tree nodes (defensive copy will be created)
     * @param source the TreeFocusComponent that was searched (the editor context)
     */
    public SearchResults(String searchText, List<DefaultMutableTreeNode> results, TreeFocusComponent source) {
        this.searchText = searchText != null ? searchText : "";
        this.results = results != null ? new ArrayList<>(results) : new ArrayList<>();
        this.timestamp = Instant.now();
        this.source = source;
    }

    /**
     * Returns the descriptive search text.
     */
    public String getSearchText() {
        return searchText;
    }

    /**
     * Returns the list of matching tree nodes.
     * The returned list is unmodifiable.
     */
    public List<DefaultMutableTreeNode> getResults() {
        return Collections.unmodifiableList(results);
    }

    /**
     * Returns the number of matching results.
     */
    public int getResultCount() {
        return results.size();
    }

    /**
     * Returns whether there are any results.
     */
    public boolean hasResults() {
        return !results.isEmpty();
    }

    /**
     * Returns the timestamp when this search was performed.
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the TreeFocusComponent that was the source of this search.
     * This allows tracking which editor context the results belong to.
     * From the TreeFocusComponent, you can access the EditTree via getModel().getEditTree().
     */
    public TreeFocusComponent getSource() {
        return source;
    }

    @Override
    public String toString() {
        return searchText + " (" + results.size() + " results)";
    }
}
