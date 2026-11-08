/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.memory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The WorkCache (BearbeitungsCache) - intermediate state of the current run, owned by the Quest:
 * retrieved RAG chunks, tool outputs, partial results. Never persisted as recipe data; lives and
 * dies with the WorkOrder (docs/working/DESIGN_Quest_WorkOrder.md).
 *
 * @author Janusch Rentenatus
 */
public class WorkCache {

    private final Map<String, String> entries = new LinkedHashMap<>();

    public WorkCache put(String key, String value) {
        entries.put(key, value);
        return this;
    }

    public String get(String key) {
        return entries.get(key);
    }

    public boolean has(String key) {
        return entries.containsKey(key);
    }

    public Map<String, String> getEntries() {
        return entries;
    }
}
