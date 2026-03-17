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
public final class JsonPropertyData implements JsonTreeNodeData {

    private final long editId;
    private String propName;
    private String type; // "string", "number", "object", "array", ...
    private String primValue; // "string", "number", aber kein Object, Array, ...

    protected JsonPropertyData(long editId, String propName, String type, String primValue) {
        this.editId = editId;
        this.propName = propName;
        this.type = type;
        this.primValue = primValue;
    }

    public JsonPropertyData(String propName) {
        this.editId = IdGenerator.EDIT_ID_GENERATOR.nextId();
        this.propName = propName;
        this.type = "";
        this.primValue = null;
    }

    @Override
    public long getEditId() {
        return editId;
    }

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
        return primValue == null ? propName + " = " : propName + " = '" + primValue + "'";
    }

    @Override
    public JsonTreeNodeData createChild(String aName) {
        JsonObjectData ret = new JsonObjectData(IdGenerator.EDIT_ID_GENERATOR.nextId(), primValue, aName);
        primValue = null;
        return ret;
    }

    @Override
    public JsonTreeNodeData createNeighbor(String aName) {
        return new JsonPropertyData(aName);
    }

    @Override
    public JsonTreeNodeData deepCopy(boolean regenerateEditId) {
        return new JsonPropertyData(regenerateEditId ? IdGenerator.EDIT_ID_GENERATOR.nextId() : editId, propName, type, primValue);
    }

    @Override
    public void sayOnRemoved(JsonTreeNodeData parent) {
        parent.onChilPropertyDataRemoved(this);
    }

    @Override
    public void onChildObjectDataRemoved(JsonObjectData child) {
        primValue = child.getPrimValue();
    }

    @Override
    public boolean canBeChildOf(JsonTreeNodeData nodeDate) {
        if (nodeDate == null) {
            return false;
        }
        return nodeDate.canBeParentOfPropertyData();
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
    public String getInfoText() {
        return primValue == null ? " =" : " = '" + primValue + "'";
    }

    @Override
    public Color getForecolor() {
        return Color.BLUE;
    }

}
