/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import java.awt.Component;
import java.util.function.IntPredicate;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

/**
 * Renderer that shows a JButton in the "^" column of the attribute and fields tables: "^"
 * marks an editable row (inline or through its editor dialog), "-" a read-only row (the
 * detail dialog opens in read-only mode). A disabled button shows a blank label when the row
 * offers nothing to open.
 */
public class JsonJackTableButtonRenderer extends JButton implements TableCellRenderer {

    private static final String EDITABLE_TEXT = "^";
    private static final String READONLY_TEXT = "-";

    private static final long serialVersionUID = 1L;

    private final transient IntPredicate buttonEnabled;
    private final transient IntPredicate rowEditable;

    /**
     * Creates the renderer.
     *
     * @param buttonEnabled tells for a model row whether its button is enabled
     * @param rowEditable tells for a model row whether the row is editable
     */
    public JsonJackTableButtonRenderer(IntPredicate buttonEnabled, IntPredicate rowEditable) {
        this.buttonEnabled = buttonEnabled;
        this.rowEditable = rowEditable;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        final int modelRow = table.convertRowIndexToModel(row);
        final boolean enabled = buttonEnabled.test(modelRow);
        setEnabled(enabled);
        if (!enabled) {
            setText(" ");
        } else {
            setText(rowEditable.test(modelRow) ? EDITABLE_TEXT : READONLY_TEXT);
        }
        return this;
    }
}
