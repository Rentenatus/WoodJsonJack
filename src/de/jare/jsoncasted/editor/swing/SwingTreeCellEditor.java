/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.swing;

import de.jare.jsoncasted.editor.command.SetValueCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.tree.settings.WoodSettings;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import javax.swing.AbstractCellEditor;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellEditor;

/**
 * New version of JsonTreeCellEditor that uses TreeEditor and EditCommand.
 * This replaces the old de.jare.tree.ui.JsonTreeCellEditor class.
 */
public class SwingTreeCellEditor extends AbstractCellEditor implements TreeCellEditor {

    private final JTextField textField = new JTextField();
    private EditNode currentData;
    private final SwingMasterControl master;

    public SwingTreeCellEditor(SwingMasterControl master) {
        // Optional: Grundspaltenzahl, falls Metrics noch nicht da sind
        textField.setColumns(10);
        this.master = master;

        textField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateFieldSize();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateFieldSize();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateFieldSize();
            }
        });
    }

    // Backward compatibility constructor
    public SwingTreeCellEditor(de.jare.tree.control.UndoManager undoMan) {
        this((SwingMasterControl) null);
        // Legacy support - not used in new architecture
    }

    @Override
    public Component getTreeCellEditorComponent(
            JTree tree, Object value, boolean isSelected,
            boolean expanded, boolean leaf, int row) {

        currentData = null;
        if (value instanceof DefaultMutableTreeNode dmtn
                && dmtn.getUserObject() instanceof EditNode data) {
            currentData = data;
            textField.setText(data.getEditText());
            String foreKey = "light." + data.getTypeKey();
            textField.setForeground(WoodSettings.INSTANCE.getShownTheme().getColor(foreKey));
        } else {
            textField.setText("");
            textField.setForeground(tree.getForeground());
        }
        updateFieldSize();

        return textField;
    }

    private void updateFieldSize() {
        String text = textField.getText();
        if (text == null) {
            text = "";
        }

        FontMetrics fm = textField.getFontMetrics(textField.getFont());
        // Breite des Textes + etwas Padding
        int textWidth = fm.stringWidth(text) + 10;

        // Mindestbreite: 10 Zeichen oder 128px, je nachdem was gr??er ist
        int min10Chars = Math.max(textWidth, fm.charWidth('M') * 10);
        int minWidth = Math.max(min10Chars, 128);

        int width = Math.max(textWidth, minWidth);
        Dimension size = textField.getPreferredSize();
        size.width = width;
        textField.setPreferredSize(size);
        textField.setMinimumSize(size);
        textField.setSize(size);
    }

    @Override
    public Object getCellEditorValue() {
        updateEditedObject();
        return textField.getText();
    }

    protected void updateEditedObject() {
        if (currentData == null) {
            return;
        }
        
        String newValue = textField.getText();
        String oldValue = currentData.getEditText();
        
        if (oldValue != null && oldValue.equals(newValue)) {
            return;
        }
        
        // Use the new SetValueCommand from editor package
        if (master != null) {
            SetValueCommand cmd = new SetValueCommand(currentData, newValue);
            master.execute(cmd);
        } else {
            // Fallback: direct update if no master
            currentData.setEditText(newValue);
        }
    }
}
