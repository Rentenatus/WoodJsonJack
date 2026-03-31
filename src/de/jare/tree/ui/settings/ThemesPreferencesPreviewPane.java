package de.jare.tree.ui.settings;

import de.jare.tree.settings.theme.Theme;
import java.awt.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;

public class ThemesPreferencesPreviewPane extends JPanel {

    private final JPanel previewPane;
    private final JPanel previewContent;

    private JTextField themeIdField;
    private JTextField themeNameField;
    private JComboBox<String> themeVariantComboBox1, themeVariantComboBox2;

    private Theme currentTheme;
    private PreviewPaneListener previewPaneListener;

    public interface PreviewPaneListener {
        void onThemeVariantChanged(boolean isDark);
    }

    public ThemesPreferencesPreviewPane() {
        super(new BorderLayout(8, 8));
        this.previewPane = new JPanel(new BorderLayout(8, 8));
        this.previewContent = new JPanel(new BorderLayout(8, 8));
        
        buildUi();
    }

    private void buildUi() {
        previewPane.setBorder(BorderFactory.createTitledBorder("Application Preview"));
        previewPane.add(buildPreviewContent(), BorderLayout.CENTER);
        
        add(previewPane, BorderLayout.CENTER);
    }

    private JPanel buildPreviewContent() {
        JPanel content = new JPanel(new BorderLayout(8, 8));

        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 8, 8));

        JPanel formPanel = new JPanel(new GridLayout(0, 1, 6, 6));
        formPanel.setBorder(BorderFactory.createTitledBorder("Form"));
        formPanel.add(new JLabel("Theme ID"));
        themeIdField = new JTextField("default-light");
        formPanel.add(themeIdField);

        formPanel.add(new JLabel("Theme Name"));
        themeNameField = new JTextField("Default Light");
        formPanel.add(themeNameField);

        formPanel.add(new JLabel("Theme variant"));
        themeVariantComboBox2 = new JComboBox<>(new String[]{"Light", "Dark"});
        formPanel.add(themeVariantComboBox2);
        themeVariantComboBox2.addActionListener(e -> updateThemeVariantFromComboBox());

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
        themeVariantComboBox1.addActionListener(e -> updateThemeVariantFromComboBox());

        samplePanel.add(structurePanel);
        samplePanel.add(controlsPanel);

        centerPanel.add(formPanel);
        centerPanel.add(samplePanel);

        content.add(centerPanel, BorderLayout.CENTER);
        return content;
    }

    private void updateThemeVariantFromComboBox() {
        if (currentTheme == null) {
            return;
        }
        String selectedVariant = (String) themeVariantComboBox1.getSelectedItem();
        boolean isDark = "Dark".equals(selectedVariant);
        currentTheme.getColors().setDark(isDark);
        
        if (previewPaneListener != null) {
            previewPaneListener.onThemeVariantChanged(isDark);
        }
    }

    public void loadThemeDetails(Theme theme) {
        if (theme == null) {
            return;
        }
        this.currentTheme = theme;
        themeIdField.setText(theme.getThemeId());
        themeNameField.setText(theme.getThemeName());
        boolean isDark = theme.getColors().isDark();
        themeVariantComboBox1.setSelectedItem(isDark ? "Dark" : "Light");
        themeVariantComboBox2.setSelectedItem(isDark ? "Dark" : "Light");
    }

    public JPanel getPreviewPane() {
        return previewPane;
    }

    public JTextField getThemeIdField() {
        return themeIdField;
    }

    public JTextField getThemeNameField() {
        return themeNameField;
    }

    public JComboBox<String> getThemeVariantComboBox() {
        return themeVariantComboBox1;
    }

    public void setPreviewPaneListener(PreviewPaneListener listener) {
        this.previewPaneListener = listener;
    }
}