/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.settings;

import de.jare.tree.settings.theme.ColorScheme;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

        // Double click on a row opens the color chooser
        colorsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    int row = colorsTable.rowAtPoint(evt.getPoint());
                    if (row >= 0) {
                        openColorChooser(row);
                    }
                }
            }
        });

        buildUi();
    }

    private void setupContextMenu() {
        popupMenu = new JPopupMenu();

        JMenuItem chooseColorItem = new JMenuItem("Choose color...");
        chooseColorItem.addActionListener(e -> {
            int row = colorsTable.getSelectedRow();
            if (row >= 0) {
                openColorChooser(row);
            }
        });
        popupMenu.add(chooseColorItem);
        popupMenu.addSeparator();

        colorsTable.setComponentPopupMenu(popupMenu);

        // Update enabled state based on current state
        updateMenuItemsEnabledState();
    }

    /**
     * Opens a color chooser for the scheme key of the given row. The chosen color is applied
     * to the scheme, the table row and all listeners (work theme and preview).
     *
     * @param row the table row holding the scheme key to edit
     */
    void openColorChooser(int row) {
        if (currentColorScheme == null || row < 0 || row >= colorsTableModel.getRowCount()) {
            return;
        }
        final String key = String.valueOf(colorsTableModel.getValueAt(row, 0));
        final Color current = (Color) colorsTableModel.getValueAt(row, 2);
        final Color chosen = JColorChooser.showDialog(this, "Choose color for " + key, current);
        if (chosen != null) {
            applyColor(row, chosen);
        }
    }

    /**
     * Applies the given color to the scheme key of the row, updates the table row and
     * notifies the color table listener and the change listener.
     *
     * @param row the table row holding the scheme key
     * @param chosen the color to set
     */
    void applyColor(int row, Color chosen) {
        if (currentColorScheme == null || chosen == null || row < 0 || row >= colorsTableModel.getRowCount()) {
            return;
        }
        final String key = String.valueOf(colorsTableModel.getValueAt(row, 0));
        currentColorScheme.setColor(key, chosen);
        colorsTableModel.setValueAt(ColorScheme.colorToHex(chosen), row, 1);
        colorsTableModel.setValueAt(chosen, row, 2);
        notifyListeners();
    }

    private void notifyListeners() {
        if (colorTableListener != null) {
            colorTableListener.onColorsUpdated(currentColorScheme);
        }
        if (changeListener != null) {
            changeListener.stateChanged(new javax.swing.event.ChangeEvent(this));
        }
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
        colorsTable.setToolTipText("Double-click a row to choose its color");
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

        notifyListeners();
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
