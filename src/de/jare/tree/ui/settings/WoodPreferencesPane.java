/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.tree.ui.settings;

import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.JsonWriteException;
import de.jare.tree.settings.SettingsService;
import de.jare.tree.settings.WoodSettings;
import de.jare.tree.settings.theme.LafCatalog;
import de.jare.tree.settings.theme.Theme;
import de.jare.tree.settings.theme.ThemeSuite;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.ListCellRenderer;
import javax.swing.border.TitledBorder;

/**
 * First preferences tab. Holds the color schema selection of both color modes (light and dark
 * FlatLaf look and feels), the mode switch and the active theme. Every change is applied
 * immediately and persisted.
 *
 * @author Janusch Rentenatus
 */
public class WoodPreferencesPane extends JPanel {

    private final WoodSettings settings;
    private final ThemeSuite themeSuite;
    private final SettingsService settingsService;

    private final JComboBox<LafCatalog.LafEntry> lightThemeCombo;
    private final JComboBox<LafCatalog.LafEntry> darkThemeCombo;
    private final JRadioButton lightModeButton;
    private final JRadioButton darkModeButton;
    private final JComboBox<Theme> themeCombo;

    public WoodPreferencesPane(WoodSettings settings, ThemeSuite themeSuite,
            SettingsService settingsService) {
        super(new BorderLayout());
        this.settings = settings;
        this.themeSuite = themeSuite;
        this.settingsService = settingsService;

        lightThemeCombo = new JComboBox<>(LafCatalog.getLightSchemas());
        selectLaf(lightThemeCombo, settings.getLightLaf());

        darkThemeCombo = new JComboBox<>(LafCatalog.getDarkSchemas());
        selectLaf(darkThemeCombo, settings.getDarkLaf());

        lightModeButton = new JRadioButton("Light");
        darkModeButton = new JRadioButton("Dark");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(lightModeButton);
        modeGroup.add(darkModeButton);
        lightModeButton.setSelected(!settings.isDarkMode());
        darkModeButton.setSelected(settings.isDarkMode());

        themeCombo = new JComboBox<>();
        for (Theme theme : themeSuite.getAvailableThemes()) {
            themeCombo.addItem(theme);
        }
        themeCombo.setRenderer(new ThemeComboRenderer());
        selectTheme(settings.getThemeId());

        initializeUI();
        setupListeners();
    }

    private void initializeUI() {
        JPanel schemaPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        TitledBorder schemaBorder = BorderFactory.createTitledBorder("Color Schema");
        schemaPanel.setBorder(schemaBorder);

        schemaPanel.add(new JLabel("Light:"));
        schemaPanel.add(lightThemeCombo);
        schemaPanel.add(new JLabel("Dark:"));
        schemaPanel.add(darkThemeCombo);
        schemaPanel.add(new JLabel("Mode:"));
        JPanel modePanel = new JPanel(new GridLayout(1, 2, 5, 5));
        modePanel.add(lightModeButton);
        modePanel.add(darkModeButton);
        schemaPanel.add(modePanel);

        JPanel themePanel = new JPanel(new GridLayout(1, 2, 5, 5));
        themePanel.setBorder(BorderFactory.createTitledBorder("Theme"));

        themePanel.add(new JLabel("Active theme:"));
        themePanel.add(themeCombo);

        JPanel northPanel = new JPanel(new BorderLayout(5, 5));
        northPanel.add(schemaPanel, BorderLayout.NORTH);
        northPanel.add(themePanel, BorderLayout.CENTER);

        add(northPanel, BorderLayout.NORTH);
    }

    private void setupListeners() {
        lightThemeCombo.addActionListener(e -> {
            LafCatalog.LafEntry entry = (LafCatalog.LafEntry) lightThemeCombo.getSelectedItem();
            if (entry == null) {
                return;
            }
            settings.setLightLaf(entry.getClassName());
            if (!settings.isDarkMode()) {
                LafCatalog.apply(settings);
            }
            persist();
        });

        darkThemeCombo.addActionListener(e -> {
            LafCatalog.LafEntry entry = (LafCatalog.LafEntry) darkThemeCombo.getSelectedItem();
            if (entry == null) {
                return;
            }
            settings.setDarkLaf(entry.getClassName());
            if (settings.isDarkMode()) {
                LafCatalog.apply(settings);
            }
            persist();
        });

        lightModeButton.addActionListener(e -> switchMode(false));
        darkModeButton.addActionListener(e -> switchMode(true));

        themeCombo.addActionListener(e -> {
            Theme theme = (Theme) themeCombo.getSelectedItem();
            if (theme == null) {
                return;
            }
            settings.setThemeId(theme.getThemeId());
            settings.useThemeSuite(themeSuite);
            LafCatalog.refreshAllUi();
            persist();
        });
    }

    private void switchMode(boolean darkMode) {
        if (settings.isDarkMode() == darkMode) {
            return;
        }
        settings.setDarkMode(darkMode);
        LafCatalog.apply(settings);
        persist();
    }

    private void selectLaf(JComboBox<LafCatalog.LafEntry> combo, String className) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).getClassName().equals(className)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private void selectTheme(String themeId) {
        for (int i = 0; i < themeCombo.getItemCount(); i++) {
            if (themeCombo.getItemAt(i).getThemeId().equals(themeId)) {
                themeCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    private void persist() {
        try {
            settingsService.saveWoodSettings(settings);
        } catch (IOException | JsonParseException | JsonWriteException ex) {
            Logger.getGlobal().log(Level.SEVERE, "Could not save wood settings: ", ex);
        }
    }

    public JComboBox<LafCatalog.LafEntry> getLightThemeCombo() {
        return lightThemeCombo;
    }

    public JComboBox<LafCatalog.LafEntry> getDarkThemeCombo() {
        return darkThemeCombo;
    }

    public JRadioButton getLightModeButton() {
        return lightModeButton;
    }

    public JRadioButton getDarkModeButton() {
        return darkModeButton;
    }

    public JComboBox<Theme> getThemeCombo() {
        return themeCombo;
    }

    private static class ThemeComboRenderer extends JLabel implements ListCellRenderer<Theme> {

        @Override
        public Component getListCellRendererComponent(JList<? extends Theme> list, Theme value,
                int index, boolean isSelected, boolean cellHasFocus) {
            setText(value == null ? "" : value.getThemeName());
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
                setOpaque(true);
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
                setOpaque(false);
            }
            return this;
        }
    }
}
