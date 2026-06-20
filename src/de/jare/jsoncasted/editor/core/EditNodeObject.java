/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.model.builder.JsonStringBuilder;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a JSON object node in the tree structure.
 */
public final class EditNodeObject extends EditNodeAbstract implements EditNode {

    private String objektInfo;
    private String objektId;
    private JsonTypeDescriptor jsonType;

    public EditNodeObject(String objektInfo) {
        super();
        this.objektInfo = objektInfo;
    }

    private EditNodeObject(long editId, long leftRange, long rightRange, long timesRange, String objektInfo) {
        super(editId, leftRange, rightRange, timesRange);
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

    /**
     * Returns the value of this node
     *
     * @return default objektInfo
     */
    public String getValue() {

        // todo: Spaeter muss der Value aus den Properties hier hin!!! 
        return objektInfo;
    }

    // ========== JsonTreeNodeData methods ==========
    public String getObjektInfo() {
        return objektInfo;
    }

    public void setObjektInfo(String objektInfo) {
        this.objektInfo = objektInfo;
    }

    public String getObjektId() {
        return objektId;
    }

    public void setObjektId(String objektId) {
        this.objektId = objektId;
    }

    public JsonTypeDescriptor getJsonType() {
        return jsonType;
    }

    public void setJsonType(JsonTypeDescriptor jsonType) {
        this.jsonType = jsonType;
    }

    @Override
    public String toString() {
        String value = getValue();
        return maskEscapes(value == null ? String.valueOf(objektInfo) : value + " : " + rightString());
    }

    @Override
    public String rightString() {
        return (objektInfo == null || objektInfo.isEmpty()) ? "" : ": " + objektInfo;
    }

    // ========== Factory methods ==========
    @Override
    public EditNodeAbstract addNewChild(String aName, final EditTimes weightMonitor) {
        EditNodeProperty child = createChild(aName);
        addChild(child, weightMonitor);
        return child;
    }

    @Override
    EditNodeAbstract addNewChild(String aName, int index, final EditTimes weightMonitor) {
        EditNodeProperty child = createChild(aName);
        addChild(child, index, weightMonitor);
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
                getLeftRange(), getRightRange(), getTimesRange(),
                objektInfo);

        for (EditNodeAbstract child : getAbstractChildren()) {
            final EditNodeAbstract deepCopy = child.deepCopy(regenerateEditId);
            copy.addChildPhase1(deepCopy, copy.getChildCount());
            copy.addChildPhase2Fast(deepCopy);
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

    @Override
    public String getTypeKey() {
        return "fore.object";
    }

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("name", getName());
        attributes.put("objektId", getObjektId());
        attributes.put("jsonType", getJsonType());
        return putEditAttributes(attributes);
    }

    @Override
    public void setAttributes(Map<String, Object> props) {
        if (props == null) {
            return;
        }
        if (props.containsKey("name")) {
            setName((String) props.get("name"));
        }
        if (props.containsKey("objektId")) {
            setObjektId((String) props.get("objektId"));
        }
        if (props.containsKey("jsonType")) {
            setJsonType((JsonTypeDescriptor) props.get("jsonType"));
        }
    }

}
