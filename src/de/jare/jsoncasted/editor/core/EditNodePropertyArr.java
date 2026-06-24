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
 * Represents a JSON array property node in the tree structure. This is a
 * specialized property that is always of type ARRAY.
 */
public final class EditNodePropertyArr extends EditNodeProperty implements EditNode {

    /**
     * Creates a new array property with the given name. The type is
     * automatically set to ARRAY.
     *
     */
    public EditNodePropertyArr() {
        super("array");
        setType(JsonNodeType.ARRAY);
    }

    /**
     * Creates a new array property with the given parameters. The type is
     * automatically set to ARRAY.
     *
     * @param editId the edit identifier
     * @param leftRange the left range value
     * @param rightRange the right range value
     * @param timesRange the times range value
     * @param propName the name of the array property
     * @param primValue the primitive value (may be null for arrays)
     */
    private EditNodePropertyArr(long editId, long leftRange, long rightRange, long timesRange, String propName, String primValue) {
        super(editId, leftRange, rightRange, timesRange, propName, JsonNodeType.ARRAY, primValue);
    }

    // ========== Type constraints ==========
    @Override
    public void setType(JsonNodeType type) {
        // Array properties are always of type ARRAY
        // Ignore any attempts to change the type
        if (type != JsonNodeType.ARRAY) {
            throw new IllegalArgumentException("Array property type cannot be changed. It must always be ARRAY.");
        }
        super.setType(type);
    }

    @Override
    public JsonNodeType getType() {
        return JsonNodeType.ARRAY;
    }

    @Override
    public boolean canBeChildOf(EditNode parent) {
        // Array properties can only be children of properties (not objects)
        return parent != null && parent.canBeParentOfPropertyArrData();
    }

    // ========== Factory methods ==========
    @Override
    public EditNodePropertyArr createNeighbor(String aName) {
        return new EditNodePropertyArr();
    }

    // ========== Deep Copy ==========
    @Override
    public EditNodeAbstract deepCopy(boolean regenerateEditId) {
        EditNodePropertyArr copy = new EditNodePropertyArr(
                regenerateEditId ? IdGenerator.EDIT_ID_GENERATOR.nextId() : getEditId(),
                getLeftRange(), getRightRange(), getTimesRange(),
                getPropName(), getValue());

        for (EditNodeAbstract child : getAbstractChildren()) {
            final EditNodeAbstract deepCopy = child.deepCopy(regenerateEditId);
            copy.addChildPhase1(deepCopy, copy.getChildCount());
            copy.addChildPhase2Fast(deepCopy);
        }
        copy.setJsonField(getJsonField());
        return copy;
    }

    // ========== Type identification ==========
    @Override
    public String getTypeKey() {
        return FOREARRAY;
    }

    // ========== Attributes ==========
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
        // Type is always ARRAY, so we ignore type from props
        if (props.containsKey("jsonField")) {
            setJsonField((JsonFieldDescriptor) props.get("jsonField"));
        }
    }

    @Override
    public String toString() {
        return maskEscapes(getName() + rightString());
    }

    @Override
    public String rightString() {
        return "[]";
    }
}
