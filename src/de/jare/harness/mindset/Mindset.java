/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.mindset;

import de.jare.harness.io.AtomicFileWrite;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The mindset of the AI (CONCEPT_Harness.md §16): only global under
 * {@code ~/.wood/mindset/} - never project-local, never in the project RAG. Categorized
 * entries that different calculators read via {@code ctx.mindset.get(category)}; the AI
 * itself stores user preferences via {@code set}. Max 1024 chars per entry - exceeding
 * only warns, does not abort.
 *
 * @author Janusch Rentenatus
 */
public class Mindset {

    public static final int MAX_CHARS = 1024;
    public static final String GLOBAL_MINDSET_DIR = ".wood/mindset";

    private final Map<String, String> categories = new LinkedHashMap<>();
    private String lastWarning = "";

    public String get(String category) {
        return categories.getOrDefault(category, "");
    }

    /** @return true when within the 1024 char limit, false when only a warning was emitted */
    public boolean set(String category, String text) {
        if (text != null && text.length() > MAX_CHARS) {
            lastWarning = "mindset '" + category + "' exceeds " + MAX_CHARS + " chars ("
                    + text.length() + ") - stored anyway, warning only";
            categories.put(category, text);
            return false;
        }
        categories.put(category, text == null ? "" : text);
        return true;
    }

    public String getLastWarning() {
        return lastWarning;
    }

    public Map<String, String> getCategories() {
        return categories;
    }

    /** One file per category under the mindset dir - the user has full file-level control. */
    public void save(Path mindsetDir) throws IOException {
        for (Map.Entry<String, String> e : categories.entrySet()) {
            AtomicFileWrite.writeString(mindsetDir.resolve(safeFileName(e.getKey()) + ".txt"), e.getValue());
        }
    }

    public static Mindset load(Path mindsetDir) throws IOException {
        Mindset m = new Mindset();
        if (mindsetDir == null || !Files.isDirectory(mindsetDir)) {
            return m;
        }
        try (var s = Files.list(mindsetDir)) {
            for (Path f : s.filter(p -> p.getFileName().toString().endsWith(".txt")).toList()) {
                String name = f.getFileName().toString();
                m.categories.put(name.substring(0, name.length() - 4), Files.readString(f));
            }
        }
        return m;
    }

    private static String safeFileName(String category) {
        return category.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
