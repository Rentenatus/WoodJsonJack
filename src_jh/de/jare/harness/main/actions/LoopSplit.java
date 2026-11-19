/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.main.actions;

import de.jare.harness.LoopAction;
import de.jare.harness.model.Loop;
import de.jare.jsoncasted.lang.JsonNode;
import java.util.List;

/**
 * SPLIT as a loop action (§15.6): the action carries the N atomic sub-prompts and the target
 * subloop id ({@code loopId}, default {@link Loop#SUB "sub"}). The driver creates the N subtasks
 * (task starts) and starts one <b>subloop</b> per subtask — not a fresh {@code "main"} loop.
 * The shared Limits instance is inherited, so every subloop sees the last counter state; after
 * the N subloops return the driver computes the consumption deltas and updates the current
 * output with slot + N deltas summed.
 *
 * @author Janusch Rentenatus
 */
public record LoopSplit(List<String> prompts, String loopId, int parts) implements LoopAction {
    public LoopSplit {
        if (loopId == null || loopId.isBlank()) {
            loopId = Loop.SUB;
        }
        if (parts < 0) {
            parts = 0;
        }
    }

    /** Default target subloop: {@link Loop#SUB}. */
    public LoopSplit(List<String> prompts) {
        this(prompts, Loop.SUB, 0);
    }

    /** Default target subloop {@link Loop#SUB}, prompts carried: {@link #prompts}. */
    public LoopSplit(List<String> prompts, String loopId) {
        this(prompts, loopId, 0);
    }

    @Override public String type() { return "split"; }
    @Override public Flow flow() { return Flow.SPLIT; }

    /** The carried sub-prompts and the target loop id, projected into the task tree (§3). */
    @Override public JsonNode detail() {
        JsonNode d = JsonNode.objectNode();
        d.put("loopId", JsonNode.stringNode(loopId));
        if (parts > 0) {
            d.put("parts", JsonNode.longNode(parts));
        }
        d.put("count", JsonNode.longNode(prompts == null ? 0 : prompts.size()));
        JsonNode arr = JsonNode.arrayNode();
        if (prompts != null) {
            for (String p : prompts) {
                arr.add(JsonNode.stringNode(p == null ? "" : p));
            }
        }
        d.put("prompts", arr);
        return d;
    }
}
