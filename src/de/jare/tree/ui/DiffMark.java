/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * [http://www.eclipse.org/legal/epl-v20.html](http://www.eclipse.org/legal/epl-v20.html)
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.diff.ConflictRecord;
import de.jare.jsoncasted.diff.DeltaOp;

/**
 * The visual diff mark of one node in the tree diff view (concept CONCEPT_TreeDiffView.md section 4): a view-layer
 * value that summarizes one {@link DeltaOp} or {@link ConflictRecord} for exactly one node. It never rides along in
 * the node models, in copies or in the Merkle hash; the controller maps it to the nodes of the live ours tree and of
 * the frozen theirs copy.
 *
 * <p>
 * The mark carries the node's Merkle hash at diff time: the renderer compares it with the node's current hash at
 * paint time and paints the edited color when they differ (section 5, self-validating marks - no event wiring).
 * The mark also keeps the engine object it summarizes - the cherry-pick applies exactly that delta (section 7).
 * </p>
 *
 * @author Janusch Rentenatus
 */
public final class DiffMark {

    /**
     * The change states of the diff view, one per op family of the tree diff engine.
     */
    public enum MarkType {

        ADD("fore.diff.add"),
        REMOVE("fore.diff.remove"),
        MODIFY("fore.diff.modify"),
        MOVE("fore.diff.move"),
        REORDER("fore.diff.move"),
        CONFLICT("fore.diff.conflict"),
        EDITED("fore.diff.edited");

        private final String themeKey;

        private MarkType(String themeKey) {
            this.themeKey = themeKey;
        }

        /**
         * Returns the base theme key of the mark's color (without the light./dark. prefix).
         *
         * @return the theme key, never null.
         */
        public String getThemeKey() {
            return themeKey;
        }
    }

    private final MarkType type;
    private final boolean radiating;
    private final long merkleHashAtDiff;
    private final String tooltip;
    private final DeltaOp delta;
    private final ConflictRecord conflict;
    private boolean taken;

    /**
     * Creates a mark of a delta op.
     *
     * @param op the op the mark summarizes (the cherry-pick applies it), not null.
     * @param type the change state shown.
     * @param tooltip the change description (old -> new, reason, reorder order), shown in the tooltip.
     */
    public DiffMark(DeltaOp op, MarkType type, String tooltip) {
        this(type, op.isRadiating(), 0L, tooltip, op, null);
    }

    /**
     * Creates a mark of a conflict record.
     *
     * @param record the conflict the mark summarizes (the cherry-pick takes its theirs delta), not null.
     * @param tooltip the conflict description (reason, both deltas), shown in the tooltip.
     */
    public DiffMark(ConflictRecord record, String tooltip) {
        this(MarkType.CONFLICT, record.isRadiating(), 0L, tooltip, null, record);
    }

    private DiffMark(MarkType type, boolean radiating, long merkleHashAtDiff, String tooltip,
            DeltaOp delta, ConflictRecord conflict) {
        this.type = type;
        this.radiating = radiating;
        this.merkleHashAtDiff = merkleHashAtDiff;
        this.tooltip = tooltip == null ? "" : tooltip;
        this.delta = delta;
        this.conflict = conflict;
    }

    /**
     * Returns the mark with the node's Merkle hash captured - the builder calls this when it maps the mark onto the
     * node; the hash drives the staleness check at paint time.
     *
     * @param hash the node's Merkle hash at diff time.
     * @return the mark with the hash.
     */
    DiffMark withMerkleHash(long hash) {
        return new DiffMark(type, radiating, hash, tooltip, delta, conflict);
    }

    public MarkType getType() {
        return type;
    }

    /**
     * Tells whether the op radiates into dependent resources (wood id or link target change).
     *
     * @return true when the view shows the radiating badge.
     */
    public boolean isRadiating() {
        return radiating;
    }

    /**
     * Returns the node's Merkle hash at diff time.
     *
     * @return the hash captured at diff time.
     */
    public long getMerkleHashAtDiff() {
        return merkleHashAtDiff;
    }

    /**
     * Returns the change description for the tooltip.
     *
     * @return the description, never null.
     */
    public String getTooltip() {
        return tooltip;
    }

    /**
     * Returns the delta the mark summarizes, or null for conflict marks.
     *
     * @return the delta op, or null.
     */
    public DeltaOp getDelta() {
        return delta;
    }

    /**
     * Returns the conflict the mark summarizes, or null for delta marks.
     *
     * @return the conflict record, or null.
     */
    public ConflictRecord getConflict() {
        return conflict;
    }

    /**
     * Tells whether the mark summarizes a conflict (instead of a delta).
     *
     * @return true for conflict marks.
     */
    public boolean isConflictMark() {
        return conflict != null;
    }

    /**
     * Tells whether this change was already cherry-picked into the ours tree (the mark shows grayed out).
     *
     * @return true after the pick.
     */
    public boolean isTaken() {
        return taken;
    }

    /**
     * Flags the mark as taken after its change was cherry-picked.
     */
    public void setTaken() {
        taken = true;
    }
}
