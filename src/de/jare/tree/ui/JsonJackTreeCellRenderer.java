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
import javax.swing.SwingUtilities;
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
            String foreKey = WoodSettings.INSTANCE.getColorPrefix() + data.getTypeKey();
            editLabel.setForeground(WoodSettings.INSTANCE.getShownTheme().getColor(foreKey));
            infoLabel.setText(data.rightString() + " ");
        } else {
            editLabel.setText(String.valueOf(value));
            editLabel.setForeground(tree.getForeground());
            infoLabel.setText(" ");
        }

        // mimic selection/focus according to JTree defaults
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

        // for readability the edit label gets the same base color as the tree
        if (!selected) {
            String foreKey = data != null
                    ? WoodSettings.INSTANCE.getColorPrefix() + data.getTypeKey() : null;
            editLabel.setForeground(data != null ? WoodSettings.INSTANCE.getShownTheme().getColor(foreKey) : fg);

            // the edit status color at WARNING/ERROR overlays the type key color
            if (data != null) {
                EditStatus status = data.getEditStatus();
                if (status == EditStatus.ERROR || status == EditStatus.WARNING) {
                    String statusKey = WoodSettings.INSTANCE.getColorPrefix()
                            + "fore." + status.getLiteral();
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

        // tree diff view marks (concept CONCEPT_TreeDiffView.md sections 4 and 5): the marks live on the
        // JackEditTree this tree belongs to; a tree without marks renders exactly as before. The mark color
        // overlays the type and status colors and stays visible when selected - the mark is the point of the view.
        final DiffMark mark = diffMarkOf(tree, data);
        final boolean stale = mark != null && data instanceof EditNodeAbstract absNode
                && mark.getMerkleHashAtDiff() != absNode.getMerkleHash();
        if (mark != null) {
            if (mark.isRadiating()) {
                editLabel.setText(editLabel.getText() + " \u21bb");
            }
            if (mark.isTaken()) {
                editLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            } else {
                final DiffMark.MarkType type = stale ? DiffMark.MarkType.EDITED : mark.getType();
                final String markKey = WoodSettings.INSTANCE.getColorPrefix() + type.getThemeKey();
                final Color markColor = WoodSettings.INSTANCE.getShownTheme().getColor(markKey);
                if (markColor != null) {
                    editLabel.setForeground(markColor);
                }
            }
        }

        // tooltip with parse state, edit status and type info
        if (data != null) {
            panel.setToolTipText(buildTooltip(data, mark, stale));
        } else {
            panel.setToolTipText(null);
        }

        return panel;
    }

    /**
     * Returns the diff mark of the rendered node: the map lives on the JackEditTree the rendered tree belongs to,
     * the node identity is the key. Trees without a diff view have no map and render unchanged.
     *
     * @param tree the rendered tree.
     * @param data the rendered edit node.
     * @return the mark, or null when this node carries none.
     */
    private DiffMark diffMarkOf(JTree tree, EditNode data) {
        if (tree == null || !(data instanceof EditNodeAbstract)) {
            return null;
        }
        final Object ancestor = SwingUtilities.getAncestorOfClass(JackEditTree.class, tree);
        if (!(ancestor instanceof JackEditTree editor) || editor.getDiffMarks() == null) {
            return null;
        }
        return editor.getDiffMarks().get(data);
    }

    private String buildTooltip(EditNode data, DiffMark mark, boolean stale) {
        StringBuilder sb = new StringBuilder("<html>");
        sb.append("<b>").append(escapeHtml(data.getName())).append("</b><br>");
        if (mark != null) {
            sb.append("Diff: ").append(mark.getType().name());
            if (mark.isRadiating()) {
                sb.append(" (radiating)");
            }
            if (!mark.getTooltip().isEmpty()) {
                sb.append("<br>").append(escapeHtml(mark.getTooltip()));
            }
            if (mark.isTaken()) {
                sb.append("<br>taken into this tree");
            } else if (stale) {
                sb.append("<br>changed since the diff ran - re-diff");
            }
            sb.append("<br>");
        }
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
                sb.append("<br>Type: ").append(escapeHtml(type.getTypeName()));
            }
        } else if (data instanceof EditNodeProperty propNode) {
            JsonFieldDescriptor field = propNode.getJsonField();
            if (field != null) {
                sb.append("<br>Field: ").append(escapeHtml(field.getFieldName()));
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
