/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * [http://www.eclipse.org/legal/epl-v20.html](http://www.eclipse.org/legal/epl-v20.html)
 * </copyright>
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
 * that still exists, a MODIFY lights up on both sides. Unresolvable keys (drifted structure) are skipped - the mark
 * maps must never break the view. No-ops are not marked: both sides did the same, there is nothing to review.
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
     * Builds the mark maps of both panes.
     *
     * @param kind W2 (report of ours vs copy) or W3 (merge plan classification).
     * @param oursTree the live ours tree, not null.
     * @param theirsSourceRoot the live theirs root, kept for the re-diff source identity, not null.
     * @param theirsCopyTree the frozen theirs copy shown in the right pane, not null.
     * @param marksLeft the map for the left pane (ours nodes), filled by this call.
     * @param marksRight the map for the right pane (copy nodes), filled by this call.
     * @return the merge plan of a W3 view (the cherry-pick applies against it), or null for W2.
     */
    static MergePlan build(DiffViewController.Kind kind, EditTree oursTree, EditNodeAbstract theirsSourceRoot,
            EditTree theirsCopyTree, Map<EditNodeAbstract, DiffMark> marksLeft,
            Map<EditNodeAbstract, DiffMark> marksRight) {
        final EditNodeAbstract oursRoot = oursTree.getRoot();
        final EditNodeAbstract copyRoot = theirsCopyTree.getRoot();
        if (oursRoot == null || copyRoot == null) {
            return null;
        }
        if (kind == DiffViewController.Kind.W3 && oursTree.getBaseSnapshot() != null) {
            return buildFromPlan(oursTree, oursRoot, copyRoot, marksLeft, marksRight);
        }
        final DiffReport report = TreeDiffer.diff(oursRoot, copyRoot);
        for (DeltaOp op : report.getOps()) {
            markPairDiff(op, oursRoot, copyRoot, marksLeft, marksRight);
        }
        return null;
    }

    private static MergePlan buildFromPlan(EditTree oursTree, EditNodeAbstract oursRoot, EditNodeAbstract copyRoot,
            Map<EditNodeAbstract, DiffMark> marksLeft, Map<EditNodeAbstract, DiffMark> marksRight) {
        final MergePlan plan = EditTreeMerger.analyze(oursTree, copyRoot);
        for (DeltaOp op : plan.getOursReport().getOps()) {
            markOursDelta(op, oursRoot, copyRoot, marksLeft, marksRight);
        }
        for (DeltaOp op : plan.getApplyOps()) {
            markTheirsDelta(op, oursRoot, copyRoot, marksLeft, marksRight);
        }
        // no-ops: both sides did the same - nothing to review, no marks
        for (ConflictRecord record : plan.getConflicts()) {
            markConflict(record, oursRoot, copyRoot, marksLeft, marksRight);
        }
        return plan;
    }

    /**
     * W2: the report of ours against the copy classifies each op directly - the mark type is the op type.
     */
    private static void markPairDiff(DeltaOp op, EditNodeAbstract oursRoot, EditNodeAbstract copyRoot,
            Map<EditNodeAbstract, DiffMark> marksLeft, Map<EditNodeAbstract, DiffMark> marksRight) {
        switch (op.getType()) {
            case ADD ->
                put(marksRight, resolve(copyRoot, op.getKey()), markOf(op, op.toString()));
            case REMOVE ->
                put(marksLeft, resolve(oursRoot, op.getKey()), markOf(op, op.toString()));
            case MODIFY ->
                markModify(op, oursRoot, copyRoot, marksLeft, marksRight, op.toString());
            case MOVE -> {
                // the op key is the first side's path; the second side carries the node at newParent[newIndex]
                put(marksLeft, resolve(oursRoot, op.getKey()), markOf(op, op.toString()));
                put(marksRight, resolve(copyRoot, theirPathOf(op)), markOf(op, op.toString()));
            }
            case REORDER ->
                markReorderChildren(op, oursRoot, copyRoot, marksLeft, marksRight, op.toString(), false);
            default -> {
                // no other delta types exist today
            }
        }
    }

    /**
     * W3 apply list: incoming theirs changes. ADD lives in the copy, REMOVE on the ours node that still exists,
     * MODIFY on both sides.
     */
    private static void markTheirsDelta(DeltaOp op, EditNodeAbstract oursRoot, EditNodeAbstract copyRoot,
            Map<EditNodeAbstract, DiffMark> marksLeft, Map<EditNodeAbstract, DiffMark> marksRight) {
        final String tooltip = THEIRS_PREFIX + op;
        switch (op.getType()) {
            case ADD ->
                put(marksRight, resolve(copyRoot, op.getKey()), markOf(op, tooltip));
            case REMOVE ->
                put(marksLeft, resolve(oursRoot, op.getKey()), markOf(op, tooltip));
            case MODIFY ->
                markModify(op, oursRoot, copyRoot, marksLeft, marksRight, tooltip);
            case MOVE -> {
                // MOVE never reaches the apply list in v1 (it stays a conflict), this is defensive completeness
                put(marksLeft, resolve(oursRoot, op.getKey()), markOf(op, tooltip));
                put(marksRight, resolve(copyRoot, theirPathOf(op)), markOf(op, tooltip));
            }
            case REORDER ->
                markReorderChildren(op, oursRoot, copyRoot, marksLeft, marksRight, tooltip, false);
            default -> {
                // no other delta types exist today
            }
        }
    }

    /**
     * Marks the MOVED children of a REORDER and the parent as modified: the children show WHO traveled (lila, move
     * family, "position i -> j"), the parent shows THAT the array changed (orange, modify family - the order is
     * the value of an array, like the characters are the value of a string) with the order transition in the
     * tooltip. The children pair like the engine's array matcher - wood id first, then unique content hash
     * (duplicates are copies, not moves) - and a pair with different positions is a move.
     *
     * @param oursIsNew true when OURS reordered (ours delta: the copy pane holds the old order); false when theirs
     * or the pair diff reordered (the ours pane holds the old order).
     */
    private static void markReorderChildren(DeltaOp op, EditNodeAbstract oursRoot, EditNodeAbstract copyRoot,
            Map<EditNodeAbstract, DiffMark> marksLeft, Map<EditNodeAbstract, DiffMark> marksRight,
            String tooltip, boolean oursIsNew) {
        final EditNodeAbstract oursParent = resolve(oursRoot, op.getParentKey());
        final EditNodeAbstract copyParent = resolve(copyRoot, op.getParentKey());
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
                j = uniqueHashMatch(oursKid, copyKids);
            }
            if (j == null || j == i) {
                continue; // no counterpart or unchanged position
            }
            if (oursKid instanceof EditNodeAbstract oursChild
                    && copyKids.get(j) instanceof EditNodeAbstract copyChild) {
                put(marksLeft, oursChild, markOf(op, tooltip + "  \u2014  position " + i + " \u2192 " + j));
                put(marksRight, copyChild, markOf(op, tooltip + "  \u2014  position " + i + " \u2192 " + j));
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
        put(marksLeft, oursParent, parentMark);
        put(marksRight, copyParent, parentMark);
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

    /**
     * Returns the copy child that is the unique content twin of the given ours child (equal Merkle hash, no
     * duplicate on the other side) - the engine's second pairing step for arrays.
     */
    private static Integer uniqueHashMatch(DifferableNode oursKid, List<DifferableNode> copyKids) {
        Integer match = null;
        int hits = 0;
        for (int j = 0; j < copyKids.size(); j++) {
            if (copyKids.get(j).getMerkleHash() == oursKid.getMerkleHash()
                    && NodeValidator.isEqual(copyKids.get(j), oursKid)) {
                match = j;
                hits++;
            }
        }
        return hits == 1 ? match : null;
    }
    /**
     * W3 ours deltas: the user's own edits against the base. ADD and MODIFY live in ours; a REMOVE lives only in
     * the copy (theirs still has the node) - the mark says so in its tooltip.
     */
    private static void markOursDelta(DeltaOp op, EditNodeAbstract oursRoot, EditNodeAbstract copyRoot,
            Map<EditNodeAbstract, DiffMark> marksLeft, Map<EditNodeAbstract, DiffMark> marksRight) {
        final String tooltip = OURS_PREFIX + op;
        switch (op.getType()) {
            case ADD, MODIFY ->
                put(marksLeft, resolve(oursRoot, op.getKey()), markOf(op, tooltip));
            case REMOVE ->
                put(marksRight, resolve(copyRoot, op.getKey()), markOf(op, tooltip));
            case MOVE -> {
                // ours moved the node: ours carries it at newParent[newIndex], the copy at the base path
                put(marksLeft, resolve(oursRoot, theirPathOf(op)), markOf(op, tooltip));
                put(marksRight, resolve(copyRoot, op.getKey()), markOf(op, tooltip));
            }
            case REORDER ->
                markReorderChildren(op, oursRoot, copyRoot, marksLeft, marksRight, tooltip, true);
            default -> {
                // no other delta types exist today
            }
        }
    }

    /**
     * W3 conflicts: both sides changed the same node differently - the conflict color on both panes, the tooltip
     * names the reason and both deltas.
     */
    private static void markConflict(ConflictRecord record, EditNodeAbstract oursRoot, EditNodeAbstract copyRoot,
            Map<EditNodeAbstract, DiffMark> marksLeft, Map<EditNodeAbstract, DiffMark> marksRight) {
        final EditNodeAbstract oursNode = resolve(oursRoot, record.getMatchKey());
        final EditNodeAbstract theirsNode = resolve(copyRoot, record.getMatchKey());
        final DiffMark mark = new DiffMark(record, record.toString());
        put(marksLeft, oursNode, mark);
        put(marksRight, theirsNode, mark);
    }

    /**
     * A MODIFY touches the same node on both sides - both panes get the mark so the values can be compared in
     * place.
     */
    private static void markModify(DeltaOp op, EditNodeAbstract oursRoot, EditNodeAbstract copyRoot,
            Map<EditNodeAbstract, DiffMark> marksLeft, Map<EditNodeAbstract, DiffMark> marksRight, String tooltip) {
        put(marksLeft, resolve(oursRoot, op.getKey()), markOf(op, tooltip));
        put(marksRight, resolve(copyRoot, op.getKey()), markOf(op, tooltip));
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
}
