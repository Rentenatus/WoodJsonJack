package de.jare.tree.ui.settings;

import de.jare.tree.settings.theme.FontSettings;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class ThemesPreferencesFontTable extends JPanel {

    private final DefaultTableModel fontsTableModel;
    private final JTable fontsTable;

    private FontSettings currentFontSettings;
    private FontTableListener fontTableListener;

    public interface FontTableListener {
        void onFontsUpdated(FontSettings fontSettings);
    }

    public ThemesPreferencesFontTable() {
        super(new BorderLayout(8, 8));
        this.fontsTableModel = new DefaultTableModel(new Object[]{"Key", "Value"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.fontsTable = new JTable(fontsTableModel);
        
        buildUi();
    }

    private void buildUi() {
        JScrollPane fontsScrollPane = new JScrollPane(fontsTable);
        fontsScrollPane.setBorder(BorderFactory.createTitledBorder("Fonts"));
        fontsScrollPane.setMinimumSize(new Dimension(180, 120));
        
        add(fontsScrollPane, BorderLayout.CENTER);
    }

    public void updateFontsTable(FontSettings fontSettings) {
        this.currentFontSettings = fontSettings;
        fontsTableModel.setRowCount(0);
        fontSettings.forEachFont((key, value) -> {
            fontsTableModel.addRow(new Object[]{key, value.getFontName()});
        });
        
        if (fontTableListener != null) {
            fontTableListener.onFontsUpdated(fontSettings);
        }
    }

    public DefaultTableModel getFontsTableModel() {
        return fontsTableModel;
    }

    public JTable getFontsTable() {
        return fontsTable;
    }

    public FontSettings getCurrentFontSettings() {
        return currentFontSettings;
    }

    public void setFontTableListener(FontTableListener listener) {
        this.fontTableListener = listener;
    }
}