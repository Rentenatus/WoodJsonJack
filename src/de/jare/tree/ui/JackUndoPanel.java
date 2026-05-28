/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.jsoncasted.editor.events.HistoryEvent;
import de.jare.jsoncasted.editor.events.HistoryListener;
import de.jare.jsoncasted.editor.events.HistoryManager;
import de.jare.tree.control.JackMasterControl;
import de.jare.tree.control.JackUndoManager;
import de.jare.tree.control.JackUndoManagerModel;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import de.jare.tree.control.listeners.UndoRedoListener;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreeModel;

public class JackUndoPanel extends JPanel implements UndoRedoListener, TreeFocusListener, HistoryListener {

    private final JackUndoTableModel undoModel;
    private final JTable undoTable;
    private final JackUndoManager undoMan;
    private final JackUndoButtonPanel buttonPanel;
    private JackUndoManagerModel currentManager;

    public JackUndoPanel(JackMasterControl master) {
        super(new BorderLayout());
        this.undoMan = master.getUndoManager();

        undoModel = new JackUndoTableModel();
        undoTable = new JTable(undoModel);

        undoTable.setRowSelectionAllowed(true);
        undoTable.setColumnSelectionAllowed(false);
        undoTable.setCellSelectionEnabled(false);
        undoTable.setFocusable(false);

        // linkes Buttonpanel
        buttonPanel = new JackUndoButtonPanel(master);
        add(buttonPanel, BorderLayout.WEST);

        // Tabelle in der Mitte
        add(new JScrollPane(undoTable), BorderLayout.CENTER);

        master.addUndoRedoListener(this);
        master.addSelectionListener(9, this);
        
        // Set initial model from active manager
        updateModelFromActiveManager();
    }

    private void updateModelFromActiveManager() {
        JackUndoManagerModel activeManager = undoMan.getActiveManager();
        if (activeManager != null && activeManager != currentManager) {
            if (currentManager != null) {
                currentManager.removeListener(this);
            }
            currentManager = activeManager;
            undoModel.setManager(activeManager);
            activeManager.addListener(this);
        }
    }

    @Override
    public void onExecute(TreeModel model, CommandResult result) {
        SwingUtilities.invokeLater(() -> {
            updateModelFromActiveManager();
            undoModel.fireTableDataChanged();
            selectCurrent();
        });
    }

    @Override
    public void onUndo(TreeModel model, CommandResult result) {
        SwingUtilities.invokeLater(() -> {
            updateModelFromActiveManager();
            undoModel.fireTableDataChanged();
            selectCurrent();
        });
    }

    @Override
    public void onSkipped(TreeModel model, EditCommand command) {
        SwingUtilities.invokeLater(() -> {
            updateModelFromActiveManager();
            undoModel.fireTableDataChanged();
            selectCurrent();
        });
    }

    @Override
    public void onClear(HistoryEvent event) {
        SwingUtilities.invokeLater(() -> {
            undoModel.fireTableDataChanged();
            selectCurrent();
        });
    }

    @Override
    public void onAction(HistoryEvent event) {
        SwingUtilities.invokeLater(() -> {
            undoModel.fireTableDataChanged();
            selectCurrent();
        });
    }

    @Override
    public void onNodeSelected(Object node, Object trigger, boolean rootSelected) {
        // NoOp
    }

    @Override
    public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
        updateModelFromActiveManager();
        selectCurrent();
    }

    private void selectCurrent() {
        int viewRow = undoModel.getRedoCount();
        if (viewRow >= 0) {
            undoTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
            undoTable.scrollRectToVisible(undoTable.getCellRect(viewRow, 0, true));
        } else {
            undoTable.clearSelection();
        }
        buttonPanel.updateButtons();
    }
}
