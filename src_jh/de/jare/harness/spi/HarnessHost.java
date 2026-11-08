/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.spi;

import de.jare.harness.HarnessEngine;
import de.jare.harness.TaskNode;
import de.jare.jsoncasted.lang.JsonNode;
import java.io.File;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The host SPI (CONCEPT_Harness.md §13, §14): everything the harness needs from its embedding
 * application (WJJ Swing editor today, a TUI later) without depending on it. The host starts
 * the harness and hands over its structures; the harness never imports host classes.
 * All methods have defaults so a headless/TUI host only overrides what it can do.
 *
 * @author Janusch Rentenatus
 */
public interface HarnessHost {

    /** API key for hosted providers (WJJ: WoodSettings agent preferences). Default: none. */
    default String hostedApiKey() {
        return "";
    }

    /** The harness model as the host parses it in its own editor (JsonEdit structure handover).
     * Default: null - the harness then loads {@code <project>/.wood/harness-model.json} itself
     * or falls back to the bundled default model. */
    default JsonNode harnessModelJson(File projectDir) {
        return null;
    }

    /** editor.open target: open a project-relative file in the host editor. Default: no-op. */
    default void openInEditor(String projectRelativePath) {
    }

    /** ctx.ui.askUser: ask the user a question with options. Default: first option. */
    default String askUser(String question, List<String> options) {
        return options.isEmpty() ? "" : options.get(0);
    }

    /** Live updates of the run (task tree changes, notifications) for the host's view. */
    default void addRunListener(BiConsumer<TaskNode, String> listener) {
    }

    /** Convenience: wire this host into an engine (askUser, editor.open). Run listeners stay
     *  on the host side - a view binds to the instance/engine directly via HarnessView. */
    default void wireInto(HarnessEngine engine) {
        engine.setUserAskHandler(this::askUser);
        engine.setEditorOpenHandler(this::openInEditor);
    }
}
