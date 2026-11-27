/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.model;

import de.jare.harness.Kind;

/**
 * One item of a {@link Loop} (CONCEPT_Harness.md §5 Loop-Regel, §15.6): fixed attribute set
 * {@code id}, {@code kind}, optional {@code loop} (subloop reference), {@code trigger}
 * (default {@link Trigger#EXECUTE}) and optional {@code skipAfter} (list of item ids: when one
 * of them steers a non-CONTINUE LoopAction in the same EXECUTE iteration, this item is skipped
 * for the rest of that iteration - the steered set is fresh per iteration). The item carries
 * NO conditions and NO flow control - all semantic decisions stay in the calculators; the
 * loop only executes per prescription and evaluates the calculator's LoopAction.
 *
 * @author Janusch Rentenatus
 */
public class LoopItem {

    private final String id;
    private final Kind kind;
    private String loop;                    // optional: ref to another loop id (subloop call instead of quest execution)
    private Trigger trigger = Trigger.EXECUTE;
    private java.util.List<String> skipAfter = new java.util.ArrayList<>();   // item ids whose steering skips this item in the same EXECUTE iteration

    public LoopItem(String id, Kind kind) {
        this.id = id;
        this.kind = kind;
    }

    public String getId() {
        return id;
    }

    public Kind getKind() {
        return kind;
    }

    /** Subloop reference: when set, the driver calls the named loop instead of executing the quest of {@link #getKind()}. */
    public String getLoop() {
        return loop;
    }

    public LoopItem loop(String loop) {
        this.loop = loop;
        return this;
    }

    public boolean isSubLoop() {
        return loop != null;
    }

    public Trigger getTrigger() {
        return trigger;
    }

    public LoopItem trigger(Trigger trigger) {
        this.trigger = trigger == null ? Trigger.EXECUTE : trigger;
        return this;
    }

    /**
     * IDs of steering items (e.g. {@code "split"}): when one of them steered a non-CONTINUE
     * LoopAction in the current EXECUTE iteration, this item is skipped for the rest of that
     * iteration (the split slot handles its own aggregation/work via subloops).
     */
    public java.util.List<String> getSkipAfter() {
        return skipAfter;
    }

    public LoopItem skipAfter(java.util.List<String> skipAfter) {
        this.skipAfter = skipAfter == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(skipAfter);
        return this;
    }

    /** True when one of {@code steeredIds} is in this item's skipAfter list. */
    public boolean isSkippedAfter(java.util.Collection<String> steeredIds) {
        if (skipAfter.isEmpty() || steeredIds == null || steeredIds.isEmpty()) {
            return false;
        }
        for (String s : steeredIds) {
            if (skipAfter.contains(s)) {
                return true;
            }
        }
        return false;
    }

    public LoopItem copy() {
        LoopItem c = new LoopItem(id, kind);
        if (loop != null) {
            c.loop(loop);
        }
        c.trigger(trigger);
        c.skipAfter(skipAfter);
        return c;
    }
}
