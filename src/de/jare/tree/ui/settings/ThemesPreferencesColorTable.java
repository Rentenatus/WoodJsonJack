package de.jare.tree.ui.settings;

import de.jare.tree.settings.theme.ColorScheme;
import java.awt.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class ThemesPreferencesColorTable extends JPanel {

    private final DefaultTableModel colorsTableModel;
    private final JTable colorsTable;

    private ColorScheme currentColorScheme; 
    private ColorTableListener colorTableListener;

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
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem groupColorsItem = new JMenuItem("Group Colors");
        groupColorsItem.addActionListener(e -> groupColors());
        popupMenu.add(groupColorsItem);

        JMenuItem splitColorsItem = new JMenuItem("Split Colors");
        splitColorsItem.addActionListener(e -> splitColors());
        popupMenu.add(splitColorsItem);

        colorsTable.setComponentPopupMenu(popupMenu);

        // Update enabled state based on current state
        updateMenuItemsEnabledState();
    }

    private void updateMenuItemsEnabledState() {
        JPopupMenu popupMenu = (JPopupMenu) colorsTable.getComponentPopupMenu();
        if (popupMenu != null) {
            boolean hasGroupKeys = hasGroupKeys();
            boolean hasIndividualKeys = hasIndividualKeys();

            for (Component comp : popupMenu.getComponents()) {
                if (comp instanceof JMenuItem menuItem) {
                    String text = menuItem.getText();
                    if ("Group Colors".equals(text)) {
                        menuItem.setEnabled(hasIndividualKeys && !hasGroupKeys);
                    } else if ("Split Colors".equals(text)) {
                        menuItem.setEnabled(hasGroupKeys);
                    }
                }
            }
        }
    }

    private boolean hasGroupKeys() {
        if (currentColorScheme == null) {
            return false;
        }
        for (String groupKey : ColorScheme.GROUP_MAPPING.keySet()) {
            if (currentColorScheme.hasColor(groupKey)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasIndividualKeys() {
        if (currentColorScheme == null) {
            return false;
        }
        for (String groupKey : ColorScheme.GROUP_MAPPING.keySet()) {
            String[] keys = ColorScheme.GROUP_MAPPING.get(groupKey);
            for (String key : keys) {
                if (currentColorScheme.hasColor(key)) {
                    return true;
                }
            }
        }
        return false;
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
            System.out.println(key + "_" + ColorScheme.colorToHex(value));
            colorsTableModel.addRow(new Object[]{key, ColorScheme.colorToHex(value), value});
        }

        // Set custom renderer for the color column (column index 2)
        colorsTable.getColumnModel().getColumn(2).setCellRenderer(new ColorSwatchRenderer());

        // Update menu items enabled state
        updateMenuItemsEnabledState();

        if (colorTableListener != null) {
            colorTableListener.onColorsUpdated(colorScheme);
        }
    }

    private void groupColors() {
        // First, collect all keys that should be grouped and calculate averages
        Map<String, Color> groupColors = new HashMap<>();
        Set<String> keysToRemove = new HashSet<>();
        
        for (String groupKey : ColorScheme.GROUP_MAPPING.keySet()) {
            String[] keys = ColorScheme.GROUP_MAPPING.get(groupKey);

            // Calculate average color
            int redSum = 0, greenSum = 0, blueSum = 0;
            int count = 0;

            for (String key : keys) {
                if (currentColorScheme.hasColor(key)) {
                    Color color = currentColorScheme.getColor(key);
                    redSum += color.getRed();
                    greenSum += color.getGreen();
                    blueSum += color.getBlue();
                    count++;
                    keysToRemove.add(key);
                }
            }

            if (count > 0) {
                int avgRed = redSum / count;
                int avgGreen = greenSum / count;
                int avgBlue = blueSum / count;
                Color averageColor = new Color(avgRed, avgGreen, avgBlue);
                groupColors.put(groupKey, averageColor);
            }
        }

        // Clear all existing colors
        currentColorScheme.getColorMap().clear();
        
        // Add only group colors
        for (Map.Entry<String, Color> entry : groupColors.entrySet()) {
            currentColorScheme.setColor(entry.getKey(), entry.getValue());
        }

        // Refresh table
        updateColorsTable(currentColorScheme);
    }

    private void splitColors() {
        // First, collect all group colors to split
        Map<String, Color> groupsToSplit = new HashMap<>();
        
        for (String groupKey : ColorScheme.GROUP_MAPPING.keySet()) {
            if (currentColorScheme.hasColor(groupKey)) {
                groupsToSplit.put(groupKey, currentColorScheme.getColor(groupKey));
            }
        }
        
        // Split all group colors into individual keys
        for (Map.Entry<String, Color> entry : groupsToSplit.entrySet()) {
            String groupKey = entry.getKey();
            Color groupColor = entry.getValue();
            String[] keys = ColorScheme.GROUP_MAPPING.get(groupKey);

            // Set individual colors to group color
            for (String key : keys) {
                currentColorScheme.setColor(key, groupColor);
            }

            // Remove group color
            currentColorScheme.getColorMap().remove(groupKey);
        }

        // Refresh table
        updateColorsTable(currentColorScheme);
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
                setBorder(BorderFactory.createLineBorder(table.getForeground(), 1));
                // Set preferred size
                setPreferredSize(new Dimension(30, 20));
                setOpaque(true);
            }

            return this;
        }
    }
}
