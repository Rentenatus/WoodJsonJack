/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.settings;

import de.jare.tree.settings.theme.FontSettings;
import javax.swing.BorderFactory;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import javax.swing.event.ChangeListener;

public class ThemesPreferencesFontTable extends JPanel {

    private final DefaultTableModel fontsTableModel;
    private final JTable fontsTable;

    private FontSettings currentFontSettings;
    private FontTableListener fontTableListener;
    private ChangeListener changeListener;

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

        // Add context menu
        setupContextMenu();

        buildUi();
    }

    private void setupContextMenu() {
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem groupFontsItem = new JMenuItem("xxx");
        //groupFontsItem.addActionListener(e -> groupFonts());
        popupMenu.add(groupFontsItem);

        JMenuItem splitFontsItem = new JMenuItem("yyy");
        //splitFontsItem.addActionListener(e -> splitFonts());
        popupMenu.add(splitFontsItem);

        fontsTable.setComponentPopupMenu(popupMenu);

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
            System.out.println(key + ", " + value.getFontName() + ", " + style + ", " + value.getSize());
            fontsTableModel.addRow(new Object[]{key, value.getFontName(), style, value.getSize()});
        }

        if (fontTableListener != null) {
            fontTableListener.onFontsUpdated(fontSettings);
        }

        // Notify change listener
        if (changeListener != null) {
            changeListener.stateChanged(new javax.swing.event.ChangeEvent(this));
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

    public void setChangeListener(ChangeListener listener) {
        this.changeListener = listener;
    }
}
