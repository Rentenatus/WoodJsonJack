/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.tree.control.JackMasterControl;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.TableColumn;

/**
 * Object pane: a view on the model side of the selected object. The first row
 * shows the json type, one row per field follows. A clicked field still shows
 * the owning object and focuses the field's row. A parsed object appends its
 * missing declared fields as empty rows - an edit creates the child node. The
 * pane is a sandbox: it accepts changes (inline value edits and the results
 * of the list editors) and writes them back only through the two exceptions
 * of the sandbox contract - primitive values and missing field creations
 * reach the node as commands, everything else stays in the pane. The model is
 * held in a field on purpose: the orator of the master control keeps its
 * listeners via weak references only, the garbage collector would silently
 * deregister a local model.
 */
public class JsonJackObjectPane extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JsonJackObjectTableModel fieldsModel;
    private final JTable fieldsTable;
    private final Frame owner;

    public JsonJackObjectPane(JackMasterControl jackmaster, Frame owner) {
        super(new BorderLayout());
        this.owner = owner;
        fieldsModel = new JsonJackObjectTableModel(jackmaster.getUndoManager(), jackmaster.getFastLog());
        jackmaster.addSelectionListener(fieldsModel);
        jackmaster.addContentListener(fieldsModel);
        jackmaster.addParseProblemsListener(fieldsModel);
        jackmaster.addUndoRedoListener(fieldsModel);
        fieldsTable = new JTable(fieldsModel);
        fieldsTable.setFillsViewportHeight(true);
        fieldsTable.getTableHeader().setVisible(true);
        fieldsTable.setDefaultEditor(Object.class, new JsonJackComboBoxCellEditor());
        installFieldsDetailButtonColumn();

        // After every model refresh: focus the row of the field clicked in the tree.
        fieldsTable.getModel().addTableModelListener(e -> applyFocusedField());

        // A node switch rebuilds the rows underneath an open cell editor: the stale editor
        // keeps its row index and would write into the wrong node on commit - cancel it on
        // every whole-table rebuild (fireTableDataChanged: firstRow 0, lastRow MAX_VALUE).
        fieldsTable.getModel().addTableModelListener(e -> {
            if (e.getFirstRow() == 0 && e.getLastRow() == Integer.MAX_VALUE
                    && fieldsTable.isEditing()) {
                fieldsTable.removeEditor();
            }
        });

        // Array fields are not edited inline: clicking the value cell opens the collection editor
        // (object lists) or the primitive list editor (primitive lists and array annotations).
        // The "^" column opens the same editors on array rows, the detail dialog otherwise.
        fieldsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                final int viewRow = fieldsTable.rowAtPoint(e.getPoint());
                final int viewCol = fieldsTable.columnAtPoint(e.getPoint());
                if (viewRow < 0 || viewCol < 0) {
                    return;
                }
                final int row = fieldsTable.convertRowIndexToModel(viewRow);
                if (fieldsTable.convertColumnIndexToModel(viewCol) == 4) {
                    if (fieldsModel.isButtonEnabled(row)) {
                        if (fieldsModel.isDescriptorInfoRow(row)) {
                            openDescriptorInfoDialog(row);
                        } else if (fieldsModel.isArrayFieldRow(row)) {
                            openArrayFieldEditor(row);
                        } else if (fieldsModel.isSingleObjectFieldRow(row)) {
                            openSingleObjectFieldEditor(row);
                        } else {
                            openFieldDetailDialog(row);
                        }
                    }
                    return;
                }
                if (viewCol == 1 && fieldsModel.isArrayFieldRow(row)) {
                    openArrayFieldEditor(row);
                }
            }
        });

        add(new JScrollPane(fieldsTable), BorderLayout.CENTER);
    }

    /**
     * Returns the table of the pane - test access to the editor guard.
     *
     * @return the fields table
     */
    javax.swing.JTable getTable() {
        return fieldsTable;
    }

    /**
     * Selects and scrolls to the row of the field that was clicked in the tree - the pane always shows the owning
     * object, the clicked field gets the focus. The row is matched by edit id inside the model.
     */
    private void applyFocusedField() {
        // Deferred on purpose: JTable clears the selection model while processing
        // fireTableDataChanged - AFTER the TableModelListener has run. Applying the
        // focus inside the listener is therefore destroyed immediately. On the event
        // queue the selection lands after the whole rebuild cascade (selection, content
        // command, parse problems), and the last queued call wins.
        javax.swing.SwingUtilities.invokeLater(() -> {
            final int row = fieldsModel.getFocusedRowIndex();
            if (row >= 0 && row < fieldsTable.getRowCount()) {
                fieldsTable.clearSelection();
                fieldsTable.getSelectionModel().setSelectionInterval(row, row);
                fieldsTable.scrollRectToVisible(fieldsTable.getCellRect(row, 0, true));
            }
        });
    }

    /**
     * Opens the dedicated editor of an array field: the collection editor for object lists, the primitive list
     * editor for primitive lists and array annotations. The result is applied to the sandbox of the pane only -
     * the edit node is never written back, whatever the parse mode of the tree is.
     *
     * @param row the model row of the array field
     */
    private void openArrayFieldEditor(int row) {
        if (fieldsModel.isPrimitiveArrayListRow(row)) {
            final JsonJackPrimitiveListEditorDialog dialog = new JsonJackPrimitiveListEditorDialog(owner,
                    String.valueOf(fieldsModel.getValueAt(row, 0)),
                    fieldsModel.getCurrentPrimitiveList(row));
            dialog.setVisible(true);
            if (dialog.isAccepted()) {
                fieldsModel.applyPrimitiveListResult(row, dialog.getResult());
            }
        } else {
            openCollectionEditor(row, JsonJackCollectionEditorDialog.Cardinality.MANY);
        }
    }

    /**
     * Opens the collection editor in single mode on a parsed object reference field: the choice
     * replaces the field value, an empty choice sets it to null. Both is always allowed - there
     * is no nullable declaration in the model yet.
     *
     * @param row the model row of the object reference field
     */
    private void openSingleObjectFieldEditor(int row) {
        openCollectionEditor(row, JsonJackCollectionEditorDialog.Cardinality.SINGLE);
    }

    /**
     * Opens the collection editor with the given target cardinality. The result is applied to the sandbox of the
     * pane only - the edit node is never written back, whatever the parse mode of the tree is.
     *
     * @param row the model row of the edited field
     * @param cardinality the target cardinality of the edited field
     */
    private void openCollectionEditor(int row, JsonJackCollectionEditorDialog.Cardinality cardinality) {
        final JsonJackCollectionEditorDialog dialog = new JsonJackCollectionEditorDialog(owner,
                String.valueOf(fieldsModel.getValueAt(row, 0)),
                fieldsModel.getInstanceCandidates(row),
                fieldsModel.getCurrentObjectList(row),
                cardinality);
        dialog.setVisible(true);
        if (dialog.isAccepted()) {
            fieldsModel.applyObjectListResult(row, dialog.getResult());
        }
    }

    /**
     * Installs the button column (column 4) of the fields table: a field rendered as a button
     * that opens the editor of the row on click - the list editors for array fields, the single
     * mode collection editor for parsed object reference fields, the detail dialog for every
     * other row with a value.The button shows "^" for an editable row (see
    {@link JsonJackObjectTableModel#isEditableRow(int)}) and "-" for a read-only row whose
 dialog opens in read-only mode.
     */
    private void installFieldsDetailButtonColumn() {
        TableColumn buttonCol = fieldsTable.getColumnModel().getColumn(4);
        buttonCol.setCellRenderer(new JsonJackTableButtonRenderer(
                fieldsModel::isButtonEnabled, fieldsModel::isEditableRow,
                fieldsModel::isDescriptorInfoRow));
        buttonCol.setPreferredWidth(36);
        buttonCol.setMaxWidth(36);
        buttonCol.setMinWidth(28);
        buttonCol.setResizable(false);
    }

    /**
     * Opens the descriptor info dialog of the row: the verbose string representation of
     * the JsonTypeDescriptor of the jsonType header, read-only.
     *
     * @param modelRow the model row index
     */
    private void openDescriptorInfoDialog(int modelRow) {
        EditNode node = fieldsModel.getCurrentObject();
        String nodeName = (node != null && node.getName() != null) ? node.getName() : "";
        String nodePath = JsonJackNodePaths.buildNodePath(node);
        String text = fieldsModel.descriptorInfoText(modelRow);
        JsonJackAttrDetailDialog dialog = new JsonJackAttrDetailDialog(
                owner, nodeName, nodePath, "", String.valueOf(fieldsModel.getValueAt(modelRow, 0)),
                text == null ? "" : text, false, null);
        dialog.setVisible(true);
    }

    /**
     * Opens the detail dialog on a field row of the Fields pane: name, value and json type of
     * the field, writable when the row is inline editable. The accepted value lands in the
     * sandbox of the pane - the edit node is never written back.
     *
     * @param modelRow the model row of the field
     */
    private void openFieldDetailDialog(int modelRow) {
        EditNode node = fieldsModel.getCurrentObject();
        String nodeName = (node != null && node.getName() != null) ? node.getName() : "";
        String nodePath = JsonJackNodePaths.buildNodePath(node);
        Object value = fieldsModel.getValueAt(modelRow, 1);
        Object jsonType = fieldsModel.getValueAt(modelRow, 3);
        JsonJackAttrDetailDialog dialog = new JsonJackAttrDetailDialog(
                owner, nodeName, nodePath,
                jsonType == null ? "" : jsonType.toString(),
                String.valueOf(fieldsModel.getValueAt(modelRow, 0)),
                value == null ? "" : value.toString(),
                fieldsModel.isCellEditable(modelRow, 1),
                newValue -> fieldsModel.setValueAt(newValue, modelRow, 1));
        dialog.setVisible(true);
    }
}
