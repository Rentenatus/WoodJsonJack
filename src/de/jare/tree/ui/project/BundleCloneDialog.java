/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.project;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;

/**
 * The clone dialog in its dry form (concept: CONCEPT_ProjectView.md, section 5, and
 * implementation plan phase 4: the dialog without execution until phase 6). Shows the source,
 * the sibling-convention destination and the two copy modes with their effects; the preview
 * follows the mode choice live. Nothing is copied - the button says so.
 *
 * @author Janusch Rentenatus
 */
public class BundleCloneDialog extends JDialog {

    private final File sourceFolder;
    private final JRadioButton deepButton;
    private final JRadioButton shallowButton;
    private final JLabel targetLabel;
    private final JTextArea previewArea;

    /**
     * Shows the dry clone dialog for the given bundle folder.
     *
     * @param parentWindow the window the dialog belongs to (modal anchor)
     * @param sourceFolder the bundle folder to clone
     */
    public static void show(java.awt.Window parentWindow, File sourceFolder) {
        BundleCloneDialog dialog = new BundleCloneDialog(parentWindow, sourceFolder);
        dialog.setVisible(true);
    }

    private BundleCloneDialog(java.awt.Window parentWindow, File sourceFolder) {
        super(parentWindow, "Clone bundle (preview)", ModalityType.APPLICATION_MODAL);
        this.sourceFolder = sourceFolder;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(4, 8, 2, 8);

        form.add(new JLabel("Source bundle:"), gbc);
        gbc.gridx = 1;
        form.add(new JLabel(sourceFolder.getAbsolutePath()), gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        form.add(new JLabel("Destination (sibling convention):"), gbc);
        gbc.gridx = 1;
        targetLabel = new JLabel();
        form.add(targetLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        deepButton = new JRadioButton("Deep copy (default) - linked files are copied into the clone");
        deepButton.setSelected(true);
        form.add(deepButton, gbc);

        gbc.gridy++;
        shallowButton = new JRadioButton("Shallow copy - linked files stay referenced in place");
        form.add(shallowButton, gbc);

        ButtonGroup modes = new ButtonGroup();
        modes.add(deepButton);
        modes.add(shallowButton);

        gbc.gridy++;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        previewArea = new JTextArea(8, 60);
        previewArea.setEditable(false);
        form.add(previewArea, gbc);

        updatePreview();
        deepButton.addActionListener(e -> updatePreview());
        shallowButton.addActionListener(e -> updatePreview());

        JButton closeButton = new JButton("Close (execution comes with phase 6)");
        closeButton.addActionListener(e -> dispose());
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonRow.add(closeButton);

        add(form, BorderLayout.CENTER);
        add(buttonRow, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(parentWindow);
    }

    private void updatePreview() {
        BundleClonePlanner.ClonePlan plan = BundleClonePlanner.plan(sourceFolder, deepButton.isSelected());
        targetLabel.setText(plan.getTargetFolder() != null
                ? plan.getTargetFolder().getAbsolutePath() : "-");
        StringBuilder text = new StringBuilder();
        if (plan.isRefused()) {
            text.append("Refused: ").append(plan.refusalReason()).append('\n');
        } else {
            for (String note : plan.getNotes()) {
                text.append("- ").append(note).append('\n');
            }
        }
        text.append("\nThis is a dry preview - nothing is copied in this phase.");
        previewArea.setText(text.toString());
    }

}
