/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.GeneralPath;
import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * Icon access for menus and toolbar buttons of the Wood editor. The file-ish icons (new, open,
 * save) come from the installed look and feel - via the stable Swing UI keys - so they follow the
 * theme automatically: FlatLaf paints its flat vector icons, any other LaF paints its own set.
 * They are wrapped in {@link LafIcon} which resolves the key at PAINT time, because a theme
 * switch replaces the icon objects under the same keys and a captured reference would go stale.
 *
 * @author Janusch Rentenatus
 */
public final class UiIcons {

    private UiIcons() {
    }

    /** The blank page: "New Project" (that page is project.json itself, concept sections 1 and 8). */
    public static Icon newProjectIcon() {
        return new LafIcon("FileChooser.newFolderIcon");
    }

    /** The folder: "Open Project" and the project bar's open button. */
    public static Icon openProjectIcon() {
        return new LafIcon("FileView.directoryIcon");
    }

    /** The folder with the up arrow: plain "Open..." of a single JSON file. */
    public static Icon openFileIcon() {
        return new LafIcon("FileChooser.upFolderIcon");
    }

    /** The page of a single file: "Open Bundle..." (the main resource file of a bundle). */
    public static Icon openBundleIcon() {
        return new LafIcon("FileView.fileIcon");
    }

    /** The diskette: both save variants (standard practice shares the symbol, the text tells them apart). */
    public static Icon saveIcon() {
        return new LafIcon("FileView.floppyDriveIcon");
    }

    /** The circular arrows for every reload/refresh action; no LaF ships one, so it is painted here. */
    public static Icon refreshIcon() {
        return new RefreshIcon();
    }

    /**
     * Delegates to the icon that the look and feel currently publishes under the given UI key.
     * Resolving at paint time keeps the icon correct across theme switches (dark mode, FlatLaf
     * light/dark, any other LaF); a null resolution paints nothing rather than crashing.
     */
    private static final class LafIcon implements Icon {

        private final String uiKey;

        LafIcon(String uiKey) {
            this.uiKey = uiKey;
        }

        private Icon current() {
            return UIManager.getIcon(uiKey);
        }

        @Override
        public int getIconWidth() {
            Icon icon = current();
            return icon == null ? 16 : icon.getIconWidth();
        }

        @Override
        public int getIconHeight() {
            Icon icon = current();
            return icon == null ? 16 : icon.getIconHeight();
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Icon icon = current();
            if (icon != null) {
                icon.paintIcon(c, g, x, y);
            }
        }
    }

    /**
     * A hand painted reload symbol: a circular arrow in the style of the FlatLaf file icons -
     * thin stroke, no fill, theme color. It paints with the component's foreground (and therefore
     * follows light/dark) and, like any button icon, greys out automatically when disabled.
     */
    private static final class RefreshIcon implements Icon {

        private static final int SIZE = 16;
        private static final double STROKE = 1.5;

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color color = c == null ? Color.GRAY : c.getForeground();
                g2.setColor(color);
                g2.setStroke(new java.awt.BasicStroke((float) STROKE, java.awt.BasicStroke.CAP_ROUND,
                        java.awt.BasicStroke.JOIN_MITER));
                // the arc opens at the top right quarter; the arrow head sits at its lower end
                g2.draw(new Arc2D.Double(x + 2.25, y + 2.25, SIZE - 4.5, SIZE - 4.5, -30, 290, Arc2D.OPEN));
                GeneralPath head = new GeneralPath();
                double tipX = x + SIZE / 2.0 + (SIZE / 2.0 - 2.25) * Math.cos(Math.toRadians(-30));
                double tipY = y + SIZE / 2.0 - (SIZE / 2.0 - 2.25) * Math.sin(Math.toRadians(-30));
                double insetX = x + SIZE / 2.0 + (SIZE / 2.0 - 5.0) * Math.cos(Math.toRadians(-30));
                double insetY = y + SIZE / 2.0 - (SIZE / 2.0 - 5.0) * Math.sin(Math.toRadians(-30));
                double inX = x + SIZE / 2.0 + (SIZE / 2.0 - 2.25) * Math.cos(Math.toRadians(-90));
                double inY = y + SIZE / 2.0 - (SIZE / 2.0 - 2.25) * Math.sin(Math.toRadians(-90));
                head.moveTo(insetX, insetY - 1.5);
                head.lineTo(tipX + 1.5, tipY - 1.0);
                head.lineTo(inX, inY + 2.0);
                head.closePath();
                g2.fill(head);
            } finally {
                g2.dispose();
            }
        }
    }
}
