/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.model;

/**
 * One entry of the top-level {@code modelclasses} list (CONCEPT_Harness.md §2, §5):
 * provider + model + purpose + size + costFactor + tool capability. The list key is gone -
 * entries are addressed by {@code purpose} (e.g. "investigation"), so several modelclasses
 * can share one purpose; calculators pick among them by {@code size} (in B parameters).
 * A failed provider is remembered and marked here ({@code available = false} plus
 * timestamp of the last outage).
 *
 * @author Janusch Rentenatus
 */
public class Modelclass {

    private final String provider;      // "ollama" (default http://localhost:11434) | "hosted"
    private final String model;         // e.g. "qwen3.8:27b"
    private final String purpose;       // e.g. "investigation", "edit", "rag" - NOT unique
    private final int size;             // parameters in B (billions), integer; 0 = unknown
    private final double costFactor;
    private final boolean toolCapable;
    private boolean available = true;
    private long lastOutageTs = 0L;

    public Modelclass(String provider, String model, String purpose, int size,
                      double costFactor, boolean toolCapable) {
        this.provider = provider;
        this.model = model;
        this.purpose = purpose;
        this.size = size > 0 ? size : sizeOf(model);
        this.costFactor = costFactor;
        this.toolCapable = toolCapable;
    }

    /** Derives the integer size in B from the model name: "qwen3.8:27b" → 27, "granite3.3:8b" → 8; 0 when unknown. */
    public static int sizeOf(String modelName) {
        if (modelName == null) {
            return 0;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+)\\s*b\\b", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(modelName);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    /** The purpose (role) of this modelclass - not unique, several classes may share it. */
    public String getPurpose() {
        return purpose;
    }

    /** Model size in B parameters (integer); 0 = unknown. Calculators use it to pick among same-purpose classes. */
    public int getSize() {
        return size;
    }

    public double getCostFactor() {
        return costFactor;
    }

    public boolean isToolCapable() {
        return toolCapable;
    }

    public boolean isAvailable() {
        return available;
    }

    public void markUnavailable() {
        this.available = false;
        this.lastOutageTs = System.currentTimeMillis();
    }

    public void markAvailable() {
        this.available = true;
    }

    public long getLastOutageTs() {
        return lastOutageTs;
    }
}
