/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.diff.ConflictRecord;
import de.jare.jsoncasted.diff.DiffReport;
import de.jare.jsoncasted.diff.DeltaOp;
import de.jare.jsoncasted.diff.DeltaType;
import de.jare.jsoncasted.diff.DifferableNode;
import de.jare.jsoncasted.diff.MergePlan;
import de.jare.jsoncasted.diff.NodeValidator;
import de.jare.jsoncasted.diff.TreeDiffer;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.merge.EditPathResolver;
import de.jare.jsoncasted.editor.merge.EditTreeMerger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps the engine results to the visual marks of the tree diff view (concept CONCEPT_TreeDiffView.md section 4).
 * W2 maps the {@link DiffReport} of ours against the frozen copy; W3 maps the {@link MergePlan}: ours deltas (the
 * user's own edits), the apply list (incoming theirs changes) and the conflicts.
 *
 * <p>
 * Mapping is spatially strict: a mark can only live on a node that exists on that side, so each op resolves on the
 * side that carries the node - an ADD (theirs added) lights up in the copy, a REMOVE lights up on the ours node
 * that still exists, a MODIFY lights up on both sides. No-ops are not marked: both sides did the same, there is
 * nothing to review.
 * </p>
 *
 * <p>
 * Path resolution is ANCHORED: the op paths are base-relative (W2: ours-relative), so they resolve on the side
 * that kept the base order - the ANCHOR side. The other pane is reached through the identity counterpart map
 * (the selection coupling correspondence), never through a second path resolve: under a reordered ancestor the
 * same path hits the WRONG node on the moved side (a swapped pair would compare a's children against b's). For
 * theirs deltas and pair diffs the anchor is ours; for ours deltas the copy holds the base order and is the
 * anchor. One-sided nodes (ADD/REMOVE) resolve the parent on the anchor side, hop, and take the child on the
 * owning side. Unresolvable keys (drifted structure) are skipped - the mark maps must never break the view.
 * </p>
 *
 * @author Janusch Rentenatus
 */
final class DiffMarkBuilder {

    private static final String THEIRS_PREFIX = "theirs: ";
    private static final String OURS_PREFIX = "yours: ";

    private DiffMarkBuilder() {
        // utility class, no instances
    }

    /**
     * The resolution context of one build: the two pane roots, the two mark maps and the identity counterpart
     * maps between the panes (the anchor hopping).
     */
    private static final class Ctx {

        private final EditNodeAbstract oursRoot;
        private final EditNodeAbstract copyRoot;
        private final Map<EditNodeAbstract, DiffMark> marksLeft;
        private final Map<EditNodeAbstract, DiffMark> marksRight;
        private final Map<EditNodeAbstract, EditNodeAbstract> toTheirs;
        private final Map<EditNodeAbstract, EditNodeAbstract> toOurs;

        private Ctx(EditNodeAbstract oursRoot, EditNodeAbstract copyRoot,
                Map<EditNodeAbstract, DiffMark> marksLeft, Map<EditNodeAbstract, DiffMark> marksRight,
                Map<EditNodeAbstract, EditNodeAbstract> toTheirs,
                Map<EditNodeAbstract, EditNodeAbstract> toOurs) {
            this.oursRoot = oursRoot;
            this.copyRoot = copyRoot;
            this.marksLeft = marksLeft;
            this.marksRight = marksRight;
            this.toTheirs = toTheirs;
            this.toOurs = toOurs;
        }
    }

    /**
     * Builds the mark maps of both panes.
     *
     * @param kind W2 (report of ours vs copy) or W3 (merge plan classification).
     * @param oursTree the live ours tree, not null.
     * @param theirsSourceRoot the live theirs root, kept for the re-diff source identity, not null.
     * @param theirsCopyTree the frozen theirs copy shown in the right pane, not null.
     * @param marksLeft the map for the left pane (ours nodes), filled by this call.
     * @param marksRight the map for the right pane (copy nodes), filled by this call.
     * @param counterpartToTheirs the identity correspondence ours -> copy (anchor hopping).
     * @param counterpartToOurs the identity correspondence copy -> ours (anchor hopping).
     * @return the merge plan of a W3 view (the cherry-pick applies against it), or null for W2.
     */
    static MergePlan build(DiffViewController.Kind kind, EditTree oursTree, EditNodeAbstract theirsSourceRoot,
            EditTree theirsCopyTree, Map<EditNodeAbstract, DiffMark> marksLeft,
            Map<EditNodeAbstract, DiffMark> marksRight,
            Map<EditNodeAbstract, EditNodeAbstract> counterpartToTheirs,
            Map<EditNodeAbstract, EditNodeAbstract> counterpartToOurs) {
        final EditNodeAbstract oursRoot = oursTree.getRoot();
        final EditNodeAbstract copyRoot = theirsCopyTree.getRoot();
        if (oursRoot == null || copyRoot == null) {
            return null;
        }
        final Ctx ctx = new Ctx(oursRoot, copyRoot, marksLeft, marksRight,
                counterpartToTheirs, counterpartToOurs);
        if (kind == DiffViewController.Kind.W3 && oursTree.getBaseSnapshot() != null) {
            return buildFromPlan(oursTree, ctx);
        }
        final DiffReport report = TreeDiffer.diff(oursRoot, copyRoot);
        for (DeltaOp op : report.getOps()) {
            markPairDiff(ctx, op);
        }
        return null;
    }

    private static MergePlan buildFromPlan(EditTree oursTree, Ctx ctx) {
        final MergePlan plan = EditTreeMerger.analyze(oursTree, ctx.copyRoot);
        for (DeltaOp op : plan.getOursReport().getOps()) {
            markOursDelta(ctx, op);
        }
        for (DeltaOp op : plan.getApplyOps()) {
            markTheirsDelta(ctx, op);
        }
        // no-ops: both sides did the same - nothing to review, no marks
        for (ConflictRecord record : plan.getConflicts()) {
            markConflict(ctx, record);
        }
        return plan;
    }

    /**
     * W2: the report of ours against the copy classifies each op directly - the mark type is the op type. The
     * paths are ours-relative: ours is the anchor, the copy side hops.
     */
    private static void markPairDiff(Ctx ctx, DeltaOp op) {
        switch (op.getType()) {
            case ADD ->
                put(ctx.marksRight, theirsAdded(ctx, op), markOf(op, op.toString()));
            case REMOVE ->
                put(ctx.marksLeft, resolve(ctx.oursRoot, op.getKey()), markOf(op, op.toString()));
            case MODIFY ->
                markModify(ctx, op, op.toString());
            case MOVE -> {
                // the op key is the first side's path; the second side carries the node at newParent[newIndex]
                put(ctx.marksLeft, resolve(ctx.oursRoot, op.getKey()), markOf(op, op.toString()));
                put(ctx.marksRight, resolve(ctx.copyRoot, theirPathOf(op)), markOf(op, op.toString()));
            }
            case REORDER ->
                markReorderChildren(ctx, op, op.toString(), false);
            default -> {
                // no other delta types exist today
            }
        }
    }

    /**
     * W3 apply list: incoming theirs changes. Ours kept the base order - ours is the anchor, the copy hops.
     */
    private static void markTheirsDelta(Ctx ctx, DeltaOp op) {
        final String tooltip = THEIRS_PREFIX + op;
        switch (op.getType()) {
            case ADD ->
                put(ctx.marksRight, theirsAdded(ctx, op), markOf(op, tooltip));
            case REMOVE ->
                put(ctx.marksLeft, resolve(ctx.oursRoot, op.getKey()), markOf(op, tooltip));
            case MODIFY ->
                markModify(ctx, op, tooltip);
            case MOVE -> {
                // MOVE never reaches the apply list in v1 (it stays a conflict), this is defensive completeness
                put(ctx.marksLeft, resolve(ctx.oursRoot, op.getKey()), markOf(op, tooltip));
                put(ctx.marksRight, resolve(ctx.copyRoot, theirPathOf(op)), markOf(op, tooltip));
            }
            case REORDER ->
                markReorderChildren(ctx, op, tooltip, false);
            default -> {
                // no other delta types exist today
            }
        }
    }

    /**
     * W3 ours deltas: the user's own edits against the base. The copy kept the base order - the COPY is the
     * anchor, ours hops. An ours REMOVE lives only in the copy; an ours ADD only in ours (its parent hops).
     */
    private static void markOursDelta(Ctx ctx, DeltaOp op) {
        final String tooltip = OURS_PREFIX + op;
        switch (op.getType()) {
            case ADD ->
                put(ctx.marksLeft, oursAdded(ctx, op), markOf(op, tooltip));
            case MODIFY ->
                markOursModify(ctx, op, tooltip);
            case REMOVE ->
                put(ctx.marksRight, resolve(ctx.copyRoot, op.getKey()), markOf(op, tooltip));
            case MOVE -> {
                // ours moved the node: ours carries it at newParent[newIndex], the copy at the base path
                put(ctx.marksLeft, resolve(ctx.oursRoot, theirPathOf(op)), markOf(op, tooltip));
                put(ctx.marksRight, resolve(ctx.copyRoot, op.getKey()), markOf(op, tooltip));
            }
            case REORDER ->
                markReorderChildren(ctx, op, tooltip, true);
            default -> {
                // no other delta types exist today
            }
        }
    }

    /**
     * A MODIFY touches the same node on both sides (theirs changed it, ours kept the base): the ours node
     * resolves on the anchor, the copy node is its counterpart.
     */
    private static void markModify(Ctx ctx, DeltaOp op, String tooltip) {
        final EditNodeAbstract oursNode = resolve(ctx.oursRoot, op.getKey());
        EditNodeAbstract copyNode = hop(ctx.toTheirs, oursNode);
        if (copyNode == null) {
            copyNode = resolve(ctx.copyRoot, op.getKey()); // fallback: ancestors unmoved or drifted pairing
        }
        put(ctx.marksLeft, oursNode, markOf(op, tooltip));
        put(ctx.marksRight, copyNode, markOf(op, tooltip));
    }

    /**
     * A MODIFY of ours (the copy kept the base node): the copy resolves on the anchor, the ours node is its
     * counterpart.
     */
    private static void markOursModify(Ctx ctx, DeltaOp op, String tooltip) {
        final EditNodeAbstract copyNode = resolve(ctx.copyRoot, op.getKey());
        EditNodeAbstract oursNode = hop(ctx.toOurs, copyNode);
        if (oursNode == null) {
            oursNode = resolve(ctx.oursRoot, op.getKey()); // fallback: ancestors unmoved or drifted pairing
        }
        put(ctx.marksLeft, oursNode, markOf(op, tooltip));
        put(ctx.marksRight, copyNode, markOf(op, tooltip));
    }

    /**
     * The added node of theirs exists only in the copy: the parent resolves on the anchor (ours), hops, and the
     * child is taken on the copy at the op's field or index.
     */
    private static EditNodeAbstract theirsAdded(Ctx ctx, DeltaOp op) {
        final EditNodeAbstract copyParent = hop(ctx.toTheirs, resolve(ctx.oursRoot, op.getParentKey()));
        final EditNodeAbstract copyNode = childOf(copyParent, op.getFieldOrIndex());
        return copyNode != null ? copyNode : resolve(ctx.copyRoot, op.getKey());
    }

    /**
     * The added node of ours exists only in ours: the parent resolves on the anchor (the copy), hops, and the
     * child is taken on ours at the op's field or index.
     */
    private static EditNodeAbstract oursAdded(Ctx ctx, DeltaOp op) {
        final EditNodeAbstract oursParent = hop(ctx.toOurs, resolve(ctx.copyRoot, op.getParentKey()));
        final EditNodeAbstract oursNode = childOf(oursParent, op.getFieldOrIndex());
        return oursNode != null ? oursNode : resolve(ctx.oursRoot, op.getKey());
    }

    /**
     * W3 conflicts: both sides changed the same node differently - the conflict color on both panes, the tooltip
     * names the reason and both deltas.
     */
    private static void markConflict(Ctx ctx, ConflictRecord record) {
        final EditNodeAbstract oursNode = resolve(ctx.oursRoot, record.getMatchKey());
        final EditNodeAbstract theirsNode = resolve(ctx.copyRoot, record.getMatchKey());
        final DiffMark mark = new DiffMark(record, record.toString());
        put(ctx.marksLeft, oursNode, mark);
        put(ctx.marksRight, theirsNode, mark);
    }

    /**
     * Marks the MOVED children of a REORDER and the parent as modified: the children show WHO traveled (lila,
     * move family, "position i -> j"), the parent shows THAT the array changed (orange, modify family - the
     * order is the value of an array, like the characters are the value of a string) with the order transition
     * in the tooltip. The children pair like the engine's array matcher - wood id first, then unique content
     * hash (duplicates are copies, not moves) - and a pair with different positions is a move.
     *
     * <p>
     * The parent pair resolves ANCHORED: on the side that kept the base order, the other pane hops through the
     * identity counterpart. A second path resolve would hit the wrong subtree when an ANCESTOR reordered - the
     * inner array of a swapped object is exactly that case.
     * </p>
     *
     * @param oursIsNew true when OURS reordered (ours delta: the copy is the anchor and holds the old order);
     * false when theirs or the pair diff reordered (ours is the anchor and holds the old order).
     */
    private static void markReorderChildren(Ctx ctx, DeltaOp op, String tooltip, boolean oursIsNew) {
        EditNodeAbstract oursParent;
        EditNodeAbstract copyParent;
        if (oursIsNew) {
            // ours reordered: the copy kept the base order and anchors the base-relative path
            copyParent = resolve(ctx.copyRoot, op.getParentKey());
            oursParent = hop(ctx.toOurs, copyParent);
            if (oursParent == null) {
                oursParent = resolve(ctx.oursRoot, op.getParentKey());
            }
        } else {
            // theirs (or the pair diff) reordered: ours kept the base order and anchors
            oursParent = resolve(ctx.oursRoot, op.getParentKey());
            copyParent = hop(ctx.toTheirs, oursParent);
            if (copyParent == null) {
                copyParent = resolve(ctx.copyRoot, op.getParentKey());
            }
        }
        if (oursParent == null || copyParent == null) {
            return; // drifted structure - nothing to mark honestly
        }
        final List<DifferableNode> oursKids = oursParent.children();
        final List<DifferableNode> copyKids = copyParent.children();
        final Map<String, Integer> copyById = new HashMap<>();
        for (int j = 0; j < copyKids.size(); j++) {
            final String id = copyKids.get(j).getObjektId();
            if (id != null) {
                copyById.putIfAbsent(id, j);
            }
        }
        for (int i = 0; i < oursKids.size(); i++) {
            final DifferableNode oursKid = oursKids.get(i);
            Integer j = oursKid.getObjektId() != null ? copyById.get(oursKid.getObjektId()) : null;
            if (j == null) {
                j = uniqueHashMatch(oursKid, copyKids, new boolean[copyKids.size()]);
            }
            if (j == null || j == i) {
                continue; // no counterpart or unchanged position
            }
            if (oursKid instanceof EditNodeAbstract oursChild
                    && copyKids.get(j) instanceof EditNodeAbstract copyChild) {
                put(ctx.marksLeft, oursChild, markOf(op, tooltip + "  \u2014  position " + i + " \u2192 " + j));
                put(ctx.marksRight, copyChild, markOf(op, tooltip + "  \u2014  position " + i + " \u2192 " + j));
            }
        }

        // the parent carries the whole order change like a value change of the array (the string analogy):
        // modify color, with the old -> new order in the tooltip. The old order comes from the pane that kept it,
        // the new order rides in the op (renderer-mirrored labels, as the dialogs show them too).
        final List<DifferableNode> oldKids = (oursIsNew ? copyParent : oursParent).children();
        final String oldOrder = String.join(", ", orderLabels(oldKids));
        final String newOrder = String.join(", ", op.getNewOrder());
        final DiffMark parentMark = new DiffMark(op, DiffMark.MarkType.MODIFY,
                tooltip + "  \u2014  [" + oldOrder + "] \u2192 [" + newOrder + "]");
        put(ctx.marksLeft, oursParent, parentMark);
        put(ctx.marksRight, copyParent, parentMark);
    }

    /**
     * Returns the not-yet-taken theirs child that is the unique content twin of the given ours child (equal
     * Merkle hash, no duplicate on the other side) - the engine's second pairing step for arrays.
     */
    private static Integer uniqueHashMatch(DifferableNode oursKid, List<DifferableNode> copyKids,
            boolean[] theirsTaken) {
        Integer match = null;
        int hits = 0;
        for (int j = 0; j < copyKids.size(); j++) {
            if (theirsTaken[j]) {
                continue;
            }
            if (copyKids.get(j).getMerkleHash() == oursKid.getMerkleHash()
                    && NodeValidator.isEqual(copyKids.get(j), oursKid)) {
                match = j;
                hits++;
            }
        }
        return hits == 1 ? match : null;
    }

    /**
     * Returns the visible identity of the children in their pane order - the same mirroring the engine's reorder
     * labels use: member key, wood id, name (the value for primitive rows), with "#index" when a label occurs
     * twice.
     */
    private static List<String> orderLabels(List<DifferableNode> kids) {
        final Map<String, Integer> counts = new HashMap<>();
        for (DifferableNode kid : kids) {
            counts.merge(childLabel(kid), 1, Integer::sum);
        }
        final List<String> labels = new ArrayList<>(kids.size());
        for (int i = 0; i < kids.size(); i++) {
            final String raw = childLabel(kids.get(i));
            labels.add(counts.get(raw) > 1 ? raw + "#" + i : raw);
        }
        return labels;
    }

    /**
     * The visible identity of one child, mirroring the engine's label rule.
     */
    private static String childLabel(DifferableNode node) {
        if (node.getMemberKey() != null) {
            return node.getMemberKey();
        }
        if (node.getObjektId() != null) {
            return node.getObjektId();
        }
        final String name = node.getName();
        if (name != null && !name.isEmpty()) {
            return name; // edit layer: a primitive row renders its value as the node name
        }
        final String value = node.getValue();
        return value != null && !value.isEmpty() ? value : "?";
    }

    // ========== anchored resolution helpers ==========

    /**
     * Hops from the anchor node to the other pane through the identity counterpart - the anchor's path resolve
     * is valid, the moved side must not resolve base-relative paths.
     */
    private static EditNodeAbstract hop(Map<EditNodeAbstract, EditNodeAbstract> map, EditNodeAbstract anchorNode) {
        return anchorNode == null ? null : map.get(anchorNode);
    }

    /**
     * Resolves a base-relative path on the anchor side; null when it does not exist there.
     */
    private static EditNodeAbstract resolve(EditNodeAbstract root, String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        try {
            return EditPathResolver.resolve(root, path);
        } catch (EditPathResolver.PathUnresolvableException e) {
            return null; // drifted structure - the mark is skipped, the view stays intact
        }
    }

    /**
     * Takes the child of the given parent at the op's field or index - the one-sided node of an ADD lives on the
     * owning side only, its parent hopped there over the anchor.
     */
    private static EditNodeAbstract childOf(EditNodeAbstract parent, String fieldOrIndex) {
        if (parent == null || fieldOrIndex == null) {
            return null;
        }
        try {
            return EditPathResolver.resolve(parent, DeltaOp.childPath("$", fieldOrIndex));
        } catch (EditPathResolver.PathUnresolvableException e) {
            return null;
        }
    }

    /**
     * Returns the second side's path of a MOVE: the op key addresses the first side, the new parent and index
     * address the second.
     */
    private static String theirPathOf(DeltaOp op) {
        return op.getNewParentKey() != null && op.getNewIndex() != null
                ? DeltaOp.childPath(op.getNewParentKey(), String.valueOf(op.getNewIndex()))
                : null;
    }

    private static DiffMark markOf(DeltaOp op, String tooltip) {
        return new DiffMark(op, markTypeOf(op.getType()), tooltip);
    }

    private static DiffMark.MarkType markTypeOf(DeltaType type) {
        switch (type) {
            case ADD:
                return DiffMark.MarkType.ADD;
            case REMOVE:
                return DiffMark.MarkType.REMOVE;
            case MODIFY:
                return DiffMark.MarkType.MODIFY;
            case MOVE:
                return DiffMark.MarkType.MOVE;
            case REORDER:
                return DiffMark.MarkType.REORDER;
            default:
                return DiffMark.MarkType.MODIFY;
        }
    }

    private static void put(Map<EditNodeAbstract, DiffMark> marks, EditNodeAbstract node, DiffMark mark) {
        if (node != null) {
            // the node's hash at diff time drives the staleness check at paint time
            marks.put(node, mark.withMerkleHash(node.getMerkleHash()));
        }
    }
}
