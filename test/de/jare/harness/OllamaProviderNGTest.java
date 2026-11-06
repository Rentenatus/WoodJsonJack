/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import com.sun.net.httpserver.HttpServer;
import de.jare.harness.impl.ollama.OllamaProvider;
import de.jare.harness.model.Modelclass;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests that {@link OllamaProvider} runs its HTTP round trip through jsonCasted's
 * PostJsonClient (postRaw for /api/generate, getRaw for the /api/tags probe) against a
 * local stub server - no real ollama needed.
 *
 * @author Janusch Rentenatus
 */
public class OllamaProviderNGTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeClass
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tags", exchange -> {
            byte[] body = "{\"models\":[{\"name\":\"qwen2.5:3b\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.createContext("/api/generate", exchange -> {
            byte[] req = exchange.getRequestBody().readAllBytes();
            String body = new String(req, StandardCharsets.UTF_8);
            Assert.assertTrue(body.contains("\"format\":\"json\""), "json-strict requested");
            String resp = "{\"model\":\"qwen2.5:3b\",\"response\":\"{\\\"decision\\\":\\\"worked\\\",\\\"score\\\":91}\",\"done\":true}";
            byte[] out = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterClass
    public void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void testProbeAndCompleteViaPostJsonClient() throws Exception {
        Modelclass mc = new Modelclass("ollama", "qwen2.5:3b", 1.0, true);
        OllamaProvider provider = new OllamaProvider(baseUrl, mc);

        Assert.assertTrue(provider.isAvailable(), "GET /api/tags probe must succeed");
        Assert.assertTrue(mc.isAvailable());

        String answer = provider.complete(List.of("SYSTEM: orchestrator", "AUFTRAG: do it"));
        Assert.assertEquals(answer, "{\"decision\":\"worked\",\"score\":91}",
                "response field extracted from the generate answer");
    }

    @Test
    public void testClosedPortMarksUnavailable() {
        Modelclass mc = new Modelclass("ollama", "qwen2.5:3b", 1.0, true);
        OllamaProvider provider = new OllamaProvider("http://127.0.0.1:1", mc);
        Assert.assertFalse(provider.isAvailable());
        Assert.assertFalse(mc.isAvailable(), "outage marked on the modelclass");
        Assert.assertTrue(mc.getLastOutageTs() > 0);
    }
}
