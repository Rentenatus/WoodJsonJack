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
 * detail dialog opens in read-only mode), "?" a descriptor row (jsonField, jsonType) -
 * the detail dialog shows the verbose string representation of the descriptor. A disabled
 * button shows a blank label when the row offers nothing to open.
 */
public class JsonJackTableButtonRenderer extends JButton implements TableCellRenderer {

    private static final String EDITABLE_TEXT = "^";
    private static final String READONLY_TEXT = "-";
    private static final String INFO_TEXT = "?";

    private static final long serialVersionUID = 1L;

    private final transient IntPredicate buttonEnabled;
    private final transient IntPredicate rowEditable;
    private final transient IntPredicate infoRow;

    /**
     * Creates the renderer.
     *
     * @param buttonEnabled tells for a model row whether its button is enabled
     * @param rowEditable tells for a model row whether the row is editable
     */
    public JsonJackTableButtonRenderer(IntPredicate buttonEnabled, IntPredicate rowEditable) {
        this(buttonEnabled, rowEditable, row -> false);
    }

    /**
     * Creates the renderer with the descriptor info predicate: an info row shows the
     * "?" label (instead of "^" and "-") and opens the verbose representation of its
     * descriptor in the detail dialog.
     *
     * @param buttonEnabled tells for a model row whether its button is enabled
     * @param rowEditable tells for a model row whether the row is editable
     * @param infoRow tells for a model row whether it is a descriptor info row
     */
    public JsonJackTableButtonRenderer(IntPredicate buttonEnabled, IntPredicate rowEditable,
            IntPredicate infoRow) {
        this.buttonEnabled = buttonEnabled;
        this.rowEditable = rowEditable;
        this.infoRow = infoRow;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        final int modelRow = table.convertRowIndexToModel(row);
        final boolean enabled = buttonEnabled.test(modelRow);
        setEnabled(enabled);
        if (!enabled) {
            setText(" ");
        } else if (infoRow.test(modelRow)) {
            setText(INFO_TEXT);
        } else {
            setText(rowEditable.test(modelRow) ? EDITABLE_TEXT : READONLY_TEXT);
        }
        return this;
    }
}
