/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.io.def;

import static de.jare.jsoncasted.model.JsonCollectionType.LIST;

import de.jare.harness.io.def.HarnessModelBeans.AbortLimitsBean;
import de.jare.harness.io.def.HarnessModelBeans.CalculatorBean;
import de.jare.harness.io.def.HarnessModelBeans.HarnessModelBean;
import de.jare.harness.io.def.HarnessModelBeans.ModelclassBean;
import de.jare.harness.io.def.HarnessModelBeans.QuestBean;
import de.jare.harness.io.def.HarnessModelBeans.SlotBean;
import de.jare.jsoncasted.io.JsonCastingLevel;
import de.jare.jsoncasted.io.JsonItemDefinition;
import de.jare.jsoncasted.lang.JsonInstance;
import de.jare.jsoncasted.model.JsonModel;
import de.jare.jsoncasted.model.item.JsonClass;
import de.jare.jsoncasted.model.item.JsonMap;

/**
 * The JsonItemDefinition for {@code harness-model.json} (CONCEPT_Harness.md §5): with this
 * definition the harness model is a normal WJJ model - parseable, soft-validatable and
 * editable in the tree like any bundle. Runtime conversion to
 * {@link de.jare.harness.model.HarnessModel} happens in {@link HarnessModelBeanConverter}.
 *
 * @author Janusch Rentenatus
 */
public class JsonHarnessModelDefinition implements JsonItemDefinition {

    public static final JsonHarnessModelDefinition INSTANCE = new JsonHarnessModelDefinition();

    private final JsonModel model;
    private final JsonClass root;

    public JsonHarnessModelDefinition() {
        model = new JsonModel("Harness");
        model.addBasicModel();

        final JsonClass asString = model.getJsonClass("String");
        final JsonClass asBoolean = model.getJsonClass("Boolean");
        final JsonClass asInt = model.getJsonClass("int");
        final JsonClass asDouble = model.getJsonClass("double");

        JsonClass abortLimits = model.newJsonReflect(AbortLimitsBean.class);
        abortLimits.addField("maxIterations", asInt);
        abortLimits.addField("maxDepth", asInt);
        abortLimits.addField("maxRetries", asInt);
        abortLimits.addField("maxToolCalls", asInt);

        JsonClass modelclass = model.newJsonReflect(ModelclassBean.class);
        modelclass.addField("provider", asString);
        modelclass.addField("model", asString);
        modelclass.addField("costFactor", asDouble);
        modelclass.addField("toolCapable", asBoolean);

        JsonMap modelclasses = model.newRawJsonMapIndividually(
                (new JsonInstance<ModelclassBean>()).getClass(), null, modelclass);

        JsonClass calculator = model.newJsonReflect(CalculatorBean.class);
        calculator.addField("type", asString);
        calculator.addField("impl", asString);
        calculator.addField("modelclass", asString);
        calculator.addField("tool", asString);
        calculator.addField("strategy", asString);
        calculator.addField("children", calculator, LIST);
        JsonMap onMap = model.newRawJsonMapIndividually(
                (new JsonInstance<CalculatorBean>()).getClass(), null, calculator);
        calculator.addField("on", onMap);
        calculator.addField("catch", onMap, "getCatchMapping", "setCatchMapping");

        JsonClass slot = model.newJsonReflect(SlotBean.class);
        slot.addField("id", asString);
        slot.addField("mutation", asString);
        slot.addField("calculator", calculator);

        JsonClass quest = model.newJsonReflect(QuestBean.class);
        quest.addField("kind", asString);
        quest.addField("evolvable", asBoolean);
        quest.addField("slots", slot, LIST);

        JsonMap promptBudgets = model.newRawJsonMapIndividually(
                (new JsonInstance<Integer>()).getClass(), null, asInt);

        root = model.newJsonReflect(HarnessModelBean.class);
        root.addField("name", asString);
        root.addField("version", asString);
        root.addField("taskTypes", asString, LIST);
        root.addField("promptBudgets", promptBudgets);
        root.addField("abortLimits", abortLimits);
        root.addField("modelclasses", modelclasses);
        root.addField("quests", quest, LIST);

        model.setRootNodeCast(root.getcName());
    }

    @Override
    public JsonModel getModel() {
        return model;
    }

    @Override
    public JsonCastingLevel getCastingLevel() {
        return JsonCastingLevel.NEVER;
    }
}
