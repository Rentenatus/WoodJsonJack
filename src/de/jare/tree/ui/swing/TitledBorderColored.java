/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.swing;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.Border;

/**
 * A custom TitledBorder that dynamically reads colors from UIManager defaults.
 * Renders the title text directly to ensure colors are always applied
 * correctly.
 */
public class TitledBorderColored implements Border {

    private Border innerBorder;
    private String title;
    private int titleJustification = javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION;
    private int titlePosition = javax.swing.border.TitledBorder.DEFAULT_POSITION;
    private String titleColorKey;
    private String titleFontKey;
    private Color titleColor;
    private Font titleFont;
    private int titleInset = 5;

    /**
     * Create a colored titled border
     */
    public TitledBorderColored(String title, String titleColorKey, String titleFontKey) {
        this.title = title;
        this.titleColorKey = titleColorKey;
        this.titleFontKey = titleFontKey;
        this.innerBorder = BorderFactory.createEtchedBorder();
        applyColors();
    }

    /**
     * Create a colored titled border with inner border
     */
    public TitledBorderColored(Border innerBorder, String title, int titleJustification,
            int titlePosition, String titleColorKey, String titleFontKey) {
        this.innerBorder = innerBorder;
        this.title = title;
        this.titleJustification = titleJustification;
        this.titlePosition = titlePosition;
        this.titleColorKey = titleColorKey;
        this.titleFontKey = titleFontKey;
        applyColors();
    }

    /**
     * Apply current UIManager colors and fonts
     */
    public void applyColors() {
        if (titleColorKey != null) {
            Object color = UIManager.get(titleColorKey);
            if (color instanceof Color) {
                this.titleColor = (Color) color;
            }
        }

        if (titleFontKey != null) {
            Object font = UIManager.get(titleFontKey);
            if (font instanceof Font) {
                this.titleFont = (Font) font;
            }
        }
    }

    /**
     * Apply explicit colors (used when updating from preview defaults)
     */
    public void applyDirectColors(Color color, Font font) {
        if (color != null) {
            this.titleColor = color;
        }
        if (font != null) {
            this.titleFont = font;
        }
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Paint inner border
        if (innerBorder != null) {
            innerBorder.paintBorder(c, g2d, x, y, width, height);
        }

        // Paint title
        if (title != null && !title.isEmpty()) {
            Font oldFont = g2d.getFont();
            Color oldColor = g2d.getColor();

            Font useFont = titleFont != null ? titleFont : oldFont;
            Color useColor = titleColor != null ? titleColor : oldColor;

            g2d.setFont(useFont);
            g2d.setColor(useColor);

            FontMetrics fm = g2d.getFontMetrics(useFont);
            int textWidth = fm.stringWidth(title);
            int textHeight = fm.getAscent();

            int titleX = x + titleInset;
            if (titleJustification == javax.swing.border.TitledBorder.CENTER) {
                titleX = x + (width - textWidth) / 2;
            } else if (titleJustification == javax.swing.border.TitledBorder.RIGHT) {
                titleX = x + width - textWidth - titleInset;
            }

            int titleY = y + textHeight;
            if (titlePosition == javax.swing.border.TitledBorder.BELOW_TOP) {
                titleY = y + height - fm.getDescent();
            }

            // Draw white background behind title to cover the border line
            int bgPadding = 2;
            g2d.setColor(c.getBackground());
            g2d.fillRect(titleX - bgPadding, titleY - textHeight - bgPadding,
                    textWidth + bgPadding * 2, textHeight + bgPadding);

            // Draw title text
            g2d.setColor(useColor);
            g2d.drawString(title, titleX, titleY);

            g2d.setFont(oldFont);
            g2d.setColor(oldColor);
        }
    }

    @Override
    public Insets getBorderInsets(Component c) {
        int top = 20;
        int bottom = 2;
        int left = 2;
        int right = 2;

        if (innerBorder != null) {
            Insets inner = innerBorder.getBorderInsets(c);
            top = Math.max(top, inner.top);
            bottom = Math.max(bottom, inner.bottom);
            left = Math.max(left, inner.left);
            right = Math.max(right, inner.right);
        }

        return new Insets(top, left, bottom, right);
    }

    @Override
    public boolean isBorderOpaque() {
        return false;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setTitleColor(Color color) {
        this.titleColor = color;
    }

    public void setTitleFont(Font font) {
        this.titleFont = font;
    }

    public void setTitleColorKey(String key) {
        this.titleColorKey = key;
        applyColors();
    }

    public void setTitleFontKey(String key) {
        this.titleFontKey = key;
        applyColors();
    }
}
