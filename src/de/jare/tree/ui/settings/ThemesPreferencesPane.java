package de.jare.tree.ui.settings;

import de.jare.tree.settings.WoodSettings;
import de.jare.tree.settings.theme.ColorScheme;
import de.jare.tree.settings.theme.FontSettings;
import de.jare.tree.settings.theme.Theme;
import de.jare.tree.settings.theme.ThemeSuite;
import java.awt.BorderLayout;
import java.awt.Color;
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
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;

public class ThemesPreferencesPane extends JPanel {

    private final ThemeSuite themeSuite;

    private final DefaultListModel<Theme> themesListModel;
    private final JList<Theme> themesList;

    private final JPanel themeDetailsPane;
    private final JPanel themeDetailsButtonPane;

    private final JButton importThemesButton;
    private final JButton exportThemesButton;
    private final JButton applyThemesButton;
    private final JButton newThemeButton;
    private final JButton duplicateThemeButton;

    private final JButton restoreDefaultsButton;
    private final JButton invertColorsButton;

    private final JTable colorsTable;
    private final DefaultTableModel colorsTableModel;

    private final JTable fontsTable;
    private final DefaultTableModel fontsTableModel;

    private final JPanel previewPane;

    private JTextField themeIdField;
    private JComboBox<String> themeVariantComboBox1, themeVariantComboBox2;
    

    private de.jare.tree.settings.theme.Theme activeTheme;
    private de.jare.tree.settings.theme.Theme workTheme;

    public ThemesPreferencesPane(ThemeSuite themeSuite) {
        super(new BorderLayout(8, 8));

        this.themeSuite = themeSuite;

        this.themesListModel = new DefaultListModel<>();
        this.themesList = new JList<>(themesListModel);

        this.themeDetailsPane = new JPanel(new BorderLayout(8, 8));
        this.themeDetailsButtonPane = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        this.importThemesButton = new JButton("Import");
        this.exportThemesButton = new JButton("Export");
        this.applyThemesButton = new JButton("Apply");
        this.newThemeButton = new JButton("New Theme");
        this.duplicateThemeButton = new JButton("Duplicate");

        this.restoreDefaultsButton = new JButton("Restore defaults");
        this.invertColorsButton = new JButton("Invert colors");

        this.colorsTableModel = new DefaultTableModel(new Object[]{"Key", "Value"}, 0);
        this.colorsTable = new JTable(colorsTableModel);

        this.fontsTableModel = new DefaultTableModel(new Object[]{"Key", "Value"}, 0);
        this.fontsTable = new JTable(fontsTableModel);

        this.previewPane = new JPanel();

        buildUi();
        updateThemesList();
    }

    private void updateThemesList() {
        if (themeSuite == null) {
            return;
        }

        themesListModel.clear();
        for (de.jare.tree.settings.theme.Theme theme : themeSuite.getAvailableThemes()) {
            themesListModel.addElement(theme);
        }

        if (!themesListModel.isEmpty()) {
            themesList.setSelectedIndex(0);
        }
    }

    private void buildUi() {
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel titleLabel = new JLabel("Themes");
        add(titleLabel, BorderLayout.NORTH);

        buildThemesList();
        buildThemeDetailsPane();

        JScrollPane themesListScrollPane = new JScrollPane(themesList);
        themesListScrollPane.setBorder(BorderFactory.createTitledBorder("Available Themes"));
        themesListScrollPane.setPreferredSize(new Dimension(220, 300));

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                themesListScrollPane,
                themeDetailsPane
        );
        splitPane.setResizeWeight(0.25);

        add(splitPane, BorderLayout.CENTER);
        add(buildOuterButtonPane(), BorderLayout.SOUTH);
    }

    private void buildThemesList() {
        themesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        themesList.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof de.jare.tree.settings.theme.Theme) {
                    de.jare.tree.settings.theme.Theme theme = (de.jare.tree.settings.theme.Theme) value;
                    setText(theme.getThemeName());
                }
                return this;
            }
        });

        themesList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    Theme selectedTheme = (Theme) themesList.getSelectedValue();
                    if (selectedTheme != null) {
                        activeTheme = selectedTheme;
                        workTheme = selectedTheme.deepCopy();
                        loadThemeDetails(workTheme);
                    }
                }
            }
        });
    }

    private void loadThemeDetails(de.jare.tree.settings.theme.Theme theme) {
        if (theme == null) {
            return;
        }

        updateColorsTable(theme.getColors());
        updateFontsTable(theme.getFonts());
    }

    private void updateColorsTable(ColorScheme colorScheme) {
        colorsTableModel.setRowCount(0);
        colorScheme.forEachColor((key, value) -> {
            colorsTableModel.addRow(new Object[]{key, ColorScheme.colorToHex(value)});
        });
    }

    private void updateFontsTable(FontSettings fontSettings) {
        fontsTableModel.setRowCount(0);
        fontSettings.forEachFont((key, value) -> {
            fontsTableModel.addRow(new Object[]{key, value.getFontName()});
        });
    }

    private void buildThemeDetailsPane() {
        themeDetailsPane.setBorder(BorderFactory.createTitledBorder("Theme Details"));

        JPanel detailsContent = new JPanel(new BorderLayout(8, 8));

        JPanel tablesPane = buildTablesPane();
        JPanel previewWrapperPane = buildPreviewWrapperPane();
        previewWrapperPane.setPreferredSize(new Dimension(10, 260));

        detailsContent.add(tablesPane, BorderLayout.CENTER);
        detailsContent.add(previewWrapperPane, BorderLayout.SOUTH);

        themeDetailsButtonPane.add(invertColorsButton);
        themeDetailsButtonPane.add(restoreDefaultsButton);

        themeDetailsPane.add(detailsContent, BorderLayout.CENTER);
        themeDetailsPane.add(themeDetailsButtonPane, BorderLayout.SOUTH);
    }

    private JPanel buildTablesPane() {
        JPanel tablesPane = new JPanel(new GridLayout(1, 2, 8, 8));

        JScrollPane colorsScrollPane = new JScrollPane(colorsTable);
        colorsScrollPane.setBorder(BorderFactory.createTitledBorder("Colors"));
        colorsScrollPane.setMinimumSize(new Dimension(180, 120));

        JScrollPane fontsScrollPane = new JScrollPane(fontsTable);
        fontsScrollPane.setBorder(BorderFactory.createTitledBorder("Fonts"));
        fontsScrollPane.setMinimumSize(new Dimension(180, 120));

        tablesPane.add(colorsScrollPane);
        tablesPane.add(fontsScrollPane);
        return tablesPane;
    }

    private JPanel buildPreviewWrapperPane() {
        previewPane.setLayout(new BorderLayout(8, 8));
        previewPane.setBorder(BorderFactory.createTitledBorder("Application Preview"));
        previewPane.add(buildPreviewContent(), BorderLayout.CENTER);
        return previewPane;
    }

    private JPanel buildPreviewContent() {
        JPanel content = new JPanel(new BorderLayout(8, 8));

        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 8, 8));

        JPanel formPanel = new JPanel(new GridLayout(0, 1, 6, 6));
        formPanel.setBorder(BorderFactory.createTitledBorder("Form"));
        formPanel.add(new JLabel("Theme ID"));
        themeIdField = new JTextField("default-light");
        formPanel.add(themeIdField);

        formPanel.add(new JLabel("Project name"));
        formPanel.add(new JTextField("Wood JSON Jack"));

        formPanel.add(new JLabel("Theme variant"));
        themeVariantComboBox2 = new JComboBox<>(new String[]{"Light", "Dark"});
        formPanel.add(themeVariantComboBox2);

        JPanel samplePanel = new JPanel(new GridLayout(1, 2, 8, 8));
        samplePanel.setBorder(BorderFactory.createTitledBorder("Sample Elements"));

        JPanel structurePanel = new JPanel(new GridLayout(2, 1, 6, 6));

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("project");
        DefaultMutableTreeNode src = new DefaultMutableTreeNode("src");
        src.add(new DefaultMutableTreeNode("Main.java"));
        src.add(new DefaultMutableTreeNode("ThemeService.java"));
        DefaultMutableTreeNode data = new DefaultMutableTreeNode("data");
        data.add(new DefaultMutableTreeNode("config.json"));
        root.add(src);
        root.add(data);

        JTree sampleTree = new JTree(root);
        JScrollPane treeScrollPane = new JScrollPane(sampleTree);
        treeScrollPane.setBorder(BorderFactory.createTitledBorder("Tree"));

        DefaultTableModel sampleTableModel = new DefaultTableModel(
                new Object[]{"Name", "Type"},
                0
        );
        sampleTableModel.addRow(new Object[]{"Main.java", "Java"});
        sampleTableModel.addRow(new Object[]{"config.json", "JSON"});
        sampleTableModel.addRow(new Object[]{"theme-dark", "Theme"});

        JTable sampleTable = new JTable(sampleTableModel);
        JScrollPane sampleTableScrollPane = new JScrollPane(sampleTable);
        sampleTableScrollPane.setBorder(BorderFactory.createTitledBorder("Table"));

        structurePanel.add(treeScrollPane);
        structurePanel.add(sampleTableScrollPane);

        JPanel controlsPanel = new JPanel(new GridLayout(0, 1, 6, 6));

        JLabel infoLabel = new JLabel("Example label");
        infoLabel.setOpaque(true);
        infoLabel.setBackground(new Color(230, 230, 230));

        controlsPanel.add(infoLabel);
        controlsPanel.add(new JButton("Preview Button"));
        controlsPanel.add(new JTextField("Sample text field"));

        controlsPanel.add(new JLabel("Theme variant"));
        themeVariantComboBox1 = new JComboBox<>(new String[]{"Light", "Dark"});
        controlsPanel.add(themeVariantComboBox1);

        samplePanel.add(structurePanel);
        samplePanel.add(controlsPanel);

        centerPanel.add(formPanel);
        centerPanel.add(samplePanel);

        content.add(centerPanel, BorderLayout.CENTER);
        return content;
    }

    private JPanel buildOuterButtonPane() {
        JPanel buttonPane = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPane.add(importThemesButton);
        buttonPane.add(exportThemesButton);
        buttonPane.add(applyThemesButton);
        buttonPane.add(duplicateThemeButton);
        buttonPane.add(newThemeButton);

        duplicateThemeButton.addActionListener(e -> duplicateCurrentTheme());

        return buttonPane;
    }

    private void duplicateCurrentTheme() {
        if (themeSuite == null) {
            return;
        }

        de.jare.tree.settings.theme.Theme selectedTheme = (de.jare.tree.settings.theme.Theme) themesList.getSelectedValue();
        if (selectedTheme == null) {
            return;
        }

        String newThemeName = selectedTheme.getThemeName() + " (Copy)";
        de.jare.tree.settings.theme.Theme newTheme = new de.jare.tree.settings.theme.Theme(selectedTheme.getThemeId() + "-copy", newThemeName);
        newTheme.setColors(selectedTheme.getColors());
        newTheme.setFonts(selectedTheme.getFonts());

        themeSuite.getAvailableThemes().add(newTheme);
        themesListModel.addElement(newTheme);
    }

    private void fillDummyData() {
        themesListModel.addElement(new Theme());
        themesList.setSelectedIndex(0);

        ColorScheme dummyColorScheme = new ColorScheme();
        FontSettings dummyFontSettings = new FontSettings();

        dummyColorScheme.resetDefault();
        dummyFontSettings.resetDefault();

        dummyColorScheme.forEachColor((key, value) -> {
            colorsTableModel.addRow(new Object[]{key, ColorScheme.colorToHex(value)});
        });

        dummyFontSettings.forEachFont((key, value) -> {
            fontsTableModel.addRow(new Object[]{key, value.getFontName()});
        });
    }

    public void setThemes(Iterable<Theme> themeNames) {
        themesListModel.clear();
        if (themeNames == null) {
            return;
        }

        for (Theme themeName : themeNames) {
            themesListModel.addElement(themeName);
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

    public JButton getRestoreDefaultsButton() {
        return restoreDefaultsButton;
    }

    public JButton getInvertColorsButton() {
        return invertColorsButton;
    }

    public JTable getColorsTable() {
        return colorsTable;
    }

    public DefaultTableModel getColorsTableModel() {
        return colorsTableModel;
    }

    public JTable getFontsTable() {
        return fontsTable;
    }

    public DefaultTableModel getFontsTableModel() {
        return fontsTableModel;
    }

    public JPanel getPreviewPane() {
        return previewPane;
    }

    public JTextField getThemeIdField() {
        return themeIdField;
    }

    public JComboBox<String> getThemeVariantComboBox() {
        return themeVariantComboBox1;
    }
}
