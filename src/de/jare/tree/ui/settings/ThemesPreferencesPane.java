/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.settings;

import de.jare.tree.settings.theme.Theme;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;

public class ThemesPreferencesPane extends JPanel implements ChangeListener {

    private final ThemesModel themesModel;

    private final ThemesPreferencesList themesPreferencesList;
    private final ThemesPreferencesColorTable themesPreferencesColorTable;
    private final ThemesPreferencesFontTable themesPreferencesFontTable;
    private final ThemesPreferencesPreviewPane themesPreferencesPreviewPane;

    private final JPanel themeDetailsPane;
    private final JPanel themeDetailsButtonPane;

    private final JButton importThemesButton;
    private final JButton exportThemesButton;
    private final JButton applyThemesButton;
    private final JButton newThemeButton;
    private final JButton duplicateThemeButton;

    private final JButton applyButton;
    private final JButton restoreButton;
    private final JButton invertColorsButton;

    private JTextField themeIdField;
    private JTextField themeNameField;

    private Theme currentTheme;

    public ThemesPreferencesPane(ThemesModel themesModel) {
        super(new BorderLayout(8, 8));

        this.themesModel = themesModel;

        this.themesPreferencesList = new ThemesPreferencesList(themesModel);
        this.themesPreferencesColorTable = new ThemesPreferencesColorTable();
        this.themesPreferencesFontTable = new ThemesPreferencesFontTable();
        this.themesPreferencesPreviewPane = new ThemesPreferencesPreviewPane();

        this.themeDetailsPane = new JPanel(new BorderLayout(8, 8));
        this.themeDetailsButtonPane = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        this.importThemesButton = new JButton("Import");
        this.exportThemesButton = new JButton("Export");
        this.applyThemesButton = new JButton("Apply");
        this.newThemeButton = new JButton("New Theme");
        this.duplicateThemeButton = new JButton("Duplicate");

        this.applyButton = new JButton("Apply");
        this.restoreButton = new JButton("Restore");
        this.invertColorsButton = new JButton("Invert colors");

        buildUi();
        setupListeners();
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (currentTheme != null) {
            themesPreferencesPreviewPane.loadThemeDetails(currentTheme);
        }
    }

    private void setupListeners() {
        themesPreferencesList.setThemeListener(selectedTheme -> {
            if (selectedTheme != null) {
                themesModel.setActiveTheme(selectedTheme);
                loadThemeDetails(themesModel.getWorkTheme());
            }
        });

        themesPreferencesColorTable.setColorTableListener(colorScheme -> {
            Theme workTheme = themesModel.getWorkTheme();
            if (workTheme != null) {
                workTheme.setColors(colorScheme);
                currentTheme = workTheme;
            }
        });

        themesPreferencesFontTable.setFontTableListener(fontSettings -> {
            Theme workTheme = themesModel.getWorkTheme();
            if (workTheme != null) {
                workTheme.setFonts(fontSettings);
                currentTheme = workTheme;
            }
        });

        themesPreferencesColorTable.setChangeListener(themesPreferencesPreviewPane);
        themesPreferencesFontTable.setChangeListener(themesPreferencesPreviewPane);
    }

    private void buildUi() {
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel titleLabel = new JLabel("Themes");
        add(titleLabel, BorderLayout.NORTH);

        buildThemeDetailsPane();

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                themesPreferencesList,
                themeDetailsPane
        );
        splitPane.setResizeWeight(0.25);

        add(splitPane, BorderLayout.CENTER);
        add(buildOuterButtonPane(), BorderLayout.SOUTH);
    }

    private void buildThemeDetailsPane() {
        themeDetailsPane.setBorder(BorderFactory.createTitledBorder("Theme Details"));

        JMenuItem groupColorsItem = new JMenuItem("Invert Colors");
        groupColorsItem.addActionListener(e -> invertColors());
        themesPreferencesColorTable.addPopupMenuItem(groupColorsItem);

        JPanel detailsContent = new JPanel(new GridLayout(2, 2, 8, 8));
        detailsContent.add(themesPreferencesColorTable);
        detailsContent.add(themesPreferencesFontTable);
        detailsContent.add(buildFormPanel());
        detailsContent.add(themesPreferencesPreviewPane);

        themeDetailsButtonPane.add(invertColorsButton);
        themeDetailsButtonPane.add(applyButton);
        themeDetailsButtonPane.add(restoreButton);

        themeDetailsPane.add(detailsContent, BorderLayout.CENTER);
        themeDetailsPane.add(themeDetailsButtonPane, BorderLayout.SOUTH);
    }

    private JPanel buildFormPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Form"));

        panel.add(new JLabel("Theme ID"));
        themeIdField = new JTextField("default-light");
        panel.add(themeIdField);

        panel.add(new JLabel("Theme Name"));
        themeNameField = new JTextField("Default Light");
        panel.add(themeNameField);

        return panel;
    }

    private JPanel buildOuterButtonPane() {
        JPanel buttonPane = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPane.add(importThemesButton);
        buttonPane.add(exportThemesButton);
        buttonPane.add(applyThemesButton);
        buttonPane.add(duplicateThemeButton);
        buttonPane.add(newThemeButton);

        duplicateThemeButton.addActionListener(e -> duplicateCurrentTheme());
        applyButton.addActionListener(e -> apply());
        restoreButton.addActionListener(e -> restore());
        invertColorsButton.addActionListener(e -> invertColors());

        return buttonPane;
    }

    private void loadThemeDetails(Theme theme) {
        if (theme == null) {
            return;
        }

        currentTheme = theme;
        themesPreferencesColorTable.updateColorsTable(theme.getColors());
        themesPreferencesFontTable.updateFontsTable(theme.getFonts());

        if (themeIdField != null) {
            themeIdField.setText(theme.getThemeId());
        }
        if (themeNameField != null) {
            themeNameField.setText(theme.getThemeName());
        }

        themesPreferencesPreviewPane.loadThemeDetails(theme);
    }

    private void duplicateCurrentTheme() {
        themesPreferencesList.duplicateCurrentTheme();
    }

    private void apply() {
        themesModel.accept();
    }

    private void restore() {
        themesModel.restore();
        loadThemeDetails(themesModel.getWorkTheme());
    }

    private void invertColors() {
        Theme workTheme = themesModel.getWorkTheme();
        if (workTheme == null) {
            return;
        }

        workTheme.getColors().invert();
        themesPreferencesColorTable.updateColorsTable(workTheme.getColors());
        currentTheme = workTheme;
        themesPreferencesPreviewPane.loadThemeDetails(workTheme);
    }

    public void setThemes(Iterable<Theme> themes) {
        themesPreferencesList.setThemes(themes);
    }

    public DefaultListModel<Theme> getThemesListModel() {
        return themesPreferencesList.getThemesListModel();
    }

    public JList<Theme> getThemesList() {
        return themesPreferencesList.getThemesList();
    }

    public JPanel getThemeDetailsPane() {
        return themeDetailsPane;
    }

    public JButton getImportThemesButton() {
        return importThemesButton;
    }

    public JButton getExportThemesButton() {
        return exportThemesButton;
    }

    public JButton getApplyThemesButton() {
        return applyThemesButton;
    }

    public JButton getNewThemeButton() {
        return newThemeButton;
    }

    public JButton getDuplicateThemeButton() {
        return duplicateThemeButton;
    }

    public JButton getApplyButton() {
        return applyButton;
    }

    public JButton getRestoreButton() {
        return restoreButton;
    }

    public JButton getInvertColorsButton() {
        return invertColorsButton;
    }

    public JTable getColorsTable() {
        return themesPreferencesColorTable.getColorsTable();
    }

    public DefaultTableModel getColorsTableModel() {
        return themesPreferencesColorTable.getColorsTableModel();
    }

    public JTable getFontsTable() {
        return themesPreferencesFontTable.getFontsTable();
    }

    public DefaultTableModel getFontsTableModel() {
        return themesPreferencesFontTable.getFontsTableModel();
    }

    public JPanel getPreviewPane() {
        return themesPreferencesPreviewPane;
    }

    public JTextField getThemeIdField() {
        return themeIdField;
    }

    public JTextField getThemeNameField() {
        return themeNameField;
    }

    public ThemesModel getThemesModel() {
        return themesModel;
    }
}
