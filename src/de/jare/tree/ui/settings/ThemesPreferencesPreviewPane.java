/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * [http://www.eclipse.org/legal/epl-v20.html](http://www.eclipse.org/legal/epl-v20.html)
 * </copyright>
 */
package de.jare.tree.ui.settings;

import de.jare.tree.settings.theme.ColorScheme;
import de.jare.tree.settings.theme.Theme;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.util.Map;

public class ThemesPreferencesPreviewPane extends JPanel implements ChangeListener {

    private final JPanel previewPane;
    private final JPanel previewContent;

    private JTextField themeIdField;
    private JTextField themeNameField;
    private JComboBox<String> themeVariantComboBox1;
    private JComboBox<String> themeVariantComboBox2;

    private Theme currentTheme;
    private PreviewPaneListener previewPaneListener;

    private PreviewSampleDialog sampleDialog;

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

        buildUi();
    }

    private void buildUi() {
        previewPane.setBorder(BorderFactory.createTitledBorder("Application Preview"));
        previewPane.add(buildPreviewContent(), BorderLayout.CENTER);

        add(previewPane, BorderLayout.CENTER);
    }

    private JPanel buildPreviewContent() {
        JPanel content = new JPanel(new BorderLayout(8, 8));

        JPanel centerPanel = new JPanel(new GridLayout(1, 1, 8, 8));

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
        themeVariantComboBox2.addActionListener(e -> updateThemeVariantFromComboBox());
        formPanel.add(themeVariantComboBox2);

        JButton openPreviewButton = new JButton("Open Sample Preview");
        openPreviewButton.addActionListener(e -> openSampleDialog());
        formPanel.add(openPreviewButton);

        centerPanel.add(formPanel);

        content.add(centerPanel, BorderLayout.CENTER);
        return content;
    }

    private void openSampleDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);

        if (sampleDialog == null) {
            sampleDialog = new PreviewSampleDialog(owner);
        }

        if (currentTheme != null) {
            updatePreviewDefaults(currentTheme);
        }

        sampleDialog.setLocationRelativeTo(this);
        sampleDialog.setVisible(true);
        sampleDialog.toFront();
    }

    private void updateThemeVariantFromComboBox() {
        if (currentTheme == null) {
            return;
        }

        String selectedVariant = null;
        if (themeVariantComboBox1 != null && themeVariantComboBox1.getSelectedItem() != null) {
            selectedVariant = (String) themeVariantComboBox1.getSelectedItem();
        }
        if (selectedVariant == null && themeVariantComboBox2 != null && themeVariantComboBox2.getSelectedItem() != null) {
            selectedVariant = (String) themeVariantComboBox2.getSelectedItem();
        }
        if (selectedVariant == null) {
            return;
        }

        boolean isDark = "Dark".equals(selectedVariant);
        currentTheme.getColors().setDark(isDark);

        if (themeVariantComboBox1 != null) {
            themeVariantComboBox1.setSelectedItem(selectedVariant);
        }
        if (themeVariantComboBox2 != null) {
            themeVariantComboBox2.setSelectedItem(selectedVariant);
        }

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
        if (themeVariantComboBox1 != null) {
            themeVariantComboBox1.setSelectedItem(isDark ? "Dark" : "Light");
        }
        if (themeVariantComboBox2 != null) {
            themeVariantComboBox2.setSelectedItem(isDark ? "Dark" : "Light");
        }

        updatePreviewDefaults(theme);
    }

    private void updatePreviewDefaults(Theme theme) {
        if (theme == null) {
            return;
        }

        ColorScheme detailedScheme = theme.getColors().getDetailedScheme();

        for (Map.Entry<String, Color> entry : detailedScheme.getColorMap().entrySet()) {
            Color color = entry.getValue();
            if (color != null) {
                UIManager.put(entry.getKey(), new ColorUIResource(color));
            }
        }

        for (Map.Entry<String, Font> entry : theme.getFonts().getFontMap().entrySet()) {
            Font font = entry.getValue();
            if (font != null) {
                UIManager.put(entry.getKey(), new FontUIResource(font));
            }
        }

        if (sampleDialog != null) {
            SwingUtilities.updateComponentTreeUI(sampleDialog);
            sampleDialog.pack();
            sampleDialog.repaint();
        }
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