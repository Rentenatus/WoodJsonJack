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
import java.util.Map;
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

        // the mode signal of this tree: no marks map = edit mode (type and parse status colors show), a map =
        // diff mode (neutral base, only the diff colors show, concept CONCEPT_TreeDiffView.md)
        final Map<EditNodeAbstract, DiffMark> treeDiffMarks = diffMarksOfTree(tree);
        final DiffMark mark = data instanceof EditNodeAbstract
                && treeDiffMarks != null ? treeDiffMarks.get(data) : null;
        final boolean stale = mark != null
                && mark.getMerkleHashAtDiff() != ((EditNodeAbstract) data).getMerkleHash();

        if (data != null) {
            editLabel.setText(data.getName());
            infoLabel.setText(data.rightString() + " ");
        } else {
            editLabel.setText(String.valueOf(value));
            infoLabel.setText(" ");
        }

        // The effective special color of this row, if any: the type/status color in edit mode, the diff mark color
        // in diff mode (stale -> edited, taken -> disabled). It decides the selection bar: a special row INVERTS
        // its spec when selected - the special color becomes the bar background and the row background the text
        // color - so the row stays recognizable as what it is, selected or not. A row without a special color
        // keeps the usual selection colors.
        Color special = null;
        if (treeDiffMarks == null) {
            if (data != null) {
                special = WoodSettings.INSTANCE.getShownTheme().getColor(
                        WoodSettings.INSTANCE.getColorPrefix() + data.getTypeKey());
                // the edit status color at WARNING/ERROR overlays the type key color - but only in edit mode: a
                // tree in diff mode shows ONLY the diff colors, a parse red would read as a REMOVE mark
                if (data.getEditStatus() == EditStatus.ERROR || data.getEditStatus() == EditStatus.WARNING) {
                    final Color statusColor = WoodSettings.INSTANCE.getShownTheme().getColor(
                            WoodSettings.INSTANCE.getColorPrefix() + "fore." + data.getEditStatus().getLiteral());
                    if (statusColor != null) {
                        special = statusColor;
                    }
                }
            }
        } else if (mark != null) {
            if (mark.isTaken()) {
                special = UIManager.getColor("Label.disabledForeground");
            } else {
                final DiffMark.MarkType type = stale ? DiffMark.MarkType.EDITED : mark.getType();
                special = WoodSettings.INSTANCE.getShownTheme().getColor(
                        WoodSettings.INSTANCE.getColorPrefix() + type.getThemeKey());
            }
        }

        // the selection bar: inverted for special rows, the usual one otherwise
        Color bg, fg;
        if (selected && special != null) {
            bg = special;
            fg = tree.getBackground();
        } else if (selected) {
            bg = UIManager.getColor("Tree.selectionBackground");
            fg = UIManager.getColor("Tree.selectionForeground");
        } else {
            bg = tree.getBackground();
            fg = tree.getForeground();
        }

        panel.setBackground(bg);
        panel.setOpaque(true);
        // edit mode: the type/status color shows (edit mode special); diff mode: a neutral base - the type
        // colors stay out of the diff view, ONLY the diff marks color their nodes
        editLabel.setForeground(!selected && special != null ? special : fg);
        infoLabel.setForeground(selected
                ? fg
                : UIManager.getColor("Label.disabledForeground"));

        // tree diff view marks (concept CONCEPT_TreeDiffView.md sections 4 and 5): a tree without marks renders
        // exactly as before; the radiating badge is the only text change, the colors flow through the special
        // color above.
        if (mark != null && mark.isRadiating()) {
            editLabel.setText(editLabel.getText() + " \u21bb");
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
     * Returns the diff marks map of the rendered tree's JackEditTree - the mode signal for the color rules: null
     * means edit mode (parse status colors show), a non-null map means diff mode (only diff colors show).
     *
     * @param tree the rendered tree.
     * @return the marks map, or null when this tree shows no diff view.
     */
    private Map<EditNodeAbstract, DiffMark> diffMarksOfTree(JTree tree) {
        if (tree == null) {
            return null;
        }
        final Object ancestor = SwingUtilities.getAncestorOfClass(JackEditTree.class, tree);
        return ancestor instanceof JackEditTree editor ? editor.getDiffMarks() : null;
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
