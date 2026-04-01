/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.settings;

import de.jare.tree.settings.theme.Theme;
import de.jare.tree.settings.theme.ThemeSuite;
import de.jare.tree.ui.swing.BorderFactoryColored;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;

public class ThemesPreferencesList extends JPanel {

    private final DefaultListModel<Theme> themesListModel;
    private final JList<Theme> themesList;

    private Theme selectedTheme;
    private ThemeListener themeListener;
    private ThemesModel themesModel;

    public interface ThemeListener {

        void onThemeSelected(Theme theme);
    }

    public ThemesPreferencesList(ThemesModel themesModel) {
        super(new BorderLayout(8, 8));
        this.themesModel = themesModel;
        this.themesListModel = new DefaultListModel<>();
        this.themesList = new JList<>(themesListModel);

        buildUi();
        updateThemesList();
    }

    private void buildUi() {
        themesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        themesList.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Theme) {
                    Theme theme = (Theme) value;
                    setText(theme.getThemeName());

                    // Create a box around the theme name
                    setBorder(BorderFactoryColored.createLineBorder("Panel.background"));

                    // If this is the active theme, use font color for the border
                    if (theme.equals(ThemesPreferencesList.this.selectedTheme)) {
                        setBorder(BorderFactoryColored.createLineBorder("Panel.foreground"));
                    }
                }
                return this;
            }
        });

        themesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    Theme selectedTheme = (Theme) themesList.getSelectedValue();
                    if (selectedTheme != null) {
                        ThemesPreferencesList.this.selectedTheme = selectedTheme;
                        if (themeListener != null) {
                            themeListener.onThemeSelected(selectedTheme);
                        }
                    }
                }
            }
        });

        themesList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && themesList.getSelectedValue() != null) {
                // Don't update selectedTheme on simple selection
                // Only double-click should set the active theme
            }
        });

        JScrollPane themesListScrollPane = new JScrollPane(themesList);
        themesListScrollPane.setBorder(BorderFactoryColored.createTitledBorder("Available Themes", "Panel.foreground", "Panel.font"));
        themesListScrollPane.setPreferredSize(new Dimension(220, 300));

        add(themesListScrollPane, BorderLayout.CENTER);
    }

    public void updateThemesList() {
        if (themesModel == null || themesModel.getThemeSuite() == null) {
            return;
        }

        themesListModel.clear();
        for (Theme theme : themesModel.getAllThemes()) {
            themesListModel.addElement(theme);
        }

        if (!themesListModel.isEmpty()) {
            themesList.setSelectedIndex(0);
        }
    }

    public void setThemes(Iterable<Theme> themes) {
        themesListModel.clear();
        if (themes == null) {
            return;
        }

        for (Theme theme : themes) {
            themesListModel.addElement(theme);
        }

        if (!themesListModel.isEmpty()) {
            themesList.setSelectedIndex(0);
        }
    }

    public DefaultListModel<Theme> getThemesListModel() {
        return themesListModel;
    }

    public JList<Theme> getThemesList() {
        return themesList;
    }

    public Theme getSelectedTheme() {
        return selectedTheme;
    }

    public void setThemeListener(ThemeListener listener) {
        this.themeListener = listener;
    }

    public void duplicateCurrentTheme() {
        if (themesModel == null || themesModel.getThemeSuite() == null) {
            return;
        }

        Theme selectedTheme = (Theme) themesList.getSelectedValue();
        if (selectedTheme == null) {
            return;
        }

        String newThemeName = selectedTheme.getThemeName() + " (Copy)";
        Theme newTheme = new Theme(selectedTheme.getThemeId() + "-copy", newThemeName);
        newTheme.setColors(selectedTheme.getColors());
        newTheme.setFonts(selectedTheme.getFonts());

        themesModel.getThemeSuite().getAvailableThemes().add(newTheme);
        themesListModel.addElement(newTheme);
    }
}
