/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.tree.ui.settings;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.TitledBorder;

public class WoodPreferencesPane extends JPanel {

    private JComboBox<ThemeEntry> lightThemeCombo;
    private JComboBox<ThemeEntry> darkThemeCombo;

    public WoodPreferencesPane() {
        super(new BorderLayout());
        initializeUI();
    }

    private void initializeUI() {
        ThemeEntry[] lightThemes = {
            new ThemeEntry("FlatLaf Light", FlatLightLaf.class),
            new ThemeEntry("FlatLaf IntelliJ", FlatIntelliJLaf.class)
        };

        ThemeEntry[] darkThemes = {
            new ThemeEntry("FlatLaf Dark", FlatDarkLaf.class),
            new ThemeEntry("FlatLaf Darcula", FlatDarculaLaf.class)
        };

        lightThemeCombo = new JComboBox<>(lightThemes);
        darkThemeCombo = new JComboBox<>(darkThemes);

        JPanel gridPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        TitledBorder titledBorder = BorderFactory.createTitledBorder("Color Schemas");
        gridPanel.setBorder(titledBorder);

        gridPanel.add(new JLabel("Light:"));
        gridPanel.add(lightThemeCombo);
        gridPanel.add(new JLabel("Dark:"));
        gridPanel.add(darkThemeCombo);

        add(gridPanel, BorderLayout.NORTH);
    }

    public JComboBox<ThemeEntry> getLightThemeCombo() {
        return lightThemeCombo;
    }

    public JComboBox<ThemeEntry> getDarkThemeCombo() {
        return darkThemeCombo;
    }

    public static class ThemeEntry {

        private final String displayName;
        private final Class<? extends LookAndFeel> lafClass;

        public ThemeEntry(String displayName, Class<? extends LookAndFeel> lafClass) {
            this.displayName = displayName;
            this.lafClass = lafClass;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Class<? extends LookAndFeel> getLafClass() {
            return lafClass;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
