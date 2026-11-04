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

    // The row background of an unselected signal row: the tree background on the left end of the row, the
    // signal color blended with 32% into the tree background on the right end (paintComponent below). The
    // signal is the parse status in edit mode, the diff mark in diff mode; selected rows keep the inverted
    // selection bar - the gradient never fights it.
    private Color gradientLeft;
    private Color gradientRight;

    private final JPanel panel = new JPanel(new BorderLayout()) {
        @Override
        protected void paintComponent(java.awt.Graphics g) {
            if (gradientLeft != null && gradientRight != null) {
                final java.awt.Graphics2D g2 = (java.awt.Graphics2D) g;
                g2.setPaint(new java.awt.GradientPaint(0f, 0f, gradientLeft, getWidth(), 0f, gradientRight));
                g2.fillRect(0, 0, getWidth(), getHeight());
            } else {
                super.paintComponent(g);
            }
        }
    };
    private final JPanel labels = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
    private final JLabel editLabel = new JLabel();
    private final JLabel infoLabel = new JLabel();

    public JsonJackTreeCellRenderer() {
        panel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        labels.setOpaque(false);
        labels.add(editLabel);
        labels.add(infoLabel);
        panel.add(labels, BorderLayout.WEST);

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

        // Accessibility: a text prefix in front of the cell text as a second, color independent signal for
        // color-blind users - parse status in edit mode ([!] error, [?] warning), diff mark in diff mode
        // ([+] add, [-] remove, [~] modify, [\u2192] move, [\u2195] reorder, [!!] conflict, [*] stale, [\u2713] taken).
        String prefix = "";
        if (treeDiffMarks == null) {
            if (data != null && data.getEditStatus() == EditStatus.ERROR) {
                prefix = "[!] ";
            } else if (data != null && data.getEditStatus() == EditStatus.WARNING) {
                prefix = "[?] ";
            }
        } else if (mark != null) {
            prefix = prefixOf(mark, stale);
        }

        if (data != null) {
            editLabel.setText(prefix + data.getName());
            infoLabel.setText(data.rightString() + " ");
        } else {
            editLabel.setText(String.valueOf(value));
            infoLabel.setText(" ");
        }

        // The label color of the not-selected row - the type key color in edit mode (overlaid by the status
        // color at WARNING/ERROR - but only in edit mode: a tree in diff mode shows ONLY the diff colors, a
        // parse red would read as a REMOVE mark), the diff mark color in diff mode (stale -> edited, taken ->
        // disabled). The SIGNAL part of it - the status color in edit mode, the mark color in diff mode - is
        // the special color: a selected special row INVERTS its spec, the special color becomes the bar
        // background and the row background the text color. The type key colors alone are the normal text
        // colors, not a signal - they keep the usual selection bar; a stateless root carries nothing special.
        Color special = null;
        Color plain = null;
        if (treeDiffMarks == null) {
            if (data != null) {
                plain = WoodSettings.INSTANCE.getShownTheme().getColor(
                        WoodSettings.INSTANCE.getColorPrefix() + data.getTypeKey());
                // the status color overlays the type key color - but only in edit mode: a tree in diff mode
                // shows ONLY the diff colors, a parse red would read as a REMOVE mark.
                if (data.getEditStatus() == EditStatus.ERROR || data.getEditStatus() == EditStatus.WARNING) {
                    final Color statusColor = WoodSettings.INSTANCE.getShownTheme().getColor(
                            WoodSettings.INSTANCE.getColorPrefix() + "fore." + data.getEditStatus().getLiteral());
                    if (statusColor != null) {
                        plain = statusColor;
                        special = statusColor;
                    }
                }
            }
        } else if (mark != null) {
            if (mark.isTaken()) {
                plain = UIManager.getColor("Label.disabledForeground");
                special = plain;
            } else {
                final DiffMark.MarkType type = stale ? DiffMark.MarkType.EDITED : mark.getType();
                special = WoodSettings.INSTANCE.getShownTheme().getColor(
                        WoodSettings.INSTANCE.getColorPrefix() + type.getThemeKey());
                plain = special;
            }
        }

        // the selection bar: a special row paints it itself, inverted; a row without special color stays
        // transparent and lets the look and feel paint its usual bar - FlatLaf's rounded, focus aware one
        Color bg, fg;
        gradientLeft = null;
        gradientRight = null;
        if (selected && special != null) {
            bg = special;
            fg = tree.getBackground();
            panel.setOpaque(true);
        } else if (selected) {
            bg = null;
            fg = UIManager.getColor("Tree.selectionForeground");
            panel.setOpaque(false);
        } else {
            bg = tree.getBackground();
            fg = plain != null ? plain : tree.getForeground();
            panel.setOpaque(true);
            // unselected signal row: the gradient bar - tree background on the left, the signal color (32%)
            // blended with the tree background (68%) on the right. Signals: the parse status in edit mode,
            // the diff mark in diff mode; a taken mark is grayed out and stays flat. The underlay is the
            // gradientUnderlay switch of the user preferences - someone bothered by it turns it off there.
            if (special != null
                    && !(treeDiffMarks != null && mark != null && mark.isTaken())
                    && WoodSettings.INSTANCE.getUserPreferences().isGradientUnderlay()) {
                gradientLeft = bg;
                gradientRight = blend(special, bg, 0.32f);
            }
        }

        panel.setBackground(bg);
        editLabel.setForeground(fg);
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
     * Blends two colors: the first with the given weight, the second with the rest. Both stay opaque.
     *
     * @param first the dominant color, not null.
     * @param second the base color, not null.
     * @param weight the weight of the first color (0 to 1).
     * @return the blended color, never null.
     */
    private static Color blend(Color first, Color second, float weight) {
        final float[] f = first.getRGBColorComponents(null);
        final float[] s = second.getRGBColorComponents(null);
        return new Color(
                weight * f[0] + (1 - weight) * s[0],
                weight * f[1] + (1 - weight) * s[1],
                weight * f[2] + (1 - weight) * s[2]);
    }

    /**
     * Returns the accessibility prefix of a diff mark - the color independent twin of the mark color. The
     * stale mark shows its own prefix ([*] edited since the diff ran); a taken mark shows the checkmark only
     * (it is grayed out anyway).
     *
     * @param mark the diff mark, not null.
     * @param stale whether the marked node changed since the diff ran.
     * @return the prefix with trailing space, never null.
     */
    private static String prefixOf(DiffMark mark, boolean stale) {
        if (mark.isTaken()) {
            return "[\u2713] ";
        }
        final DiffMark.MarkType type = stale ? DiffMark.MarkType.EDITED : mark.getType();
        return switch (type) {
            case ADD -> "[+] ";
            case REMOVE -> "[-] ";
            case MODIFY -> "[~] ";
            case MOVE -> "[\u2192] ";
            case REORDER -> "[\u2195] ";
            case CONFLICT -> "[!!] ";
            case EDITED -> "[*] ";
        };
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
