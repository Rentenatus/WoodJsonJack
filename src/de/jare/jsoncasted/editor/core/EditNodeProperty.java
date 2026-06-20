/**
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import de.jare.jsoncasted.lang.JsonNodeType;
import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a JSON property node in the tree structure.
 */
public final class EditNodeProperty extends EditNodeAbstract implements EditNode {

    private String propName;
    private String primValue;
    private JsonNodeType type;
    private JsonFieldDescriptor jsonField;

    public EditNodeProperty(String propName) {
        super();
        this.propName = propName;
        this.primValue = null;
        this.type = JsonNodeType.NULL;
    }

    private EditNodeProperty(long editId, long leftRange, long rightRange, long timesRange, String propName, JsonNodeType type, String primValue) {
        super(editId, leftRange, rightRange, timesRange);
        this.propName = propName;
        this.primValue = primValue;
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

    @Override
    public String getValue() {
        return primValue;
    }

    @Override
    public void setValue(String value) {
        this.primValue = value;
    }

    public JsonNodeType getType() {
        return type;
    }

    public void setType(JsonNodeType type) {
        this.type = type;
    }

    public JsonFieldDescriptor getJsonField() {
        return jsonField;
    }

    public void setJsonField(JsonFieldDescriptor jsonField) {
        this.jsonField = jsonField;
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
    public EditNodeAbstract addNewChild(String aName, final EditTimes weightMonitor) {
        EditNodeObject child = createChild(aName);
        addChild(child, weightMonitor);
        return child;
    }

    @Override
    EditNodeAbstract addNewChild(String aName, int index, final EditTimes weightMonitor) {
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
                getLeftRange(), getRightRange(), getTimesRange(),
                propName, type, getValue());

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

    @Override
    public String getTypeKey() {
        return "fore.property";
    }

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("name", getName());
        attributes.put("primValue", getValue());
        attributes.put("type", getType());
        attributes.put("jsonField", getJsonField());
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
        if (props.containsKey("primValue")) {
            setValue((String) props.get("primValue"));
        }
        if (props.containsKey("type")) {
            setType((JsonNodeType) props.get("type"));
        }
        if (props.containsKey("jsonField")) {
            setJsonField((JsonFieldDescriptor) props.get("jsonField"));
        }
    }
}
