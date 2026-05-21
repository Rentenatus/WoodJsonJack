/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a JSON object node in the tree structure. Based on JsonObjectData
 * with tree structure support added.
 */
public abstract non-sealed class EditNodeAbstract implements EditNode {

    private final long editId;
    private String primValue;
    private EditNodeAbstract parent;
    private final List<EditNodeAbstract> children = new ArrayList<>();

    public EditNodeAbstract(String objektInfo) {
        this.editId = IdGenerator.EDIT_ID_GENERATOR.nextId();
        this.primValue = null;
    }

    public EditNodeAbstract(long editId, String primValue, String objektInfo) {
        this.editId = editId;
        this.primValue = primValue;
    }

    @Override
    public long getEditId() {
        return editId;
    }

    // ========== Tree structure methods ==========
    @Override
    public long getId() {
        return editId;
    }

    @Override
    public String getValue() {
        return primValue;
    }

    @Override
    public void setValue(String value) {
        this.primValue = value;
    }

    @Override
    public EditNodeAbstract getParent() {
        return parent;
    }

    void setParent(EditNodeAbstract parent) {
        this.parent = parent;
    }

    @Override
    public List<EditNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    List<EditNodeAbstract> getAbstractChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public int getChildCount() {
        return children.size();
    }

    @Override
    public EditNodeAbstract getChildAt(int index) {
        return children.get(index);
    }

    @Override
    public int getChildIndex(EditNode child) {
        return children.indexOf(child);
    }

    void addChild(EditNodeAbstract child) {
        addChild(child, children.size());
    }

    void addChild(EditNodeAbstract child, int index) {
        if (child == null) {
            throw new IllegalArgumentException("Child cannot be null");
        }
        if (index < 0 || index > children.size()) {
            throw new IndexOutOfBoundsException("Index: " + index);
        }
        EditNodeAbstract oldParent = child.getParent();
        if (oldParent != null && oldParent != this) {
            oldParent.removeChild(child);
        }
        children.add(index, child);
        child.setParent(this);
    }

    public boolean removeChild(EditNodeAbstract child) {
        boolean removed = children.remove(child);
        if (removed) {
            child.setParent(null);
            // Notify child that it was removed
            child.sayOnRemoved(this);
        }
        return removed;
    }

    public abstract EditNodeAbstract deepCopy(boolean regenerateEditId);

    public EditNodeAbstract deepCopy() {
        return deepCopy(true);
    }

    /**
     * Called when this node is removed from its parent node, giving the parent
     * a chance to update its internal state.
     */
    abstract void sayOnRemoved(EditNode parent);

    // ========== Factory methods ==========
    abstract EditNodeAbstract addNewChild(String aName);

    abstract EditNodeAbstract addNewChild(String aName, int index);

    public abstract EditNodeAbstract createChild(String aName);

    public abstract EditNodeAbstract createNeighbor(String aName);

}
