/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.impl.hosted.HostedProvider;
import de.jare.harness.model.Modelclass;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for the hosted provider seam (CONCEPT_Harness.md §2): without an API key the
 * provider is unavailable and marks the modelclass; with a key it is available. The
 * HTTP path itself is not exercised here (no network in tests).
 *
 * @author Janusch Rentenatus
 */
public class HarnessHostedProviderNGTest {

    @Test
    public void testNoApiKeyMeansUnavailableAndMarked() {
        Modelclass mc = new Modelclass("hosted", "mistral-small", 3.0, true);
        HostedProvider p = new HostedProvider("", mc);
        Assert.assertFalse(p.isAvailable());
        try {
            p.complete(List.of("AUFTRAG: hi"));
            Assert.fail("expected IOException");
        } catch (Exception e) {
            Assert.assertFalse(mc.isAvailable(), "outage must be marked on the modelclass");
        }
    }

    @Test
    public void testApiKeyMeansAvailable() {
        Modelclass mc = new Modelclass("hosted", "mistral-small", 3.0, true);
        HostedProvider p = new HostedProvider("dummy-key", mc);
        Assert.assertTrue(p.isAvailable());
        Assert.assertEquals(p.modelclass().getModel(), "mistral-small");
    }
}
