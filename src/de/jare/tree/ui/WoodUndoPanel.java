/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.control.MasterControl;
import de.jare.tree.control.UndoManager;
import de.jare.tree.control.commands.WoodCommand;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import de.jare.tree.control.listeners.UndoRedoListener;
import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreeModel;

/**
 * Panel displaying undo/redo history with a table and button panel.
 * Supports both TreeModel-based and EditTree-based operations.
 */
public class WoodUndoPanel extends JPanel implements UndoRedoListener, TreeFocusListener {

    private final UndoTableModel undoModel;
    private final JTable undoTable;
    private final UndoManager undoMan;
    private final UndoButtonPanel buttonPanel;

    public WoodUndoPanel(MasterControl master) {
        super(new BorderLayout());
        this.undoMan = master.getUndoManager();

        undoModel = new UndoTableModel();
        undoTable = new JTable(undoModel);

        undoTable.setRowSelectionAllowed(true);
        undoTable.setColumnSelectionAllowed(false);
        undoTable.setCellSelectionEnabled(false);
        undoTable.setFocusable(false);

        // linkes Buttonpanel
        buttonPanel = new UndoButtonPanel(master);
        add(buttonPanel, BorderLayout.WEST);

        // Tabelle in der Mitte
        add(new JScrollPane(undoTable), BorderLayout.CENTER);

        master.addUndoRedoListener(this);
        master.addSelectionListener(9, this);
        undoModel.setUndoManModel(undoMan.getActiveManager());
    }

    @Override
    public void onUndo(TreeModel tm, WoodCommand cmd) {
        undoModel.fireTableDataChanged();
        selectCurrent();
    }

    @Override
    public void onRedo(TreeModel tm, WoodCommand cmd) {
        undoModel.fireTableDataChanged();
        selectCurrent();
    }

    @Override
    public void onAddCommand(TreeModel tm, WoodCommand cmd) {
        undoModel.fireTableDataChanged();
        SwingUtilities.invokeLater(this::selectCurrent);
    }

    @Override
    public void onClear(TreeModel tm) {
        undoModel.fireTableDataChanged();
        selectCurrent();
    }

    @Override
    public void onNodeSelected(Object node, Object trigger, boolean rootSelected) {
        // NoOp
    }

    @Override
    public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
        undoModel.setUndoManModel(undoMan.getActiveManager());
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

    // ===== EditCommand-based listener methods (for editor package integration) =====

    @Override
    public void onEditCommandExecuted(Object editTree, EditCommand editCommand, CommandResult result) {
        undoModel.fireTableDataChanged();
        SwingUtilities.invokeLater(this::selectCurrent);
    }

    @Override
    public void onEditCommandUndone(Object editTree, EditCommand editCommand, CommandResult result) {
        undoModel.fireTableDataChanged();
        selectCurrent();
    }

    @Override
    public void onEditCommandRedone(Object editTree, EditCommand editCommand, CommandResult result) {
        undoModel.fireTableDataChanged();
        selectCurrent();
    }

    @Override
    public void onEditCommandSkipped(Object editTree, EditCommand editCommand) {
        undoModel.fireTableDataChanged();
        selectCurrent();
    }

}
