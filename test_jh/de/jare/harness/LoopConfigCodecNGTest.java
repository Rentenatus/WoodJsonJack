/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.debug.JsonDebugLevel;
import de.jare.harness.io.HarnessModelCodec;
import de.jare.harness.model.HarnessModel;
import de.jare.harness.model.Loop;
import de.jare.harness.model.LoopItem;
import de.jare.harness.model.Trigger;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.lang.JsonNode;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for the free loop configuration (CONCEPT_Harness.md §5 Loop-Regel, §15.6;
 * docs/working/DESIGN_Loops.md §2): codec round-trip of {@code loops[]}, the legacy
 * default "main" loop when the JSON has no loops, and fail-fast validation of unknown
 * kinds, unknown loop refs, duplicate ids and unknown triggers.
 *
 * @author Janusch Rentenatus
 */
public class LoopConfigCodecNGTest {

    private static HarnessModel sampleModelWithLoops() {
        HarnessModel model = new HarnessModel().name("loop-test");
        Loop main = new Loop(Loop.MAIN);
        main.addItem(new LoopItem("abort", Kind.ABORT).trigger(Trigger.START));
        main.addItem(new LoopItem("split", Kind.SPLIT));
        main.addItem(new LoopItem("work", Kind.WORK));
        main.addItem(new LoopItem("sub", Kind.WORK).loop("resolve"));
        main.addItem(new LoopItem("err", Kind.ABORT).trigger(Trigger.CATCH));
        main.addItem(new LoopItem("close", Kind.CLOSE).trigger(Trigger.CLOSE));
        model.addLoop(main);
        Loop resolve = new Loop("resolve");
        resolve.addItem(new LoopItem("rwork", Kind.WORK));
        resolve.addItem(new LoopItem("reval", Kind.EVALUATE));
        model.addLoop(resolve);
        return model;
    }

    @Test
    public void testLoopRoundTripThroughFile() throws Exception {
        File tmp = Files.createTempDirectory("harness-loops").resolve("harness-model.json").toFile();
        HarnessModel model = sampleModelWithLoops();
        HarnessModelCodec.save(model, tmp);
        HarnessModel loaded = HarnessModelCodec.load(tmp);

        Assert.assertEquals(loaded.getLoops().keySet().stream().toList(), List.of("main", "resolve"));
        Loop main = loaded.getLoop("main");
        Assert.assertNotNull(main);
        Assert.assertEquals(main.getItems().size(), 6);

        LoopItem abort = main.getItems().get(0);
        Assert.assertEquals(abort.getId(), "abort");
        Assert.assertEquals(abort.getKind(), Kind.ABORT);
        Assert.assertEquals(abort.getTrigger(), Trigger.START);
        Assert.assertFalse(abort.isSubLoop());

        LoopItem split = main.getItems().get(1);
        Assert.assertEquals(split.getTrigger(), Trigger.EXECUTE, "trigger defaults to EXECUTE");

        LoopItem sub = main.getItems().get(3);
        Assert.assertTrue(sub.isSubLoop());
        Assert.assertEquals(sub.getLoop(), "resolve");
        Assert.assertEquals(sub.getKind(), Kind.WORK);

        Assert.assertEquals(main.itemsFor(Trigger.START).size(), 1);
        Assert.assertEquals(main.itemsFor(Trigger.EXECUTE).size(), 3);
        Assert.assertEquals(main.itemsFor(Trigger.CATCH).size(), 1);
        Assert.assertEquals(main.itemsFor(Trigger.CLOSE).size(), 1);
        Assert.assertEquals(loaded.getLoop("resolve").getItems().size(), 2);
    }

    @Test
    public void testDefaultMainLoopWhenLoopsAbsent() throws Exception {
        JsonNode root = JsonParserService.parse("{\"name\":\"x\",\"quests\":[]}", JsonDebugLevel.SIMPLE).getRoot();
        HarnessModel model = HarnessModelCodec.fromJson(root);
        Loop main = model.getLoop(Loop.MAIN);
        Assert.assertNotNull(main, "missing loops[] must fall back to the legacy default main loop");
        Assert.assertEquals(main.itemsFor(Trigger.START).get(0).getKind(), Kind.ABORT);
        Assert.assertEquals(main.itemsFor(Trigger.EXECUTE).stream().map(i -> i.getKind()).toList(),
                List.of(Kind.SPLIT, Kind.COMPLETE, Kind.WORK, Kind.EVALUATE,
                        Kind.RAG_STORE, Kind.CHECK, Kind.SUMMARIZE));
        Assert.assertEquals(main.itemsFor(Trigger.CLOSE).get(0).getKind(), Kind.CLOSE);
    }

    @Test
    public void testUnknownKindFails() {
        Assert.assertThrows(IllegalArgumentException.class, () -> parse(
                "{\"loops\":[{\"id\":\"main\",\"items\":[{\"id\":\"x\",\"kind\":\"NOPE\"}]}]}"));
    }

    @Test
    public void testUnknownLoopRefFails() {
        Assert.assertThrows(IllegalArgumentException.class, () -> parse(
                "{\"loops\":[{\"id\":\"main\",\"items\":[{\"id\":\"x\",\"kind\":\"WORK\",\"loop\":\"ghost\"}]}]}"));
    }

    @Test
    public void testDuplicateLoopIdFails() {
        Assert.assertThrows(IllegalArgumentException.class, () -> parse(
                "{\"loops\":[{\"id\":\"main\",\"items\":[]},{\"id\":\"main\",\"items\":[]}]}"));
    }

    @Test
    public void testDuplicateItemIdFails() {
        Assert.assertThrows(IllegalArgumentException.class, () -> parse(
                "{\"loops\":[{\"id\":\"main\",\"items\":[{\"id\":\"x\",\"kind\":\"WORK\"},{\"id\":\"x\",\"kind\":\"WORK\"}]}]}"));
    }

    @Test
    public void testUnknownTriggerFails() {
        Assert.assertThrows(IllegalArgumentException.class, () -> parse(
                "{\"loops\":[{\"id\":\"main\",\"items\":[{\"id\":\"x\",\"kind\":\"WORK\",\"trigger\":\"WHENEVER\"}]}]}"));
    }

    @Test
    public void testModelCopyCarriesLoops() {
        HarnessModel copy = sampleModelWithLoops().copy();
        Assert.assertEquals(copy.getLoops().keySet().stream().toList(), List.of("main", "resolve"));
        LoopItem sub = copy.getLoop("main").getItems().get(3);
        Assert.assertEquals(sub.getLoop(), "resolve");
        Assert.assertNotSame(sub, sampleModelWithLoops().getLoop("main").getItems().get(3));
    }

    private static HarnessModel parse(String json) throws Exception {
        JsonNode root = JsonParserService.parse(json, JsonDebugLevel.SIMPLE).getRoot();
        return HarnessModelCodec.fromJson(root);
    }
}
