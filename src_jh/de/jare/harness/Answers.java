/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared answer extraction over a task tree: a task that split again carries only the
 * "closed:summary" placeholder as its own artifact - the real answers live at the LEAF
 * tasks (each leaf's artifact is its WORK answer, written by CLOSE). Used by the engine
 * (live partial answers), the deterministic SUMMARIZE and the summarize.compose calculator.
 *
 * @author Janusch Rentenatus
 */
public final class Answers {

    private Answers() {
    }

    /** Artifacts of all leaf tasks below {@code t} (inclusive when {@code t} itself is a leaf). */
    public static List<String> leafAnswers(TaskView t) {
        List<String> out = new ArrayList<>();
        collectLeafArtifacts(t, out);
        return out;
    }

    /** The answer text of a (sub-)task: joined leaf artifacts, else its own artifact. */
    public static String answerOf(TaskView t) {
        List<String> leaves = leafAnswers(t);
        if (!leaves.isEmpty()) {
            return String.join(" | ", leaves);
        }
        String a = t.getArtifact();
        return a == null ? "" : a;
    }

    private static void collectLeafArtifacts(TaskView t, List<String> out) {
        if (t.getChildren().isEmpty()) {
            if (t.getArtifact() != null && !t.getArtifact().isBlank()) {
                out.add(t.getArtifact());
            }
            return;
        }
        for (TaskView c : t.getChildren()) {
            collectLeafArtifacts(c, out);
        }
    }
}
