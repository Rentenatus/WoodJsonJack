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
            // live view: open the harness window as soon as a run starts, bound to instance+engine
            service.setRunStartHook((inst, eng) -> javax.swing.SwingUtilities.invokeLater(() -> showLiveView(inst, eng)));
            // attach the orator listeners registered before the service existed (§18.6)
            synchronized (pendingCommandListeners) {
                for (var l : pendingCommandListeners) {
                    service.addCommandListener(l);
                }
            }
            synchronized (pendingResponseListeners) {
                for (var l : pendingResponseListeners) {
                    service.addResponseListener(l);
                }
            }
            System.out.println("[DIAG." + de.jare.harness.Diag.runNo() + "] HarnessController: service created, attached "
                    + pendingCommandListeners.size() + " command + "
                    + pendingResponseListeners.size() + " response listeners");
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

    // pending orator registrations (§18.6): no projectRoot needed - they are attached to the
    // service as soon as the service instance exists, so the menu can register in its
    // constructor without touching the (not yet existing) project sidebar
    private final List<java.util.function.Consumer<de.jare.harness.memory.CommandHistory.Entry>> pendingCommandListeners = new ArrayList<>();
    private final List<java.util.function.Consumer<de.jare.harness.memory.WorkResponse>> pendingResponseListeners = new ArrayList<>();

    /**
     * Orator pass-through (§18): registers a listener on the CommandHistory of every run's
     * MemoryQuest. The controller holds NO UI reference - the caller (menu) maps entries
     * to whatever view it wants.
     */
    public void addCommandListener(java.util.function.Consumer<de.jare.harness.memory.CommandHistory.Entry> l) {
        if (l == null) {
            return;
        }
        synchronized (pendingCommandListeners) {
            pendingCommandListeners.add(l);
        }
        System.out.println("[DIAG." + de.jare.harness.Diag.runNo() + "] HarnessController.addCommandListener: pending now "
                + pendingCommandListeners.size() + ", service exists=" + (service != null));
        if (service != null) {
            service.addCommandListener(l);
        }
    }

    /** Orator pass-through: registers a listener on the WorkResponse of every run's MemoryQuest. */
    public void addResponseListener(java.util.function.Consumer<de.jare.harness.memory.WorkResponse> l) {
        if (l == null) {
            return;
        }
        synchronized (pendingResponseListeners) {
            pendingResponseListeners.add(l);
        }
        if (service != null) {
            service.addResponseListener(l);
        }
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
    /** True while a background run is active - parallel runs would collide on the
     *  .wood/harness/* files (AccessDeniedException on lineage/memory/instance). */
    private volatile boolean runActive;

    public void runTask(File projectRoot, String type, String task) {
        if (runActive) {
            JOptionPane.showMessageDialog(owner, "A harness run is already in progress - please wait.",
                    "Harness", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        runActive = true;
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
                runActive = false;
                try {
                    get();
                    showView();
                } catch (Throwable t) {   // incl. OutOfMemoryError - never let an Error kill the EDT
                    Throwable cause = t instanceof java.util.concurrent.ExecutionException && t.getCause() != null
                            ? t.getCause() : t;
                    System.out.println("[DIAG." + de.jare.harness.Diag.runNo() + "] harness run failed:");
                    cause.printStackTrace(System.out);
                    JOptionPane.showMessageDialog(owner, "Harness run failed: " + cause,
                            "Harness", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /** Shows the harness window with the last run's task tree (status colors, score badges, protocol).
     *  Without a run yet the window still opens - with an empty state instead of a dialog. */
    public void showView() {
        try {
            if (viewDialog == null || !viewDialog.isDisplayable()) {
                viewDialog = new JDialog(owner, "Harness", false);
                HarnessViewPanel panel = new HarnessViewPanel();
                viewDialog.setContentPane(panel);
                viewDialog.setSize(900, 500);
                viewDialog.setLocationRelativeTo(owner);
                viewPanel = panel;
            }
            if (lastInstance != null) {
                if (lastInstance != boundInstance) {
                    // bind without engine: show the persisted tree + logs of the last run
                    viewPanel.bind(lastInstance, new HarnessEngine(
                            HarnessService.defaultModel(), lastInstance, null));
                    boundInstance = lastInstance;
                }
            } else {
                viewPanel.showEmpty("No harness run yet - start one via the KI pane (Ask AI) "
                        + "or the Harness menu.");
            }
            viewDialog.setVisible(true);
        } catch (HeadlessException e) {
            // headless environment (tests): nothing to show
        }
    }

    private HarnessViewPanel viewPanel;
    private HarnessInstance boundInstance;

    /** Opens (or reuses) the harness window bound to the LIVE engine - the tree updates during the run. */
    private void showLiveView(HarnessInstance instance, HarnessEngine engine) {
        try {
            if (viewDialog == null || !viewDialog.isDisplayable()) {
                viewDialog = new JDialog(owner, "Harness", false);
                viewPanel = new HarnessViewPanel();
                viewDialog.setContentPane(viewPanel);
                viewDialog.setSize(900, 500);
                viewDialog.setLocationRelativeTo(owner);
            }
            if (instance != boundInstance) {
                viewPanel.bind(instance, engine);   // engine listener drives the live refresh
                boundInstance = instance;
            }
            viewDialog.setVisible(true);
        } catch (HeadlessException e) {
            // headless environment (tests): nothing to show
        }
    }
}
