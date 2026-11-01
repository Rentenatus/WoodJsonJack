/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.command.RenameNodeCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.HardEditAdvisor;
import de.jare.jsoncasted.editor.core.ParseMode;
import de.jare.tree.control.JackUndoManager;
import de.jare.tree.control.fastlog.FastLog;
import de.jare.tree.control.model.JackTreeModel;
import de.jare.tree.settings.WoodSettings;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.util.List;
import javax.swing.AbstractCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellEditor;

public class JsonJackTreeCellEditor extends AbstractCellEditor implements TreeCellEditor {

    private final JTextField textField = new JTextField();
    private JComboBox<String> comboBox;
    private EditNode currentData;
    private JTree currentTree;
    private final JackUndoManager undoMan;
    private final FastLog fastLog;
    private boolean readonly = false;

    public JsonJackTreeCellEditor(JackUndoManager undoMan) {
        this(undoMan, null);
    }

    /**
     * Creates the tree cell editor with the fast log of the master control: a rename refused
     * by the hard parse mode leaves a note instead of silently vanishing.
     *
     * @param undoMan the undo manager of the master control
     * @param fastLog the fast log of the master control, may be {@code null}
     */
    public JsonJackTreeCellEditor(JackUndoManager undoMan, FastLog fastLog) {
        // optional: base column count in case metrics are not available yet
        textField.setColumns(10);
        this.undoMan = undoMan;
        this.fastLog = fastLog;

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
        currentTree = tree;
        comboBox = null;
        String text;
        if (value instanceof DefaultMutableTreeNode dmtn
                && dmtn.getUserObject() instanceof EditNode data) {
            currentData = data;
            textField.setText(text = data.getName());
            String foreKey = WoodSettings.INSTANCE.getColorPrefix() + data.getTypeKey();
            textField.setForeground(WoodSettings.INSTANCE.getShownTheme().getColor(foreKey));
        } else {
            textField.setText(text = "");
            textField.setForeground(tree.getForeground());
        }
        updateFieldSize(text);

        // The hard parse mode edits names only through the permissible candidates of the
        // model (combobox) - free text stays refused there (parse mode concept, decision 2).
        final List<String> candidates = permissibleRenames();
        if (!candidates.isEmpty()) {
            comboBox = new JComboBox<>(candidates.toArray(new String[0]));
            comboBox.setSelectedItem(text);
            comboBox.addActionListener(e -> stopCellEditing());
            return comboBox;
        }
        textField.setEditable(!readonly);
        return textField;
    }

    /**
     * The permissible new names for the edited node in the hard parse mode: ALL declared
     * fields of the owning type - constructor params and fields, the supertype chain
     * included. Nothing without a typed parent or a known model - the rename stays refused
     * there (see {@link #updateEditedObject()}).
     *
     * @return the permissible field names, empty outside the hard parse mode
     */
    private List<String> permissibleRenames() {
        if (currentData == null || !isHardParseMode()
                || !(currentTree.getModel() instanceof JackTreeModel model)
                || !(currentData instanceof EditNodeAbstract)) {
            return List.of();
        }
        return HardEditAdvisor.permissibleRenames((EditNodeAbstract) currentData,
                model.getJsonModelDescriptor());
    }

    private void updateFieldSize(String text) {
        if (text == null) {
            text = "";
        }

        final FontMetrics fm = textField.getFontMetrics(textField.getFont());
        // width of the text plus some padding
        int textWidth = fm.stringWidth(text) + 10;

        // minimum width: 10 characters or 128px, whichever is greater
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
        final String newValue = comboBox != null && comboBox.getSelectedItem() != null
                ? comboBox.getSelectedItem().toString() : textField.getText();
        comboBox = null;
        if (currentData == null || currentData.getName() != null && currentData.getName().equals(newValue)) {
            return;
        }
        if (isHardParseMode()) {
            // The hard parse mode allows renames only as a choice among the permissible
            // names of the model (decision 2): a candidate re-binds like the jsonField
            // exception - the name is model-known, the parser judges the content, a
            // parse ERROR rolls the rename back with a fast log note. Free text stays
            // refused - the refusal must not be silent.
            if (permissibleRenames().contains(newValue)) {
                JsonJackHardRenameGuard.executeGuarded(undoMan, fastLog, currentData, newValue);
                return;
            }
            if (fastLog != null) {
                fastLog.tryWrite("editor", "[hard parse] rename of '" + currentData.getName()
                        + "' to '" + newValue + "' rejected: '" + newValue + "' is no permissible "
                        + "field of the owning type - pick one of the offered names instead");
            }
            return;
        }
        RenameNodeCommand command = new RenameNodeCommand(currentData, newValue);
        undoMan.executeCommand(command);
    }

    /**
     * Checks whether the edited tree is in the hard parse mode: the current tree model of the
     * edited JTree carries the edit tree, so the check survives model swaps (a loaded file
     * replaces the model of the same JTree).
     *
     * @return true when the edited tree parses hard
     */
    private boolean isHardParseMode() {
        return currentTree != null
                && currentTree.getModel() instanceof JackTreeModel model
                && model.getEditTree() != null
                && model.getEditTree().getParseMode() == ParseMode.HARD_PARSE;
    }

    public void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }

    public boolean isReadonly() {
        return readonly;
    }
}
