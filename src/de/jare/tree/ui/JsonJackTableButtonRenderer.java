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
 * Renderer that shows a JButton in the "^" column of the attribute and
 * fields tables. The button is only enabled (and labeled "^") when the
 * row offers something to open; otherwise it is disabled and shows a
 * blank label.
 */
public class JsonJackTableButtonRenderer extends JButton implements TableCellRenderer {

    private static final long serialVersionUID = 1L;

    private final transient IntPredicate buttonEnabled;

    /**
     * Creates the renderer.
     *
     * @param buttonEnabled tells for a model row whether its button is enabled
     */
    public JsonJackTableButtonRenderer(IntPredicate buttonEnabled) {
        this.buttonEnabled = buttonEnabled;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        final boolean enabled = buttonEnabled.test(table.convertRowIndexToModel(row));
        setEnabled(enabled);
        setText(enabled ? "^" : " ");
        return this;
    }
}
