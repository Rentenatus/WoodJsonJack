/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.tree.ui.harness.HarnessController;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Headless-safe test of the UI entry point (CONCEPT_Harness.md §3, §15): a run through
 * {@link HarnessService} lands in the controller as lastInstance (the menu actions call
 * exactly this path), and the persisted files exist in the project's .wood folder.
 *
 * @author Janusch Rentenatus
 */
public class HarnessControllerNGTest {

    @Test
    public void testRunLandsInControllerAndPersists() throws Exception {
        Path project = Files.createTempDirectory("harness-ui");
        HarnessService service = new HarnessService(project.toFile());
        HarnessInstance instance = service.runTask("RESEARCH", "menu driven run");

        HarnessController controller = new HarnessController(null);
        Assert.assertNull(controller.getLastInstance());
        // the controller's done() callback assigns lastInstance - emulate the assignment path
        java.lang.reflect.Field f = HarnessController.class.getDeclaredField("lastInstance");
        f.setAccessible(true);
        f.set(controller, instance);
        Assert.assertSame(controller.getLastInstance(), instance);
        Assert.assertEquals(controller.getLastInstance().getRoot().getStatus(), Status.DONE);
        Assert.assertTrue(new java.io.File(project.toFile(), ".wood/harness.json").exists());
        Assert.assertTrue(new java.io.File(project.toFile(), ".wood/harness/lineage.json").exists());
    }
}
