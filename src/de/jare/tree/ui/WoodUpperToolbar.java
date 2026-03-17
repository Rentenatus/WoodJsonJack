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
import de.jare.tree.control.listeners.ContentListener;
import de.jare.tree.control.listeners.TreeSelectionListener;
import de.jare.tree.control.listeners.UndoRedoListener;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.*;
import javax.swing.tree.TreeModel;

/**
 * Upper toolbar above the editor tabs with undo/redo buttons.
 */
public class WoodUpperToolbar extends JPanel implements ContentListener, TreeSelectionListener, UndoRedoListener {

    private final MasterControl master;
    private final UndoManager undoMan;

    private final JButton btnUndo;
    private final JButton btnRedo;

    public WoodUpperToolbar(MasterControl master) {
        super(new FlowLayout(FlowLayout.LEFT));
        this.master = master;
        this.undoMan = master.getUndoManager();

        btnUndo = createIconButton("/icons/undo.png", "Undo");
        btnRedo = createIconButton("/icons/redo.png", "Redo");

        btnUndo.addActionListener(e -> {
            undoMan.undo();
            updateButtons();
        });
        btnRedo.addActionListener(e -> {
            undoMan.redo();
            updateButtons();
        });

        add(btnUndo);
        add(btnRedo);

        // initialer Zustand
        updateButtons();

        // im MasterControl registrieren:
        master.addContentListener(10, this);
        master.addSelectionListener(9, this);
        master.addUndoRedoListener(this);
    }

    private JButton createIconButton(String resource, String tooltip) {
        Icon icon = new ImageIcon(getClass().getResource(resource));
        JButton b = new JButton(icon);
        b.setToolTipText(tooltip);
        b.setFocusPainted(false);
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
    }

    @Override
    public void onCommand(String commandId, Object trigger) {
        SwingUtilities.invokeLater(this::updateButtons);
    }

    @Override
    public void onNodeSelected(Object node, Object trigger, boolean rootSelected) {
        updateButtons();
    }

    @Override
    public void onEditorSelected(JTree editor, Object trigger) {
        updateButtons();
    }

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
        SwingUtilities.invokeLater(this::updateButtons);
    }

    @Override
    public void onClear(TreeModel model) {
        SwingUtilities.invokeLater(this::updateButtons);
    }
}
