/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The fixed purposes of a {@link HarnessCalculator} (CONCEPT_Harness.md §2). One global
 * interface, the kind acts as the discriminator.
 *
 * <p>Kind is <b>not a closed enum</b> but a registry class: the fourteen built-in purposes
 * are {@code public static final} constants (source-compatible with the old enum), and
 * additional literals are declared in the model root as {@code kinddefs[]} and resolved at
 * load time via {@link #valueOf(String)} — no recompiling for project-specific kinds.
 * The codec validates every kind literal against built-ins ∪ {@code kinddefs}, so typos
 * still fail with a clear message.
 *
 * @author Janusch Rentenatus
 */
public final class Kind {

    private static final Map<String, Kind> REGISTRY = new LinkedHashMap<>();
    private static final Set<String> BUILTINS = new LinkedHashSet<>();

    public static final Kind RELEVANCE = builtin("RELEVANCE");
    public static final Kind SPLIT = builtin("SPLIT");
    public static final Kind COMPLETE = builtin("COMPLETE");
    public static final Kind WORK = builtin("WORK");
    public static final Kind EVALUATE = builtin("EVALUATE");
    public static final Kind CHECK = builtin("CHECK");
    public static final Kind ABORT = builtin("ABORT");
    public static final Kind SUMMARIZE = builtin("SUMMARIZE");
    public static final Kind CLOSE = builtin("CLOSE");
    public static final Kind RAG_STORE = builtin("RAG_STORE");
    public static final Kind RAG_RETRIEVE = builtin("RAG_RETRIEVE");
    public static final Kind RAG_PRUNE = builtin("RAG_PRUNE");
    /** Entry guard of the main loop's START (§15.6), only in "main". */
    public static final Kind GUARD = builtin("GUARD");
    /** Collects the current skills at the main loop's START (§12), only in "main". */
    public static final Kind SKILL = builtin("SKILL");

    private final String name;

    private Kind(String name) {
        this.name = name;
    }

    private static Kind builtin(String name) {
        Kind k = new Kind(name);
        REGISTRY.put(name, k);
        BUILTINS.add(name);
        return k;
    }

    /**
     * Registry lookup by literal: returns the built-in constant or the already registered
     * custom kind; an unknown literal is auto-registered (declare it in the model's
     * {@code kinddefs[]} so the codec accepts it).
     */
    public static Kind valueOf(String name) {
        if (name == null) {
            throw new IllegalArgumentException("kind name is null");
        }
        return REGISTRY.computeIfAbsent(name, Kind::new);
    }

    /** All registered kinds (built-ins first, then custom literals in registration order). */
    public static Kind[] values() {
        return REGISTRY.values().toArray(new Kind[0]);
    }

    /** All registered kind literals. */
    public static List<String> names() {
        return new ArrayList<>(REGISTRY.keySet());
    }

    /** True when the literal is one of the built-in constants (not a kinddef). */
    public static boolean isBuiltin(String name) {
        return BUILTINS.contains(name);
    }

    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Kind k && k.name.equals(name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
