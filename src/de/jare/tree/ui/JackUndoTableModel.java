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
                case 0 -> "---";
                case 1, 2 ->
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

        return switch (columnIndex) {
            case 0 ->
                formatStatus(cmd);
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

    private String formatStatus(EditCommand cmd) {
        if (cmd instanceof de.jare.jsoncasted.editor.command.AbstractEditCommand) {
            de.jare.jsoncasted.editor.command.AbstractEditCommand absCmd = 
                (de.jare.jsoncasted.editor.command.AbstractEditCommand) cmd;
            CommandAction action = absCmd.getLastAction();
            int updated = absCmd.getLastUpdatedCount();
            int failed = absCmd.getLastFailedCount();
            
            if (action == null) {
                return "";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append(action.toString());
            if (updated > 0 || failed > 0) {
                sb.append("(").append(updated);
                if (failed > 0) {
                    sb.append("/").append(failed);
                }
                sb.append(")");
            }
            return sb.toString();
        }
        return "";
    }
}
