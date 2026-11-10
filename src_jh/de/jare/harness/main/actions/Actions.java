/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.main.actions;

import de.jare.harness.HarnessAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Factory for the built-in {@link HarnessAction} representatives (package
 * {@code de.jare.harness.main.actions} - the interface itself stays in
 * {@code de.jare.harness}). New actions are added here as new records implementing
 * {@link HarnessAction}.
 *
 * @author Janusch Rentenatus
 */
public final class Actions {

    private Actions() {
    }

    /** Maps a legacy decision string + payload back to an action (log compatibility). Never null. */
    public static HarnessAction fromDecision(String decision, Map<String, String> payload, String message) {
        if (decision == null) {
            return new None();
        }
        return switch (decision) {
            case "abort" -> new Abort(message);
            case "split" -> {
                List<String> prompts = new ArrayList<>();
                String q = payload == null ? null : payload.get("quests");
                if (q != null && !q.isBlank()) {
                    prompts.addAll(Arrays.asList(q.split("\n")));
                }
                int parts = 2;
                if (payload != null) {
                    try {
                        parts = Integer.parseInt(payload.getOrDefault("parts", "2"));
                    } catch (NumberFormatException ignored) {
                        parts = 2;
                    }
                }
                yield new Split(prompts, parts);
            }
            case "incomplete" -> new ReasoningSubtask();
            case "retry" -> new Retry(message);
            default -> new None();
        };
    }
}
