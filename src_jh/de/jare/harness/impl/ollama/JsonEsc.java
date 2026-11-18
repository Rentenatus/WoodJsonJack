/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.impl.ollama;

/**
 * JSON string escaping for the raw request bodies posted to ollama - previously
 * copy-pasted as {@code esc}/{@code quote} into {@link OllamaProvider} and
 * {@link OllamaEmbeddingProvider}.
 *
 * @author Janusch Rentenatus
 */
final class JsonEsc {

    private JsonEsc() {
    }

    /** Escapes a string for embedding into a JSON string literal (without the quotes). */
    static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** A quoted, escaped JSON string literal. */
    static String quote(String s) {
        return '"' + esc(s) + '"';
    }
}
