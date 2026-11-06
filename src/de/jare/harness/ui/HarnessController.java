/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.ui;

import de.jare.harness.HarnessEngine;
import de.jare.harness.HarnessInstance;
import de.jare.harness.HarnessService;
import java.awt.HeadlessException;
import java.io.File;
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
    private HarnessInstance lastInstance;
    private HarnessEngine lastEngine;
    private JDialog viewDialog;

    public HarnessController(JFrame owner) {
        this.owner = owner;
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
        final String type = taskType.trim().toUpperCase();
        final String task = title;
        new SwingWorker<HarnessInstance, Void>() {
            @Override
            protected HarnessInstance doInBackground() throws Exception {
                HarnessService service = new HarnessService(projectRoot);
                HarnessInstance instance = service.runTask(type, task);
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
