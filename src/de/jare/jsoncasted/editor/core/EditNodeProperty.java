/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

/**
 * Represents a JSON property node in the tree structure.
 */
public final class EditNodeProperty extends EditNodeAbstract implements EditNode {

    private String propName;
    private String type;

    public EditNodeProperty(String propName) {
        super(propName);
        this.propName = propName;
        this.type = "";
    }

    private EditNodeProperty(long editId, long leftRange, long rightRange, String propName, String type, String primValue) {
        super(editId, leftRange, rightRange, primValue, propName);
        this.propName = propName;
        this.type = type;
    }

    // ========== Name / Label ==========
    @Override
    public String getName() {
        return propName;
    }

    @Override
    public void setName(String name) {
        this.propName = name;
    }

    // ========== JsonTreeNodeData methods ==========
    public String getPropName() {
        return propName;
    }

    public void setPropName(String propName) {
        this.propName = propName.replace('=', ' ')
                .trim()
                .replace(' ', '_');
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return propName + rightString();
    }

    @Override
    public String rightString() {
        String value = getValue();
        return value == null ? " =" : " = '" + value + "'";
    }

    // ========== Factory methods ==========
    @Override
    public EditNodeAbstract addNewChild(String aName, final Object weightMonitor) {
        EditNodeObject child = createChild(aName);
        addChild(child, weightMonitor);
        return child;
    }

    @Override
    EditNodeAbstract addNewChild(String aName, int index, final Object weightMonitor) {
        EditNodeObject child = createChild(aName);
        addChild(child, index, weightMonitor);
        return child;
    }

    @Override
    public EditNodeObject createChild(String aName) {
        return new EditNodeObject(aName);
    }

    @Override
    public EditNodeProperty createNeighbor(String aName) {
        return new EditNodeProperty(aName);
    }

    // ========== Deep Copy ==========
    @Override
    public EditNodeAbstract deepCopy(boolean regenerateEditId) {
        EditNodeProperty copy = new EditNodeProperty(
                regenerateEditId ? IdGenerator.EDIT_ID_GENERATOR.nextId() : getEditId(),
                getLeftRange(), getRightRange(),
                propName, type, getValue());

        for (EditNodeAbstract child : getAbstractChildren()) {
            final EditNodeAbstract deepCopy = child.deepCopy(regenerateEditId);
            copy.addChildPhase1(deepCopy, copy.getChildCount(), this);
            copy.addChildPhase2Fast(deepCopy);
        }
        return copy;
    }

    // ========== Removal callback ==========
    @Override
    public void sayOnRemoved(EditNode parent) {
        // No special handling for properties
    }

    @Override
    public void onChildObjectDataRemoved(EditNodeObject child) {
        // When an object child is removed, store its primitive value
        setValue(child.getValue());
    }

    // ========== Type constraints ==========
    @Override
    public boolean canBeChildOf(EditNode parent) {
        return parent != null && parent.canBeParentOfPropertyData();
    }

    @Override
    public boolean canBeParentOfObjectData() {
        return true;
    }

    // ========== Edit text ==========
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
