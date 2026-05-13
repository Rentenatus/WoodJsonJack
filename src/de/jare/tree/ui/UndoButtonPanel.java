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
import java.awt.Dimension;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.TreeModel;

/**
 * Panel with undo, redo, and skip redo buttons.
 * Supports both TreeModel-based and EditTree-based operations.
 */
public class UndoButtonPanel extends JPanel implements UndoRedoListener, TreeFocusListener {

    private final UndoManager undoMan;
    private final JButton btnUndo;
    private final JButton btnRedo;
    private final JButton btnSkipRedo;

    public UndoButtonPanel(MasterControl master) {
        this.undoMan = master.getUndoManager();

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(4, 4, 4, 4));

        btnUndo = createIconButton("/icons/undo.png", "Undo");
        btnRedo = createIconButton("/icons/redo.png", "Redo");
        btnSkipRedo = createIconButton("/icons/skip_redo.png", "Skip Redo");

        btnUndo.addActionListener(e -> undoMan.undo());
        btnRedo.addActionListener(e -> undoMan.redo());
        btnSkipRedo.addActionListener(e -> undoMan.skip_redo());

        add(btnUndo);
        add(Box.createVerticalStrut(4));
        add(btnRedo);
        add(Box.createVerticalStrut(8));
        add(btnSkipRedo);

        // Register for EditTree-based events
        master.addUndoRedoListener(this);
        master.addSelectionListener(9, this);

        // initialer Zustand
        updateButtons();
    }

    private JButton createIconButton(String resource, String tooltip) {
        Icon icon = new ImageIcon(getClass().getResource(resource));
        JButton b = new JButton(icon);
        b.setToolTipText(tooltip);
        b.setFocusable(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(true);
        b.setOpaque(false);
        // kleine Standardgroesse
        b.setMaximumSize(new Dimension(32, 32));
        b.setPreferredSize(new Dimension(32, 32));
        return b;
    }

    protected final void updateButtons() {
        final boolean canUndo = undoMan.canUndo();
        final boolean canRedo = undoMan.canRedo();
        btnUndo.setEnabled(canUndo);
        btnRedo.setEnabled(canRedo);
        btnSkipRedo.setEnabled(canRedo);
    }

    // ===== WoodCommand-based listener methods (backward compatible) =====

    @Override
    public void onUndo(TreeModel model, WoodCommand command) {
        updateButtons();
    }

    @Override
    public void onRedo(TreeModel model, WoodCommand command) {
        updateButtons();
    }

    @Override
    public void onAddCommand(TreeModel model, WoodCommand command) {
        updateButtons();
    }

    @Override
    public void onClear(TreeModel model) {
        updateButtons();
    }

    @Override
    public void onNodeSelected(Object node, Object trigger, boolean rootSelected) {
        // NoOp
    }

    @Override
    public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
        updateButtons();
    }

    // ===== EditCommand-based listener methods (for editor package integration) =====

    @Override
    public void onEditCommandExecuted(Object editTree, EditCommand editCommand, CommandResult result) {
        updateButtons();
    }

    @Override
    public void onEditCommandUndone(Object editTree, EditCommand editCommand, CommandResult result) {
        updateButtons();
    }

    @Override
    public void onEditCommandRedone(Object editTree, EditCommand editCommand, CommandResult result) {
        updateButtons();
    }

    @Override
    public void onEditCommandSkipped(Object editTree, EditCommand editCommand) {
        updateButtons();
    }

}
