/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.settings;

import de.jare.tree.control.MasterControl;
import de.jare.tree.settings.theme.ColorScheme;
import de.jare.tree.settings.theme.Theme;
import de.jare.tree.ui.WoodEditTree;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.Map;

public class ThemesPreferencesPreviewPane extends JPanel implements ChangeListener {

    private final MasterControl previewMasterControl;
    private final WoodEditTree previewTree;

    private Theme currentTheme;

    public ThemesPreferencesPreviewPane() {
        super(new BorderLayout(8, 8));

        this.previewMasterControl = new MasterControl();
        this.previewTree = new WoodEditTree(previewMasterControl, "woodedit", "project", "assets", "scene");

        setBorder(BorderFactory.createTitledBorder("Preview"));

        previewMasterControl.setActiveEditor(previewTree,this);
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
        for (int row = 0; row < previewTree.getRowCount(); row++) {
            previewTree.expandRow(row);
        }
    }

    private void updatePreviewDefaults(Theme theme) {
        if (theme == null) {
            return;
        }

        ColorScheme detailedScheme = theme.getColors().getDetailedScheme();

        for (Map.Entry<String, Color> entry : detailedScheme.getColorMap().entrySet()) {
            Color color = entry.getValue();
            if (color != null) {
                //UIManager.put(entry.getKey(), new ColorUIResource(color));
            }
        }

        for (Map.Entry<String, Font> entry : theme.getFonts().getFontMap().entrySet()) {
            Font font = entry.getValue();
            if (font != null) {
                //UIManager.put(entry.getKey(), new FontUIResource(font));
            }
        }

        SwingUtilities.updateComponentTreeUI(this);
        revalidate();
        repaint();
    }

    public MasterControl getPreviewMasterControl() {
        return previewMasterControl;
    }

    public WoodEditTree getPreviewTree() {
        return previewTree;
    }
}