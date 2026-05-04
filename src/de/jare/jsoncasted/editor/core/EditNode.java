/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

import java.util.List;

/**
 * Represents a node in the editable JSON tree structure.
 * Based on JsonTreeNodeData with tree structure methods added.
 */
public sealed interface EditNode permits EditNodeObject, EditNodeProperty {

    // ========== From JsonTreeNodeData ==========
    
    long getEditId();
    
    EditNode createChild(String aName);
    
    EditNode createNeighbor(String aName);
    
    EditNode deepCopy(boolean regenerateEditId);
    
    /**
     * Called when this node is removed from its parent node, giving the parent
     * a chance to update its internal state.
     */
    void sayOnRemoved(EditNode parent);
    
    /**
     * Callback that is invoked when an object child has been removed from this node.
     */
    default void onChildObjectDataRemoved(EditNodeObject child) { }
    
    /**
     * Callback that is invoked when a property child has been removed from this node.
     */
    default void onChildPropertyDataRemoved(EditNodeProperty child) { }
    
    boolean canBeChildOf(EditNode parent);
    
    default boolean canBeParentOfObjectData() { return false; }
    
    default boolean canBeParentOfPropertyData() { return false; }
    
    String getEditText();
    
    void setEditText(String editText);

    // ========== Tree structure methods ==========
    
    /** Returns a unique identifier for this node (alias for getEditId) */
    default long getId() { return getEditId(); }
    
    /** Returns the display name of this node */
    default String getName() { return getEditText(); }
    
    /** Sets the display name of this node */
    default void setName(String name) { setEditText(name); }
    
    /** Returns the value of this node */
    default String getValue() { return null; }
    
    /** Sets the value of this node */
    default void setValue(String value) { }
    
    EditNode getParent();
    
    void setParent(EditNode parent);
    
    List<EditNode> getChildren();
    
    int getChildCount();
    
    EditNode getChildAt(int index);
    
    int getChildIndex(EditNode child);
    
    void addChild(EditNode child);
    
    void addChild(EditNode child, int index);
    
    boolean removeChild(EditNode child);
    
    default EditNode deepCopy() { return deepCopy(true); }
}
