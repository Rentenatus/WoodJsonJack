/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.impl.hosted;

import de.jare.harness.impl.ModelProvider;
import de.jare.harness.model.Modelclass;
import de.jare.jsonpost.PostJsonClient;
import java.io.IOException;
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
    private final PostJsonClient client;

    public HostedProvider(String apiKey, Modelclass modelclass) {
        this(MISTRAL_URL, apiKey, modelclass);
    }

    public HostedProvider(String baseUrl, String apiKey, Modelclass modelclass) {
        this(baseUrl, apiKey, modelclass, new PostJsonClient());
    }

    public HostedProvider(String baseUrl, String apiKey, Modelclass modelclass, PostJsonClient client) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelclass = modelclass;
        this.client = client;
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
        // typed chat request via jsonpost (jsonpost.item + jsonpost.def), Bearer header,
        // JSON-strict via response_format - hosted chat-completions shape
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
        request.setResponseFormat("{\"type\":\"json_object\"}");

        de.jare.jsonpost.def.JsonPostDefinition def = de.jare.jsonpost.def.JsonPostDefinition.INSTANCE;
        java.util.Map<String, String> headers = java.util.Map.of("Authorization", "Bearer " + apiKey);
        final String response;
        try {
            response = client.post(request, baseUrl, 120000, def, def.getChatRequestRoot(), headers);
        } catch (IOException e) {
            modelclass.markUnavailable();
            throw e;
        }
        modelclass.markAvailable();
        return extractContent(response);
    }

    /** Extracts choices[0].message.content from the hosted chat-completion response. */
    static String extractContent(String json) {
        for (String key : new String[]{"\"content\": \"", "\"content\":\""}) {
            int i = json.indexOf(key);
            if (i < 0) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            for (int j = i + key.length(); j < json.length(); j++) {
                char c = json.charAt(j);
                if (c == '\\' && j + 1 < json.length()) {
                    char n = json.charAt(j + 1);
                    sb.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
                    j++;
                } else if (c == '"') {
                    return sb.toString();
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
        return json;
    }
}
