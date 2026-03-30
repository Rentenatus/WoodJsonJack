package de.jare.tree.ui.settings;

import de.jare.tree.settings.theme.ColorScheme;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

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
        this.colorsTableModel = new DefaultTableModel(new Object[]{"Key", "Value"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.colorsTable = new JTable(colorsTableModel);
        
        buildUi();
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
        colorScheme.forEachColor((key, value) -> {
            colorsTableModel.addRow(new Object[]{key, ColorScheme.colorToHex(value)});
        });
        
        if (colorTableListener != null) {
            colorTableListener.onColorsUpdated(colorScheme);
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
}