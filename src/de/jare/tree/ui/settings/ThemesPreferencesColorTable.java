/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.settings;

import de.jare.tree.settings.theme.ColorScheme;
import javax.swing.BorderFactory;
import java.awt.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.ChangeListener;

public class ThemesPreferencesColorTable extends JPanel {

    private final DefaultTableModel colorsTableModel;
    private final JTable colorsTable;
    JPopupMenu popupMenu;

    private ColorScheme currentColorScheme;
    private ColorTableListener colorTableListener;
    private ChangeListener changeListener;

    public interface ColorTableListener {

        void onColorsUpdated(ColorScheme colorScheme);
    }

    public ThemesPreferencesColorTable() {
        super(new BorderLayout(8, 8));
        this.colorsTableModel = new DefaultTableModel(new Object[]{"Key", "Value", "Color"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.colorsTable = new JTable(colorsTableModel);

        // Add custom renderer for color column
        colorsTable.setDefaultRenderer(Color.class, new ColorSwatchRenderer());

        // Add context menu
        setupContextMenu();

        buildUi();
    }

    private void setupContextMenu() {
        popupMenu = new JPopupMenu();

        colorsTable.setComponentPopupMenu(popupMenu);

        // Update enabled state based on current state
        updateMenuItemsEnabledState();
    }

    public void addPopupMenuItem(JMenuItem groupColorsItem) {
        popupMenu.add(groupColorsItem);
    }

    private void updateMenuItemsEnabledState() {
        JPopupMenu popupMenu = (JPopupMenu) colorsTable.getComponentPopupMenu();
        if (popupMenu != null) {

            for (Component comp : popupMenu.getComponents()) {
                if (comp instanceof JMenuItem menuItem) {
                    String text = menuItem.getText();
                    if ("Group Colors".equals(text)) {
                        menuItem.setEnabled(false);
                    } else if ("Split Colors".equals(text)) {
                        menuItem.setEnabled(false);
                    }
                }
            }
        }
    }

    private void buildUi() {
        JScrollPane colorsScrollPane = new JScrollPane(colorsTable);
        colorsScrollPane.setBorder(BorderFactory.createTitledBorder("Colors"));
        colorsScrollPane.setMinimumSize(new Dimension(180, 120));

        add(colorsScrollPane, BorderLayout.CENTER);
    }

    public void updateColorsTable(ColorScheme colorScheme) {
        this.currentColorScheme = colorScheme;
        colorsTableModel.setRowCount(0);

        final Set<String> keySet = colorScheme.getColorMap().keySet();

        // Sort keys alphabetically
        ArrayList<String> sortedKeys = new ArrayList<>(keySet);
        Collections.sort(sortedKeys);

        for (String key : sortedKeys) {
            Color value = colorScheme.getColor(key);
            //System.out.println(key + ", " + ColorScheme.colorToHex(value));
            colorsTableModel.addRow(new Object[]{key, ColorScheme.colorToHex(value), value});
        }

        // Set custom renderer for the color column (column index 2)
        colorsTable.getColumnModel().getColumn(2).setCellRenderer(new ColorSwatchRenderer());

        // Update menu items enabled state
        updateMenuItemsEnabledState();

        if (colorTableListener != null) {
            colorTableListener.onColorsUpdated(colorScheme);
        }

        // Notify change listener
        if (changeListener != null) {
            changeListener.stateChanged(new javax.swing.event.ChangeEvent(this));
        }
    }

    public DefaultTableModel getColorsTableModel() {
        return colorsTableModel;
    }

    public JTable getColorsTable() {
        return colorsTable;
    }

    public ColorScheme getCurrentColorScheme() {
        return currentColorScheme;
    }

    public void setColorTableListener(ColorTableListener listener) {
        this.colorTableListener = listener;
    }

    public void setChangeListener(ChangeListener listener) {
        this.changeListener = listener;
    }

    private static class ColorSwatchRenderer extends javax.swing.table.DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            // Call parent to get default settings
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof Color color) {
                // Set empty text
                setText("");
                // Set background color
                setBackground(color);
                // Set border
                setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
                // Set preferred size
                setPreferredSize(new Dimension(30, 20));
                setOpaque(true);
            }

            return this;
        }
    }
}
