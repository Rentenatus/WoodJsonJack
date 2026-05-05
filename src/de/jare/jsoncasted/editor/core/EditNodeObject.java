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
public final class EditNodeObject implements EditNode {

    private final long editId;
    private String objektInfo;
    private String primValue;
    private EditNode parent;
    private final List<EditNode> children = new ArrayList<>();

    public EditNodeObject(String objektInfo) {
        this.editId = IdGenerator.EDIT_ID_GENERATOR.nextId();
        this.objektInfo = objektInfo;
        this.primValue = null;
    }

    public EditNodeObject(long editId, String primValue, String objektInfo) {
        this.editId = editId;
        this.objektInfo = objektInfo;
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
    public String getName() {
        return objektInfo;
    }

    @Override
    public void setName(String name) {
        this.objektInfo = name;
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
    public EditNode getParent() {
        return parent;
    }

    @Override
    public void setParent(EditNode parent) {
        this.parent = parent;
    }

    @Override
    public List<EditNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public int getChildCount() {
        return children.size();
    }

    @Override
    public EditNode getChildAt(int index) {
        return children.get(index);
    }

    @Override
    public int getChildIndex(EditNode child) {
        return children.indexOf(child);
    }

    @Override
    public void addChild(EditNode child) {
        addChild(child, children.size());
    }

    @Override
    public void addChild(EditNode child, int index) {
        if (child == null) {
            throw new IllegalArgumentException("Child cannot be null");
        }
        if (index < 0 || index > children.size()) {
            throw new IndexOutOfBoundsException("Index: " + index);
        }
        EditNode oldParent = child.getParent();
        if (oldParent != null && oldParent != this) {
            oldParent.removeChild(child);
        }
        children.add(index, child);
        child.setParent(this);
    }

    @Override
    public boolean removeChild(EditNode child) {
        boolean removed = children.remove(child);
        if (removed) {
            child.setParent(null);
            // Notify child that it was removed
            child.sayOnRemoved(this);
        }
        return removed;
    }

    @Override
    public EditNode deepCopy() {
        EditNodeObject copy = new EditNodeObject(getEditId(), primValue, objektInfo);
        for (EditNode child : children) {
            copy.addChild(child.deepCopy());
        }
        return copy;
    }

    // ========== JsonTreeNodeData methods ==========
    public String getObjektInfo() {
        return objektInfo;
    }

    public void setObjektInfo(String objektInfo) {
        this.objektInfo = objektInfo;
    }

    public String getPrimValue() {
        return primValue;
    }

    public void setPrimValue(String primValue) {
        this.primValue = primValue;
    }

    @Override
    public String toString() {
        return primValue == null ? objektInfo : primValue + " : " + rightString();
    }

    @Override
    public String rightString() {
        return (objektInfo == null || objektInfo.isEmpty()) ? "" : ": " + objektInfo;
    }

    @Override
    public EditNode createChild(String aName) {
        EditNodeProperty child = new EditNodeProperty(aName);
        addChild(child);
        return child;
    }

    @Override
    public EditNode createNeighbor(String aName) {
        return new EditNodeObject(aName);
    }

    @Override
    public EditNode deepCopy(boolean regenerateEditId) {
        EditNodeObject copy = new EditNodeObject(
                regenerateEditId ? IdGenerator.EDIT_ID_GENERATOR.nextId() : editId,
                primValue, objektInfo);
        for (EditNode child : children) {
            copy.addChild(child.deepCopy(regenerateEditId));
        }
        return copy;
    }

    @Override
    public void sayOnRemoved(EditNode parent) {
        // For objects: notify parent about removal
        if (parent instanceof EditNodeProperty) {
            ((EditNodeProperty) parent).onChildObjectDataRemoved(this);
        }
    }

    @Override
    public boolean canBeChildOf(EditNode parent) {
        if (parent == null) {
            return false;
        }
        return parent.canBeParentOfObjectData();
    }

    @Override
    public boolean canBeParentOfPropertyData() {
        return true;
    }

    @Override
    public String getEditText() {
        return primValue;
    }

    @Override
    public void setEditText(String editText) {
        this.primValue = editText.isEmpty() ? null : editText;
    }

    @Override
    public String getTypeKey() {
        return "fore.object";
    }
}
