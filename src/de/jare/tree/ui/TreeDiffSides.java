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
 * The pair chaining: every run also remembers the counterpart as the marked side's <em>partner</em> — so the same
 * pair stays runnable from the marked tab too (diff, view, merge in any order). Marking a new side drops the
 * partner of the old one.
 * </p>
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
    private static EditNodeAbstract partner;
    private static JackTreeModel partnerModel;
    private static final java.util.List<Runnable> changeListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private TreeDiffSides() {
        // utility class, no instances
    }

    /**
     * Registers a listener that runs whenever the marked side changes. The tree diff view enables its segment
     * control per editor container, so every container must hear the change.
     *
     * @param listener the callback, not null.
     */
    public static void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    /**
     * Removes a registered change listener.
     *
     * @param listener the callback to remove.
     */
    public static void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    private static void fireChanged() {
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    /**
     * Marks the given root as one diff side, replacing any previously marked side — there is never more than one
     * flagged tree. The marked root's tree model is remembered with it: the W3 diff takes the base (import state)
     * from the ours side, and the W3 merge books its transaction on the ours side's undo history. The partner of
     * the previous marked side does not belong to the new one — it is dropped.
     *
     * @param root the root to remember, not null.
     * @param model the tree model the root belongs to, or null when unknown.
     * @param asLeft true for the LEFT flag (the ours role), false for the RIGHT flag (the theirs role).
     */
    public static void remember(EditNodeAbstract root, JackTreeModel model, boolean asLeft) {
        remembered = root;
        rememberedModel = model;
        rememberedAsLeft = asLeft;
        partner = null;
        partnerModel = null;
        fireChanged();
    }

    /**
     * Remembers the counterpart the marked side ran against — the last pair partner. After a diff, view or merge,
     * the same pair can run again from BOTH sides: the other tab compares against the marked side as before, the
     * marked tab compares against this partner.
     *
     * @param root the counterpart root, not null.
     * @param model the counterpart's tree model, or null when unknown.
     */
    public static void rememberPartner(EditNodeAbstract root, JackTreeModel model) {
        partner = root;
        partnerModel = model;
        fireChanged();
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
     * Tells whether a pair partner exists — the marked side ran against it, so the same pair can run again from the
     * marked tab too.
     *
     * @return true when a partner is remembered.
     */
    public static boolean hasPartner() {
        return partner != null;
    }

    /**
     * Returns the last counterpart the marked side ran against.
     *
     * @return the partner root, or null.
     */
    public static EditNodeAbstract getPartner() {
        return partner;
    }

    /**
     * Returns the tree model of the partner.
     *
     * @return the partner's tree model, or null when unknown.
     */
    public static JackTreeModel getPartnerModel() {
        return partnerModel;
    }

    /**
     * Returns the edit tree of the partner — needed when the marked tab itself runs the W3 pair (the ours side is
     * the partner when the marked side carries the RIGHT flag).
     *
     * @return the partner's tree, or null when unknown.
     */
    public static EditTree getPartnerTree() {
        return partnerModel == null ? null : partnerModel.getEditTree();
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
