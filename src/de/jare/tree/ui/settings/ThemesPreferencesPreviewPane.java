/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.settings;

import de.jare.tree.settings.theme.ColorScheme;
import de.jare.tree.settings.theme.Theme;
import javax.swing.BorderFactory;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.TitledBorder;
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
        }

        private void applyDefaultsToComponent(JComponent comp) {
            // Get component-specific keys
            String compType = getComponentTypeName(comp);

            // Apply background
            Object bg = previewDefaults.get(compType + ".background");
            if (bg == null) {
                bg = previewDefaults.get("Panel.background");
            }
            if (bg instanceof Color) {
                comp.setBackground((Color) bg);
            }

            // Apply foreground
            Object fg = previewDefaults.get(compType + ".foreground");
            if (fg == null) {
                fg = previewDefaults.get("Panel.foreground");
            }
            if (fg instanceof Color) {
                comp.setForeground((Color) fg);
            }

            // Apply font
            Object font = previewDefaults.get(compType + ".font");
            if (font == null) {
                font = previewDefaults.get("Panel.font");
            }
            if (font instanceof Font) {
                comp.setFont((Font) font);
            }

            // Apply selection colors for editable components
            if (comp instanceof JTextField textField) {
                Object selBg = previewDefaults.get("TextField.selectionBackground");
                Object selFg = previewDefaults.get("TextField.selectionForeground");
                if (selBg instanceof Color) {
                    textField.setSelectionColor((Color) selBg);
                }
                if (selFg instanceof Color) {
                    textField.setSelectedTextColor((Color) selFg);
                }
            }

            // Special handling for JScrollPane - apply to scrollbars
            if (comp instanceof JScrollPane scrollPane) {
                JScrollBar vbar = scrollPane.getVerticalScrollBar();
                JScrollBar hbar = scrollPane.getHorizontalScrollBar();

                if (vbar != null) {
                    applyDefaultsToComponent(vbar);
                }
                if (hbar != null) {
                    applyDefaultsToComponent(hbar);
                }

                // Apply to viewport
                JViewport viewport = scrollPane.getViewport();
                if (viewport != null) {
                    applyDefaultsToComponent(viewport);
                    Component view = viewport.getView();
                    if (view instanceof JComponent jView) {
                        applyDefaultsToComponent(jView);
                    }
                }
            }

            // Special handling for JTable - apply to header
            if (comp instanceof JTable table) {
                javax.swing.table.JTableHeader header = table.getTableHeader();
                if (header != null) {
                    applyDefaultsToComponent(header);
                }
            }

            // Recursively apply to children
            if (comp instanceof Container container) {
                for (Component child : container.getComponents()) {
                    if (child instanceof JComponent jChild) {
                        applyDefaultsToComponent(jChild);
                    }
                }
            }
        }

        private String getComponentTypeName(JComponent comp) {
            if (comp instanceof JLabel) {
                return "Label";
            }
            if (comp instanceof JButton) {
                return "Button";
            }
            if (comp instanceof JTextField) {
                return "TextField";
            }
            if (comp instanceof JTable) {
                return "Table";
            }
            if (comp instanceof JTree) {
                return "Tree";
            }
            if (comp instanceof JScrollPane) {
                return "ScrollPane";
            }
            if (comp instanceof JScrollBar) {
                return "ScrollBar";
            }
            if (comp instanceof javax.swing.table.JTableHeader) {
                return "TableHeader";
            }
            if (comp instanceof JViewport) {
                return "Viewport";
            }
            if (comp instanceof JList) {
                return "List";
            }
            if (comp instanceof JComboBox) {
                return "ComboBox";
            }
            if (comp instanceof JCheckBox) {
                return "CheckBox";
            }
            if (comp instanceof JRadioButton) {
                return "RadioButton";
            }
            if (comp instanceof JMenu) {
                return "Menu";
            }
            if (comp instanceof JMenuBar) {
                return "MenuBar";
            }
            if (comp instanceof JToolBar) {
                return "ToolBar";
            }
            return "Panel";
        }

        public void refreshUI() {
            // Apply theme to all components immediately
            for (Component comp : getComponents()) {
                if (comp instanceof JComponent jComp) {
                    applyDefaultsToComponent(jComp);
                }
            }
            // Recursively update all TitledBorders in the entire component tree
            updateAllTitledBorders(this);
            // Force complete UI update for all child components
            SwingUtilities.updateComponentTreeUI(this);
            // Force repaint to ensure paintBorder is called with new colors
            this.revalidate();
            this.repaint();
        }

        private void updateAllTitledBorders(Component comp) {
            if (comp instanceof JComponent jComp) {
                javax.swing.border.Border border = jComp.getBorder();
                if (border instanceof  TitledBorder  titledBorder) {
                    Object fg = previewDefaults.get("Panel.foreground");
                    Object font = previewDefaults.get("Panel.font");
//                    titledBorder.applyDirectColors(
//                            fg instanceof Color ? (Color) fg : null,
//                            font instanceof Font ? (Font) font : null
//                    );
                } else if (border instanceof javax.swing.border.TitledBorder titledBorder) {
                    Object fg = previewDefaults.get("Panel.foreground");
                    Object font = previewDefaults.get("Panel.font");
                    if (fg instanceof Color fgColor) {
                        titledBorder.setTitleColor(fgColor);
                    }
                    if (font instanceof Font titleFont) {
                        titledBorder.setTitleFont(titleFont);
                    }
                }
            }

            if (comp instanceof Container container) {
                for (Component child : container.getComponents()) {
                    updateAllTitledBorders(child);
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
        //themeVariantComboBox2.setRenderer(new PreviewComboBoxRenderer());
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
        sampleTree.setCellRenderer(new PreviewTreeCellRenderer());
        JScrollPane treeScrollPane = new JScrollPane(sampleTree);
        treeScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        treeScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        treeScrollPane.setBorder(BorderFactory.createTitledBorder("Tree"));

        DefaultTableModel sampleTableModel = new DefaultTableModel(
                new Object[]{"Name", "Type"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        sampleTableModel.addRow(new Object[]{"Main.java", "Java"});
        sampleTableModel.addRow(new Object[]{"config.json", "JSON"});
        sampleTableModel.addRow(new Object[]{"theme-dark", "Theme"});

        JTable sampleTable = new JTable(sampleTableModel);
        sampleTable.setDefaultRenderer(Object.class, new PreviewTableCellRenderer());
        JScrollPane sampleTableScrollPane = new JScrollPane(sampleTable);
        sampleTableScrollPane.setBorder(BorderFactory.createTitledBorder("Table"));
        // Update scrollbar colors
        updateScrollPaneColors(sampleTableScrollPane);

        structurePanel.add(treeScrollPane);
        structurePanel.add(sampleTableScrollPane);

        JPanel controlsPanel = new JPanel(new GridLayout(0, 1, 6, 6));

        JLabel infoLabel = new JLabel("Example label");
        infoLabel.setOpaque(true);
        infoLabel.setBackground(new Color(230, 230, 230));

        controlsPanel.add(infoLabel);
        JButton previewButton = new JButton("Preview Button");
        previewButton.setUI(new PreviewButtonUI());
        controlsPanel.add(previewButton);
        controlsPanel.add(new JTextField("Sample text field"));

        controlsPanel.add(new JLabel("Theme variant"));
        themeVariantComboBox1 = new JComboBox<>(new String[]{"Light", "Dark"});
        themeVariantComboBox1.setRenderer(new PreviewComboBoxRenderer());
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

        // Sync both combo boxes
        themeVariantComboBox2.setSelectedItem(selectedVariant);

        // Update preview with new colors
        updatePreviewDefaults(currentTheme);

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

        // Get detailed (split) color scheme to ensure all individual colors are available
        ColorScheme detailedScheme = theme.getColors().getDetailedScheme();

        // Apply theme colors to preview defaults
        for (Map.Entry<String, Color> entry : detailedScheme.getColorMap().entrySet()) {
            previewDefaults.put(entry.getKey(), entry.getValue());
        }

        // Apply theme fonts to preview defaults
        for (Map.Entry<String, Font> entry : theme.getFonts().getFontMap().entrySet()) {
            previewDefaults.put(entry.getKey(), entry.getValue());
        }

        // Notify sample panel that defaults have changed
        samplePanel.refreshUI();
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

    private void updateScrollPaneColors(JScrollPane scrollPane) {
        Object sbBg = previewDefaults.get("ScrollBar.background");
        Object sbFg = previewDefaults.get("ScrollBar.foreground");

        JScrollBar vbar = scrollPane.getVerticalScrollBar();
        JScrollBar hbar = scrollPane.getHorizontalScrollBar();

        if (vbar != null) {
            if (sbBg instanceof Color) {
                vbar.setBackground((Color) sbBg);
            }
            if (sbFg instanceof Color) {
                vbar.setForeground((Color) sbFg);
            }
        }
        if (hbar != null) {
            if (sbBg instanceof Color) {
                hbar.setBackground((Color) sbBg);
            }
            if (sbFg instanceof Color) {
                hbar.setForeground((Color) sbFg);
            }
        }
    }

    /**
     * Custom tree cell renderer that uses preview defaults colors
     */
    private class PreviewTreeCellRenderer extends javax.swing.tree.DefaultTreeCellRenderer {

        @Override
        public java.awt.Component getTreeCellRendererComponent(javax.swing.JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

            // Force opaque so background is visible
            setOpaque(true);

            if (selected) {
                // Apply selection colors
                Object selFg = previewDefaults.get("Tree.selectionForeground");
                Object selBg = previewDefaults.get("Tree.selectionBackground");

                if (selBg instanceof Color) {
                    setBackground((Color) selBg);
                } else {
                    setBackground(Color.BLUE);
                }

                if (selFg instanceof Color) {
                    setForeground((Color) selFg);
                } else {
                    setForeground(Color.WHITE);
                }
            } else {
                // Apply normal colors
                Object fg = previewDefaults.get("Tree.foreground");
                Object bg = previewDefaults.get("Tree.background");

                if (bg instanceof Color) {
                    setBackground((Color) bg);
                } else {
                    setBackground(Color.WHITE);
                }

                if (fg instanceof Color) {
                    setForeground((Color) fg);
                }
            }

            // Apply font (always)
            Object font = previewDefaults.get("Tree.font");
            if (font instanceof Font) {
                setFont((Font) font);
            }

            return this;
        }
    }

    /**
     * Custom table cell renderer that uses preview defaults colors
     */
    private class PreviewTableCellRenderer extends javax.swing.table.DefaultTableCellRenderer {

        @Override
        public java.awt.Component getTableCellRendererComponent(javax.swing.JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {

            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            // Force opaque so background is visible
            setOpaque(true);

            if (isSelected) {
                // Apply selection colors
                Object selFg = previewDefaults.get("Table.selectionForeground");
                Object selBg = previewDefaults.get("Table.selectionBackground");

                if (selBg instanceof Color) {
                    setBackground((Color) selBg);
                } else {
                    setBackground(Color.BLUE);
                }

                if (selFg instanceof Color) {
                    setForeground((Color) selFg);
                } else {
                    setForeground(Color.WHITE);
                }
            } else {
                // Apply normal colors
                Object fg = previewDefaults.get("Table.foreground");
                Object bg = previewDefaults.get("Table.background");

                if (bg instanceof Color) {
                    setBackground((Color) bg);
                } else {
                    setBackground(Color.WHITE);
                }

                if (fg instanceof Color) {
                    setForeground((Color) fg);
                }
            }

            // Apply font (always)
            Object font = previewDefaults.get("Table.font");
            if (font instanceof Font) {
                setFont((Font) font);
            }

            return this;
        }
    }

    /**
     * Custom combobox renderer that uses preview defaults colors
     */
    private class PreviewComboBoxRenderer extends javax.swing.plaf.basic.BasicComboBoxRenderer {

        @Override
        public java.awt.Component getListCellRendererComponent(javax.swing.JList list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {

            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            // Force opaque so background is visible
            setOpaque(true);

            // Apply preview colors based on selection state
            Object fg, bg, font;

            if (isSelected) {
                // Selection colors
                fg = previewDefaults.get("ComboBox.selectionForeground");
                bg = previewDefaults.get("ComboBox.selectionBackground");
            } else {
                // Normal colors
                fg = previewDefaults.get("ComboBox.foreground");
                bg = previewDefaults.get("ComboBox.background");
            }

            font = previewDefaults.get("ComboBox.font");

            if (bg instanceof Color) {
                setBackground((Color) bg);
            } else {
                setBackground(isSelected ? new Color(0, 100, 200) : Color.WHITE);
            }

            if (fg instanceof Color) {
                setForeground((Color) fg);
            }

            if (font instanceof Font) {
                setFont((Font) font);
            }

            return this;
        }
    }

    /**
     * Custom button UI that respects theme colors from preview defaults
     */
    private class PreviewButtonUI extends javax.swing.plaf.basic.BasicButtonUI {

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton button = (AbstractButton) c;

            // Apply normal button colors
            Object bg = previewDefaults.get("Button.background");
            Object fg = previewDefaults.get("Button.foreground");
            Object font = previewDefaults.get("Button.font");

            if (bg instanceof Color) {
                button.setBackground((Color) bg);
            }
            if (fg instanceof Color) {
                button.setForeground((Color) fg);
                g.setColor((Color) fg);
            }
            if (font instanceof Font) {
                button.setFont((Font) font);
            }

            // Check if button is pressed or selected
            ButtonModel model = button.getModel();
            if (model.isPressed() || model.isSelected()) {
                Object pressedBg = previewDefaults.get("Button.pressedBackground");
                Object pressedFg = previewDefaults.get("Button.pressedForeground");

                if (pressedBg instanceof Color) {
                    g.setColor((Color) pressedBg);
                    g.fillRect(0, 0, button.getWidth(), button.getHeight());
                }

                if (pressedFg instanceof Color) {
                    button.setForeground((Color) pressedFg);
                    g.setColor((Color) pressedFg);
                }
            }

            super.paint(g, c);
        }
    }
}
