package de.jare.tree.ui.settings;

import de.jare.tree.settings.theme.FontSettings;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;

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
        this.fontsTableModel = new DefaultTableModel(new Object[]{"Key", "Name", "Style", "Size"}, 0) {
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
        final Set<String> keySet = fontSettings.getFontMap().keySet();

        // Sort keys alphabetically
        ArrayList<String> sortedKeys = new ArrayList<>(keySet);
        Collections.sort(sortedKeys);

        for (String key : sortedKeys) {
            Font value = fontSettings.getFont(key);
            String style = getFontStyleDescription(value.getStyle());
            fontsTableModel.addRow(new Object[]{key, value.getFontName(), style, value.getSize()});
        }

        if (fontTableListener != null) {
            fontTableListener.onFontsUpdated(fontSettings);
        }
    }

    private String getFontStyleDescription(int style) {
        StringBuilder sb = new StringBuilder();
        if ((style & Font.BOLD) == Font.BOLD) {
            sb.append("Bold");
        }
        if ((style & Font.ITALIC) == Font.ITALIC) {
            if (sb.length() > 0) {
                sb.append("+");
            }
            sb.append("Italic");
        }
        if (sb.length() == 0) {
            sb.append("Plain");
        }
        return sb.toString();
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
