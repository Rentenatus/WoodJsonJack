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
 * {@code id}, {@code kind}, optional {@code loop} (subloop reference) and {@code trigger}
 * (default {@link Trigger#EXECUTE}). The item carries NO conditions and NO flow control -
 * all semantic decisions stay in the calculators; the loop only executes per prescription
 * and evaluates the calculator's LoopAction.
 *
 * @author Janusch Rentenatus
 */
public class LoopItem {

    private final String id;
    private final Kind kind;
    private String loop;                    // optional: ref to another loop id (subloop call instead of quest execution)
    private Trigger trigger = Trigger.EXECUTE;

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

    public LoopItem copy() {
        LoopItem c = new LoopItem(id, kind);
        if (loop != null) {
            c.loop(loop);
        }
        c.trigger(trigger);
        return c;
    }
}
