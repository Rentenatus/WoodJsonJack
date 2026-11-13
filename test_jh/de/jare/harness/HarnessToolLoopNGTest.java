/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.impl.AiCalculator;
import de.jare.harness.impl.DefaultCalculatorFactory;
import de.jare.harness.impl.ModelProvider;
import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Modelclass;
import de.jare.harness.tools.HarnessTool;
import de.jare.harness.tools.HarnessTools;
import de.jare.harness.tools.PermissionPolicy;
import de.jare.harness.tools.ToolResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for the bounded ReAct tool-loop of AI calculators (CONCEPT_Harness.md §13):
 * the model answers with a tool call, the driver executes it with the permission check
 * (counted against maxToolCalls), feeds the result back, and finally returns the model's
 * answer. Tool errors are fed back too; exceeding maxToolCalls raises LIMIT.
 *
 * @author Janusch Rentenatus
 */
public class HarnessToolLoopNGTest {

    private static HarnessEngine engineWithTools(HarnessInstance instance, HarnessModel model,
                                                  HarnessTools tools, int maxToolCalls) {
        HarnessModel m = model.abortLimits(50, 5, 3, maxToolCalls);
        HarnessEngine engine = new HarnessEngine(m, instance, new DefaultCalculatorFactory(m));
        // inject tools into the engine's ctx
        engine.getTools().getPolicy(); // ensure built
        return engine;
    }

    private static CalcContext ctxFor(HarnessEngine engine, HarnessInstance instance, HarnessModel model,
                                      HarnessTools tools, Kind kind) {
        TaskData task = TaskData.create(1, "CODE_EDIT", "tool loop test");
        CalcContext ctx = new CalcContext(engine, instance, model, task, kind, "work.ai",
                CalculatorNode.atomic(CalculatorNode.IMPL_AI).modelclass("edit"),
                engine.getLimits(), List.of("AUFTRAG: read the file and answer"));
        ctx.setTools(tools);
        return ctx;
    }

    @Test
    public void testToolCallThenAnswer() {
        HarnessModel model = new HarnessModel();
        Modelclass mc = new Modelclass("hosted", "mistral-small", "test", 0, 3.0, true); // toolCapable
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger providerCalls = new AtomicInteger();
        ModelProvider provider = new ModelProvider() {
            @Override public Modelclass modelclass() { return mc; }
            @Override public boolean isAvailable() { return true; }
            @Override public String complete(List<String> segments) {
                if (providerCalls.getAndIncrement() == 0) {
                    return "{\"tool\":\"test.read\",\"args\":{\"path\":\"x\"}}";
                }
                return "{\"decision\":\"answered\",\"status\":\"DONE\",\"score\":90,\"message\":\"file says 42\"}";
            }
        };
        HarnessTools tools = new HarnessTools(new PermissionPolicy());
        tools.register(new HarnessTool() {
            @Override public String name() { return "test.read"; }
            @Override public String scope() { return "fs"; }
            @Override public RiskLevel risk() { return RiskLevel.READ; }
            @Override public ToolResult run(Map<String, String> args) {
                calls.incrementAndGet();
                return ToolResult.ok("42");
            }
        });
        HarnessInstance instance = new HarnessInstance("m", "1");
        HarnessEngine engine = new HarnessEngine(model, instance, new DefaultCalculatorFactory(model));
        CalcContext ctx = ctxFor(engine, instance, model, tools, Kind.WORK);

        AiCalculator calc = new AiCalculator(Kind.WORK, mc, provider);
        CalcResult r = calc.calculate(ctx);

        Assert.assertEquals(calls.get(), 1, "tool executed once");
        Assert.assertEquals(providerCalls.get(), 2, "provider called twice (tool call + answer)");
        Assert.assertEquals(r.getDecision(), "answered");
        Assert.assertEquals(r.getScore(), Integer.valueOf(90));
        Assert.assertEquals(engine.getLimits().getCounter(Limits.TOOL_CALLS), 1, "tool call counted");
    }

    @Test
    public void testToolErrorIsFedBackNotFatal() {
        HarnessModel model = new HarnessModel();
        Modelclass mc = new Modelclass("hosted", "mistral-small", "test", 0, 3.0, true);
        AtomicInteger providerCalls = new AtomicInteger();
        ModelProvider provider = new ModelProvider() {
            @Override public Modelclass modelclass() { return mc; }
            @Override public boolean isAvailable() { return true; }
            @Override public String complete(List<String> segments) {
                if (providerCalls.getAndIncrement() == 0) {
                    return "{\"tool\":\"test.denied\",\"args\":{}}";
                }
                // the second prompt must contain the tool error feedback
                boolean sawError = segments.stream().anyMatch(s -> s.contains("tool-error"));
                return "{\"decision\":\"recovered\",\"status\":\"DONE\",\"message\":\"" + sawError + "\"}";
            }
        };
        PermissionPolicy policy = new PermissionPolicy()
                .set(HarnessTool.WriteCategory.DISK_IN, PermissionPolicy.Setting.NEIN);
        HarnessTools tools = new HarnessTools(policy);
        tools.register(new HarnessTool() {
            @Override public String name() { return "test.denied"; }
            @Override public String scope() { return "fs"; }
            @Override public RiskLevel risk() { return RiskLevel.WRITE; }
            @Override public ToolResult run(Map<String, String> args) { return ToolResult.ok("x"); }
        });
        HarnessInstance instance = new HarnessInstance("m", "1");
        HarnessEngine engine = new HarnessEngine(model, instance, new DefaultCalculatorFactory(model));
        CalcContext ctx = ctxFor(engine, instance, model, tools, Kind.WORK);

        CalcResult r = new AiCalculator(Kind.WORK, mc, provider).calculate(ctx);
        Assert.assertEquals(r.getDecision(), "recovered");
        Assert.assertEquals(r.getMessage(), "true", "model saw the TOOL_ERROR feedback segment");
    }

    @Test
    public void testMaxToolCallsRaisesLimit() {
        HarnessModel model = new HarnessModel().abortLimits(50, 5, 3, 1); // only 1 tool call allowed
        Modelclass mc = new Modelclass("hosted", "mistral-small", "test", 0, 3.0, true);
        ModelProvider alwaysTool = new ModelProvider() {
            @Override public Modelclass modelclass() { return mc; }
            @Override public boolean isAvailable() { return true; }
            @Override public String complete(List<String> segments) {
                return "{\"tool\":\"test.read\",\"args\":{}}";
            }
        };
        HarnessTools tools = new HarnessTools(new PermissionPolicy());
        tools.register(new HarnessTool() {
            @Override public String name() { return "test.read"; }
            @Override public String scope() { return "fs"; }
            @Override public RiskLevel risk() { return RiskLevel.READ; }
            @Override public ToolResult run(Map<String, String> args) { return ToolResult.ok("x"); }
        });
        HarnessInstance instance = new HarnessInstance("m", "1");
        HarnessEngine engine = new HarnessEngine(model, instance, new DefaultCalculatorFactory(model));
        CalcContext ctx = ctxFor(engine, instance, model, tools, Kind.WORK);

        try {
            new AiCalculator(Kind.WORK, mc, alwaysTool).calculate(ctx);
            Assert.fail("expected LIMIT");
        } catch (HarnessException e) {
            Assert.assertEquals(e.getType(), HarnessException.Type.LIMIT);
        }
    }
}
