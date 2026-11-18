/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.harness;

import de.jare.harness.HarnessEngine;
import de.jare.harness.HarnessInstance;
import de.jare.harness.HarnessService;
import de.jare.harness.TaskData;
import de.jare.harness.spi.HarnessHost;
import de.jare.tree.settings.WoodSettings;
import de.jare.tree.ui.WoodWindow;
import java.awt.HeadlessException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

/**
 * UI entry point of the harness (CONCEPT_Harness.md §3, §15): the "Harness" menu in the
 * main window runs a task via {@link HarnessService} in the background and shows the
 * live task tree in a {@link HarnessViewPanel} dialog. Entry points: tool call (here via
 * menu/prompt) or editor selection - a selection is turned into a prompt and continued
 * here.
 *
 * @author Janusch Rentenatus
 */
public class HarnessController {

    private final JFrame owner;
    private HarnessService service;   // the main harness control instance of this window, reused across runs
    private HarnessInstance lastInstance;
    private HarnessEngine lastEngine;
    private JDialog viewDialog;
    private final List<BiConsumer<TaskData, String>> runListeners = new ArrayList<>();
    private java.util.function.Consumer<String> commandReplyHandler;   // §17: /cmd replies shown in the KI pane

    public HarnessController(JFrame owner) {
        this.owner = owner;
    }

    /** The main harness control instance (lazy, one per window); the host SPI is wired into it. */
    private HarnessService service(File projectRoot) {
        if (service == null) {
            service = new HarnessService(projectRoot, wjjHost(projectRoot));
        }
        return service;
    }

    /** The MemoryQuest (WorkOrder/WorkCache/WorkResponse) of the last harness run, created by the core. */
    public de.jare.harness.memory.MemoryQuest getLastMemoryQuest() {
        return service == null ? null : service.getLastMemoryQuest();
    }

    /** §17: the KI pane installs this to show /cmd replies (e.g. /clear, /memory) in the output area. */
    public void setCommandReplyHandler(java.util.function.Consumer<String> commandReplyHandler) {
        this.commandReplyHandler = commandReplyHandler;
    }

    /**
     * AskAi entry from the KI pane (Enter / "Ask AI"): runs the main AskAi quest with the user
     * prompt. The harness core (HarnessService.runTask) creates the MemoryQuest + WorkOrder -
     * this controller only triggers, it never builds TaskData/WorkOrder itself.
     * §17: input starting with "/" is a chat command (ChatCommandService), not a run.
     */
    public void runAskAi(File projectRoot, String prompt) {
        if (projectRoot == null) {
            JOptionPane.showMessageDialog(owner, "Open a project first.", "Harness", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (prompt == null || prompt.isBlank()) {
            return;
        }
        de.jare.harness.main.chat.ChatCommandService.CommandResult cmd =
                new de.jare.harness.main.chat.ChatCommandService(service(projectRoot)).dispatch(prompt);
        if (cmd.handled()) {
            if (commandReplyHandler != null) {
                commandReplyHandler.accept(cmd.reply());
            }
            return;
        }
        runTask(projectRoot, de.jare.harness.model.HarnessModel.QUEST_ASK_AI, cmd.prompt());
    }

    /**
     * The WJJ side of the host SPI: the harness core (src_jh) never imports WJJ classes -
     * api key, editor open, askUser and run listeners all flow through this implementation.
     */
    private HarnessHost wjjHost(File projectRoot) {
        return new HarnessHost() {
            @Override
            public String hostedApiKey() {
                return WoodSettings.INSTANCE.getAgentPreferences() != null
                        ? WoodSettings.INSTANCE.getAgentPreferences().getApiKey() : "";
            }

            @Override
            public void openInEditor(String projectRelativePath) {
                if (owner instanceof WoodWindow w) {
                    w.getMainActions().loadJsonFile(new File(projectRoot, projectRelativePath));
                }
            }

            @Override
            public String askUser(String question, List<String> options) {
                String answer = JOptionPane.showInputDialog(owner, question + "\n" + String.join(" | ", options),
                        "Harness", JOptionPane.QUESTION_MESSAGE);
                return answer != null && options.contains(answer) ? answer
                        : options.isEmpty() ? "" : options.get(0);
            }

            @Override
            public void addRunListener(BiConsumer<TaskData, String> listener) {
                runListeners.add(listener);
            }
        };
    }

    public HarnessInstance getLastInstance() {
        return lastInstance;
    }

    public HarnessEngine getLastEngine() {
        return lastEngine;
    }

    /** Runs one task against the current project in a background thread, then shows the view. */
    public void runTaskInteractive(File projectRoot) {
        if (projectRoot == null) {
            JOptionPane.showMessageDialog(owner, "Open a project first.", "Harness", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String taskType = JOptionPane.showInputDialog(owner, "Task type (e.g. RESEARCH, CODE_EDIT):", "Harness Run", JOptionPane.QUESTION_MESSAGE);
        if (taskType == null || taskType.isBlank()) {
            return;
        }
        String title = JOptionPane.showInputDialog(owner, "Task:", "Harness Run", JOptionPane.QUESTION_MESSAGE);
        if (title == null || title.isBlank()) {
            return;
        }
        runTask(projectRoot, taskType.trim().toUpperCase(), title);
    }

    /**
     * Entry point 2 (§3): an editor selection is turned into a prompt - the selection text
     * becomes the task, only the task type is asked, then the run continues here.
     */
    public void runTaskFromSelection(File projectRoot, String selection) {
        if (projectRoot == null) {
            JOptionPane.showMessageDialog(owner, "Open a project first.", "Harness", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (selection == null || selection.isBlank()) {
            JOptionPane.showMessageDialog(owner, "Nothing selected in the editor.", "Harness", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String taskType = JOptionPane.showInputDialog(owner, "Task type (e.g. RESEARCH, CODE_EDIT):", "Harness Run from Selection", JOptionPane.QUESTION_MESSAGE);
        if (taskType == null || taskType.isBlank()) {
            return;
        }
        // the selection itself is the constructed prompt (§3 entry point 2)
        runTask(projectRoot, taskType.trim().toUpperCase(), selection.strip());
    }

    /** Runs one task in a background thread and shows the harness view afterwards. */
    public void runTask(File projectRoot, String type, String task) {
        new SwingWorker<HarnessInstance, Void>() {
            @Override
            protected HarnessInstance doInBackground() throws Exception {
                HarnessInstance instance = service(projectRoot).runTask(type, task);
                lastInstance = instance;
                lastEngine = null; // engine is local to the run; view rebinds from the instance
                return instance;
            }

            @Override
            protected void done() {
                try {
                    get();
                    showView();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(owner, "Harness run failed: " + e.getMessage(),
                            "Harness", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /** Shows the harness window with the last run's task tree (status colors, score badges, protocol). */
    public void showView() {
        if (lastInstance == null) {
            JOptionPane.showMessageDialog(owner, "No harness run yet.", "Harness", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            if (viewDialog == null || !viewDialog.isDisplayable()) {
                viewDialog = new JDialog(owner, "Harness", false);
                HarnessViewPanel panel = new HarnessViewPanel();
                viewDialog.setContentPane(panel);
                viewDialog.setSize(900, 500);
                viewDialog.setLocationRelativeTo(owner);
                // bind without engine: show the persisted tree + logs of the last run
                panel.bind(lastInstance, new HarnessEngine(
                        HarnessService.defaultModel(), lastInstance, null));
            }
            viewDialog.setVisible(true);
        } catch (HeadlessException e) {
            // headless environment (tests): nothing to show
        }
    }
}
