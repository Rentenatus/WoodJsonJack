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
 * Represents a JSON property node in the tree structure. Based on
 * JsonPropertyData with tree structure support added.
 */
public final class EditNodeProperty implements EditNode {

    private final long editId;
    private String propName;
    private String type;
    private String primValue;
    private EditNode parent;
    private final List<EditNode> children = new ArrayList<>();

    public EditNodeProperty(long editId, String propName, String type, String primValue) {
        this.editId = editId;
        this.propName = propName;
        this.type = type;
        this.primValue = primValue;
    }

    public EditNodeProperty(String propName) {
        this.editId = IdGenerator.EDIT_ID_GENERATOR.nextId();
        this.propName = propName;
        this.type = "";
        this.primValue = null;
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
        return propName;
    }

    @Override
    public void setName(String name) {
        this.propName = name;
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
            child.sayOnRemoved(this);
        }
        return removed;
    }

    @Override
    public EditNode deepCopy() {
        EditNodeProperty copy = new EditNodeProperty(getId(), propName, type, primValue);
        for (EditNode child : children) {
            copy.addChild(child.deepCopy());
        }
        return copy;
    }

    // ========== JsonTreeNodeData methods ==========
    public String getPropName() {
        return propName;
    }

    public void setPropName(String propName) {
        this.propName = propName.replace('=', ' ').trim().replace(' ', '_');
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPrimValue() {
        return primValue;
    }

    public void setPrimValue(String primValue) {
        this.primValue = primValue;
    }

    @Override
    public String toString() {
        return propName + rightString();
    }

    @Override
    public String rightString() {
        return primValue == null ? " =" : " = '" + primValue + "'";
    }

    @Override
    public EditNode createChild(String aName) {
        EditNodeObject child = new EditNodeObject(aName);
        addChild(child);
        return child;
    }

    @Override
    public EditNode createNeighbor(String aName) {
        return new EditNodeProperty(aName);
    }

    @Override
    public EditNode deepCopy(boolean regenerateEditId) {
        EditNodeProperty copy = new EditNodeProperty(
                regenerateEditId ? IdGenerator.EDIT_ID_GENERATOR.nextId() : editId,
                propName, type, primValue);
        for (EditNode child : children) {
            copy.addChild(child.deepCopy(regenerateEditId));
        }
        return copy;
    }

    @Override
    public void sayOnRemoved(EditNode parent) {
        // For properties: if removed from parent, no special handling needed
    }

    @Override
    public void onChildObjectDataRemoved(EditNodeObject child) {
        // When an object child is removed from this property,
        // store its primitive value in this property
        primValue = child.getPrimValue();
    }

    @Override
    public boolean canBeChildOf(EditNode parent) {
        if (parent == null) {
            return false;
        }
        return parent.canBeParentOfPropertyData();
    }

    @Override
    public boolean canBeParentOfObjectData() {
        return true;
    }

    @Override
    public String getEditText() {
        return propName;
    }

    @Override
    public void setEditText(String editText) {
        this.propName = editText;
    }

    @Override
    public String getTypeKey() {
        return "fore.property";
    }
}
