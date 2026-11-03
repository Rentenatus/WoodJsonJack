/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * [http://www.eclipse.org/legal/epl-v20.html](http://www.eclipse.org/legal/epl-v20.html)
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.diff.ConflictRecord;
import de.jare.jsoncasted.diff.DeltaOp;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.merge.EditPathResolver;
import java.util.Map;

/**
 * Maps the engine results to the visual marks of the tree diff view (concept CONCEPT_TreeDiffView.md section 4).
 * W2 maps a {@link de.jare.jsoncasted.diff.DiffReport} of ours against the frozen copy; W3 maps a
 * {@link de.jare.jsoncasted.diff.MergePlan} (ours deltas, apply list, conflicts). Every reported key is resolved to
 * the node of the live ours tree and of the frozen theirs copy through the {@link EditPathResolver}; the mark
 * captures the node's Merkle hash at diff time for the staleness check.
 *
 * @author Janusch Rentenatus
 */
final class DiffMarkBuilder {

    private DiffMarkBuilder() {
        // utility class, no instances
    }

    /**
     * Builds the mark maps of both panes.
     *
     * @param kind W2 (report of ours vs copy) or W3 (merge plan classification).
     * @param oursTree the live ours tree, not null.
     * @param theirsSourceRoot the live theirs root, for the analysis input, not null.
     * @param theirsCopyTree the frozen theirs copy shown in the right pane, not null.
     * @param marksLeft the map for the left pane (ours nodes), filled by this call.
     * @param marksRight the map for the right pane (copy nodes), filled by this call.
     */
    static void build(DiffViewController.Kind kind, EditTree oursTree, EditNodeAbstract theirsSourceRoot,
            EditTree theirsCopyTree, Map<EditNodeAbstract, DiffMark> marksLeft,
            Map<EditNodeAbstract, DiffMark> marksRight) {
        // phase 4: map DiffReport (W2) resp. MergePlan (W3) onto the two panes
    }
}
