/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.command.RenameNodeCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.tree.control.JackUndoManager;
import de.jare.tree.settings.WoodSettings;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import javax.swing.AbstractCellEditor;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellEditor;

public class JsonJackTreeCellEditor extends AbstractCellEditor implements TreeCellEditor {

    private final JTextField textField = new JTextField();
    private EditNode currentData;
    private final JackUndoManager undoMan;
    private boolean readonly = false;

    public JsonJackTreeCellEditor(JackUndoManager undoMan) {
        // Optional: Grundspaltenzahl, falls Metrics noch nicht da sind
        textField.setColumns(10);
        this.undoMan = undoMan;

        textField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateByDocument();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateByDocument();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateByDocument();
            }

            public void updateByDocument() {
                final String text = textField.getText();
                if (text != null && !text.isBlank()) {
                    updateFieldSize(text);
                }
            }
        });

    }

    @Override
    public Component getTreeCellEditorComponent(
            JTree tree, Object value, boolean isSelected,
            boolean expanded, boolean leaf, int row) {

        currentData = null;
        String text;
        if (value instanceof DefaultMutableTreeNode dmtn
                && dmtn.getUserObject() instanceof EditNode data) {
            currentData = data;
            textField.setText(text = data.getName());
            String foreKey = "light." + data.getTypeKey();
            textField.setForeground(WoodSettings.INSTANCE.getShownTheme().getColor(foreKey));
        } else {
            textField.setText(text = "");
            textField.setForeground(tree.getForeground());
        }
        updateFieldSize(text);

        textField.setEditable(!readonly);
        return textField;
    }

    private void updateFieldSize(String text) {
        if (text == null) {
            text = "";
        }

        final FontMetrics fm = textField.getFontMetrics(textField.getFont());
        // Breite des Textes + etwas Padding
        int textWidth = fm.stringWidth(text) + 10;

        // Mindestbreite: 10 Zeichen oder 128px, je nachdem was gr��er ist
        final int min10Chars = Math.max(textWidth, fm.charWidth('M') * 10);
        final int minWidth = Math.max(min10Chars, 128);

        final int width = Math.max(textWidth, minWidth);
        final Dimension size = textField.getPreferredSize();
        size.width = width;
        textField.setPreferredSize(size);
        textField.setMinimumSize(size);
        SwingUtilities.invokeLater(() -> {
            textField.setSize(size);
        });
    }

    @Override
    public Object getCellEditorValue() {
        updateEditedObject();
        return textField.getText();
    }

    protected void updateEditedObject() {
        if (readonly) {
            return;
        }
        if (currentData == null || currentData.getName() != null && currentData.getName().equals(textField.getText())) {
            return;
        }
        RenameNodeCommand command = new RenameNodeCommand(currentData, textField.getText());
        undoMan.executeCommand(command);
    }

    public void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }

    public boolean isReadonly() {
        return readonly;
    }
}
