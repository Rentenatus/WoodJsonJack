/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.io.def;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reflect-bindable beans mirroring the {@code harness-model.json} schema
 * (CONCEPT_Harness.md §5), so the model file is parseable/validatable/editable in the
 * WJJ tree via {@link JsonHarnessModelDefinition}. Converted to/from the runtime
 * {@link de.jare.harness.model.HarnessModel} by {@link HarnessModelBeanConverter}.
 *
 * @author Janusch Rentenatus
 */
public class HarnessModelBeans {

    public static class AbortLimitsBean {
        private int maxIterations;
        private int maxDepth;
        private int maxRetries;
        private int maxToolCalls;

        public int getMaxIterations() { return maxIterations; }
        public void setMaxIterations(int v) { this.maxIterations = v; }
        public int getMaxDepth() { return maxDepth; }
        public void setMaxDepth(int v) { this.maxDepth = v; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int v) { this.maxRetries = v; }
        public int getMaxToolCalls() { return maxToolCalls; }
        public void setMaxToolCalls(int v) { this.maxToolCalls = v; }
    }

    public static class ModelclassBean {
        private String provider;
        private String model;
        private String purpose;
        private int size;
        private double costFactor;
        private boolean toolCapable;

        public String getProvider() { return provider; }
        public void setProvider(String v) { this.provider = v; }
        public String getModel() { return model; }
        public void setModel(String v) { this.model = v; }
        public String getPurpose() { return purpose; }
        public void setPurpose(String v) { this.purpose = v; }
        public int getSize() { return size; }
        public void setSize(int v) { this.size = v; }
        public double getCostFactor() { return costFactor; }
        public void setCostFactor(double v) { this.costFactor = v; }
        public boolean isToolCapable() { return toolCapable; }
        public void setToolCapable(boolean v) { this.toolCapable = v; }
    }

    public static class CalculatorBean {
        private String type;
        private String impl;
        private String calculator;
        private Map<String, String> params = new LinkedHashMap<>();
        private String modelclass;
        private String tool;
        private String strategy;
        private List<CalculatorBean> children = new ArrayList<>();
        private Map<String, CalculatorBean> on = new LinkedHashMap<>();
        private Map<String, CalculatorBean> catchMapping = new LinkedHashMap<>();

        public String getType() { return type; }
        public void setType(String v) { this.type = v; }
        public String getImpl() { return impl; }
        public void setImpl(String v) { this.impl = v; }
        public String getCalculator() { return calculator; }
        public void setCalculator(String v) { this.calculator = v; }
        public Map<String, String> getParams() { return params; }
        public void setParams(Map<String, String> v) { this.params = v; }
        public String getModelclass() { return modelclass; }
        public void setModelclass(String v) { this.modelclass = v; }
        public String getTool() { return tool; }
        public void setTool(String v) { this.tool = v; }
        public String getStrategy() { return strategy; }
        public void setStrategy(String v) { this.strategy = v; }
        public List<CalculatorBean> getChildren() { return children; }
        public void setChildren(List<CalculatorBean> v) { this.children = v; }
        public Map<String, CalculatorBean> getOn() { return on; }
        public void setOn(Map<String, CalculatorBean> v) { this.on = v; }
        public Map<String, CalculatorBean> getCatchMapping() { return catchMapping; }
        public void setCatchMapping(Map<String, CalculatorBean> v) { this.catchMapping = v; }
    }

    public static class SlotBean {
        private String id;
        private String mutation;
        private CalculatorBean calculator;

        public String getId() { return id; }
        public void setId(String v) { this.id = v; }
        public String getMutation() { return mutation; }
        public void setMutation(String v) { this.mutation = v; }
        public CalculatorBean getCalculator() { return calculator; }
        public void setCalculator(CalculatorBean v) { this.calculator = v; }
    }

    public static class QuestBean {
        private String kind;
        private boolean evolvable;
        private List<SlotBean> slots = new ArrayList<>();

        public String getKind() { return kind; }
        public void setKind(String v) { this.kind = v; }
        public boolean isEvolvable() { return evolvable; }
        public void setEvolvable(boolean v) { this.evolvable = v; }
        public List<SlotBean> getSlots() { return slots; }
        public void setSlots(List<SlotBean> v) { this.slots = v; }
    }

    public static class HarnessModelBean {
        private String name;
        private String version;
        private List<String> taskTypes = new ArrayList<>();
        private Map<String, Integer> promptBudgets = new LinkedHashMap<>();
        private AbortLimitsBean abortLimits;
        private List<ModelclassBean> modelclasses = new ArrayList<>();
        private List<QuestBean> quests = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String v) { this.name = v; }
        public String getVersion() { return version; }
        public void setVersion(String v) { this.version = v; }
        public List<String> getTaskTypes() { return taskTypes; }
        public void setTaskTypes(List<String> v) { this.taskTypes = v; }
        public Map<String, Integer> getPromptBudgets() { return promptBudgets; }
        public void setPromptBudgets(Map<String, Integer> v) { this.promptBudgets = v; }
        public AbortLimitsBean getAbortLimits() { return abortLimits; }
        public void setAbortLimits(AbortLimitsBean v) { this.abortLimits = v; }
        public List<ModelclassBean> getModelclasses() { return modelclasses; }
        public void setModelclasses(List<ModelclassBean> v) { this.modelclasses = v; }
        public List<QuestBean> getQuests() { return quests; }
        public void setQuests(List<QuestBean> v) { this.quests = v; }
    }
}
