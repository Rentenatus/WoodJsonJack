/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.prompts;

import de.jare.harness.memory.WorkCache;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PromptFormatter - resolves {@code {data.<key>}} placeholders in a prompt template against the
 * runtime WorkCache (the "data cache" of the run). A calculator that produced a value writes it
 * into the cache (e.g. scoring writes {@code count}), a following calculator formats its prompt
 * template with it: {@code "You decompose the user prompt into {data.count} atomic quests ..."}.
 * Unknown keys stay as-is (visible in the prompt, never silently dropped); a null cache leaves
 * the template unchanged.
 *
 * @author Janusch Rentenatus
 */
public final class PromptFormatter {

    private static final Pattern DATA_PLACEHOLDER = Pattern.compile("\\{data\\.([A-Za-z0-9_\\-]+)}");

    private PromptFormatter() {
    }

    /** Replaces every {@code {data.<key>}} with {@code cache.get(key)}; unknown keys stay literal. */
    public static String format(String template, WorkCache cache) {
        if (template == null || cache == null) {
            return template;
        }
        Matcher m = DATA_PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = cache.get(m.group(1));
            m.appendReplacement(sb, value == null ? Matcher.quoteReplacement(m.group(0))
                    : Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
