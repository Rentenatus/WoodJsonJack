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
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeCellRenderer;

/**
 * Renderer of the project sidebar: partitions carry their role as suffix so the virtual level 1
 * reads as what it is - mounts, not folders. Read-only mounts render gray, files keep the plain
 * look. A broken bundle colors like a parse error in JSON: the theme's error tone (double bang
 * marker) for a missing main file, the warning tone (single bang) for a degraded descriptor - the
 * same fore.error / fore.warning keys the parsed trees use, so both worlds speak one color
 * language.
 *
 * @author Janusch Rentenatus
 */
public class ProjectTreeCellRenderer extends DefaultTreeCellRenderer {

    // fallbacks of the scheme defaults (ColorScheme) - only used when a theme carries no colors
    private static final Color FALLBACK_ERROR = new Color(195, 0, 0);
    private static final Color FALLBACK_WARNING = new Color(127, 127, 0);

    public ProjectTreeCellRenderer() {
        // no state - the look and feel decides the colors
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
            boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
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
            if (sel) {
                setForeground(null);
            } else if (node.getError() != null) {
                setForeground(schemeColor("fore." + "error", FALLBACK_ERROR));
            } else if (node.getWarning() != null) {
                setForeground(schemeColor("fore.warning", FALLBACK_WARNING));
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

}
