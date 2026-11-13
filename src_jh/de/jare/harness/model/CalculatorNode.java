/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.model;

import de.jare.harness.HarnessException;
import de.jare.harness.signals.Signal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private String modelclass;              // atomic ai only: purpose in the modelclasses list
    private String calculator;              // atomic only: NAME of a registered calculator (wins over impl)
    private Map<String, String> params = new LinkedHashMap<>();   // named-calculator parameters, e.g. {"model": "phi4-mini:latest"}
    private String tool;                    // atomic deterministic only, e.g. "fs.read"
    private String strategy = "sequence";   // puzzle only: sequence | parallel
    private final List<CalculatorNode> children = new ArrayList<>();
    private final Map<Signal, CalculatorNode> on = new EnumMap<>(Signal.class);          // §13 signal mapping
    private final Map<HarnessException.Type, CalculatorNode> catchMapping = new EnumMap<>(HarnessException.Type.class); // §13 catch

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

    /** Reference a registered calculator by name: {@code "calculator": "scoring.countQuests"}. */
    public CalculatorNode calculator(String name) {
        this.calculator = name;
        return this;
    }

    public String getCalculator() {
        return calculator;
    }

    /** Parameters for a named calculator, e.g. {"model": "phi4-mini:latest"}. */
    public CalculatorNode params(Map<String, String> params) {
        this.params = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
        return this;
    }

    public Map<String, String> getParams() {
        return params;
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

    /** on-mapping: signal → child calculator invoked by the driver (§13). */
    public CalculatorNode on(Signal signal, CalculatorNode child) {
        on.put(signal, child);
        return this;
    }

    public Map<Signal, CalculatorNode> getOn() {
        return on;
    }

    /** catch-mapping: error type → catch calculator, analog to a catch block (§13). */
    public CalculatorNode catchWith(HarnessException.Type type, CalculatorNode child) {
        catchMapping.put(type, child);
        return this;
    }

    public Map<HarnessException.Type, CalculatorNode> getCatchMapping() {
        return catchMapping;
    }

    /** Deep copy for lineage snapshots. */
    public CalculatorNode copy() {
        CalculatorNode c = isPuzzle() ? puzzle(strategy) : atomic(impl);
        if (!isPuzzle()) {
            if (modelclass != null) {
                c.modelclass(modelclass);
            }
            if (calculator != null) {
                c.calculator(calculator);
            }
            if (!params.isEmpty()) {
                c.params(params);
            }
            if (tool != null) {
                c.tool(tool);
            }
        }
        for (CalculatorNode child : children) {
            c.addChild(child.copy());
        }
        for (Map.Entry<Signal, CalculatorNode> e : on.entrySet()) {
            c.on(e.getKey(), e.getValue().copy());
        }
        for (Map.Entry<HarnessException.Type, CalculatorNode> e : catchMapping.entrySet()) {
            c.catchWith(e.getKey(), e.getValue().copy());
        }
        return c;
    }
}
