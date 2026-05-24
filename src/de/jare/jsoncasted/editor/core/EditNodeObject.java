/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

/**
 * Represents a JSON object node in the tree structure.
 */
public final class EditNodeObject extends EditNodeAbstract implements EditNode {

    private String objektInfo;

    public EditNodeObject(String objektInfo) {
        super(objektInfo);
        this.objektInfo = objektInfo;
    }

    private EditNodeObject(long editId, long leftRange, long rightRange, String primValue, String objektInfo) {
        super(editId, leftRange, rightRange, primValue, objektInfo);
        this.objektInfo = objektInfo;
    }

    // ========== Name / Label ==========
    @Override
    public String getName() {
        return objektInfo;
    }

    @Override
    public void setName(String name) {
        this.objektInfo = name;
    }

    // ========== JsonTreeNodeData methods ==========
    public String getObjektInfo() {
        return objektInfo;
    }

    public void setObjektInfo(String objektInfo) {
        this.objektInfo = objektInfo;
    }

    @Override
    public String toString() {
        String value = getValue();
        return value == null ? objektInfo : value + " : " + rightString();
    }

    @Override
    public String rightString() {
        return (objektInfo == null || objektInfo.isEmpty()) ? "" : ": " + objektInfo;
    }

    // ========== Factory methods ==========
    @Override
    public EditNodeAbstract addNewChild(String aName) {
        EditNodeProperty child = createChild(aName);
        addChild(child);
        return child;
    }

    @Override
    EditNodeAbstract addNewChild(String aName, int index) {
        EditNodeProperty child = createChild(aName);
        addChild(child, index);
        return child;
    }

    @Override
    public EditNodeProperty createChild(String aName) {
        return new EditNodeProperty(aName);
    }

    @Override
    public EditNodeObject createNeighbor(String aName) {
        return new EditNodeObject(aName);
    }

    // ========== Deep Copy ==========
    @Override
    public EditNodeAbstract deepCopy(boolean regenerateEditId) {
        EditNodeObject copy = new EditNodeObject(
                regenerateEditId ? IdGenerator.EDIT_ID_GENERATOR.nextId() : getEditId(),
                getLeftRange(), getRightRange(),
                getValue(), objektInfo);

        for (EditNodeAbstract child : getAbstractChildren()) {
            copy.addChildPhase1(child.deepCopy(regenerateEditId), copy.getChildCount());
        }
        return copy;
    }

    // ========== Removal callback ==========
    @Override
    public void sayOnRemoved(EditNode parent) {
        if (parent instanceof EditNodeProperty prop) {
            prop.onChildObjectDataRemoved(this);
        }
    }

    // ========== Type constraints ==========
    @Override
    public boolean canBeChildOf(EditNode parent) {
        return parent != null && parent.canBeParentOfObjectData();
    }

    @Override
    public boolean canBeParentOfPropertyData() {
        return true;
    }

    // ========== Edit text ==========
    @Override
    public String getEditText() {
        return getValue();
    }

    @Override
    public void setEditText(String editText) {
        setValue(editText == null || editText.isEmpty() ? null : editText);
    }

    @Override
    public String getTypeKey() {
        return "fore.object";
    }
}
