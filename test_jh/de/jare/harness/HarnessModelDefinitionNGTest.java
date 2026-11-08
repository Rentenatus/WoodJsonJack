/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.io.HarnessModelCodec;
import de.jare.harness.io.def.HarnessModelBeanConverter;
import de.jare.harness.io.def.HarnessModelBeans.HarnessModelBean;
import de.jare.harness.io.def.JsonHarnessModelDefinition;
import de.jare.harness.model.HarnessModel;
import de.jare.jsoncasted.io.JsonParser;
import de.jare.jsoncasted.io.convertservice.WoodResolution;
import de.jare.jsoncasted.item.builder.JsonBuilder;
import java.io.File;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Test for the JsonItemDefinition binding of harness-model.json (CONCEPT_Harness.md §5):
 * the bundled asset parses through the model definition (WJJ-tree-editable), builds into
 * beans, and converts to the same runtime model as the plain codec.
 *
 * @author Janusch Rentenatus
 */
public class HarnessModelDefinitionNGTest {

    @Test
    public void testBundledAssetParsesThroughDefinitionAndConverts() throws Exception {
        File asset = new File("assets/harness/harness-model.json");
        JsonHarnessModelDefinition def = JsonHarnessModelDefinition.INSTANCE;

        // the definition itself must be valid
        Assert.assertFalse(def.validateDefinition().hasErrors(), "definition must validate");

        WoodResolution reso = JsonParser.parse(asset, def, def.getRootClass());
        Assert.assertFalse(reso.hasExceptions(), "asset must parse without exceptions: "
                + reso.getUnmodifiableExceptions());
        HarnessModelBean bean = (HarnessModelBean) JsonBuilder.buildInstance(
                def.getModel(), true, reso.getAnswer());

        HarnessModel viaDefinition = HarnessModelBeanConverter.toModel(bean);
        HarnessModel viaCodec = HarnessModelCodec.load(asset);

        Assert.assertEquals(viaDefinition.getName(), viaCodec.getName());
        Assert.assertEquals(viaDefinition.getVersion(), viaCodec.getVersion());
        Assert.assertEquals(viaDefinition.getModelclasses().size(), viaCodec.getModelclasses().size());
        Assert.assertEquals(viaDefinition.questFor(Kind.WORK).getSlots().size(), 4);
        Assert.assertEquals(viaDefinition.questFor(Kind.WORK).getSlots().get(1).getMutation(), "Mutation-Resolve-01");
        Assert.assertTrue(viaDefinition.questFor(Kind.WORK).getSlots().get(1).getCalculator().isPuzzle());
        Assert.assertEquals(viaDefinition.questFor(Kind.WORK).getSlots().get(1).getCalculator()
                .getChildren().size(), 2);
        Assert.assertEquals(viaDefinition.getMaxIterations(), viaCodec.getMaxIterations());
        Assert.assertEquals(viaDefinition.getPromptBudgets(), viaCodec.getPromptBudgets());
    }
}
