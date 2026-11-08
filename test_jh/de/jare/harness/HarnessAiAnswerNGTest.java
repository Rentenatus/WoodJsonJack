/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.impl.AiCalculator;
import de.jare.harness.impl.ModelProvider;
import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Modelclass;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for the strict JSON answer contract of AI calculators (CONCEPT_Harness.md §2,
 * §13): a model answer must be a JSON object with decision/status/score - anything else
 * raises SCHEMA_VIOLATION and the engine's catch-mapping falls back deterministically.
 *
 * @author Janusch Rentenatus
 */
public class HarnessAiAnswerNGTest {

    private static CalcContext ctx(HarnessEngine engine, HarnessInstance instance, HarnessModel model) {
        TaskNode task = new TaskNode(1, "RESEARCH", "ai answer test");
        return new CalcContext(engine, instance, model, task, Kind.WORK, "work.ai",
                CalculatorNode.atomic(CalculatorNode.IMPL_AI).modelclass("edit"),
                engine.getLimits(), List.of("AUFTRAG: test"));
    }

    private static HarnessEngine engineWith(HarnessModel model) {
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        return new HarnessEngine(model, instance, null);
    }

    private static ModelProvider providerReturning(String answer) {
        return new ModelProvider() {
            @Override public Modelclass modelclass() { return null; }
            @Override public boolean isAvailable() { return true; }
            @Override public String complete(List<String> segments) { return answer; }
        };
    }

    @Test
    public void testStrictJsonAnswerIsParsedIntoCalcResult() {
        HarnessModel model = new HarnessModel();
        Modelclass mc = new Modelclass("hosted", "mistral-small", "test", 0, 3.0, false);
        AiCalculator calc = new AiCalculator(Kind.WORK, mc, providerReturning(
                "{\"decision\":\"worked\",\"status\":\"DONE\",\"score\":88,\"message\":\"ok\",\"payload\":{\"artifact\":\"a1\"}}"));
        CalcResult r = calc.calculate(ctx(engineWith(model), null, model));
        Assert.assertEquals(r.getDecision(), "worked");
        Assert.assertEquals(r.getStatus(), Status.DONE);
        Assert.assertEquals(r.getScore(), Integer.valueOf(88));
        Assert.assertEquals(r.getPayload().get("artifact"), "a1");
    }

    @Test
    public void testNonJsonAnswerRaisesSchemaViolation() {
        HarnessModel model = new HarnessModel();
        Modelclass mc = new Modelclass("hosted", "mistral-small", "test", 0, 3.0, false);
        AiCalculator calc = new AiCalculator(Kind.EVALUATE, mc, providerReturning("sure, here you go!"));
        try {
            calc.calculate(ctx(engineWith(model), null, model));
            Assert.fail("expected SCHEMA_VIOLATION");
        } catch (HarnessException e) {
            Assert.assertEquals(e.getType(), HarnessException.Type.SCHEMA_VIOLATION);
        }
    }

    @Test
    public void testMalformedJsonRaisesSchemaViolation() {
        HarnessModel model = new HarnessModel();
        Modelclass mc = new Modelclass("hosted", "mistral-small", "test", 0, 3.0, false);
        AiCalculator calc = new AiCalculator(Kind.EVALUATE, mc, providerReturning("{\"decision\": broken"));
        try {
            calc.calculate(ctx(engineWith(model), null, model));
            Assert.fail("expected SCHEMA_VIOLATION");
        } catch (HarnessException e) {
            Assert.assertEquals(e.getType(), HarnessException.Type.SCHEMA_VIOLATION);
        }
    }

    @Test
    public void testUnavailableProviderRaisesModelUnavailable() {
        HarnessModel model = new HarnessModel();
        Modelclass mc = new Modelclass("ollama", "qwen2.5:3b", "test", 0, 1.0, true);
        mc.markUnavailable();
        AiCalculator calc = new AiCalculator(Kind.WORK, mc, null);
        try {
            calc.calculate(ctx(engineWith(model), null, model));
            Assert.fail("expected MODEL_UNAVAILABLE");
        } catch (HarnessException e) {
            Assert.assertEquals(e.getType(), HarnessException.Type.MODEL_UNAVAILABLE);
        }
    }
}
