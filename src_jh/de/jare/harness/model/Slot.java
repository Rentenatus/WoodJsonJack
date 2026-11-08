/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.model;

/**
 * One slot of a quest (CONCEPT_Harness.md §5): carries only {@code id}, the
 * {@code mutation} label (default unassigned - it names the evolution variant, §7)
 * and the calculator (which may be a whole puzzle subtree).
 *
 * @author Janusch Rentenatus
 */
public class Slot {

    private final String id;
    private String mutation;            // default null/unbelegt
    private CalculatorNode calculator;

    public Slot(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getMutation() {
        return mutation;
    }

    public Slot mutation(String mutation) {
        this.mutation = mutation;
        return this;
    }

    public boolean isBase() {
        return mutation == null;
    }

    public CalculatorNode getCalculator() {
        return calculator;
    }

    public Slot calculator(CalculatorNode calculator) {
        this.calculator = calculator;
        return this;
    }
}
