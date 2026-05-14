/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.control.UndoManager;
import de.jare.tree.control.commands.WoodCommand;
import de.jare.tree.control.commands.WoodCommandEditNodeData;
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
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.tree.control.JackUndoManager;

public class JackJsonTreeCellEditor extends AbstractCellEditor implements TreeCellEditor {

    private final JTextField textField = new JTextField();
    private EditNode currentData;
    private final JackUndoManager undoMan;

    public JackJsonTreeCellEditor(JackUndoManager undoMan) {
        // Optional: Grundspaltenzahl, falls Metrics noch nicht da sind
        textField.setColumns(10);
        this.undoMan = undoMan;

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

        // Mindestbreite: 10 Zeichen oder 128px, je nachdem was gr��er ist
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
        if (currentData == null || currentData.getEditText() != null && currentData.getEditText().equals(textField.getText())) {
            return;
        }
        EditNode oldData = currentData.deepCopy(false);
        currentData.setEditText(textField.getText());
        WoodCommand command = new WoodCommandEditNodeData(currentData, oldData, currentData);
        undoMan.pushCommand(command);
    }

}
