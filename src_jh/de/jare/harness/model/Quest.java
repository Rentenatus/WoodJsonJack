/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.model;

import de.jare.harness.Kind;
import java.util.ArrayList;
import java.util.List;

/**
 * A quest of the harness model (CONCEPT_Harness.md §5): {@code kind} and
 * {@code evolvable} live on the quest, not on the slot. The slots hold the
 * alternative calculators; the three {@code Mutation-Resolve-*} slots of an
 * evolvable quest are the three parallel candidates of one evolution step (§7).
 *
 * This class is the RECIPE side only - pure blueprint, copied into lineage snapshots.
 * The runtime memory of a run lives in {@link de.jare.harness.memory.MemoryQuest}, never here
 * (docs/working/DESIGN_Quest_WorkOrder.md).
 *
 * @author Janusch Rentenatus
 */
public class Quest {

    private final Kind kind;
    private boolean evolvable;
    private final List<Slot> slots = new ArrayList<>();

    public Quest(Kind kind) {
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isEvolvable() {
        return evolvable;
    }

    public Quest evolvable(boolean evolvable) {
        this.evolvable = evolvable;
        return this;
    }

    public List<Slot> getSlots() {
        return slots;
    }

    public Quest addSlot(Slot slot) {
        slots.add(slot);
        return this;
    }

    /** The base slot (mutation unassigned) - the default calculator for this kind. */
    public Slot baseSlot() {
        return slots.stream().filter(Slot::isBase).findFirst()
                .orElse(slots.isEmpty() ? null : slots.get(0));
    }
}
