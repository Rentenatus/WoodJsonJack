/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * [http://www.eclipse.org/legal/epl-v20.html](http://www.eclipse.org/legal/epl-v20.html)
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.diff.DeltaOp;
import de.jare.jsoncasted.diff.DeltaType;
import de.jare.jsoncasted.diff.DiffReport;
import de.jare.jsoncasted.diff.DifferableNode;
import de.jare.jsoncasted.diff.MergePlan;
import de.jare.jsoncasted.diff.NodeValidator;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.events.HistoryManager;
import de.jare.jsoncasted.editor.merge.EditTreeMerger;
import de.jare.jsoncasted.editor.merge.MergeOutcome;
import de.jare.tree.control.model.JackTreeModel;
import java.awt.Component;
import java.awt.Window;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JTabbedPane;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

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

        W2("Diff W2", "W2"),
        W3("Diff W3", "W3");

        private final String label;
        private final String badge;

        private Kind(String label, String badge) {
            this.label = label;
            this.badge = badge;
        }

        public String getLabel() {
            return label;
        }

        /**
         * Returns the short kind badge for the diff banner (the banner itself says "diff", the badge only the
         * kind - the pair description does not repeat it).
         *
         * @return "W2" or "W3".
         */
        public String getBadge() {
            return badge;
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
    /** The selection coupling: ours node -> its copy counterpart, and back. */
    private final Map<EditNodeAbstract, EditNodeAbstract> counterpartToTheirs = new HashMap<>();
    private final Map<EditNodeAbstract, EditNodeAbstract> counterpartToOurs = new HashMap<>();
    private TreeSelectionListener selectionCoupler;
    private boolean syncingSelection;

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
        counterpartToTheirs.clear();
        counterpartToOurs.clear();
        uninstallSelectionCoupler();
        container.getLeftTree().setDiffMarks(null);
        container.getRightTree().setDiffMarks(null);
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
        counterpartToTheirs.clear();
        counterpartToOurs.clear();
        // the identity correspondence comes FIRST: the mark building resolves base-relative paths on the
        // anchor side only and hops to the other pane through it (see DiffMarkBuilder)
        buildCounterparts(oursTree.getRoot(), theirsCopyTree.getRoot());
        installSelectionCoupler();
        plan = DiffMarkBuilder.build(kind, oursTree, theirsSourceRoot, theirsCopyTree, marksLeft, marksRight,
                counterpartToTheirs, counterpartToOurs);

        container.getRightTree().getTree().setModel(new JackTreeModel(theirsCopyTree));
        container.getRightTree().refreshResourceInfo();
        container.getLeftTree().setDiffMarks(marksLeft);
        container.getRightTree().setDiffMarks(marksRight);
        pairText = TreeDiffSides.describe(oursTree.getRoot()) + " \u2194 "
                + TreeDiffSides.describe(theirsCopy);

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
     * Builds the node correspondence of the two panes with the match semantics of the engine's array matcher:
     * keyed members pair by member key, anonymous children pair by wood id first, then by unique content hash
     * (duplicates are copies, not moves), the remainder pairs in order (the positional semantics of the diff
     * paths - data children and unidentifiable anonymous children). A REORDERED array therefore pairs each object
     * with ITSELF on the other side, not with its slot. Nodes without a counterpart (added or removed) stay
     * unmapped - clicking them selects nothing on the other side.
     *
     * @param oursNode a node of the live ours tree, may be null.
     * @param theirsNode the counterpart node in the frozen copy, may be null.
     */
    private void buildCounterparts(EditNodeAbstract oursNode, EditNodeAbstract theirsNode) {
        if (oursNode == null || theirsNode == null) {
            return;
        }
        counterpartToTheirs.put(oursNode, theirsNode);
        counterpartToOurs.put(theirsNode, oursNode);

        final List<DifferableNode> oursChildren = oursNode.children();
        final List<DifferableNode> theirsChildren = theirsNode.children();
        if (oursChildren.isEmpty() || theirsChildren.isEmpty()) {
            return;
        }

        final Map<String, Integer> theirsByKey = new HashMap<>();
        final Map<String, Integer> theirsById = new HashMap<>();
        for (int j = 0; j < theirsChildren.size(); j++) {
            final String key = theirsChildren.get(j).getMemberKey();
            if (key != null && !key.startsWith("#")) {
                theirsByKey.putIfAbsent(key, j);
            }
            final String id = theirsChildren.get(j).getObjektId();
            if (id != null) {
                theirsById.putIfAbsent(id, j);
            }
        }

        final boolean[] oursMatched = new boolean[oursChildren.size()];
        final boolean[] theirsMatched = new boolean[theirsChildren.size()];

        // pass 1: keyed members by member key, anonymous children by wood id
        for (int i = 0; i < oursChildren.size(); i++) {
            final DifferableNode oursChild = oursChildren.get(i);
            final String key = oursChild.getMemberKey();
            Integer j = key != null && !key.startsWith("#") ? theirsByKey.get(key) : null;
            if (j == null && key == null && oursChild.getObjektId() != null) {
                j = theirsById.get(oursChild.getObjektId());
            }
            if (j != null) {
                oursMatched[i] = true;
                theirsMatched[j] = true;
                buildCounterparts((EditNodeAbstract) oursChild, (EditNodeAbstract) theirsChildren.get(j));
            }
        }

        // pass 2: anonymous children with a unique content twin (duplicates are copies, not moves)
        for (int i = 0; i < oursChildren.size(); i++) {
            if (oursMatched[i] || oursChildren.get(i).getMemberKey() != null) {
                continue;
            }
            final Integer j = uniqueHashMatch(oursChildren.get(i), theirsChildren, theirsMatched);
            if (j != null) {
                oursMatched[i] = true;
                theirsMatched[j] = true;
                buildCounterparts((EditNodeAbstract) oursChildren.get(i), (EditNodeAbstract) theirsChildren.get(j));
            }
        }

        // pass 3: the remainders pair in order - data children ('#n') and unidentifiable anonymous children
        // follow the positional semantics of the diff paths. Keyed members that did not match stay unmatched:
        // the other side does not have them.
        int theirsCursor = 0;
        for (int i = 0; i < oursChildren.size(); i++) {
            if (oursMatched[i]) {
                continue;
            }
            while (theirsCursor < theirsChildren.size()
                    && (theirsMatched[theirsCursor]
                    || (oursChildren.get(i).getMemberKey() != null
                    && oursChildren.get(i).getMemberKey().startsWith("#")) != (theirsChildren.get(theirsCursor).getMemberKey() != null))) {
                theirsCursor++;
            }
            if (theirsCursor >= theirsChildren.size()) {
                continue;
            }
            oursMatched[i] = true;
            theirsMatched[theirsCursor] = true;
            buildCounterparts((EditNodeAbstract) oursChildren.get(i),
                    (EditNodeAbstract) theirsChildren.get(theirsCursor));
            theirsCursor++;
        }
    }

    /**
     * Returns the not-yet-taken theirs child that is the unique content twin of the given ours child (equal
     * Merkle hash, validated equal, no duplicate on the other side) - the engine's second pairing step.
     */
    private static Integer uniqueHashMatch(DifferableNode oursKid, List<DifferableNode> theirsKids,
            boolean[] theirsTaken) {
        Integer match = null;
        int hits = 0;
        for (int j = 0; j < theirsKids.size(); j++) {
            if (theirsTaken[j]) {
                continue;
            }
            if (theirsKids.get(j).getMerkleHash() == oursKid.getMerkleHash()
                    && NodeValidator.isEqual(theirsKids.get(j), oursKid)) {
                match = j;
                hits++;
            }
        }
        return hits == 1 ? match : null;
    }

    /**
     * Registers the selection coupler on both trees - once per container, the listener reads the open view state.
     */
    private void installSelectionCoupler() {
        if (selectionCoupler != null) {
            return;
        }
        selectionCoupler = e -> {
            if (!open || syncingSelection) {
                return;
            }
            final JTree from = e.getSource() instanceof JTree jtree ? jtree : null;
            if (from == null) {
                return;
            }
            final boolean fromOurs = from == container.getLeftTree().getTree();
            final JackEditTree toTree = fromOurs ? container.getRightTree() : container.getLeftTree();
            final JTree to = toTree.getTree();
            final Map<EditNodeAbstract, EditNodeAbstract> map
                    = fromOurs ? counterpartToTheirs : counterpartToOurs;

            final List<TreePath> counterparts = new ArrayList<>();
            final TreePath[] selected = from.getSelectionPaths();
            if (selected != null) {
                for (TreePath path : selected) {
                    if (path.getLastPathComponent() instanceof DefaultMutableTreeNode dmtn
                            && dmtn.getUserObject() instanceof EditNodeAbstract node) {
                        final EditNodeAbstract counterpart = map.get(node);
                        final TreePath counterpartPath = counterpart == null
                                ? null : pathOf(toTree, counterpart);
                        if (counterpartPath != null) {
                            counterparts.add(counterpartPath);
                        }
                    }
                }
            }
            syncingSelection = true;
            try {
                if (counterparts.isEmpty()) {
                    to.setSelectionPath(null);
                } else {
                    to.setSelectionPaths(counterparts.toArray(new TreePath[0]));
                    to.scrollPathToVisible(counterparts.get(0));
                }
            } finally {
                syncingSelection = false;
            }
        };
        container.getLeftTree().getTree().addTreeSelectionListener(selectionCoupler);
        container.getRightTree().getTree().addTreeSelectionListener(selectionCoupler);
    }

    /**
     * Removes the selection coupler - the trees select independently again.
     */
    private void uninstallSelectionCoupler() {
        if (selectionCoupler == null) {
            return;
        }
        container.getLeftTree().getTree().removeTreeSelectionListener(selectionCoupler);
        container.getRightTree().getTree().removeTreeSelectionListener(selectionCoupler);
        selectionCoupler = null;
    }

    /**
     * Returns the swing tree path of an edit node in the given tree - the same id/range lookup the selection
     * handling of {@code JackEditTree} uses.
     *
     * @param tree the rendered JackEditTree, not null.
     * @param node the edit node to find, not null.
     * @return the path, or null when the tree does not show the node.
     */
    private TreePath pathOf(JackEditTree tree, EditNodeAbstract node) {
        final DefaultMutableTreeNode swingNode = tree.getModel().findNodeByIdAndRange(
                node.getEditId(), node.getLeftRange(), node.getTimesRange());
        return swingNode == null ? null : new TreePath(swingNode.getPath());
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
     * Tells why a pick is not possible, or null when it is (concept section 7). Picking takes a theirs change
     * into ours through the one application rail - W3 protected by the merge plan, W2 with the mark's own
     * report op (no merge plan exists for a plain pair); structural changes (MOVE/REORDER) stay deferred.
     *
     * @param mark the mark to check, may be null.
     * @return the hint for the user, or null when the pick may run.
     */
    public String pickHint(DiffMark mark) {
        if (!open) {
            return "The diff view is closed.";
        }
        if (kind == Kind.W3 && plan == null) {
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
     * Opens the report dialog of the open view (the banner's Report button, concept section 9): the precise op
     * list as the list counterpart of the spatial view. W2 shows the delta ops of ours against the frozen copy,
     * W3 the fresh merge plan of ours against the frozen copy. The report recomputes from the trees, so it shows
     * the CURRENT state - not the state the marks were built from (stale marks say so themselves).
     *
     * @param owner the owner window, may be null.
     */
    public void showReport(Window owner) {
        if (!open || oursTree == null || oursTree.getRoot() == null
                || theirsCopyTree == null || theirsCopyTree.getRoot() == null) {
            return;
        }
        if (kind == Kind.W3) {
            final MergePlan current = EditTreeMerger.analyze(oursTree, theirsCopyTree.getRoot());
            new MergePlanDialog(owner, current, oursTree.getRoot(), theirsCopyTree.getRoot()).setVisible(true);
        } else {
            new TreeDiffDialog(owner, oursTree.getRoot(), theirsCopyTree.getRoot()).setVisible(true);
        }
    }

    /**
     * Applies the deltas as one transaction and flags the taken marks of the right pane (identity match on the
     * delta - the builder mapped the plan's own instances). W2 picks carry a neutral plan - the report op
     * carries everything the application needs, the plan only books the outcome.
     */
    private String applyAndFlag(List<DeltaOp> deltas, String what) {
        final MergeOutcome outcome = EditTreeMerger.applyDeltas(oursTree, theirsCopyTree.getRoot(),
                planOrCarrier(), deltas, history());
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
     * The plan of the application rail: the W3 merge plan, or the neutral carrier for W2 (an empty pair of
     * reports - applyDeltas books its outcome on a plan, a plain pair has none, the report op carries
     * everything the application itself needs).
     */
    private MergePlan planOrCarrier() {
        if (plan != null) {
            return plan;
        }
        if (neutralCarrier == null) {
            neutralCarrier = new MergePlan(new DiffReport(), new DiffReport());
        }
        return neutralCarrier;
    }

    /** Neutral plan carrier of a W2 view, built on first pick. */
    private MergePlan neutralCarrier;

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
}
