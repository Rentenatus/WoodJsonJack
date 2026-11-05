/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.project;

import de.jare.jsoncasted.project.BundleImportResolver;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;

/**
 * The imports view of a bundle (concept: CONCEPT_ProjectView.md, section 6 - phase 7): the
 * descriptor's imports with their resolutions - the reactor path hit, the project-local
 * convenience hit or the materialized repository stand, each with the channel it came through;
 * unresolved imports carry the guidance line. No transitive resolution (binding): the imported
 * bundle's own imports are its own dialog, never silently wired here. Project-wide version
 * conflicts (two versions of one imported bundle) show as refusal lines.
 *
 * @author Janusch Rentenatus
 */
public class BundleImportsDialog extends JDialog {

    private BundleImportsDialog(Window parentWindow, String title, String content) {
        super(parentWindow, title, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JTextArea area = new JTextArea(content);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonRow.add(closeButton);

        add(new javax.swing.JScrollPane(area), BorderLayout.CENTER);
        add(buttonRow, BorderLayout.SOUTH);
        setSize(700, 400);
        setLocationRelativeTo(parentWindow);
    }

    /**
     * Shows the imports view of the given bundle within the open project.
     *
     * @param parentWindow the window the dialog belongs to (modal anchor).
     * @param projectRoot the open project root (the resolver's base), not null.
     * @param bundleFolder the bundle folder whose imports are shown, not null.
     */
    public static void show(Window parentWindow, File projectRoot, File bundleFolder) {
        final BundleImportResolver resolver = new BundleImportResolver();
        StringBuilder text = new StringBuilder();
        text.append("Imports of ").append(bundleFolder.getName()).append(":\n\n");
        boolean any = false;
        for (BundleImportResolver.ImportResolution resolution
                : resolver.resolveImports(projectRoot, bundleFolder)) {
            any = true;
            final String name = resolution.getEntry() != null
                    ? String.valueOf(resolution.getEntry().getBundle()) : "?";
            if (resolution.getChannel() == BundleImportResolver.Channel.UNRESOLVED) {
                text.append("[UNRESOLVED] ").append(name).append('\n')
                        .append("    ").append(resolution.getProblem()).append('\n');
            } else {
                text.append('[').append(resolution.getChannel()).append("] ").append(name)
                        .append('\n').append("    ")
                        .append(resolution.getBundleFolder().getAbsolutePath()).append('\n');
            }
        }
        if (!any) {
            text.append("(no imports declared)\n");
        }
        text.append("\nNo transitive resolution: an imported bundle's own imports are its own"
                + " wiring, shown in its own dialog.\n");
        for (String refusal : resolver.findVersionConflicts(projectRoot)) {
            text.append("\n! ").append(refusal).append('\n');
        }
        new BundleImportsDialog(parentWindow, "Imports - " + bundleFolder.getName(),
                text.toString()).setVisible(true);
    }

}
