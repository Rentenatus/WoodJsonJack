/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.command.CommandResult;
import de.jare.jsoncasted.editor.command.EditCommand;
import de.jare.tree.control.MasterControl;
import de.jare.tree.control.SelectionStackManager;
import de.jare.tree.control.UndoManager;
import de.jare.tree.control.commands.WoodCommand;
import de.jare.tree.control.listeners.ContentListener;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import de.jare.tree.control.listeners.UndoRedoListener;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.*;
import javax.swing.tree.TreeModel;

/**
 * Upper toolbar above the editor tabs with undo/redo buttons.
 */
public class WoodUpperToolbar extends JPanel implements ContentListener, TreeFocusListener, UndoRedoListener {

    private final MasterControl master;
    private final UndoManager undoMan;

    private final JButton btnUndo;
    private final JButton btnRedo;
    private final JButton btnSelBackward;
    private final JButton btnSelForward;
    private final SelectionStackManager selMan;

    public WoodUpperToolbar(MasterControl master) {
        super(new FlowLayout(FlowLayout.LEFT));
        this.master = master;
        this.undoMan = master.getUndoManager();
        this.selMan = master.getSelectionStackManager();

        btnUndo = createIconButton("/icons/undo.png", "Undo");
        btnRedo = createIconButton("/icons/redo.png", "Redo");

        btnSelBackward = createIconButton("/icons/selback.png", "Selection backward");
        btnSelForward = createIconButton("/icons/selforw.png", "Selection forward");

        btnSelBackward.setEnabled(false);
        btnSelForward.setEnabled(false);

        btnUndo.addActionListener(e -> {
            undoMan.undo();
            updateButtons();
        });
        btnRedo.addActionListener(e -> {
            undoMan.redo();
            updateButtons();
        });
        btnSelBackward.addActionListener(e -> {
            selMan.selectionBackward();
            updateButtons();
        });
        btnSelForward.addActionListener(e -> {
            selMan.selectionForward();
            updateButtons();
        });

        add(btnUndo);
        add(btnRedo);
        add(btnSelBackward);
        add(btnSelForward);

        // Tooltip global aktivieren
        ToolTipManager.sharedInstance().setEnabled(true);
        ToolTipManager.sharedInstance().setDismissDelay(10000); // 10s sichtbar
        ToolTipManager.sharedInstance().setInitialDelay(800);

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
        final boolean canBck = selMan.canBackward();
        final boolean canFwd = selMan.canForward();
        btnSelBackward.setEnabled(canBck);
        btnSelForward.setEnabled(canFwd);
    }

    protected void updateToolTips() {
        // bis zu 5 Eintr�ge nach hinten/vorne
        var back = selMan.getBackwardLabels(5);
        var fwd = selMan.getForwardLabels(5);

        btnSelBackward.setToolTipText(buildTooltip("Selection backward", back));
        btnSelForward.setToolTipText(buildTooltip("Selection forward", fwd));
    }

    private String buildTooltip(String title, java.util.List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return title;
        }
        StringBuilder sb = new StringBuilder("<html><b>")
                .append(title)
                .append("</b><br>");
        for (String line : lines) {
            sb.append(line).append("<br>");
        }
        sb.append("</html>");
        return sb.toString();
    }

    @Override
    public void onCommand(String commandId, Object trigger) {
        SwingUtilities.invokeLater(this::updateButtons);
    }

    @Override
    public void onNodeSelected(Object node, Object trigger, boolean rootSelected) {
        updateButtons();
        updateToolTips();
    }

    @Override
    public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
        updateButtons();
        updateToolTips();
    }

    @Override
    public void onUndo(TreeModel model, CommandResult historyEvent) {
        updateButtons();
    }

    @Override
    public void onExecute(TreeModel model, CommandResult historyEvent) {
        updateButtons();
    }

    @Override
    public void onSkipped(TreeModel model, EditCommand command) {
        updateButtons();
    }

    @Override
    public void onAddCommand(TreeModel model, EditCommand command) {
        SwingUtilities.invokeLater(this::updateButtons);
    }

    @Override
    public void onClear(TreeModel model) {
        SwingUtilities.invokeLater(this::updateButtons);
    }

}
