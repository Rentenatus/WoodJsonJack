/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.impl.hosted;

import de.jare.harness.impl.ModelProvider;
import de.jare.harness.model.Modelclass;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Hosted provider (CONCEPT_Harness.md §2): e.g. the Mistral chat completions API, same
 * {@link ModelProvider} interface as ollama - a local and a hosted model can run as two
 * candidates of the same quest. The API key comes from the WJJ agent preferences.
 * Answers are requested with JSON response format; the strict parse happens in the
 * AiCalculator (SCHEMA_VIOLATION on garbage).
 *
 * @author Janusch Rentenatus
 */
public class HostedProvider implements ModelProvider {

    public static final String MISTRAL_URL = "https://api.mistral.ai/v1/chat/completions";

    private final String baseUrl;
    private final String apiKey;
    private final Modelclass modelclass;

    public HostedProvider(String apiKey, Modelclass modelclass) {
        this(MISTRAL_URL, apiKey, modelclass);
    }

    public HostedProvider(String baseUrl, String apiKey, Modelclass modelclass) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelclass = modelclass;
    }

    @Override
    public Modelclass modelclass() {
        return modelclass;
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String complete(List<String> segments) throws Exception {
        if (!isAvailable()) {
            modelclass.markUnavailable();
            throw new IOException("no api key configured for hosted provider");
        }
        String prompt = String.join("\n\n", segments);
        String body = "{\"model\":\"" + modelclass.getModel() + "\",\"messages\":[{\"role\":\"user\",\"content\":"
                + jsonString(prompt) + "}],\"response_format\":{\"type\":\"json_object\"}}";
        HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(120000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        if (conn.getResponseCode() != 200) {
            modelclass.markUnavailable();
            throw new IOException("hosted provider HTTP " + conn.getResponseCode());
        }
        modelclass.markAvailable();
        String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return extractContent(response);
    }

    /** Extracts choices[0].message.content from the chat completion response. */
    private static String extractContent(String json) {
        String key = "\"content\":\"";
        int i = json.indexOf(key);
        if (i < 0) {
            return json;
        }
        StringBuilder sb = new StringBuilder();
        for (int j = i + key.length(); j < json.length(); j++) {
            char c = json.charAt(j);
            if (c == '\\' && j + 1 < json.length()) {
                char n = json.charAt(j + 1);
                sb.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
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
