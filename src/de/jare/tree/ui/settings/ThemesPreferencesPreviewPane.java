package de.jare.tree.ui.settings;

import de.jare.tree.settings.theme.Theme;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;

public class ThemesPreferencesPreviewPane extends JPanel implements ChangeListener {

    private final JPanel previewPane;
    private final JPanel previewContent;
    private final PreviewPanel samplePanel;

    private JTextField themeIdField;
    private JTextField themeNameField;
    private JComboBox<String> themeVariantComboBox1, themeVariantComboBox2;

    private Theme currentTheme;
    private PreviewPaneListener previewPaneListener;
    
    private UIDefaults previewDefaults;
    
    @Override
    public void stateChanged(ChangeEvent e) {
        if (currentTheme != null) {
            updatePreviewDefaults(currentTheme);
        }
    }

    public interface PreviewPaneListener {
        void onThemeVariantChanged(boolean isDark);
    }

    public ThemesPreferencesPreviewPane() {
        super(new BorderLayout(8, 8));
        this.previewPane = new JPanel(new BorderLayout(8, 8));
        this.previewContent = new JPanel(new BorderLayout(8, 8));
        this.previewDefaults = new UIDefaults();
        this.samplePanel = new PreviewPanel();
        
        buildUi();
    }
    
    private class PreviewPanel extends JPanel {
        public PreviewPanel() {
            super(new GridLayout(1, 2, 8, 8));
            setBorder(BorderFactory.createTitledBorder("Sample Elements"));
        }
        
        @Override
        public void addNotify() {
            super.addNotify();
            updateUIFromDefaults();
        }
        
        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            updateUIFromDefaults();
        }
        
        private void updateUIFromDefaults() {
            // Apply preview defaults to all children
            for (Component comp : getComponents()) {
                if (comp instanceof JComponent jComp) {
                    applyDefaultsToComponent(jComp);
                }
            }
        }
        
        private void applyDefaultsToComponent(JComponent comp) {
            // Apply background
            Object bg = previewDefaults.get("Panel.background");
            if (bg instanceof Color) {
                comp.setBackground((Color) bg);
            }
            
            // Apply foreground
            Object fg = previewDefaults.get("Panel.foreground");
            if (fg instanceof Color) {
                comp.setForeground((Color) fg);
            }
            
            // Apply font
            Object font = previewDefaults.get("Panel.font");
            if (font instanceof Font) {
                comp.setFont((Font) font);
            }
            
            // Recursively apply to children
            if (comp instanceof JScrollPane scrollPane) {
                JViewport viewport = scrollPane.getViewport();
                if (viewport != null) {
                    Component view = viewport.getView();
                    if (view instanceof JComponent jView) {
                        applyDefaultsToComponent(jView);
                    }
                }
            }
            
            if (comp instanceof Container container) {
                for (Component child : container.getComponents()) {
                    if (child instanceof JComponent jChild) {
                        applyDefaultsToComponent(jChild);
                    }
                }
            }
        }
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
        
        // Update preview defaults from theme
        updatePreviewDefaults(theme);
    }
    
    private void updatePreviewDefaults(Theme theme) {
        previewDefaults.clear();
        
        // Copy all colors from theme to preview defaults
//        for (Map.Entry<Object, Object> entry : UIManager.getDefaults().entrySet()) {
//            String key = String.valueOf(entry.getKey());
//            Object value = entry.getValue();
//            
//            if (value instanceof Color) {
//                Color color = (Color) value;
//                previewDefaults.put(key, color);
//            }
//        }
        
        // Apply theme colors to preview defaults
        for (Map.Entry<String, Color> entry : theme.getColors().getColorMap().entrySet()) {
            previewDefaults.put(entry.getKey(), entry.getValue());
        }
        
        // Apply theme fonts to preview defaults
        for (Map.Entry<String, Font> entry : theme.getFonts().getFontMap().entrySet()) {
            previewDefaults.put(entry.getKey(), entry.getValue());
        }
        
        // Force UI update
        samplePanel.revalidate();
        samplePanel.repaint();
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