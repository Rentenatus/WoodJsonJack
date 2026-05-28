/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.events.HistoryManager;
import java.util.List;
import javax.swing.table.AbstractTableModel;

public class JackUndoTableModel extends AbstractTableModel {

    private HistoryManager historyManager = null;

    public void setHistoryManager(HistoryManager historyManager) {
        HistoryManager historyManagerAlt = this.historyManager;
        this.historyManager = historyManager;
        if (historyManagerAlt != this.historyManager) {
            fireTableDataChanged();
        }
    }

    private static final String[] COLS = {"Index", "Action", "Description"};

    @Override
    public int getColumnCount() {
        return COLS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLS[column];
    }

    // --- Sichtberechnung -----------------------------------------------------
    public int getRedoCount() {
        if (historyManager == null) {
            return 0;
        }
        return historyManager.redoSize();
    }

    @Override
    public int getRowCount() {
        if (historyManager == null) {
            return 0;
        }
        return historyManager.getTotalSize() + 1;
    }

    // --- TableModel-API ------------------------------------------------------
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        int redoCount = getRedoCount();

        if (rowIndex == redoCount) {
            // Trenner-Zeile
            return switch (columnIndex) {
                case 0, 1, 2 ->
                    "<---";
                default ->
                    "";
            };
        }

        if (historyManager == null) {
            return "";
        }

        EditCommand cmd = (rowIndex < redoCount)
                ? historyManager.getRedo(redoCount - 1 - rowIndex)
                : historyManager.getUndo(rowIndex - redoCount - 1);

        if (cmd == null) {
            return "";
        }

        return switch (columnIndex) {
            case 0 ->
                (rowIndex < redoCount) ? (redoCount - rowIndex) : (rowIndex - redoCount);
            case 1 ->
                cmd.getTypeText();
            case 2 ->
                cmd.getDescription();
            default ->
                "";
        };
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }
}
