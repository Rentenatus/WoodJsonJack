/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A named loop of the harness model (CONCEPT_Harness.md §5 Loop-Regel, §15.6): an ordered
 * list of {@link LoopItem}s. Loops call each other via {@link LoopItem#getLoop()} (subloop
 * recursion on the driver's call stack, bounded by {@code maxDepth}). {@code "main"} is the
 * entry loop of a run.
 *
 * @author Janusch Rentenatus
 */
public class Loop {

    /** The entry loop id of a run. */
    public static final String MAIN = "main";

    /** The default subloop id: split children run this loop, not a fresh "main" (§15.6). */
    public static final String SUB = "sub";

    private final String id;
    private final List<LoopItem> items = new ArrayList<>();

    public Loop(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public List<LoopItem> getItems() {
        return items;
    }

    public Loop addItem(LoopItem item) {
        items.add(item);
        return this;
    }

    /** The items firing on this trigger, in list order. */
    public List<LoopItem> itemsFor(Trigger trigger) {
        List<LoopItem> out = new ArrayList<>();
        for (LoopItem item : items) {
            if (item.getTrigger() == trigger) {
                out.add(item);
            }
        }
        return out;
    }

    public Loop copy() {
        Loop c = new Loop(id);
        for (LoopItem item : items) {
            c.addItem(item.copy());
        }
        return c;
    }
}
