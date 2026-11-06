/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.impl.ollama;

import de.jare.harness.impl.ModelProvider;
import de.jare.harness.model.Modelclass;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Local ollama provider (CONCEPT_Harness.md §2): defaults to
 * {@code http://localhost:11434}. One stateless call = one POST to {@code /api/generate}
 * with the assembled segment list as prompt and JSON-strict format. Local models are
 * served sequentially - no concurrency inside ollama.
 *
 * @author Janusch Rentenatus
 */
public class OllamaProvider implements ModelProvider {

    public static final String DEFAULT_BASE_URL = "http://localhost:11434";

    private final String baseUrl;
    private final Modelclass modelclass;
    private volatile Boolean availableCache;

    public OllamaProvider(Modelclass modelclass) {
        this(DEFAULT_BASE_URL, modelclass);
    }

    public OllamaProvider(String baseUrl, Modelclass modelclass) {
        this.baseUrl = baseUrl;
        this.modelclass = modelclass;
    }

    @Override
    public Modelclass modelclass() {
        return modelclass;
    }

    @Override
    public boolean isAvailable() {
        if (availableCache != null) {
            return availableCache;
        }
        boolean ok = probe();
        availableCache = ok;
        if (!ok) {
            modelclass.markUnavailable();
        }
        return ok;
    }

    /** Cheap TCP probe of the ollama port - no HTTP round trip needed for the health check. */
    private boolean probe() {
        try {
            URI uri = URI.create(baseUrl);
            String host = uri.getHost() == null ? "localhost" : uri.getHost();
            int port = uri.getPort() < 0 ? 11434 : uri.getPort();
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), 500);
                return true;
            }
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String complete(List<String> segments) throws Exception {
        String prompt = String.join("\n\n", segments);
        String body = "{\"model\":\"" + modelclass.getModel() + "\",\"prompt\":"
                + jsonString(prompt) + ",\"format\":\"json\",\"stream\":false}";
        HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl + "/api/generate")
                .toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(120000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        if (conn.getResponseCode() != 200) {
            modelclass.markUnavailable();
            throw new IOException("ollama HTTP " + conn.getResponseCode());
        }
        availableCache = true;
        modelclass.markAvailable();
        String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return extractResponse(response);
    }

    /** Minimal extraction of the "response" field - full JSON parsing follows with the jsonCasted loader. */
    private static String extractResponse(String json) {
        String key = "\"response\":\"";
        int i = json.indexOf(key);
        if (i < 0) {
            return json;
        }
        StringBuilder sb = new StringBuilder();
        for (int j = i + key.length(); j < json.length(); j++) {
            char c = json.charAt(j);
            if (c == '\\' && j + 1 < json.length()) {
                sb.append(json.charAt(j + 1));
                j++;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
