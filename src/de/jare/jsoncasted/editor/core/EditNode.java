/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import java.util.List;

/**
 * Represents a node in the editable JSON tree structure. Based on
 * JsonTreeNodeData with tree structure methods added.
 */
public sealed interface EditNode permits EditNodeAbstract, EditNodeObject, EditNodeProperty {

    long getEditId();

    public String rightString();

    /**
     * Callback that is invoked when an object child has been removed from this
     * node.
     *
     * @param child
     */
    default void onChildObjectDataRemoved(EditNodeObject child) {
    }

    /**
     * Callback that is invoked when a property child has been removed from this
     * node.
     *
     * @param child
     */
    default void onChildPropertyDataRemoved(EditNodeProperty child) {
    }

    boolean canBeChildOf(EditNode parent);

    default boolean canBeParentOfObjectData() {
        return false;
    }

    default boolean canBeParentOfPropertyData() {
        return false;
    }

    String getEditText();

    long getLeftRange();

    long getRightRange();

    long getTimesRange();

    void setEditText(String editText);

    // ========== Tree structure methods ==========
    /**
     * Returns the display name of this node
     *
     * @return
     */
    default String getName() {
        return getEditText();
    }

    /**
     * Sets the display name of this node
     *
     * @param name
     */
    default void setName(String name) {
        setEditText(name);
    }

    /**
     * Returns the value of this node
     *
     * @return
     */
    default String getValue() {
        return null;
    }

    /**
     * Sets the value of this node
     *
     * @param value
     */
    default void setValue(String value) {
    }

    EditNode getParent();

    default boolean isOrHasParent(EditNode maybeParent) {
        return this == maybeParent || hasParent(maybeParent);

    }

    default boolean hasParent(EditNode maybeParent) {
        EditNode parent = getParent();
        if (parent == null) {
            return false;
        }
        return parent == maybeParent || parent.hasParent(maybeParent);
    }

    List<EditNode> getChildren();

    int getChildCount();

    int getWeight(EditTimes weightMonitor);

    int getCachedWeight();

    EditNode getChildAt(int index);

    int getChildIndex(EditNode child);

    public String getTypeKey();
}
