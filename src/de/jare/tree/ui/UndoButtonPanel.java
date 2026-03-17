/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.control.MasterControl;
import de.jare.tree.control.UndoManager;
import java.awt.Dimension;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class UndoButtonPanel extends JPanel {

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
}
