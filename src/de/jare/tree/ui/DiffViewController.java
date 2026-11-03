/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * [http://www.eclipse.org/legal/epl-v20.html](http://www.eclipse.org/legal/epl-v20.html)
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.tree.control.model.JackTreeModel;
import java.awt.Component;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JTabbedPane;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreeModel;

/**
 * The diff mode of an editor container's right pane (concept CONCEPT_TreeDiffView.md): the pane shows a frozen deep
 * copy of the theirs tree next to the live ours tree, both carrying {@link DiffMark}s. Geometry is the role - the
 * left tree stays live and editable (ours), the right copy is readonly (theirs). The marks live here, never on the
 * node models; they validate themselves at paint time via the Merkle hash.
 *
 * <p>
 * One controller per {@link JackEditTreeContainer}, created lazily by the container. Opening resolves the roles
 * (whoever triggers passes the live ours tree and theirs root), switches to the ours tab, freezes the copy, builds
 * the marks and hands the maps to the two trees. Closing restores the pane exactly as it was. Re-diff runs the same
 * pair again with a fresh copy and fresh marks.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public final class DiffViewController {

    /**
     * The analysis kind behind the marks: W2 compares the two trees, W3 classifies per merge plan (base = ours'
     * retained import state).
     */
    public enum Kind {

        W2("Diff W2"),
        W3("Diff W3");

        private final String label;

        private Kind(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final JackEditTreeContainer container;
    private Kind kind;
    private EditTree oursTree;
    private EditNodeAbstract theirsSourceRoot;
    private EditTree theirsCopyTree;
    private TreeModel savedRightModel;
    private String pairText;
    private boolean open;
    private final Map<EditNodeAbstract, DiffMark> marksLeft = new HashMap<>();
    private final Map<EditNodeAbstract, DiffMark> marksRight = new HashMap<>();

    /**
     * Creates the controller of one container.
     *
     * @param container the container whose right pane the controller governs, not null.
     */
    DiffViewController(JackEditTreeContainer container) {
        this.container = container;
    }

    /**
     * Tells whether the container's right pane currently shows the diff view.
     *
     * @return true in diff mode.
     */
    public boolean isOpen() {
        return open;
    }

    /**
     * Returns the kind of the open view.
     *
     * @return the kind, or null when closed.
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * Opens the diff view in this container: ours is the container's live left tree, theirs is the given root. The
     * theirs side is frozen immediately (deep copy in a fresh edit tree).
     *
     * @param oursTree the live ours tree (the container's left tree), not null.
     * @param theirsRoot the live theirs root to freeze, not null.
     * @param kind W2 (comparison) or W3 (merge plan classification).
     */
    public void open(EditTree oursTree, EditNodeAbstract theirsRoot, Kind kind) {
        if (oursTree == null || theirsRoot == null || kind == null) {
            return;
        }
        this.oursTree = oursTree;
        this.theirsSourceRoot = theirsRoot;
        this.kind = kind;
        buildView();
    }

    /**
     * Re-runs the same pair with a fresh copy and fresh marks; taken flags reset with the marks.
     */
    public void rediff() {
        if (!open) {
            return;
        }
        buildView();
    }

    /**
     * Closes the diff view and restores the right pane exactly as it was before.
     */
    public void close() {
        if (!open) {
            return;
        }
        marksLeft.clear();
        marksRight.clear();
        container.getLeftTree().setDiffMarks(null);
        container.getRightTree().setDiffMarks(null);
        container.getRightTree().setDiffInfo(null);
        if (savedRightModel != null) {
            container.getRightTree().getTree().setModel(savedRightModel);
            savedRightModel = null;
        }
        theirsCopyTree = null;
        oursTree = null;
        theirsSourceRoot = null;
        kind = null;
        pairText = null;
        open = false;
        container.refreshDiffSegments();
    }

    private void buildView() {
        if (savedRightModel == null) {
            savedRightModel = container.getRightTree().getTree().getModel();
        } else {
            // a previous copy model must not become the restored state
            container.getRightTree().getTree().setModel(savedRightModel);
        }

        final EditNodeAbstract theirsCopy = theirsSourceRoot.deepCopy(true);
        theirsCopyTree = new EditTree(theirsCopy);

        marksLeft.clear();
        marksRight.clear();
        DiffMarkBuilder.build(kind, oursTree, theirsSourceRoot, theirsCopyTree, marksLeft, marksRight);

        container.getRightTree().getTree().setModel(new JackTreeModel(theirsCopyTree));
        container.getRightTree().refreshResourceInfo();
        container.getLeftTree().setDiffMarks(marksLeft);
        container.getRightTree().setDiffMarks(marksRight);
        pairText = kind.getLabel() + ": "
                + TreeDiffSides.describe(oursTree.getRoot()) + " \u2194 "
                + TreeDiffSides.describe(theirsCopy);
        container.getRightTree().setDiffInfo(pairText);

        open = true;
        container.showDiffPane();
        container.refreshDiffSegments();
    }

    /**
     * Returns the frozen theirs edit tree of the open view.
     *
     * @return the copy tree, or null when closed.
     */
    public EditTree getTheirsCopyTree() {
        return theirsCopyTree;
    }

    /**
     * Returns the pair description of the open view (kind and both roots), shown in the right pane header.
     *
     * @return the pair text, or null when closed.
     */
    public String getPairText() {
        return pairText;
    }

    /**
     * Counts the stale marks of the left pane: ours nodes whose Merkle hash no longer equals the hash captured at
     * diff time (concept section 5). The count drives the header summary - the per-node edited color paints
     * independently at render time.
     *
     * @return the number of stale marks.
     */
    public int countStaleLeftMarks() {
        int stale = 0;
        for (Map.Entry<EditNodeAbstract, DiffMark> entry : marksLeft.entrySet()) {
            if (entry.getKey().getMerkleHash() != entry.getValue().getMerkleHashAtDiff()) {
                stale++;
            }
        }
        return stale;
    }

    /**
     * Returns the number of marks of the left pane (the aggregate of the staleness summary).
     *
     * @return the mark count.
     */
    public int getLeftMarkCount() {
        return marksLeft.size();
    }

    /**
     * Returns the live ours tree of the open view.
     *
     * @return the ours tree, or null when closed.
     */
    public EditTree getOursTree() {
        return oursTree;
    }

    /**
     * Returns the live theirs root the view freezes (the re-diff source), not the copy.
     *
     * @return the live theirs root, or null when closed.
     */
    public EditNodeAbstract getTheirsSourceRoot() {
        return theirsSourceRoot;
    }

    /**
     * Finds the editor container whose left tree carries the given ours root: the ours side of a view opened from
     * the root popup of another tab or from a diff dialog. The search walks the shared tabbed pane, so no registry
     * is needed - every editor container of the main window is a tab of it.
     *
     * @param oursRoot the ours side's live root, not null.
     * @param anchor any component inside the main window, for finding the tabbed pane.
     * @return the container, or null when no editor tab carries the root.
     */
    public static JackEditTreeContainer findOursContainer(EditNodeAbstract oursRoot, Component anchor) {
        final JTabbedPane tabs = anchor == null ? null
                : (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, anchor);
        if (tabs == null) {
            return null;
        }
        for (int i = 0; i < tabs.getTabCount(); i++) {
            final JackEditTreeContainer candidate = containerOf(tabs.getComponentAt(i));
            if (candidate != null && candidate.getLeftTree().getModel().getEditTree().getRoot() == oursRoot) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Switches the main window to the tab of the given container - the view opens in the ours tab (concept section
     * 2), which can differ from the tab that triggered it.
     *
     * @param container the ours container, not null.
     */
    public static void showContainerTab(JackEditTreeContainer container) {
        final JTabbedPane tabs = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, container);
        if (tabs == null) {
            return;
        }
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (containerOf(tabs.getComponentAt(i)) == container) {
                tabs.setSelectedIndex(i);
                return;
            }
        }
    }

    private static JackEditTreeContainer containerOf(Component tabComponent) {
        final Component c = (tabComponent instanceof JScrollPane scrollPane)
                ? scrollPane.getViewport().getView() : tabComponent;
        return c instanceof JackEditTreeContainer container ? container : null;
    }

    /**
     * Opens the W2 view of two roots (the "Show in trees" action of the diff dialogs): ours is the left root's
     * editor tab, theirs is the right root.
     *
     * @param anchor any component inside the main window, may be null (the call is a no-op then).
     * @param left the ours root, not null.
     * @param right the theirs root, not null.
     * @return true when the view opened.
     */
    public static boolean openW2View(Component anchor, EditNodeAbstract left, EditNodeAbstract right) {
        if (anchor == null || left == null || right == null) {
            return false;
        }
        final JackEditTreeContainer container = findOursContainer(left, anchor);
        if (container == null) {
            return false;
        }
        showContainerTab(container);
        return openIn(container, container.getLeftTree().getModel().getEditTree(), right, Kind.W2);
    }

    /**
     * Opens the W3 view of an ours tree against a theirs root (the "Show in trees" action of the merge dialogs):
     * the view opens in the ours tab and classifies per merge plan.
     *
     * @param anchor any component inside the main window, may be null (the call is a no-op then).
     * @param oursTree the ours tree, not null.
     * @param theirsRoot the theirs root, not null.
     * @return true when the view opened.
     */
    public static boolean openW3View(Component anchor, EditTree oursTree, EditNodeAbstract theirsRoot) {
        if (anchor == null || oursTree == null || oursTree.getRoot() == null || theirsRoot == null) {
            return false;
        }
        final JackEditTreeContainer container = findOursContainer(oursTree.getRoot(), anchor);
        if (container == null) {
            return false;
        }
        showContainerTab(container);
        return openIn(container, oursTree, theirsRoot, Kind.W3);
    }

    /**
     * Opens the W3 view of an ours root against a theirs root: the ours tree is the editor tab that carries the
     * root (its import state is the base).
     *
     * @param anchor any component inside the main window, may be null (the call is a no-op then).
     * @param oursRoot the ours root, not null.
     * @param theirsRoot the theirs root, not null.
     * @return true when the view opened.
     */
    public static boolean openW3View(Component anchor, EditNodeAbstract oursRoot, EditNodeAbstract theirsRoot) {
        if (anchor == null || oursRoot == null || theirsRoot == null) {
            return false;
        }
        final JackEditTreeContainer container = findOursContainer(oursRoot, anchor);
        if (container == null) {
            return false;
        }
        showContainerTab(container);
        return openIn(container, container.getLeftTree().getModel().getEditTree(), theirsRoot, Kind.W3);
    }

    private static boolean openIn(JackEditTreeContainer container, EditTree oursTree,
            EditNodeAbstract theirsRoot, Kind kind) {
        final DiffViewController controller = container.getDiffController();
        if (controller.isOpen()) {
            controller.close();
        }
        controller.open(oursTree, theirsRoot, kind);
        return controller.isOpen();
    }
}
