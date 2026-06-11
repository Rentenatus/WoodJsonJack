/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.core;

/**
 * Represents the editable tree structure for JSON data. Maintains a hierarchy
 * of EditNode instances and provides fast lookup by ID.
 */
public class EditTree {

    private final EditNodeAbstract root;
    private final EditTimes weightMonitor = new EditTimes();

    /**
     * Creates a new EditTree with a root node containing the specified text.
     * The root node is initialized as an EditNodeObject with the given text,
     * and the tree is ready to be manipulated with various edit commands.
     *
     * @param rootText the text to be contained in the root node of the tree
     */
    public EditTree(String rootText) {
        this(new EditNodeObject(String.valueOf(rootText)));
    }

    /**
     * Creates a new EditTree with the specified root node. The provided root
     * node becomes the top-level node of the tree, and all subsequent nodes
     * added to the tree will be organized under this root. This constructor
     * allows for initializing the tree with a pre-defined structure if desired.
     *
     * @param root the root node of the tree, which must not be null and will
     * serve as the base for all other nodes in the tree
     */
    EditTree(EditNodeAbstract root) {
        this.root = root;
    }

    /**
     * Returns the root node of the tree. The root node is the top-level node
     * from which all other nodes in the tree descend. It serves as the entry
     * point for traversing and manipulating the tree structure. This method
     * allows access to the root node for operations such as adding child nodes,
     * searching for nodes, and performing edits on the tree.
     *
     *
     * @return the root node of the tree
     *
     */
    public EditNodeAbstract getRoot() {
        return root;
    }

    /**
     * Finds a node in the tree based on the provided reference node. This
     * method uses the edit ID (and by interval labeling left range, and times
     * range) of the reference node to locate the corresponding node in the
     * tree. It performs a search starting from the root node and traverses the
     * tree structure to find a node that matches the specified criteria. If a
     * matching node is found, it is returned; otherwise, the method may return
     * null or throw an exception depending on the implementation.
     *
     * @param referenz the reference node containing the edit ID, (and by
     * interval labeling left range, and times range) to search for in the tree
     * @return the node in the tree that matches the criteria specified by the
     * reference node, or null if no such node is found
     */
    public EditNodeAbstract findNodeByIdAndRange(EditNode referenz) {
        return findNodeByIdAndRange(referenz.getEditId(), referenz.getLeftRange(), referenz.getTimesRange(),
                root, true);
    }

    /**
     * Finds a node in the tree based on the provided abstract entry. This
     * method uses the node ID (and by interval labeling left range, and times
     * range) of the abstract entry to locate the corresponding node in the
     * tree. It performs a search starting from the root node and traverses the
     * tree structure to find a node that matches the specified criteria. If a
     * matching node is found, it is returned; otherwise, the method may return
     * null or throw an exception depending on the implementation.
     *
     * Start is the root node.
     *
     * @param entry the abstract entry containing the node ID, (and by interval
     * labeling left range, and times range) to search for in the tree
     * @return the node in the tree that matches the criteria specified by the
     * abstract entry, or null if no such node is found
     */
    public EditNodeAbstract findNodeByIdAndRange(AbstractEntry entry) {
        return findNodeByIdAndRange(entry.nodeId, entry.leftRange, entry.timesRange, root, true);
    }

    /**
     * Finds a node in the tree based on the provided node ID, left range, and
     * times range. This method performs a search starting from the root node
     * and traverses the tree structure to find a node that matches the
     * specified criteria. The search can be configured to use a fallback
     * mechanism that allows for finding a node by ID alone if the initial
     * search using the left range and times range does not yield a result. If a
     * matching node is found, it is returned; otherwise, the method may return
     * null or throw an exception depending on the implementation.
     *
     * Start is the root node.
     *
     * @param id the ID of the node to search for
     * @param left the left range of the node to search for
     * @param times the times range of the node to search for
     * @return the node in the tree that matches the criteria specified by the
     * parameters, or null if no such node is found
     */
    public EditNodeAbstract findNodeByIdAndRange(long id, long left, long times) {
        return findNodeByIdAndRange(id, left, times, root, true);
    }

    /**
     * Finds a node in the tree based on the provided node ID, left range, and
     * times range, with an option for fallback. This method performs a search
     * starting from the root node and traverses the tree structure to find a
     * node that matches the specified criteria. If the fallback option is
     * enabled and the initial search using the left range and times range does
     * not yield a result, the method will attempt to find a node by ID alone.
     * If a matching node is found, it is returned; otherwise, the method may
     * return null or throw an exception depending on the implementation.
     *
     * @param id the ID of the node to search for
     * @param left the left range of the node by interval labeling to search for
     * @param times the times range of the node by interval labeling to search
     * for
     * @param fallback whether to use fallback mechanism
     * @return the node in the tree that matches the criteria specified by the
     * parameters, or null if no such node is found
     */
    public EditNodeAbstract findNodeByIdAndRange(long id, long left, long times, boolean fallback) {
        return findNodeByIdAndRange(id, left, times, root, fallback);
    }

    /**
     *
     * @param id the ID of the node to search for
     * @param left the left range of the node by interval labeling to search for
     * @param times the times range of the node by interval labeling to search
     * for
     * @param startNode the node to start the search from
     * @param fallback whether to use fallback mechanism
     * @return the node in the tree that matches the criteria specified by the
     * parameters, or null if no such node is found
     */
    public EditNodeAbstract findNodeByIdAndRange(long id, long left, long times, EditNodeAbstract startNode, boolean fallback) {
        EditNodeAbstract ret = findNodeByIdAndRange(id, left, times, startNode);
        if (ret == null && fallback) {
            return findNodeById(id, startNode);
        }
        return ret;
    }

    /**
     * Finds a node in the tree based on the provided node ID, left range, and
     * times range, starting from a specified node. This method performs a
     * search through the tree structure beginning at the given start node to
     * find a node that matches the specified criteria. The search checks if the
     * current node matches the ID, and if not, it iterates through the children
     * of the current node to find a match based on the left range and times
     * range. If a matching node is found, it is returned; otherwise, the method
     * returns null.
     *
     * @param id the ID of the node to search for
     * @param left the left range of the node by interval labeling to search for
     * @param times the times range of the node by interval labeling to search
     * for
     * @param startNode the node to start the search from
     * @return
     */
    private EditNodeAbstract findNodeByIdAndRange(long id, long left, long times, EditNodeAbstract startNode) {
        if (startNode == null) {
            return null;
        }

        // Check if current node matches the id
        if (startNode.getEditId() == id) {
            return startNode;
        }

        // Iterate through all children
        for (int i = 0; i < startNode.getChildCount(); i++) {
            EditNodeAbstract child = startNode.getChildAt(i);
            if (child == null) {
                continue;
            }

            // If child matches the id, return it
            if (child.getEditId() == id) {
                return child;
            }

            // Check if timesRange is greater than times - use old method
            if (child.getTimesRange() > times) {
                EditNodeAbstract result = findNodeById(id, child);
                if (result != null) {
                    return result;
                }
                continue;
            }

            // Check if left is within the child's range (inclusive)
            if (left >= child.getLeftRange() && left <= child.getRightRange()) {
                EditNodeAbstract result = findNodeByIdAndRange(id, left, times, child);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    /**
     * Finds a node in the tree based on the provided node ID, starting from a
     * specified node. This method performs a search through the tree structure
     * beginning at the given start node to find a node that matches the
     * specified ID. The search checks if the current node matches the ID, and
     * if not, it iterates through the children of the current node to find a
     * match based on the ID alone. If a matching node is found, it is returned;
     * otherwise, the method returns null.
     *
     * It dont using interval labeling, so it is not as efficient as
     * findNodeByIdAndRange, but it can be used as a fallback if the left range
     * and times range are not yet known or if the initial search fails to find
     * a node. This method is useful for cases where the node ID is the only
     * available information for locating a node in the tree.
     *
     * Start is the root node.
     *
     * @param id the ID of the node to search for
     * @return the node in the tree that matches the specified ID, or null if no
     * such node is found
     */
    public EditNodeAbstract findNodeById(long id) {
        return findNodeById(id, root);
    }

    /**
     * Finds a node in the tree based on the provided node ID, starting from a
     * specified node. This method performs a search through the tree structure
     * beginning at the given start node to find a node that matches the
     * specified ID. The search checks if the current node matches the ID, and
     * if not, it iterates through the children of the current node to find a
     * match based on the ID alone. If a matching node is found, it is returned;
     * otherwise, the method returns null.
     *
     * It dont using interval labeling, so it is not as efficient as
     * findNodeByIdAndRange, but it can be used as a fallback if the left range
     * and times range are not yet known or if the initial search fails to find
     * a node. This method is useful for cases where the node ID is the only
     * available information for locating a node in the tree.
     *
     * @param id the ID of the node to search for
     * @param startNode the node to start the search from
     * @return the node in the tree that matches the specified ID, or null if no
     * such node is found
     */
    public EditNodeAbstract findNodeById(long id, EditNodeAbstract startNode) {
        if (startNode == null) {
            return null;
        }

        java.util.ArrayDeque<EditNodeAbstract> stack = new java.util.ArrayDeque<>();
        stack.push(startNode);

        while (!stack.isEmpty()) {
            EditNodeAbstract node = stack.pop();

            if (node.getEditId() == id) {
                return node;
            }

            for (int i = node.getChildCount() - 1; i >= 0; i--) {
                EditNodeAbstract child = (EditNodeAbstract) node.getChildAt(i);
                if (child != null) {
                    stack.push(child);
                }
            }
        }
        return null;
    }

    /**
     * Checks if the tree contains a node with the specified ID. This method
     * performs a search through the tree structure to determine if any node in
     * the tree has an edit ID that matches the provided ID. It utilizes the
     * findNodeById method to locate a node with the given ID, and returns true
     * if such a node is found, or false if no matching node exists in the tree.
     * This is a convenient way to quickly check for the presence of a node
     * without needing to retrieve the node itself.
     *
     * It dont using interval labeling, so it is not as efficient as
     * findNodeByIdAndRange, but it can be used as a fallback if the left range
     * and times range are not yet known or if the initial search fails to find
     * a node. This method is useful for cases where the node ID is the only
     * available information for determining the existence of a node in the
     * tree.
     *
     * @param start the node to start the search from
     * @param search the node to search for within the tree
     * @return true if the tree contains a node with the specified ID, false
     * otherwise
     */
    public boolean hasNodeStarting(EditNodeAbstract start, EditNodeAbstract search) {
        if (start == null) {
            return false;
        }

        java.util.ArrayDeque<EditNodeAbstract> stack = new java.util.ArrayDeque<>();
        stack.push(start);

        while (!stack.isEmpty()) {
            EditNodeAbstract node = stack.pop();

            if (node == search) {
                return true;
            }

            for (int i = node.getChildCount() - 1; i >= 0; i--) {
                EditNodeAbstract child = (EditNodeAbstract) node.getChildAt(i);
                if (child != null) {
                    stack.push(child);
                }
            }
        }
        return false;
    }

    /**
     * Checks if the tree contains a node with the specified ID. This method
     * performs a search through the tree structure to determine if any node in
     * the tree has an edit ID that matches the provided ID. It utilizes the
     * findNodeById method to locate a node with the given ID, and returns true
     * if such a node is found, or false if no matching node exists in the tree.
     * This is a convenient way to quickly check for the presence of a node
     * without needing to retrieve the node itself.
     *
     * It dont using interval labeling, so it is not as efficient as
     * findNodeByIdAndRange, but it can be used as a fallback if the left range
     * and times range are not yet known or if the initial search fails to find
     * a node. This method is useful for cases where the node ID is the only
     * available information for determining the existence of a node in the
     * tree.
     *
     * @param id the ID of the node to check for in the tree
     * @return
     */
    public boolean containsNode(long id) {
        return findNodeById(id) != null;
    }

    /**
     * Adds a new node to the tree under the specified parent node. This method
     * takes the ID of the parent node and the new node to be added as
     * parameters. It first checks if the new node is null and throws an
     * IllegalArgumentException if it is. Then, it finds the parent node in the
     * tree using the provided parent ID. If the parent node is not found, it
     * throws an IllegalStateException. Finally, it adds the new node as a child
     * of the parent node, either at a specified index or by appending it to the
     * end of the children list, depending on whether an index is provided.
     *
     * @param parentId the ID of the parent node under which the new node should
     * be added
     * @param newNode the new node to be added to the tree
     * @return true if the node was added successfully, false otherwise
     */
    public boolean addNode(long parentId, EditNodeAbstract newNode) {
        return addNode(parentId, newNode, -1);
    }

    /**
     * Adds a new node to the tree under the specified parent node at a given
     * index. This method takes the ID of the parent node, the new node to be
     * added, and the index at which the new node should be inserted as
     * parameters. It first checks if the new node is null and throws an
     * IllegalArgumentException if it is. Then, it finds the parent node in the
     * tree using the provided parent ID. If the parent node is not found, it
     * throws an IllegalStateException. Finally, it adds the new node as a child
     * of the parent node at the specified index, or appends it to the end of
     * the children list if the index is negative.
     *
     * It dont using interval labeling, so it is not as efficient as addChild,
     * but it can be used as a fallback if the left range and times range are
     * not yet known or if the initial search fails to find a node. This method
     * is useful for cases where the node ID is the only available information
     * for locating the parent node in the tree.
     *
     * @param parentId the ID of the parent node under which the new node should
     * be added
     * @param newNode the new node to be added to the tree
     * @param index the index at which the new node should be inserted under the
     * parent node, or -1 to append to the end of the children list
     * @return true if the node was added successfully, false otherwise
     */
    public boolean addNode(long parentId, EditNodeAbstract newNode, int index) {
        if (newNode == null) {
            throw new IllegalArgumentException("New node cannot be null");
        }

        EditNodeAbstract parent = findNodeById(parentId);
        if (parent == null) {
            throw new IllegalStateException("Parent node with ID " + parentId + " not found.");
        }

        checkCycles(newNode, parent);

        if (index >= 0) {
            parent.addChild(newNode, index, weightMonitor);
        } else {
            parent.addChild(newNode, weightMonitor);
        }

        return true;
    }

    /**
     * Adds a new child node to the specified parent node. This method takes the
     * parent node and the text for the new child node as parameters. It first
     * checks if the parent node is null and throws an IllegalArgumentException
     * if it is. Then, it checks if the parent node is part of the tree using
     * the checkParentMembership method. Finally, it creates a new child node
     * with the provided text and adds it as a child of the parent node,
     * returning the newly created child node.
     *
     * @param parentNode the parent node under which the new child node should
     * be added
     * @param nodeText the text to be contained in the new child node
     * @return the newly created child node that was added to the tree
     */
    public EditNodeAbstract addNewChild(EditNodeAbstract parentNode, String nodeText) {
        checkParentProps(parentNode);
        return parentNode.addNewChild(nodeText, weightMonitor);
    }

    /**
     * Adds a new child node to the specified parent node at a given index. This
     * method takes the parent node, the text for the new child node, and the
     * index at which the new child node should be inserted as parameters. It
     * first checks if the parent node is null and throws an
     * IllegalArgumentException if it is. Then, it checks if the parent node is
     * part of the tree using the checkParentMembership method. Finally, it
     * creates a new child node with the provided text and adds it as a child of
     * the parent node at the specified index, returning the newly created child
     * node.
     *
     * @param parentNode the parent node under which the new child node should
     * be added
     * @param nodeText the text to be contained in the new child node
     * @param index the index at which the new child node should be inserted
     * under the parent node, or -1 to append to the end of the children list
     * @return the newly created child node that was added to the tree
     */
    public EditNodeAbstract addNewChild(EditNodeAbstract parentNode, String nodeText, int index) {
        checkParentProps(parentNode);
        return parentNode.addNewChild(nodeText, index, weightMonitor);
    }

    /**
     * Checks the properties of the parent node to ensure it is valid for adding
     * a child node. This method verifies that the parent node is not null and
     * that it is part of the tree structure. If the parent node is null, it
     * throws an IllegalArgumentException. If the parent node is not part of the
     * tree, it also throws an IllegalArgumentException. This method is used as
     * a precondition check before adding a child node to ensure that the
     * operation is performed on a valid parent node within the tree.
     *
     * @param parentNode the parent node to be checked for validity before
     * adding a child node
     * @throws IllegalArgumentException if the parent node is null or not part
     * of the tree
     */
    private void checkParentProps(EditNodeAbstract parentNode) throws IllegalArgumentException {
        if (parentNode == null) {
            throw new IllegalArgumentException("Parent node cannot be null.");
        }
        checkParentMembership(parentNode);
    }

    /**
     * Checks the properties of the new node and parent node to ensure they are
     * valid for adding a child node. This method verifies that both nodes are
     * not null and that the new node can be a child of the parent node. If
     * either node is null, it throws an IllegalArgumentException. If the new
     * node cannot be a child of the parent node, it also throws an
     * IllegalArgumentException. This method is used as a precondition check
     * before adding a child node to ensure that the operation is performed on
     * valid nodes within the tree.
     *
     * @param newNode the new node to be checked for validity before adding as a
     * child
     * @param parentNode the parent node to be checked for validity before
     * adding a child node
     * @throws IllegalArgumentException if either node is null or the new node
     * cannot be a child of the parent node
     */
    private void checkNewAndParentProps(EditNodeAbstract newNode, EditNodeAbstract parentNode) throws IllegalArgumentException {
        if (newNode == null || parentNode == null) {
            throw new IllegalArgumentException("New and parent nodes cannot be null.");
        }
        checkParentMembership(parentNode);
        checkCycles(newNode, parentNode);
        if (!newNode.canBeChildOf(parentNode)) {
            throw new IllegalArgumentException("This new node cannot become a child of the specified parent.");
        }
    }

    /**
     * Checks if the parent node is part of the tree structure.
     *
     * @param parentNode the parent node to be checked
     * @throws IllegalArgumentException if the parent node is not part of the
     * tree
     */
    private void checkParentMembership(EditNodeAbstract parentNode) throws IllegalArgumentException {
        if (!parentNode.isOrHasParent(root)) {
            throw new IllegalArgumentException("The parent node must be a node from the tree");
        }
    }

    /**
     * Checks for cycles in the tree structure.
     *
     * @param newNode the new node to be checked
     * @param parentNode the parent node to be checked
     * @throws IllegalArgumentException if a cycle is detected
     */
    private void checkCycles(EditNodeAbstract newNode, EditNodeAbstract parentNode) throws IllegalArgumentException {
        if (hasNodeStarting(newNode, parentNode)) {
            throw new IllegalArgumentException("A parent node must not be cyclically contained within a child node.");
        }
    }

    /**
     * Adds a child node to the specified parent node.
     *
     * @param parentNode the parent node to which the child will be added
     * @param newNode the new node to be added as a child
     */
    public void addChild(EditNodeAbstract parentNode, EditNodeAbstract newNode) {
        checkNewAndParentProps(newNode, parentNode);
        parentNode.addChild(newNode, weightMonitor);
    }

    /**
     * Adds a child node to the specified parent node at a given index.
     *
     * @param parentNode the parent node to which the child will be added
     * @param newNode the new node to be added as a child
     * @param index the index at which the new node should be inserted under the
     * parent node, or -1 to append to the end of the children list
     */
    public void addChild(EditNodeAbstract parentNode, EditNodeAbstract newNode, int index) {
        checkNewAndParentProps(newNode, parentNode);
        parentNode.addChild(newNode, index, weightMonitor);
    }

    /**
     * Removes a child node from the specified parent node. This method takes
     * the parent node and the child node to be removed as parameters. It first
     * checks if the parent node is null and throws an IllegalArgumentException
     * if it is. Then, it checks if the parent node is part of the tree using
     * the checkParentMembership method. Finally, it removes the child node from
     * the parent node and returns true if the child was successfully removed,
     * or false if the child was not found among the parent's children.
     *
     * @param parentNode the parent node from which the child node should be
     * removed
     * @param child the child node to be removed from the parent node
     * @return true if the child node was successfully removed from the parent
     * node, false otherwise
     */
    public boolean removeChild(EditNodeAbstract parentNode, EditNodeAbstract child) {
        checkParentProps(parentNode);
        return parentNode.removeChild(child);
    }

    /**
     * Removes a node from the tree based on the provided child node. This
     * method takes the child node to be removed as a parameter. It first checks
     * if the child node is null and throws an IllegalArgumentException if it
     * is. Then, it retrieves the parent node of the child node. If the parent
     * node is null, it returns false, indicating that the child node cannot be
     * removed because it has no parent. If the parent node is found, it checks
     * if the parent node is part of the tree using the checkParentMembership
     * method. Finally, it removes the child node from its parent and returns
     * true if the child was successfully removed, or false if the child was not
     * found among the parent's children.
     *
     * @param child the child node to be removed from the tree
     * @return true if the child node was successfully removed from the tree,
     * false otherwise
     */
    public boolean removeNode(EditNodeAbstract child) {
        if (child == null) {
            throw new IllegalArgumentException("Child node cannot be null");
        }
        EditNodeAbstract parentNode = child.getParent();
        if (parentNode == null) {
            return false;
        }
        checkParentMembership(parentNode);
        return parentNode.removeChild(child);
    }

    /**
     * Adds multiple nodes to the tree efficiently. Nodes are added in the order
     * they appear in the array.
     *
     * @param parentIds the parent IDs for each node
     * @param newNodes the nodes to add
     * @param indices the indices for each node (-1 for append)
     * @return true if all nodes were added successfully
     */
    public boolean addNodes(long[] parentIds, EditNodeAbstract[] newNodes, int[] indices) {
        if (newNodes == null || parentIds == null || indices == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        if (newNodes.length != parentIds.length || newNodes.length != indices.length) {
            throw new IllegalArgumentException("Arrays must have the same length");
        }

        for (int i = 0; i < newNodes.length; i++) {
            addNode(parentIds[i], newNodes[i], indices[i]);
        }
        return true;
    }

    /**
     * Removes a node from the tree based on the provided node ID.
     *
     * @param nodeId the ID of the node to be removed from the tree
     * @param nodeLeft the left range of the node to search for
     * @param nodeTimes the times range of the node to search for
     * @return the removed node if it was successfully removed from the tree, or
     * null if no node with the specified ID exists in the tree or if the node
     * could not be removed
     */
    public EditNode removeNode(long nodeId, long nodeLeft, long nodeTimes) {
        EditNodeAbstract node = findNodeByIdAndRange(nodeId, nodeLeft, nodeTimes);
        if (node == null) {
            return null;
        }

        if (node == root) {
            throw new IllegalStateException("Cannot remove root node");
        }

        EditNodeAbstract parent = node.getParent();
        if (parent == null) {
            throw new IllegalStateException("Node has no parent");
        }

        boolean removed = parent.removeChild(node);

        return removed ? node : null;
    }

    /**
     * Removes multiple nodes from the tree efficiently. Nodes are removed in
     * reverse order to maintain correct indices.
     *
     * @param entries of the nodes to remove
     */
    public void removeNodes(AbstractEntry[] entries) {
        // Remove in reverse order to maintain correct indices
        for (int i = entries.length - 1; i >= 0; i--) {
            removeNode(entries[i].nodeId, entries[i].leftRange, entries[i].timesRange);
        }
    }

    /**
     * Returns the total number of nodes in the tree. This method performs a
     * traversal of the tree structure to count all nodes, starting from the
     * root node and including all descendant nodes. It uses a stack to keep
     * track of nodes to visit, and increments a counter for each node
     * encountered during the traversal. The final count is returned as the
     * total number of nodes in the tree.
     *
     * @return the total number of nodes in the tree
     */
    public int getNodeCount() {
        if (root == null) {
            return 0;
        }

        int count = 0;
        java.util.ArrayDeque<EditNodeAbstract> stack = new java.util.ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            EditNodeAbstract node = stack.pop();
            count++;

            for (int i = node.getChildCount() - 1; i >= 0; i--) {
                EditNodeAbstract child = (EditNodeAbstract) node.getChildAt(i);
                if (child != null) {
                    stack.push(child);
                }
            }
        }

        return count;
    }

    /**
     * Clears the tree by removing all nodes. This method iterates through the
     * children of the root node and removes each child node until there are no
     * more children left. It effectively resets the tree to an empty state,
     * leaving only the root node without any descendants. This can be useful
     * for reusing the same tree structure for different data or for resetting
     * the tree after certain operations.
     */
    public void clear() {
        while (root.getChildCount() > 0) {
            EditNodeAbstract child = (EditNodeAbstract) root.getChildAt(0);
            root.removeChild(child);
        }
    }

    /**
     * Builds a set of reachable node IDs starting from the specified node. This
     * method performs a traversal of the tree structure beginning at the given
     * node and collects the edit IDs of all reachable nodes, including the
     * starting node itself. The collected IDs are added to the provided set,
     * which can be used for various purposes such as tracking visited nodes,
     * performing operations on specific subsets of the tree, or for debugging
     * and analysis of the tree structure.
     *
     * @param node the node to start the traversal from
     * @param reachable the set to which reachable node IDs will be added
     */
    public void buildReachableSet(EditNode node, java.util.Set<Long> reachable) {
        reachable.add(node.getEditId());
        for (int i = 0; i < node.getChildCount(); i++) {
            buildReachableSet(node.getChildAt(i), reachable);
        }
    }

    @Override
    public String toString() {
        return "EditTree[root=" + root + ", nodes=" + getNodeCount() + "]";
    }

    /**
     * Performs range relabeling on the specified node and its descendants. This
     * method ensures that the node and all its descendants have valid range
     * labels based on their position in the tree.
     *
     * @param node the node to perform range relabeling on
     */
    public void rangeRelabeling(EditNodeAbstract node) {
        if (!node.isOrHasParent(root)) {
            throw new IllegalArgumentException("The node must be a node from the tree");
        }
        node.rangeRelabeling(weightMonitor);
    }

}
