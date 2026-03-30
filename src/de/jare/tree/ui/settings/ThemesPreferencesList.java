package de.jare.tree.ui.settings;

import de.jare.tree.settings.theme.Theme;
import de.jare.tree.settings.theme.ThemeSuite;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;

public class ThemesPreferencesList extends JPanel {

    private final ThemeSuite themeSuite;
    private final DefaultListModel<Theme> themesListModel;
    private final JList<Theme> themesList;

    private Theme selectedTheme;
    private ThemeListener themeListener;

    public interface ThemeListener {
        void onThemeSelected(Theme theme);
    }

    public ThemesPreferencesList(ThemeSuite themeSuite) {
        super(new BorderLayout(8, 8));
        this.themeSuite = themeSuite;
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
                    setBorder(BorderFactory.createLineBorder(getBackground()));
                    
                    // If this is the active theme, use font color for the border
                    if (theme.equals(ThemesPreferencesList.this.selectedTheme)) {
                        setBorder(BorderFactory.createLineBorder(getForeground()));
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
        themesListScrollPane.setBorder(BorderFactory.createTitledBorder("Available Themes"));
        themesListScrollPane.setPreferredSize(new Dimension(220, 300));
        
        add(themesListScrollPane, BorderLayout.CENTER);
    }

    public void updateThemesList() {
        if (themeSuite == null) {
            return;
        }

        themesListModel.clear();
        for (Theme theme : themeSuite.getAvailableThemes()) {
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
        if (themeSuite == null) {
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

        themeSuite.getAvailableThemes().add(newTheme);
        themesListModel.addElement(newTheme);
    }
}