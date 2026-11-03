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
        container.getRightTree().setDiffInfo(kind.getLabel() + ": "
                + TreeDiffSides.describe(oursTree.getRoot()) + " \u2194 "
                + TreeDiffSides.describe(theirsCopy));

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
     * Finds the editor container whose left tree carries the given model: the ours side of a view opened from the
     * root popup of another tab. The search walks the shared tabbed pane, so no registry is needed - every editor
     * container of the main window is a tab of it.
     *
     * @param oursModel the ours side's tree model, not null.
     * @param anchor any component inside the main window, for finding the tabbed pane.
     * @return the container, or null when no editor tab carries the model.
     */
    public static JackEditTreeContainer findOursContainer(JackTreeModel oursModel, Component anchor) {
        final JTabbedPane tabs = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, anchor);
        if (tabs == null) {
            return null;
        }
        for (int i = 0; i < tabs.getTabCount(); i++) {
            final JackEditTreeContainer candidate = containerOf(tabs.getComponentAt(i));
            if (candidate != null && candidate.getLeftTree().getModel() == oursModel) {
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
}
