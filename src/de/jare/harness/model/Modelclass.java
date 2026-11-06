/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.model;

/**
 * One entry of the top-level {@code modelclasses} JsonMap (CONCEPT_Harness.md §2, §5):
 * resolves a modelclass name to provider + model + costFactor + tool capability.
 * A failed provider is remembered and marked here ({@code available = false} plus
 * timestamp of the last outage).
 *
 * @author Janusch Rentenatus
 */
public class Modelclass {

    private final String provider;      // "ollama" (default http://localhost:11434) | "hosted"
    private final String model;         // e.g. "qwen2.5:3b"
    private final double costFactor;
    private final boolean toolCapable;
    private boolean available = true;
    private long lastOutageTs = 0L;

    public Modelclass(String provider, String model, double costFactor, boolean toolCapable) {
        this.provider = provider;
        this.model = model;
        this.costFactor = costFactor;
        this.toolCapable = toolCapable;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
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
