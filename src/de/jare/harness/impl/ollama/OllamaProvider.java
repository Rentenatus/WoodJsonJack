/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.impl.ollama;

import de.jare.harness.impl.ModelProvider;
import de.jare.harness.model.Modelclass;
import de.jare.jsonpost.PostJsonClient;
import java.io.IOException;
import java.util.List;

/**
 * Local ollama provider (CONCEPT_Harness.md §2): defaults to
 * {@code http://localhost:11434}. One stateless call = one POST to {@code /api/generate}
 * with the assembled segment list as prompt and JSON-strict format; the HTTP/JSON round
 * trip goes through jsonCasted's {@link PostJsonClient} (postRaw), the health probe
 * through {@code getRaw("/api/tags")}. Local models are served sequentially - no
 * concurrency inside ollama.
 *
 * @author Janusch Rentenatus
 */
public class OllamaProvider implements ModelProvider {

    public static final String DEFAULT_BASE_URL = "http://localhost:11434";

    private final String baseUrl;
    private final Modelclass modelclass;
    private final PostJsonClient client;
    private volatile Boolean availableCache;

    public OllamaProvider(Modelclass modelclass) {
        this(DEFAULT_BASE_URL, modelclass);
    }

    public OllamaProvider(String baseUrl, Modelclass modelclass) {
        this(baseUrl, modelclass, new PostJsonClient());
    }

    public OllamaProvider(String baseUrl, Modelclass modelclass, PostJsonClient client) {
        this.baseUrl = baseUrl;
        this.modelclass = modelclass;
        this.client = client;
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

    /** Health probe via GET /api/tags through the shared PostJsonClient. */
    private boolean probe() {
        try {
            client.getRaw(baseUrl + "/api/tags", 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String complete(List<String> segments) throws Exception {
        // one stateless chat call: SYSTEM segments become the system message, the rest the
        // user message; JSON-strict via format pin. Round trip via jsonpost's typed layer.
        de.jare.jsonpost.item.PostChatRequest request =
                new de.jare.jsonpost.item.PostChatRequest(modelclass.getModel());
        StringBuilder user = new StringBuilder();
        for (String seg : segments) {
            if (seg.startsWith("SYSTEM:")) {
                request.addMessage("system", seg.substring("SYSTEM:".length()).trim());
            } else {
                if (user.length() > 0) {
                    user.append("\n\n");
                }
                user.append(seg);
            }
        }
        request.addMessage("user", user.toString());
        request.setStream(false);
        request.setFormat("json");
        final de.jare.jsonpost.item.PostChatResponse response;
        try {
            response = client.postChat(request, baseUrl + "/api/chat", 120000);
        } catch (IOException e) {
            availableCache = false;
            modelclass.markUnavailable();
            throw e;
        }
        availableCache = true;
        modelclass.markAvailable();
        return response.getMessage() == null ? "" : response.getMessage().getContent();
    }

    /** Minimal extraction of the "response" field - full JSON parsing happens in the AiCalculator. */
    static String extractResponse(String json) {
        String key = "\"response\":\"";
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

    static String jsonString(String s) {
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
