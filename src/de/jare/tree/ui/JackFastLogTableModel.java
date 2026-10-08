/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.control.fastlog.FastLog;
import de.jare.tree.control.fastlog.FastLogEntry;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/**
 * TableModel ueber den Eintraegen eines {@link FastLog}. Die Zeilen koennen
 * ueber {@link #setFilter(String)} auf einen Fast-Log-Typ eingeschraenkt
 * werden; null zeigt alle Typen. Die Message-Spalte ist read-only, der volle
 * Text wird ueber den Detail-Dialog angezeigt.
 *
 * @author Janusch Rentenatus
 */
public class JackFastLogTableModel extends AbstractTableModel {

    private static final String[] COLS = {"Nr", "Id", "Message"};

    private final FastLog fastLog;
    private String filterTypeId;
    private List<FastLogEntry> rows = new ArrayList<>();

    /**
     * Erzeugt das Model und laedt die initialen Eintraege.
     *
     * @param fastLog der anzuzeigende FastLog
     */
    public JackFastLogTableModel(FastLog fastLog) {
        this.fastLog = fastLog;
        reload();
    }

    /**
     * Setzt den Typ-Filter und laedt die Zeilen neu.
     *
     * @param typeId ID des Fast-Log-Typs oder null fuer alle Typen
     */
    public void setFilter(String typeId) {
        this.filterTypeId = typeId;
        reload();
    }

    /**
     * @return der aktive Typ-Filter oder null fuer alle Typen
     */
    public String getFilter() {
        return filterTypeId;
    }

    /**
     * Laedt die Zeilen als Snapshot aus dem FastLog neu.
     */
    public final void reload() {
        rows = fastLog.getEntries(filterTypeId);
        fireTableDataChanged();
    }

    /**
     * Returns the entry of the given model row.
     *
     * @param row the model row index
     * @return the entry or null when the row is out of range
     */
    public FastLogEntry getEntryAt(int row) {
        return (row >= 0 && row < rows.size()) ? rows.get(row) : null;
    }

    @Override
    public int getColumnCount() {
        return COLS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLS[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == 0 ? Long.class : String.class;
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        FastLogEntry entry = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 ->
                entry.getNr();
            case 1 ->
                entry.getTypeId();
            case 2 ->
                entry.getMessage();
            default ->
                "";
        };
    }

}
