/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.data;

import java.awt.Color;

/**
 *
 */
public final class JsonObjectData implements JsonTreeNodeData {

    private final long editId;
    private String objektInfo; // z.B. "root" 
    private String primValue; // z.B. "root"  

    public JsonObjectData(String objektInfo) {
        this.editId = IdGenerator.EDIT_ID_GENERATOR.nextId();
        this.objektInfo = objektInfo;
        this.primValue = null;
    }

    protected JsonObjectData(long editId, String primValue, String objektInfo) {
        this.editId = editId;
        this.objektInfo = objektInfo;
        this.primValue = primValue;
    }

    @Override
    public long getEditId() {
        return editId;
    }

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
        return primValue == null ? objektInfo : primValue + " : " + objektInfo;
    }

    @Override
    public JsonTreeNodeData createChild(String aName) {
        return new JsonPropertyData(aName);
    }

    @Override
    public JsonTreeNodeData createNeighbor(String aName) {
        return new JsonObjectData(aName);
    }

    @Override
    public JsonTreeNodeData deepCopy(boolean regenerateEditId) {
        return new JsonObjectData(regenerateEditId ? IdGenerator.EDIT_ID_GENERATOR.nextId() : editId, primValue, objektInfo);
    }

    @Override
    public void sayOnRemoved(JsonTreeNodeData parent) {
        parent.onChildObjectDataRemoved(this);
    }

    @Override
    public void onChilPropertyDataRemoved(JsonPropertyData child) {
    }

    @Override
    public boolean canBeChildOf(JsonTreeNodeData nodeDate) {
        if (nodeDate == null) {
            return false;
        }
        return nodeDate.canBeParentOfObjectData();
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
    public String getInfoText() {
        return (objektInfo == null || objektInfo.isEmpty()) ? "" : ": " + objektInfo;
    }

    @Override
    public String getForecolorKey() {
        return "fore.object";
    }
}
