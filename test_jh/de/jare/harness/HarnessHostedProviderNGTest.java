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
        Modelclass mc = new Modelclass("hosted", "mistral-small", "test", 0, 3.0, true);
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
        Modelclass mc = new Modelclass("hosted", "mistral-small", "test", 0, 3.0, true);
        HostedProvider p = new HostedProvider("dummy-key", mc);
        Assert.assertTrue(p.isAvailable());
        Assert.assertEquals(p.modelclass().getModel(), "mistral-small");
    }

    @Test
    public void testCompleteUsesTypedChatRequestWithBearerHeader() throws Exception {
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        final String[] auth = new String[1];
        final String[] body = new String[1];
        server.createContext("/v1/chat/completions", exchange -> {
            auth[0] = exchange.getRequestHeaders().getFirst("Authorization");
            body[0] = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String resp = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"decision\\\":\\\"worked\\\"}\"}}]}";
            byte[] out = resp.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            try (java.io.OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions";
            Modelclass mc = new Modelclass("hosted", "mistral-small", "test", 0, 3.0, true);
            HostedProvider p = new HostedProvider(base, "secret-key", mc);
            String answer = p.complete(java.util.List.of("SYSTEM: orchestrator", "AUFTRAG: do it"));

            Assert.assertEquals(auth[0], "Bearer secret-key", "bearer header sent");
            Assert.assertTrue(body[0].contains("\"model\": \"mistral-small\""), "typed request serialized");
            Assert.assertTrue(body[0].contains("json_object"), "response_format pin sent");
            Assert.assertTrue(body[0].contains("\"role\": \"system\""), "system segment mapped");
            Assert.assertEquals(answer, "{\"decision\":\"worked\"}", "choices[0].message.content extracted");
        } finally {
            server.stop(0);
        }
    }
}
