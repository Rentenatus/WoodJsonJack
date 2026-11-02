/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.tree.control.model.JackTreeModel;

/**
 * Remembers one side of the interactive tree diff (use case U4 of doc/TREE_DIFF.md in jsonCasted): the user marks
 * the root of one editor as LEFT or RIGHT — there is always at most one tree carrying the flag. On any other root,
 * "W2 Diff: with &lt;marked&gt;" compares the two live trees, "W3 Diff: with &lt;marked&gt;" runs the 3-way analysis
 * (base = import state of the ours side, the LEFT role, plus ours and theirs) and shows the merge plan, "W3 Merge"
 * applies it. The marked side survives the run, so one root can be compared against several editors in a row.
 * Sides are held live, not as snapshots — a diff after further edits shows the current state, which also proves
 * the Merkle invalidation chain in action.
 *
 * <p>
 * Deliberately static: the popup that marks a side belongs to one editor tab, while the run-diff popup can belong
 * to another tab — the memory must span tabs. WoodJsonJack runs one main window, so the static state is global to
 * the application.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public final class TreeDiffSides {

    private static EditNodeAbstract remembered;
    private static JackTreeModel rememberedModel;
    private static boolean rememberedAsLeft;

    private TreeDiffSides() {
        // utility class, no instances
    }

    /**
     * Marks the given root as one diff side, replacing any previously marked side — there is never more than one
     * flagged tree. The marked root's tree model is remembered with it: the W3 diff takes the base (import state)
     * from the ours side, and the W3 merge books its transaction on the ours side's undo history.
     *
     * @param root the root to remember, not null.
     * @param model the tree model the root belongs to, or null when unknown.
     * @param asLeft true for the LEFT flag (the ours role), false for the RIGHT flag (the theirs role).
     */
    public static void remember(EditNodeAbstract root, JackTreeModel model, boolean asLeft) {
        remembered = root;
        rememberedModel = model;
        rememberedAsLeft = asLeft;
    }

    /**
     * Tells whether a side is marked.
     *
     * @return true when a side exists.
     */
    public static boolean hasRemembered() {
        return remembered != null;
    }

    /**
     * Returns the marked side.
     *
     * @return the marked root, or null.
     */
    public static EditNodeAbstract getRemembered() {
        return remembered;
    }

    /**
     * Returns the tree model of the marked side — the W3 diff takes the base (import state) from the ours side and
     * the W3 merge books its undo history there.
     *
     * @return the marked root's tree model, or null when unknown.
     */
    public static JackTreeModel getRememberedModel() {
        return rememberedModel;
    }

    /**
     * Returns the edit tree of the marked side.
     *
     * @return the marked root's tree, or null when unknown.
     */
    public static EditTree getRememberedTree() {
        return rememberedModel == null ? null : rememberedModel.getEditTree();
    }

    /**
     * Tells which flag the marked side carries.
     *
     * @return true when the marked side is LEFT (the ours role), false for RIGHT (the theirs role).
     */
    public static boolean isRememberedAsLeft() {
        return rememberedAsLeft;
    }

    /**
     * Describes the marked side for menu texts and dialog titles: root name and edit id, so two tabs with the same
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
