/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.io.def;

import de.jare.harness.HarnessException;
import de.jare.harness.Kind;
import de.jare.harness.io.def.HarnessModelBeans.AbortLimitsBean;
import de.jare.harness.io.def.HarnessModelBeans.CalculatorBean;
import de.jare.harness.io.def.HarnessModelBeans.HarnessModelBean;
import de.jare.harness.io.def.HarnessModelBeans.ModelclassBean;
import de.jare.harness.io.def.HarnessModelBeans.QuestBean;
import de.jare.harness.io.def.HarnessModelBeans.SlotBean;
import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Modelclass;
import de.jare.harness.model.Quest;
import de.jare.harness.model.Slot;
import de.jare.harness.signals.Signal;
import java.util.Map;

/**
 * Converts between the reflect-bound beans (WJJ tree editing, CONCEPT_Harness.md §5) and
 * the runtime {@link HarnessModel}.
 *
 * @author Janusch Rentenatus
 */
public final class HarnessModelBeanConverter {

    private HarnessModelBeanConverter() {
    }

    public static HarnessModel toModel(HarnessModelBean bean) {
        HarnessModel model = new HarnessModel().name(bean.getName()).version(bean.getVersion());
        model.getTaskTypes().addAll(bean.getTaskTypes());
        model.getPromptBudgets().putAll(bean.getPromptBudgets());
        AbortLimitsBean al = bean.getAbortLimits();
        if (al != null) {
            model.abortLimits(al.getMaxIterations(), al.getMaxDepth(), al.getMaxRetries(), al.getMaxToolCalls());
        }
        for (ModelclassBean b : bean.getModelclasses()) {
            model.addModelclass(new Modelclass(b.getProvider(), b.getModel(), b.getPurpose(),
                    b.getSize(), b.getCostFactor(), b.isToolCapable()));
        }
        for (QuestBean qb : bean.getQuests()) {
            Quest quest = new Quest(Kind.valueOf(qb.getKind())).evolvable(qb.isEvolvable());
            for (SlotBean sb : qb.getSlots()) {
                Slot slot = new Slot(sb.getId());
                if (sb.getMutation() != null) {
                    slot.mutation(sb.getMutation());
                }
                if (sb.getCalculator() != null) {
                    slot.calculator(toNode(sb.getCalculator()));
                }
                quest.addSlot(slot);
            }
            model.addQuest(quest);
        }
        return model;
    }

    private static CalculatorNode toNode(CalculatorBean b) {
        CalculatorNode node;
        if (CalculatorNode.TYPE_PUZZLE.equals(b.getType())) {
            node = CalculatorNode.puzzle(b.getStrategy() == null ? "sequence" : b.getStrategy());
            for (CalculatorBean child : b.getChildren()) {
                node.addChild(toNode(child));
            }
        } else {
            node = CalculatorNode.atomic(b.getImpl() == null ? CalculatorNode.IMPL_DETERMINISTIC : b.getImpl());
            if (b.getModelclass() != null) {
                node.modelclass(b.getModelclass());
            }
            if (b.getTool() != null) {
                node.tool(b.getTool());
            }
        }
        for (Map.Entry<String, CalculatorBean> e : b.getOn().entrySet()) {
            node.on(Signal.valueOf(e.getKey()), toNode(e.getValue()));
        }
        for (Map.Entry<String, CalculatorBean> e : b.getCatchMapping().entrySet()) {
            node.catchWith(HarnessException.Type.valueOf(e.getKey()), toNode(e.getValue()));
        }
        return node;
    }
}
