/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.settings;

import de.jare.tree.control.JackMasterControl;
import de.jare.tree.settings.theme.ColorScheme;
import de.jare.tree.settings.theme.Theme;
import de.jare.tree.ui.JackEditTree;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class ThemesPreferencesPreviewPane extends JPanel implements ChangeListener {

    private final JackMasterControl previewMasterControl;
    private final JackEditTree previewTree;

    private Theme currentTheme;

    public ThemesPreferencesPreviewPane() {
        super(new BorderLayout(8, 8));

        this.previewMasterControl = new JackMasterControl();
        this.previewTree = new JackEditTree(previewMasterControl, "preview", "project", "assets", "scene");

        setBorder(BorderFactory.createTitledBorder("Preview"));

        previewMasterControl.setActiveEditor(previewTree, this);
        expandAllRows();

        JScrollPane scrollPane = new JScrollPane(previewTree);
        add(scrollPane, BorderLayout.CENTER);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (currentTheme != null) {
            updatePreviewDefaults(currentTheme);
        }
    }

    public void loadThemeDetails(Theme theme) {
        if (theme == null) {
            return;
        }

        this.currentTheme = theme;
        updatePreviewDefaults(theme);
    }

    private void expandAllRows() {
        for (int row = 0; row < previewTree.getTree().getRowCount(); row++) {
            previewTree.getTree().expandRow(row);
        }
    }

    private void updatePreviewDefaults(Theme theme) {
        if (theme == null) {
            return;
        }

        ColorScheme detailedScheme = theme.getColors();

        // ToDo..
        revalidate();
        repaint();
    }

    public JackMasterControl getPreviewMasterControl() {
        return previewMasterControl;
    }

    public JackEditTree getPreviewTree() {
        return previewTree;
    }
}
