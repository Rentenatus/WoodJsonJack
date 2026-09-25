/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.settings.WoodSettings;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditStatus;
import de.jare.jsoncasted.editor.core.ParseState;
import de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor;
import de.jare.jsoncasted.model.descriptor.JsonTypeDescriptor;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;

public class JsonJackTreeCellRenderer implements TreeCellRenderer {

    private final JPanel panel = new JPanel(new BorderLayout());
    private final JLabel editLabel = new JLabel();
    private final JLabel infoLabel = new JLabel();

    public JsonJackTreeCellRenderer() {
        panel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        panel.add(editLabel, BorderLayout.WEST);
        panel.add(infoLabel, BorderLayout.EAST);

        editLabel.setOpaque(false);
        infoLabel.setOpaque(false);
    }

    @Override
    public Component getTreeCellRendererComponent(
            JTree tree, Object value, boolean selected,
            boolean expanded, boolean leaf, int row, boolean hasFocus) {

        EditNode data = null;
        if (value instanceof DefaultMutableTreeNode dmtn
                && dmtn.getUserObject() instanceof EditNode d) {
            data = d;
        }

        if (data != null) {
            editLabel.setText(data.getName());
            String foreKey = "light." + data.getTypeKey();
            editLabel.setForeground(WoodSettings.INSTANCE.getShownTheme().getColor(foreKey));
            infoLabel.setText(data.rightString() + " ");
        } else {
            editLabel.setText(String.valueOf(value));
            editLabel.setForeground(tree.getForeground());
            infoLabel.setText(" ");
        }

        // Selektion/Focus nach JTree-Defaults nachbilden
        Color bg, fg;
        if (selected) {
            bg = UIManager.getColor("Tree.selectionBackground");
            fg = UIManager.getColor("Tree.selectionForeground");
        } else {
            bg = tree.getBackground();
            fg = tree.getForeground();
        }

        panel.setBackground(bg);
        panel.setOpaque(true);

        // Edit-Label bekommt für Lesbarkeit die gleiche Grundfarbe wie der Tree
        if (!selected) {
            String foreKey = data != null ? "light." + data.getTypeKey() : null;
            editLabel.setForeground(data != null ? WoodSettings.INSTANCE.getShownTheme().getColor(foreKey) : fg);

            // EditStatus-Farbe bei WARNING/ERROR überlagert die TypeKey-Farbe
            if (data != null) {
                EditStatus status = data.getEditStatus();
                if (status == EditStatus.ERROR || status == EditStatus.WARNING) {
                    String statusKey = "light.fore." + status.getLiteral();
                    Color statusColor = WoodSettings.INSTANCE.getShownTheme().getColor(statusKey);
                    if (statusColor != null) {
                        editLabel.setForeground(statusColor);
                    }
                }
            }
        } else {
            editLabel.setForeground(fg);
        }
        infoLabel.setForeground(selected
                ? fg
                : UIManager.getColor("Label.disabledForeground"));

        // Tooltip mit ParseState, EditStatus und Typ-Info
        if (data != null) {
            panel.setToolTipText(buildTooltip(data));
        } else {
            panel.setToolTipText(null);
        }

        return panel;
    }

    private String buildTooltip(EditNode data) {
        StringBuilder sb = new StringBuilder("<html>");
        sb.append("<b>").append(escapeHtml(data.getName())).append("</b><br>");
        if (data instanceof EditNodeAbstract absNode) {
            sb.append("Parse: ").append(absNode.getParseState().getName()).append("<br>");
        }
        sb.append("Status: ").append(data.getEditStatus().getName());
        String msg = data.getEditMessage();
        if (msg != null && !msg.isEmpty()) {
            sb.append(" - ").append(escapeHtml(msg));
        }
        if (data instanceof EditNodeObject objNode) {
            JsonTypeDescriptor type = objNode.getJsonType();
            if (type != null) {
                sb.append("<br>Typ: ").append(escapeHtml(type.getTypeName()));
            }
        } else if (data instanceof EditNodeProperty propNode) {
            JsonFieldDescriptor field = propNode.getJsonField();
            if (field != null) {
                sb.append("<br>Feld: ").append(escapeHtml(field.getFieldName()));
            }
        }
        sb.append("</html>");
        return sb.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }

}
