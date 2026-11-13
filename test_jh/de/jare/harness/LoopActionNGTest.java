/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.main.actions.Abort;
import de.jare.harness.main.actions.LoopActions;
import de.jare.harness.main.actions.LoopBreak;
import de.jare.harness.main.actions.LoopContinue;
import de.jare.harness.main.actions.LoopRestart;
import de.jare.harness.main.actions.LoopSubLoop;
import de.jare.harness.main.actions.LoopThrowError;
import de.jare.harness.main.actions.None;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for the LoopAction flow channel and ErrorSignal (CONCEPT_Harness.md §15.6,
 * docs/working/DESIGN_Loops.md §4/§5): CalcResult defaults, explicit loop actions,
 * legacy derivation from decision/Control, and the DOMAIN/TECHNICAL mapping of
 * HarnessException types.
 *
 * @author Janusch Rentenatus
 */
public class LoopActionNGTest {

    @Test
    public void testDefaultLoopActionIsContinue() {
        CalcResult r = CalcResult.of(Kind.WORK, "");
        Assert.assertTrue(r.getLoopAction() instanceof LoopContinue);
        Assert.assertEquals(r.getLoopAction().flow(), LoopAction.Flow.CONTINUE);
    }

    @Test
    public void testExplicitLoopActionWins() {
        CalcResult r = CalcResult.of(Kind.WORK, "").loopAction(new LoopSubLoop("resolve"));
        Assert.assertTrue(r.getLoopAction() instanceof LoopSubLoop);
        Assert.assertEquals(((LoopSubLoop) r.getLoopAction()).loopId(), "resolve");
        Assert.assertEquals(r.getLoopAction().flow(), LoopAction.Flow.SUBLOOP);
    }

    @Test
    public void testLegacyDecisionDerivation() {
        Assert.assertTrue(CalcResult.of(Kind.CHECK, "retry").getLoopAction() instanceof LoopRestart);
        Assert.assertTrue(CalcResult.of(Kind.CHECK, "incomplete").getLoopAction() instanceof LoopRestart);
        Assert.assertTrue(CalcResult.of(Kind.ABORT, "abort").getLoopAction() instanceof LoopThrowError);
        Assert.assertTrue(CalcResult.of(Kind.WORK, "split").getLoopAction() instanceof LoopContinue);
        Assert.assertTrue(CalcResult.of(Kind.WORK, "whatever").getLoopAction() instanceof LoopContinue);
    }

    @Test
    public void testLegacyActionControlDerivation() {
        CalcResult abort = CalcResult.of(Kind.ABORT, "").action(new Abort("done"));
        Assert.assertTrue(abort.getLoopAction() instanceof LoopBreak,
                "legacy STOP control maps to Break (leave loop, CLOSE fires)");
        CalcResult plain = CalcResult.of(Kind.WORK, "").action(new None());
        Assert.assertTrue(plain.getLoopAction() instanceof LoopContinue);
    }

    @Test
    public void testFromControlMapping() {
        Assert.assertTrue(LoopActions.fromControl(HarnessAction.Control.CONTINUE) instanceof LoopContinue);
        Assert.assertTrue(LoopActions.fromControl(HarnessAction.Control.LOOP) instanceof LoopRestart);
        Assert.assertTrue(LoopActions.fromControl(HarnessAction.Control.STOP) instanceof LoopBreak);
        Assert.assertTrue(LoopActions.fromControl(null) instanceof LoopContinue);
    }

    @Test
    public void testErrorSignalFactories() {
        ErrorSignal d = ErrorSignal.domain("no count", "scoring.countQuests");
        Assert.assertTrue(d.isDomain());
        Assert.assertEquals(d.severity(), ErrorSignal.Severity.DOMAIN);
        Assert.assertEquals(d.source(), "scoring.countQuests");

        ErrorSignal t = ErrorSignal.fromThrowable(new NullPointerException("boom"), "rag.store");
        Assert.assertEquals(t.severity(), ErrorSignal.Severity.TECHNICAL);
        Assert.assertTrue(t.reason().contains("boom"));
    }

    @Test
    public void testHarnessExceptionTypeMapping() {
        Assert.assertEquals(ErrorSignal.fromHarnessException(
                new HarnessException(HarnessException.Type.SCHEMA_VIOLATION, "bad json"), "ai").severity(),
                ErrorSignal.Severity.DOMAIN);
        Assert.assertEquals(ErrorSignal.fromHarnessException(
                new HarnessException(HarnessException.Type.LIMIT, "over budget"), "ai").severity(),
                ErrorSignal.Severity.DOMAIN);
        Assert.assertEquals(ErrorSignal.fromHarnessException(
                new HarnessException(HarnessException.Type.MODEL_UNAVAILABLE, "ollama down"), "ai").severity(),
                ErrorSignal.Severity.TECHNICAL);
        Assert.assertEquals(ErrorSignal.fromHarnessException(
                new HarnessException(HarnessException.Type.TIMEOUT, "slow"), "ai").severity(),
                ErrorSignal.Severity.TECHNICAL);
        Assert.assertEquals(ErrorSignal.fromHarnessException(
                new HarnessException(HarnessException.Type.TOOL_ERROR, "fs fail"), "tool").severity(),
                ErrorSignal.Severity.TECHNICAL);
    }

    @Test
    public void testThrowErrorCarriesSignal() {
        ErrorSignal sig = ErrorSignal.domain("nosplit", "askai.decompose");
        LoopThrowError throwError = new LoopThrowError(sig);
        Assert.assertEquals(throwError.flow(), LoopAction.Flow.THROW);
        Assert.assertSame(throwError.signal(), sig);
        Assert.assertTrue(throwError.signal().isDomain());
    }
}
