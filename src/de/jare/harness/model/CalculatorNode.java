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
 * The modelled element {@code HarnessCalculator} (CONCEPT_Harness.md §5): either
 * <b>atomic</b> ({@code impl} + optional {@code modelclass}/{@code tool}) or a
 * <b>puzzle</b> ({@code HarnessCalculatorPuzzle}) carrying a list of children that are
 * themselves atomic or puzzle again - recursion is explicit in the model.
 *
 * @author Janusch Rentenatus
 */
public class CalculatorNode {

    public static final String TYPE_ATOMIC = "atomic";
    public static final String TYPE_PUZZLE = "puzzle";

    public static final String IMPL_DETERMINISTIC = "deterministic";
    public static final String IMPL_AI = "ai";

    private final String type;              // atomic | puzzle
    private String impl;                    // atomic only: deterministic | ai
    private String modelclass;              // atomic ai only: key into modelclasses map
    private String tool;                    // atomic deterministic only, e.g. "fs.read"
    private String strategy = "sequence";   // puzzle only: sequence | parallel
    private final List<CalculatorNode> children = new ArrayList<>();

    private CalculatorNode(String type) {
        this.type = type;
    }

    public static CalculatorNode atomic(String impl) {
        CalculatorNode n = new CalculatorNode(TYPE_ATOMIC);
        n.impl = impl;
        return n;
    }

    public static CalculatorNode puzzle(String strategy) {
        CalculatorNode n = new CalculatorNode(TYPE_PUZZLE);
        n.strategy = strategy;
        return n;
    }

    public boolean isPuzzle() {
        return TYPE_PUZZLE.equals(type);
    }

    public String getType() {
        return type;
    }

    public String getImpl() {
        return impl;
    }

    public CalculatorNode modelclass(String modelclass) {
        this.modelclass = modelclass;
        return this;
    }

    public String getModelclass() {
        return modelclass;
    }

    public CalculatorNode tool(String tool) {
        this.tool = tool;
        return this;
    }

    public String getTool() {
        return tool;
    }

    public String getStrategy() {
        return strategy;
    }

    public List<CalculatorNode> getChildren() {
        return children;
    }

    public CalculatorNode addChild(CalculatorNode child) {
        children.add(child);
        return this;
    }
}
