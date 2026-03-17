/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.control.UndoManagerModel;
import de.jare.tree.control.commands.WoodCommand;
import javax.swing.table.AbstractTableModel;

public class UndoTableModel extends AbstractTableModel {

    private UndoManagerModel undoManModel = null;

    public void setUndoManModel(UndoManagerModel undoManModel) {
        UndoManagerModel undoManModelAlt = this.undoManModel;
        this.undoManModel = undoManModel;
        if (undoManModelAlt != this.undoManModel) {
            fireTableDataChanged();
        }
    }

    private static final String[] COLS = {"Status", "Action", "Description"};

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
        if (undoManModel == null) {
            return 0;
        }
        return undoManModel.redoSize();
    }

    @Override
    public int getRowCount() {
        if (undoManModel == null) {
            return 0;
        }
        return undoManModel.size() + 1;
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

        if (undoManModel == null) {
            return "";
        }
        WoodCommand cmd = (rowIndex < redoCount)
                ? undoManModel.getRedo(redoCount - 1 - rowIndex)
                : undoManModel.getUndo(rowIndex - redoCount - 1);

        return switch (columnIndex) {
            case 0 ->
                cmd.getStatus();
            case 1 ->
                cmd.getCommandText();
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
    // --- Manipulation   ------------------------

}
