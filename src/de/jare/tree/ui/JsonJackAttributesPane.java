/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditStatus;
import de.jare.tree.control.JackMasterControl;
import de.jare.tree.settings.WoodSettings;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

/**
 * Attributes pane: the properties of the node selected in the tree as a table
 * (name, value, type). The "^" column opens the detail dialog of a row. The
 * model is held in a field on purpose: the orator of the master control keeps
 * its listeners via weak references only, the garbage collector would silently
 * deregister a local model.
 */
public class JsonJackAttributesPane extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JsonJackAttrTableModel attributesModel;
    private final JTable attributesTable;
    private final Frame owner;

    public JsonJackAttributesPane(JackMasterControl jackmaster, Frame owner) {
        super(new BorderLayout());
        this.owner = owner;
        attributesModel = new JsonJackAttrTableModel();
        jackmaster.addSelectionListener(attributesModel);
        attributesTable = new JTable(attributesModel);
        attributesTable.setFillsViewportHeight(true);
        attributesTable.getTableHeader().setVisible(true);

        // edit status color at WARNING/ERROR for the text columns (Name, Value, Type)
        attributesTable.setDefaultRenderer(String.class, new EditStatusCellRenderer());

        // combo box editor for the value column (jsonType, jsonField rows)
        attributesTable.setDefaultEditor(String.class, new JsonJackComboBoxCellEditor());

        installAttrDetailButtonColumn();

        add(new JScrollPane(attributesTable), BorderLayout.CENTER);
    }

    /**
     * Installs the "^" column (column 3) of the attribute table: a field
     * rendered as a button that opens the detail dialog on click when the
     * attribute value is neither null nor empty.
     */
    private void installAttrDetailButtonColumn() {
        TableColumn buttonCol = attributesTable.getColumnModel().getColumn(3);
        buttonCol.setCellRenderer(new JsonJackTableButtonRenderer(attributesModel::isButtonEnabled));
        buttonCol.setPreferredWidth(36);
        buttonCol.setMaxWidth(36);
        buttonCol.setMinWidth(28);
        buttonCol.setResizable(false);

        attributesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewCol = attributesTable.columnAtPoint(e.getPoint());
                int viewRow = attributesTable.rowAtPoint(e.getPoint());
                if (viewRow < 0 || viewCol < 0) {
                    return;
                }
                if (attributesTable.convertColumnIndexToModel(viewCol) != 3) {
                    return;
                }
                int modelRow = attributesTable.convertRowIndexToModel(viewRow);
                if (!attributesModel.isButtonEnabled(modelRow)) {
                    return;
                }
                openAttrDetailDialog(modelRow);
            }
        });
    }

    /**
     * Opens the attribute detail dialog for the given row.
     *
     * @param modelRow the model row index
     */
    private void openAttrDetailDialog(int modelRow) {
        JsonJackAttrTableModel.PropertyRow row = attributesModel.getRow(modelRow);
        if (row == null) {
            return;
        }
        EditNode node = attributesModel.getCurrentEditNode();
        String nodeName = (node != null && node.getName() != null) ? node.getName() : "";
        String nodePath = JsonJackNodePaths.buildNodePath(node);
        String value = row.value() == null ? "" : row.value().toString();
        boolean editable = attributesModel.isAttributeEditable(modelRow);
        JsonJackAttrDetailDialog dialog = new JsonJackAttrDetailDialog(
                owner, nodeName, nodePath, row.type(), row.name(), value, editable,
                newValue -> attributesModel.setValueAt(newValue, modelRow, 1));
        dialog.setVisible(true);
    }

    /**
     * Renderer for the text columns of the attribute table. Overlays the
     * foreground color with the edit status color of the selected node when
     * it has status ERROR or WARNING – analogous to the
     * JsonJackTreeCellRenderer.
     */
    private static class EditStatusCellRenderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            c.setForeground(table.getForeground());
            if (isSelected || !(table.getModel() instanceof JsonJackAttrTableModel model)) {
                return c;
            }
            int modelRow = table.convertRowIndexToModel(row);
            JsonJackAttrTableModel.PropertyRow attrRow = model.getRow(modelRow);
            if (attrRow == null || !"|edit status".equals(attrRow.name())) {
                return c;
            }
            EditNode node = model.getCurrentEditNode();
            if (node == null) {
                return c;
            }
            EditStatus status = node.getEditStatus();
            Color statusColor = (status == EditStatus.ERROR
                    || status == EditStatus.WARNING
                    || status == EditStatus.OKAY)
                            ? (WoodSettings.INSTANCE.getShownTheme()
                                    .getColor(WoodSettings.INSTANCE.getColorPrefix()
                                            + "fore." + status.getLiteral()))
                            : null;
            if (statusColor != null) {
                c.setForeground(statusColor);
            }
            return c;
        }
    }
}
