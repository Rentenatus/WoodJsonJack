/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * [http://www.eclipse.org/legal/epl-v20.html](http://www.eclipse.org/legal/epl-v20.html)
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.diff.DeltaOp;
import de.jare.jsoncasted.diff.DeltaType;
import de.jare.jsoncasted.diff.MergePlan;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.events.HistoryManager;
import de.jare.jsoncasted.editor.merge.EditTreeMerger;
import de.jare.jsoncasted.editor.merge.MergeOutcome;
import de.jare.tree.control.model.JackTreeModel;
import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private MergePlan plan;
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
        plan = null;
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
        plan = DiffMarkBuilder.build(kind, oursTree, theirsSourceRoot, theirsCopyTree, marksLeft, marksRight);

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
     * Returns the mark of one copy node of the right pane - the pick UI asks before applying.
     *
     * @param copyNode a node of the frozen copy.
     * @return its mark, or null when the node carries none.
     */
    public DiffMark getRightMark(EditNodeAbstract copyNode) {
        return marksRight.get(copyNode);
    }

    /**
     * Tells why a pick is not possible, or null when it is (concept section 7). Cherry-picking is W3-only - the
     * merge plan protects the pick - and structural changes (MOVE/REORDER) stay deferred in v1.
     *
     * @param mark the mark to check, may be null.
     * @return the hint for the user, or null when the pick may run.
     */
    public String pickHint(DiffMark mark) {
        if (!open || kind != Kind.W3 || plan == null) {
            return "Cherry-picking needs the W3 diff view (the merge plan protects the pick).";
        }
        if (mark == null) {
            return "This node carries no diff mark.";
        }
        if (mark.isTaken()) {
            return "This change was already taken.";
        }
        if (pickDeltaOf(mark) == null) {
            return "This change is structural (MOVE/REORDER) - not applicable yet (v1).";
        }
        return null;
    }

    /**
     * Takes the change of one marked copy node into the live ours tree (concept section 7): the mark's delta - a
     * conflict's theirs delta for conflict marks, the inline take-theirs decision - applies through
     * {@link EditTreeMerger#applyDeltas} as ONE transaction on the ours history (one undo step). The mark flips to
     * taken; the ours node's own mark self-invalidates through the Merkle hash comparison.
     *
     * @param copyNode the marked node of the frozen copy, not null.
     * @return null on success, else the problem list as one message.
     */
    public String pick(EditNodeAbstract copyNode) {
        final DiffMark mark = marksRight.get(copyNode);
        final String hint = pickHint(mark);
        if (hint != null) {
            return hint;
        }
        final List<DeltaOp> applied = new ArrayList<>();
        applied.add(pickDeltaOf(mark));
        return applyAndFlag(applied, "Pick");
    }

    /**
     * Takes all incoming theirs changes of the apply list into the live ours tree (concept section 7): one
     * transaction, one undo step. Radiating ops stay out of the batch - they must be picked individually as long as
     * the dependent lookup (R10) is not built; conflicts are decisions and never part of a take-all.
     *
     * @return null on success, else a message (also when there was nothing to take).
     */
    public String takeAllIncoming() {
        if (!open || kind != Kind.W3 || plan == null) {
            return "Cherry-picking needs the W3 diff view (the merge plan protects the pick).";
        }
        final List<DeltaOp> batch = new ArrayList<>();
        int skippedRadiating = 0;
        int skippedStructural = 0;
        for (DeltaOp op : plan.getApplyOps()) {
            if (op.getType() == DeltaType.MOVE || op.getType() == DeltaType.REORDER) {
                skippedStructural++;
            } else if (op.isRadiating()) {
                skippedRadiating++;
            } else {
                batch.add(op);
            }
        }
        if (batch.isEmpty()) {
            final StringBuilder nothing = new StringBuilder("Nothing to take.");
            if (skippedRadiating > 0) {
                nothing.append(" ").append(skippedRadiating).append(" radiating change(s) wait for an individual pick.");
            }
            if (skippedStructural > 0) {
                nothing.append(" ").append(skippedStructural).append(" structural change(s) are not applicable (v1).");
            }
            return nothing.toString();
        }
        final String outcome = applyAndFlag(batch, "Take all incoming");
        final StringBuilder message = new StringBuilder(outcome == null ? "" : outcome);
        if (skippedRadiating > 0) {
            message.append(message.length() > 0 ? "\n" : "").append(skippedRadiating)
                    .append(" radiating change(s) wait for an individual pick.");
        }
        return message.length() == 0 ? null : message.toString();
    }

    /**
     * Applies the deltas as one transaction and flags the taken marks of the right pane (identity match on the
     * delta - the builder mapped the plan's own instances).
     */
    private String applyAndFlag(List<DeltaOp> deltas, String what) {
        final MergeOutcome outcome = EditTreeMerger.applyDeltas(oursTree, theirsCopyTree.getRoot(), plan,
                deltas, history());
        if (!outcome.isApplied()) {
            return String.join("\n", outcome.getProblems());
        }
        for (DiffMark mark : marksRight.values()) {
            if (!mark.isTaken() && mark.getDelta() != null && deltas.contains(mark.getDelta())) {
                mark.setTaken();
            }
        }
        // the taken flag lives in the marks; a repaint reruns the renderer for the visible rows
        container.getRightTree().getTree().repaint();
        if (!outcome.getProblems().isEmpty()) {
            return String.join("\n", outcome.getProblems());
        }
        return null;
    }

    /**
     * Returns the delta a pick of this mark would apply: the conflict's theirs delta for conflict marks (the
     * inline take-theirs), the mark's own delta otherwise. Null when there is nothing applicable (structural, or a
     * conflict without a theirs delta).
     */
    private DeltaOp pickDeltaOf(DiffMark mark) {
        final DeltaOp delta = mark.isConflictMark() ? mark.getConflict().getTheirsDelta() : mark.getDelta();
        return delta != null && delta.getType() != DeltaType.MOVE && delta.getType() != DeltaType.REORDER
                ? delta : null;
    }

    /**
     * Returns the undo history of the ours tab - every pick books its transaction there (one undo step per batch).
     *
     * @return the ours history, or null when no master control is wired.
     */
    private HistoryManager history() {
        final JackTreeModel oursModel = container.getLeftTree().getModel();
        return container.getLeftTree().getJackMaster() != null
                ? container.getLeftTree().getJackMaster().getUndoManager().getManagerFor(oursModel).getHistory()
                : null;
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
