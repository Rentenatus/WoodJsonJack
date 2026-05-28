/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.command.CommandAction;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.tree.control.JackUndoManagerModel;
import javax.swing.table.AbstractTableModel;

public class JackUndoTableModel extends AbstractTableModel {

    private JackUndoManagerModel manager = null;

    public void setManager(JackUndoManagerModel manager) {
        JackUndoManagerModel managerAlt = this.manager;
        this.manager = manager;
        if (managerAlt != this.manager) {
            fireTableDataChanged();
        }
    }

    private static final String[] COLS = {"Status", "Updated", "Failed", "Action", "Description"};

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
        if (manager == null) {
            return 0;
        }
        return manager.redoSize();
    }

    @Override
    public int getRowCount() {
        if (manager == null) {
            return 0;
        }
        return manager.getTotalSize() + 1;
    }

    // --- TableModel-API ------------------------------------------------------
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        int redoCount = getRedoCount();

        if (rowIndex == redoCount) {
            // Trenner-Zeile
            return switch (columnIndex) {
                case 0, 1, 2, 3, 4 ->
                    "<---";
                default ->
                    "";
            };
        }

        if (manager == null) {
            return "";
        }

        EditCommand cmd = (rowIndex < redoCount)
                ? manager.getRedo(redoCount - 1 - rowIndex)
                : manager.getUndo(rowIndex - redoCount - 1);

        if (cmd == null) {
            return "";
        }

        if (cmd instanceof de.jare.jsoncasted.editor.command.AbstractEditCommand) {
            de.jare.jsoncasted.editor.command.AbstractEditCommand absCmd = 
                (de.jare.jsoncasted.editor.command.AbstractEditCommand) cmd;
            CommandAction action = absCmd.getLastAction();
            int updated = absCmd.getLastUpdatedCount();
            int failed = absCmd.getLastFailedCount();
            
            return switch (columnIndex) {
                case 0 -> action != null ? action.toString() : "";
                case 1 -> String.valueOf(updated);
                case 2 -> String.valueOf(failed);
                case 3 -> cmd.getTypeText();
                case 4 -> cmd.getDescription();
                default -> "";
            };
        }
        
        return switch (columnIndex) {
            case 0, 1, 2 -> "";
            case 3 -> cmd.getTypeText();
            case 4 -> cmd.getDescription();
            default -> "";
        };
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }
}
