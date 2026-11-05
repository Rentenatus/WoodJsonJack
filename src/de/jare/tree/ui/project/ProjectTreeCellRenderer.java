/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.project;

import de.jare.tree.settings.WoodSettings;
import de.jare.tree.settings.theme.AScheme;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeCellRenderer;

/**
 * Renderer of the project sidebar: partitions carry their role as suffix so the virtual level 1
 * reads as what it is - mounts, not folders. Read-only mounts render gray, files keep the plain
 * look. A broken bundle colors like a parse error in JSON: the theme's error tone (double bang
 * marker) for a missing main file, the warning tone (single bang) for a degraded descriptor - the
 * same fore.error / fore.warning keys the parsed trees use, so both worlds speak one color
 * language. The signal row carries the gradient underlay of the json tree - background on the
 * left, the signal color blended 32% in on the right, never behind a selection - under the same
 * gradientUnderlay switch of the user preferences, so one switch governs both worlds.
 *
 * @author Janusch Rentenatus
 */
public class ProjectTreeCellRenderer extends DefaultTreeCellRenderer {

    // fallbacks of the scheme defaults (ColorScheme) - only used when a theme carries no colors
    private static final Color FALLBACK_ERROR = new Color(195, 0, 0);
    private static final Color FALLBACK_WARNING = new Color(127, 127, 0);

    // the row gradient of an unselected signal row (see the json tree renderer), only set when
    // the switch is on and this row carries a signal
    private Color gradientLeft;
    private Color gradientRight;

    public ProjectTreeCellRenderer() {
        // no state - the look and feel decides the colors
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
            boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        gradientLeft = null;
        gradientRight = null;
        if (value instanceof ProjectTreeNode) {
            ProjectTreeNode node = (ProjectTreeNode) value;
            setText(node.toString());
            setToolTipText(node.toTooltip());
            if (node.getKind() == ProjectTreeNode.Kind.PARTITION && node.getPartition() != null
                    && node.getPartition().getType() != null) {
                setText(node.toString() + " [" + node.getPartition().getType().getName() + "]");
            }
            if (node.getKind() == ProjectTreeNode.Kind.BUNDLE) {
                setText(node.toString() + " [bundle]");
            }
            // a scan problem says so in the label - the tooltip carries the reason
            if (node.getWarning() != null) {
                setText(node.toString() + " [!]");
            }
            // a broken bundle (missing main file) says so louder - the parse error tone
            if (node.getError() != null) {
                setText(node.toString() + " [!!]");
            }
            // inside a bundle the file rows carry their roles (concept phase 19, 20 and 21):
            // the star prefix (space before the name) marks the main file, the protected marker
            // the descriptor-carried read-only fence, and the GHOST prefix an undeclared file -
            // it lies in the bundle folder but not in the descriptor's truth, so it haunts
            // (register it and the ghost disappears; star and fence are for declared files)
            if (node.getKind() == ProjectTreeNode.Kind.FILE
                    && node.getParent() instanceof ProjectTreeNode parentBundle
                    && parentBundle.getKind() == ProjectTreeNode.Kind.BUNDLE
                    && parentBundle.getDescriptor() != null
                    && node.getFile() != null) {
                String text = node.toString();
                String fileName = node.getFile().getName();
                if (!parentBundle.getDescriptor().hasFile(fileName)) {
                    text = "\uD83D\uDC7B " + text;
                } else {
                    if (fileName.equals(parentBundle.getDescriptor().getMain())) {
                        text = "★ " + text;
                    }
                    if (parentBundle.getDescriptor().isProtected(fileName)) {
                        text = text + " [protected]";
                    }
                }
                setText(text);
            }
            if (sel) {
                setForeground(null);
            } else if (node.getError() != null) {
                setForeground(schemeColor("fore.error", FALLBACK_ERROR));
                prepareGradient(tree, schemeColor("fore.error", FALLBACK_ERROR));
            } else if (node.getWarning() != null) {
                setForeground(schemeColor("fore.warning", FALLBACK_WARNING));
                prepareGradient(tree, schemeColor("fore.warning", FALLBACK_WARNING));
            } else if (node.isReadonlyMount()) {
                Color disabled = UIManager.getColor("Label.disabledForeground");
                setForeground(disabled != null ? disabled : Color.GRAY);
            } else {
                setForeground(null);
            }
        }
        return this;
    }

    /**
     * Unselected signal row: the gradient bar like the json tree - tree background on the left,
     * the signal color blended 32% into the background on the right, only when the
     * gradientUnderlay switch is on (one switch for both tree worlds).
     *
     * @param tree the rendering tree (the background source)
     * @param signal the signal color of this row
     */
    private void prepareGradient(JTree tree, Color signal) {
        if (!WoodSettings.INSTANCE.getUserPreferences().isGradientUnderlay()) {
            return;
        }
        final Color background = tree.getBackground();
        gradientLeft = background;
        gradientRight = blend(signal, background, 0.32f);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (gradientLeft != null && gradientRight != null) {
            setOpaque(false);
            final Graphics2D g2 = (Graphics2D) g;
            g2.setPaint(new GradientPaint(0f, 0f, gradientLeft, getWidth(), 0f, gradientRight));
            g2.fillRect(0, 0, getWidth(), getHeight());
            super.paintComponent(g);
        } else {
            super.paintComponent(g);
        }
    }

    /**
     * Reads a theme color the way the parsed JSON trees do: the current scheme's prefix (light.
     * or dark.) plus the key, with the scheme default as the never-null fallback.
     *
     * @param suffix the key without its scheme prefix (fore.error, fore.warning, ...)
     * @param fallback the color used when the theme carries none
     * @return the theme color, never null
     */
    private static Color schemeColor(String suffix, Color fallback) {
        try {
            final String prefix = WoodSettings.INSTANCE.getColorPrefix();
            final Color color = WoodSettings.INSTANCE.getShownTheme().getColor(prefix + suffix);
            return color != null ? color : fallback;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    /**
     * Blends the first color into the second with the given weight - the twin of the json tree
     * renderer's blend, kept private at both places (no shared helper for two painters).
     *
     * @param first the signal color
     * @param second the background color
     * @param weight the share of the first color
     * @return the blended color
     */
    private static Color blend(Color first, Color second, float weight) {
        final float[] f = first.getRGBColorComponents(null);
        final float[] s = second.getRGBColorComponents(null);
        return new Color(
                weight * f[0] + (1 - weight) * s[0],
                weight * f[1] + (1 - weight) * s[1],
                weight * f[2] + (1 - weight) * s[2]);
    }

}
