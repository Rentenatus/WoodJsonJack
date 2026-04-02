/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.settings;

import de.jare.tree.settings.theme.Theme;
import de.jare.tree.settings.theme.ThemeSuite;
import javax.swing.BorderFactory;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

public class ThemesPreferencesPane extends JPanel {

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

    private void setupListeners() {
        themesPreferencesList.setThemeListener(selectedTheme -> {
            if (selectedTheme != null) {
                themesModel.setActiveTheme(selectedTheme);
                loadThemeDetails(themesModel.getWorkTheme());
            }
        });

        themesPreferencesColorTable.setColorTableListener(colorScheme -> {
            de.jare.tree.settings.theme.Theme workTheme = themesModel.getWorkTheme();
            if (workTheme != null) {
                workTheme.setColors(colorScheme);
            }
        });

        themesPreferencesFontTable.setFontTableListener(fontSettings -> {
            de.jare.tree.settings.theme.Theme workTheme = themesModel.getWorkTheme();
            if (workTheme != null) {
                workTheme.setFonts(fontSettings);
            }
        });

        // Register change listener for preview updates
        themesPreferencesColorTable.setChangeListener(themesPreferencesPreviewPane);
        themesPreferencesFontTable.setChangeListener(themesPreferencesPreviewPane);

        themesPreferencesPreviewPane.setPreviewPaneListener(isDark -> {
            de.jare.tree.settings.theme.Theme workTheme = themesModel.getWorkTheme();
            if (workTheme != null) {
                workTheme.getColors().setDark(isDark);
            }
        });
    }

    private void loadThemeDetails(de.jare.tree.settings.theme.Theme theme) {
        if (theme == null) {
            return;
        }
        themesPreferencesColorTable.updateColorsTable(theme.getColors());
        themesPreferencesFontTable.updateFontsTable(theme.getFonts());
        themesPreferencesPreviewPane.loadThemeDetails(theme);
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

        JPanel detailsContent = new JPanel(new BorderLayout(8, 8));

        JPanel tablesPane = new JPanel(new GridLayout(1, 2, 8, 8));
        tablesPane.add(themesPreferencesColorTable);
        tablesPane.add(themesPreferencesFontTable);

        JPanel previewWrapperPane = new JPanel(new BorderLayout(8, 8));
        previewWrapperPane.add(themesPreferencesPreviewPane, BorderLayout.CENTER);
        previewWrapperPane.setPreferredSize(new Dimension(10, 260));

        detailsContent.add(tablesPane, BorderLayout.CENTER);
        detailsContent.add(previewWrapperPane, BorderLayout.SOUTH);

        themeDetailsButtonPane.add(invertColorsButton);
        themeDetailsButtonPane.add(applyButton);
        themeDetailsButtonPane.add(restoreButton);

        themeDetailsPane.add(detailsContent, BorderLayout.CENTER);
        themeDetailsPane.add(themeDetailsButtonPane, BorderLayout.SOUTH);
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
        de.jare.tree.settings.theme.Theme workTheme = themesModel.getWorkTheme();
        if (workTheme == null) {
            return;
        }
        workTheme.getColors().invert();
        themesPreferencesColorTable.updateColorsTable(workTheme.getColors());
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
        return themesPreferencesPreviewPane.getPreviewPane();
    }

    public JTextField getThemeIdField() {
        return themesPreferencesPreviewPane.getThemeIdField();
    }

    public JComboBox<String> getThemeVariantComboBox() {
        return themesPreferencesPreviewPane.getThemeVariantComboBox();
    }

    public ThemesModel getThemesModel() {
        return themesModel;
    }
}
