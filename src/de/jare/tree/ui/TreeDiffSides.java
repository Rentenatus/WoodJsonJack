/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNodeAbstract;

/**
 * Remembers the two sides of the interactive tree diff (use case U4 of doc/TREE_DIFF.md in jsonCasted): the user
 * marks the root of one editor as the left side, the root of another editor as the right side, then runs the diff.
 * The sides are held live, not as snapshots — a diff after further edits shows the current state, which is exactly
 * what the debug feature is for (it also proves the Merkle invalidation chain in action).
 *
 * <p>
 * Deliberately static: the popup that marks a side belongs to one editor tab, while the run-diff popup can belong to
 * another tab — the memory must span tabs. WoodJsonJack runs one main window, so the static state is global to the
 * application.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public final class TreeDiffSides {

    private static EditNodeAbstract left;
    private static EditNodeAbstract right;

    private TreeDiffSides() {
        // utility class, no instances
    }

    /**
     * Marks the given root as the left diff side.
     *
     * @param root the root to remember, not null.
     */
    public static void rememberLeft(EditNodeAbstract root) {
        left = root;
    }

    /**
     * Marks the given root as the right diff side.
     *
     * @param root the root to remember, not null.
     */
    public static void rememberRight(EditNodeAbstract root) {
        right = root;
    }

    /**
     * Tells whether a left side is marked.
     *
     * @return true when a left side exists.
     */
    public static boolean hasLeft() {
        return left != null;
    }

    /**
     * Tells whether a right side is marked.
     *
     * @return true when a right side exists.
     */
    public static boolean hasRight() {
        return right != null;
    }

    /**
     * Returns the marked left side.
     *
     * @return the left root, or null.
     */
    public static EditNodeAbstract getLeft() {
        return left;
    }

    /**
     * Returns the marked right side.
     *
     * @return the right root, or null.
     */
    public static EditNodeAbstract getRight() {
        return right;
    }

    /**
     * Describes a marked side for menu texts and dialog titles: root name and edit id, so two tabs with the same
     * file stay distinguishable.
     *
     * @param node the marked root, may be null.
     * @return a short description.
     */
    public static String describe(EditNodeAbstract node) {
        if (node == null) {
            return "?";
        }
        final String name = node.getName();
        final String label = name == null || name.isEmpty() ? "root" : name;
        return label + " #" + node.getEditId();
    }
}
