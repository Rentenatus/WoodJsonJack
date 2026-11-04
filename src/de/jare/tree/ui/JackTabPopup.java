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
import java.awt.Component;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * The popup of one editor tab (its header): the tab-wide actions - Save, Save As..., the disk diff of the tab's
 * tree - and the diff pair entries of the root popup: mark the tab's tree as left/right side, run the W2/W3
 * view with the remembered side. The pair semantics follow the root popup (concepts CONCEPT_TreeDiffView.md
 * and doc/JSON_PROJECT.md): the flag decides the roles, the view opens in the ours tab.
 *
 * @author Janusch Rentenatus
 */
public class JackTabPopup extends JPopupMenu {

    private final WoodWindow woodWindow;
    private final JackEditTreeContainer container;

    /**
     * Builds and shows the popup of one editor tab. The menu is built fresh per invocation - the diff entry
     * texts depend on the remembered side and need no listener wiring.
     *
     * @param woodWindow the window owning the tabs
     * @param container the tab the popup belongs to
     * @param invoker the header component to pop up at
     * @param x the x position
     * @param y the y position
     */
    public static void show(WoodWindow woodWindow,
            JackEditTreeContainer container, Component invoker, int x, int y) {
        final JackTabPopup popup = new JackTabPopup(woodWindow, container);
        popup.build();
        popup.show(invoker, x, y);
    }

    private JackTabPopup(WoodWindow woodWindow,
            JackEditTreeContainer container) {
        this.woodWindow = woodWindow;

        this.container = container;
    }

    /**
     * One fresh menu per invocation: Save / Save As... / the disk diff, then the diff pair family of the
     * root popup (mark as left/right side, W2/W3 with the remembered side).
     */
    private void build() {
        final EditTree tree = woodWindow.treeOf(container);
        final boolean hasTree = tree != null && tree.getRoot() != null;

        final JMenuItem saveItem = new JMenuItem("Save");
        saveItem.setEnabled(hasTree);
        saveItem.addActionListener(e -> woodWindow.getMainActions().saveTab(container));

        final JMenuItem saveAsItem = new JMenuItem("Save As...");
        saveAsItem.setEnabled(hasTree);
        saveAsItem.addActionListener(e -> woodWindow.getMainActions().saveTabAs(container));

        final JMenuItem diskDiffItem = new JMenuItem("Diff Changes (vs. Disk)");
        diskDiffItem.setToolTipText("Working copy left, the on the fly loaded disk version right"
                + (tree != null && tree.getBaseSnapshot() != null ? " (W3)" : " (W2)"));
        diskDiffItem.setEnabled(hasTree && hasFileOnDisk());
        diskDiffItem.addActionListener(e -> woodWindow.getMainActions()
                .diskDiff("tab popup", null, container));

        add(saveItem);
        add(saveAsItem);
        add(diskDiffItem);
        addSeparator();

        final JMenuItem markLeftItem = new JMenuItem("Diff: mark as left side");
        final JMenuItem markRightItem = new JMenuItem("Diff: mark as right side");
        markLeftItem.setEnabled(hasTree);
        markRightItem.setEnabled(hasTree);
        markLeftItem.addActionListener(e -> rememberSide(true));
        markRightItem.addActionListener(e -> rememberSide(false));
        add(markLeftItem);
        add(markRightItem);
        add(diffPairItem("W2", false));
        add(diffPairItem("W3", true));
    }

    private boolean hasFileOnDisk() {
        final java.io.File file = woodWindow.fileOf(container);
        return file != null && file.exists();
    }

    private void rememberSide(boolean asLeft) {
        final EditTree tree = woodWindow.treeOf(container);
        if (tree == null || tree.getRoot() == null) {
            return;
        }
        final JackTreeModel model = container.getLeftTree().getModel();
        TreeDiffSides.remember(tree.getRoot(), model, asLeft);
    }

    /**
     * The pair entry of the root popup, tab-wide: this tab's tree and the remembered side form the pair. The
     * flag decides the roles - the LEFT flag is ours, the view opens there; the RIGHT flag makes this tab ours
     * when it triggers the run (or re-runs the remembered pair when this IS the marked tree).
     */
    private JMenuItem diffPairItem(String label, boolean threeWay) {
        final EditTree tree = woodWindow.treeOf(container);
        final JMenuItem item = new JMenuItem();
        if (tree == null || tree.getRoot() == null || !TreeDiffSides.hasRemembered()) {
            item.setText(label + " (mark a side first)");
            item.setEnabled(false);
            return item;
        }
        final EditNodeAbstract marked = TreeDiffSides.getRemembered();
        final boolean onMarked = tree.getRoot() == marked;
        final EditNodeAbstract counterpartRaw = onMarked ? TreeDiffSides.getPartner() : marked;
        if (counterpartRaw == null) {
            item.setText(label + ": this is the marked side (run from another tab first)");
            item.setEnabled(false);
            return item;
        }
        final String desc = TreeDiffSides.describe(counterpartRaw)
                + ((TreeDiffSides.isRememberedAsLeft() != onMarked) ? " (as left)" : " (as right)");
        item.setText(label + ": with " + desc);
        item.addActionListener(e -> runDiffView(threeWay));
        return item;
    }

    /**
     * Opens the diff view of this tab's tree against the remembered side (the role logic of the root popup):
     * the LEFT flag makes the marked tree ours; the RIGHT flag makes the triggering tree ours. The view opens
     * in the ours tab; W3 needs the ours side's import state as base.
     */
    private void runDiffView(boolean threeWay) {
        final EditTree tree = woodWindow.treeOf(container);
        if (tree == null || tree.getRoot() == null) {
            return;
        }
        final EditNodeAbstract marked = TreeDiffSides.getRemembered();
        final boolean onMarked = tree.getRoot() == marked;
        final EditNodeAbstract counterpartRaw = onMarked ? TreeDiffSides.getPartner() : marked;
        if (marked == null || counterpartRaw == null) {
            return;
        }
        final EditTree oursTree = TreeDiffSides.isRememberedAsLeft() && !onMarked
                ? sideTreeOf(marked) : tree;
        final EditTree theirsTree = oursTree == tree ? sideTreeOf(counterpartRaw) : tree;
        if (oursTree == null || theirsTree == null || oursTree.getRoot() == null) {
            return;
        }
        if (threeWay && (oursTree.getBaseSnapshot() == null)) {
            woodWindow.showErrorDialog("W3 needs the ours side to carry an import state as base.");
            return;
        }
        if (!onMarked) {
            TreeDiffSides.rememberPartner(tree.getRoot(), container.getLeftTree().getModel());
        }
        final JackEditTreeContainer oursContainer = woodWindow.tabOfTree(oursTree);
        if (oursContainer == null) {
            return;
        }
        DiffViewController.showContainerTab(oursContainer);
        oursContainer.getDiffController().open(oursTree, theirsTree.getRoot(),
                threeWay ? DiffViewController.Kind.W3 : DiffViewController.Kind.W2);
    }

    private EditTree sideTreeOf(Object rememberedNode) {
        // the remembered side lives in some tab - find the container that shows it
        for (final JackEditTreeContainer candidate : woodWindow.getAllContainers()) {
            final EditTree candidateTree = woodWindow.treeOf(candidate);
            if (candidateTree != null && candidateTree.getRoot() == rememberedNode) {
                return candidateTree;
            }
        }
        return TreeDiffSides.getRememberedTree();
    }

}
