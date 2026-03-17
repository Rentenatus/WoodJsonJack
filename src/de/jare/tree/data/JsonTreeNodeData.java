/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.data;

import java.awt.Color;

/**
 * Represents the model data of a node in the JSON tree.
 * <p>
 * Implementations model either a JSON object ({@link JsonObjectData}) or a JSON
 * property ({@link JsonPropertyData}) and provide operations for creating new
 * nodes, validating structural rules, and supporting UI display and editing.
 * </p>
 */
public sealed interface JsonTreeNodeData permits JsonObjectData, JsonPropertyData {

    long getEditId();

    /**
     * Creates a new child node that is structurally valid for this node and
     * uses the given name.
     * <p>
     * The concrete implementation decides which semantic type (for example,
     * property or object) is created as a child.
     * </p>
     *
     * @param aName logical name of the child node to create
     * @return new node data instance that can be used as a child
     */
    JsonTreeNodeData createChild(String aName);

    /**
     * Creates a new node that can be used as a sibling of this node.
     * <p>
     * The concrete implementation decides which type is appropriate as a
     * neighbor (for example, another property or object).
     * </p>
     *
     * @param aName logical name of the neighbor node to create
     * @return new node data instance that can be used as a neighbor
     */
    JsonTreeNodeData createNeighbor(String aName);

    /**
     * Creates a deep copy of this node data, including all recursive contents,
     * so that the copy can be modified independently of the original instance.
     *
     * @param regenerateEditId
     * @return deep-copied node data instance
     */
    JsonTreeNodeData deepCopy(boolean regenerateEditId);

    /**
     * Called when this node is removed from its parent node, giving the parent
     * a chance to update its internal state.
     * <p>
     * A typical use case is writing back intermediate values (for example,
     * primitive values) into the parent node.
     * </p>
     *
     * @param parent data of the parent node from which this node has been
     * removed
     */
    void sayOnRemoved(JsonTreeNodeData parent);

    /**
     * Callback that is invoked when a {@link JsonObjectData} child has been
     * removed from this node.
     * <p>
     * The default implementation is a no-op; concrete implementations can
     * override this to update internal state.
     * </p>
     *
     * @param child the removed object child
     */
    default void onChildObjectDataRemoved(JsonObjectData child) {
        // NoOp
    }

    /**
     * Callback that is invoked when a {@link JsonPropertyData} child has been
     * removed from this node.
     * <p>
     * The default implementation is a no-op; concrete implementations can
     * override this to update internal state.
     * </p>
     *
     * @param child the removed property child
     */
    default void onChilPropertyDataRemoved(JsonPropertyData child) {
        // NoOp
    }

    /**
     * Checks whether this node is allowed to be used as a child of the given
     * parent node.
     * <p>
     * Implementations typically use the parent node's {@code canBeParentOf...}
     * methods to enforce the allowed JSON structure (for example, object ?
     * property alternation).
     * </p>
     *
     * @param nodeDate node data of the potential parent; may be {@code null}
     * @return {@code true} if this node may be inserted under the given parent;
     * {@code false} otherwise
     */
    boolean canBeChildOf(JsonTreeNodeData nodeDate);

    /**
     * Indicates whether this node may have {@link JsonObjectData} children.
     * <p>
     * The default implementation returns {@code false}; implementations
     * override this method if object children are allowed.
     * </p>
     *
     * @return {@code true} if object children are allowed; {@code false}
     * otherwise
     */
    default boolean canBeParentOfObjectData() {
        return false;
    }

    /**
     * Indicates whether this node may have {@link JsonPropertyData} children.
     * <p>
     * The default implementation returns {@code false}; implementations
     * override this method if property children are allowed.
     * </p>
     *
     * @return {@code true} if property children are allowed; {@code false}
     * otherwise
     */
    default boolean canBeParentOfPropertyData() {
        return false;
    }

    /**
     * Returns the editable text that should be shown and edited for this node
     * in the tree editor.
     *
     * @return editable text value, never {@code null}
     */
    String getEditText();

    /**
     * Sets the editable text for this node, typically as the result of user
     * input in the tree editor.
     *
     * @param editText new text value; implementations may apply special
     * handling for empty or {@code null} values
     */
    void setEditText(String editText);

    /**
     * Returns an additional information text that can be displayed next to the
     * editable text in the tree renderer (for example, type or value
     * representation).
     *
     * @return information text, possibly empty but not {@code null}
     */
    String getInfoText();

    /**
     * Returns the foreground color in which the editable text of this node
     * should be rendered in the tree.
     *
     * @return desired foreground color for the editable text
     */
    Color getForecolor();

}
