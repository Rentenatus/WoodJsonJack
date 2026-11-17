/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.impl.DefaultCalculatorFactory;
import de.jare.harness.main.actions.LoopSplit;
import de.jare.harness.main.actions.LoopThrowError;
import de.jare.harness.memory.WorkCache;
import de.jare.harness.model.CalculatorNode;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Loop;
import de.jare.harness.model.LoopItem;
import de.jare.harness.model.Modelclass;
import de.jare.harness.model.Quest;
import de.jare.harness.model.Slot;
import de.jare.harness.model.Trigger;
import de.jare.harness.skills.SkillRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for the main loop's START items GUARD and SKILL (§15.6, only in "main"): the
 * deterministic guard throws a DOMAIN signal on blank prompt / no available model, the
 * named {@code "skill.collect"} calculator collects the current SkillRegistry into the
 * WorkCache, and an engine run proves GUARD/SKILL fire once at main START while split
 * children run the sub loop (with its own ABORT START).
 *
 * @author Janusch Rentenatus
 */
public class GuardSkillNGTest {

    private static HarnessModel modelWithAllQuests() {
        HarnessModel model = new HarnessModel().name("guard-skill-test").abortLimits(50, 5, 3, 10);
        for (Kind kind : Kind.values()) {
            model.addQuest(new Quest(kind).addSlot(new Slot(kind.name().toLowerCase() + ".base")
                    .calculator(CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC))));
        }
        model.addModelclass(new Modelclass("ollama", "qwen3.8:27b", "investigation", 27, 1.0, true));
        return model;
    }

    private static CalcContext ctxFor(HarnessModel model, Kind kind, String title) {
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        TaskData root = instance.newRootTask(instance.nextTaskId(), "AskAi", title);
        instance.setRoot(root);
        HarnessEngine engine = new HarnessEngine(model, instance, new DefaultCalculatorFactory(model));
        CalculatorNode node = CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC);
        return new CalcContext(engine, instance, model, root, kind, kind.name().toLowerCase() + ".base",
                node, engine.getLimits(), List.of("AUFTRAG:inline:task\n" + title));
    }

    @Test
    public void testGuardOk() {
        HarnessModel model = modelWithAllQuests();
        CalcContext ctx = ctxFor(model, Kind.GUARD, "research topic");
        CalcResult r = de.jare.harness.impl.deterministic.DeterministicCalculators.forKind(Kind.GUARD).calculate(ctx);
        Assert.assertEquals(r.getStatus(), Status.DONE);
        Assert.assertEquals(r.getDecision(), "guards-ok");
        Assert.assertEquals(r.getPayload().get("modelsAvailable"), "1");
    }

    @Test
    public void testGuardBlankPromptThrowsDomain() {
        HarnessModel model = modelWithAllQuests();
        CalcContext ctx = ctxFor(model, Kind.GUARD, "  ");
        CalcResult r = de.jare.harness.impl.deterministic.DeterministicCalculators.forKind(Kind.GUARD).calculate(ctx);
        Assert.assertEquals(r.getStatus(), Status.FAILED);
        Assert.assertEquals(r.getDecision(), "guard-failed");
        Assert.assertTrue(r.getLoopAction() instanceof LoopThrowError);
        Assert.assertTrue(((LoopThrowError) r.getLoopAction()).signal().isDomain(),
                "guard failure is a DOMAIN signal -> CATCH of the loop fires");
    }

    @Test
    public void testGuardNoModelAvailableThrowsDomain() {
        HarnessModel model = modelWithAllQuests();
        model.getModelclasses().forEach(Modelclass::markUnavailable);
        CalcContext ctx = ctxFor(model, Kind.GUARD, "research topic");
        CalcResult r = de.jare.harness.impl.deterministic.DeterministicCalculators.forKind(Kind.GUARD).calculate(ctx);
        Assert.assertEquals(r.getStatus(), Status.FAILED);
        Assert.assertEquals(r.getDecision(), "guard-failed");
        Assert.assertTrue(r.getLoopAction() instanceof LoopThrowError);
    }

    @Test
    public void testSkillCollectGathersRegistryIntoWorkCache() throws Exception {
        HarnessModel model = modelWithAllQuests();
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        TaskData root = instance.newRootTask(instance.nextTaskId(), "AskAi", "research topic");
        instance.setRoot(root);
        HarnessEngine engine = new HarnessEngine(model, instance, new DefaultCalculatorFactory(model));
        CalcContext ctx = new CalcContext(engine, instance, model, root, Kind.SKILL, "skill.base",
                CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC),
                engine.getLimits(), List.of("AUFTRAG:inline:task\nresearch topic"));

        // a skill folder with one skill and two description files
        Path skillDir = Files.createTempDirectory("skills");
        Path skill = Files.createDirectory(skillDir.resolve("my-skill"));
        Files.writeString(skill.resolve("general.md"), "does general things");
        Files.writeString(skill.resolve("edit.md"), "edits files");
        SkillRegistry registry = new SkillRegistry();
        registry.loadFrom(skillDir, "global");
        engine.setSkillRegistry(registry);

        WorkCache cache = new WorkCache();
        ctx.setWorkCache(cache);

        // via the named calculator, exactly as the SKILL slot in harness-model.json references it
        DefaultCalculatorFactory factory = new DefaultCalculatorFactory(model);
        CalculatorNode node = CalculatorNode.atomic(CalculatorNode.IMPL_DETERMINISTIC).calculator("skill.collect");
        HarnessCalculator calc = factory.createAtomic(node, Kind.SKILL);
        Assert.assertTrue(calc instanceof de.jare.harness.main.calculator.SkillCollectCalculator);
        CalcResult r = calc.calculate(ctx);

        Assert.assertEquals(r.getStatus(), Status.DONE);
        Assert.assertEquals(r.getDecision(), "collected");
        Assert.assertEquals(r.getPayload().get("count"), "1");
        Assert.assertEquals(r.getPayload().get("ids"), "my-skill");
        Assert.assertEquals(cache.get("skills.count"), "1");
        Assert.assertEquals(cache.get("skills.ids"), "my-skill");
        Assert.assertEquals(cache.get("skills.descriptions"), "2");
    }

    /** Scripted factory recording the call order per kind. */
    private static final class ScriptedFactory implements CalculatorFactory {
        final Map<Kind, Queue<HarnessCalculator>> scripts = new java.util.HashMap<>();
        final List<Kind> calls = new ArrayList<>();

        @Override
        public HarnessCalculator createAtomic(CalculatorNode node, Kind kind) {
            calls.add(kind);
            Queue<HarnessCalculator> q = scripts.get(kind);
            if (q != null && !q.isEmpty()) {
                return q.poll();
            }
            return new HarnessCalculator() {
                @Override public Kind kind() { return kind; }
                @Override public CalcResult calculate(CalcContext c) {
                    if (kind == Kind.SPLIT && calls.stream().filter(k -> k == Kind.SPLIT).count() == 1) {
                        return CalcResult.of(Kind.SPLIT, "split")
                                .loopAction(new LoopSplit(List.of("rechne 2*4", "rechne 7*8")));
                    }
                    if (kind == Kind.CLOSE) {
                        c.task().close("closed");
                    }
                    return CalcResult.of(kind, "done").status(Status.DONE);
                }
            };
        }

        long count(Kind kind) {
            return calls.stream().filter(k -> k == kind).count();
        }
    }

    @Test
    public void testGuardAndSkillStartOnlyInMainSubloopKeepsAbort() {
        HarnessModel model = modelWithAllQuests();
        // main: GUARD + SKILL at START (as in harness-model.json), sub keeps ABORT at START
        Loop main = new Loop(Loop.MAIN);
        main.addItem(new LoopItem("guard", Kind.GUARD).trigger(Trigger.START));
        main.addItem(new LoopItem("skill", Kind.SKILL).trigger(Trigger.START));
        main.addItem(new LoopItem("split", Kind.SPLIT).trigger(Trigger.EXECUTE));
        main.addItem(new LoopItem("close", Kind.CLOSE).trigger(Trigger.CLOSE));
        model.addLoop(main);
        Loop sub = new Loop(Loop.SUB);
        sub.addItem(new LoopItem("abort", Kind.ABORT).trigger(Trigger.START));
        sub.addItem(new LoopItem("work", Kind.WORK).trigger(Trigger.EXECUTE));
        sub.addItem(new LoopItem("close", Kind.CLOSE).trigger(Trigger.CLOSE));
        model.addLoop(sub);

        ScriptedFactory factory = new ScriptedFactory();
        HarnessInstance instance = new HarnessInstance(model.getName(), model.getVersion());
        TaskData root = instance.newRootTask(instance.nextTaskId(), "AskAi", "wie viel ist 2*4 und 7*8?");
        instance.setRoot(root);
        HarnessEngine engine = new HarnessEngine(model, instance, factory);
        engine.setWorkCache(new WorkCache());
        engine.run();

        Assert.assertEquals(factory.count(Kind.GUARD), 1, "GUARD fires once at main START");
        Assert.assertEquals(factory.count(Kind.SKILL), 1, "SKILL fires once at main START");
        Assert.assertEquals(factory.calls.get(0), Kind.GUARD);
        Assert.assertEquals(factory.calls.get(1), Kind.SKILL);
        // the 2 split children run the sub loop, which keeps the ABORT START
        Assert.assertEquals(factory.count(Kind.ABORT), 2, "sub loop keeps its ABORT START per child");
        Assert.assertEquals(factory.count(Kind.WORK), 2, "WORK runs once per subloop child");
        Assert.assertEquals(root.getChildren().size(), 2);
        Assert.assertEquals(root.getStatus(), Status.DONE);
    }
}
