/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.impl;

import de.jare.harness.model.Modelclass;
import java.util.List;

/**
 * A model provider behind a modelclass (CONCEPT_Harness.md §2): {@code ollama} (local,
 * default http://localhost:11434) or {@code hosted}. The same interface serves both -
 * an ollama and a hosted model can run as two candidates of the same quest.
 * Local ollama models are served sequentially, never concurrently.
 *
 * @author Janusch Rentenatus
 */
public interface ModelProvider {

    /** The modelclass this provider serves. */
    Modelclass modelclass();

    /** Cheap health probe; an outage marks the modelclass unavailable (§2). */
    boolean isAvailable();

    /**
     * One stateless call: an ordered list of prompt segments in, structured JSON out.
     * No conversation history across calls - every call is stateless (§4).
     */
    String complete(List<String> segments) throws Exception;
}
