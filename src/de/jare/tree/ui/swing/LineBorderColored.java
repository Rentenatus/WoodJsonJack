/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.swing;

import javax.swing.border.LineBorder;
import javax.swing.*;
import java.awt.*;

/**
 * A LineBorder that dynamically reads color from UIManager defaults.
 * Automatically applies the current L&F color without manual updates.
 */
public class LineBorderColored extends LineBorder {

    private String colorKey;

    /**
     * Create a colored line border
     *
     * @param colorKey UIManager key for border color (e.g., "Panel.foreground")
     * @param thickness border thickness
     */
    public LineBorderColored(String colorKey, int thickness) {
        super(Color.BLACK, thickness);
        this.colorKey = colorKey;
        applyColor();
    }

    /**
     * Create a colored line border with default thickness of 1
     */
    public LineBorderColored(String colorKey) {
        super(Color.BLACK, 1);
        this.colorKey = colorKey;
        applyColor();
    }

    /**
     * Apply current UIManager color
     */
    public void applyColor() {
        if (colorKey != null) {
            Object color = UIManager.get(colorKey);
            if (color instanceof Color) {
                lineColor = (Color) color;
            }
        }
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
 
        // Ensure graphics has the correct color
        if (lineColor != null) {
            g.setColor(lineColor);
        }
        super.paintBorder(c, g, x, y, width, height);
    }

    public void setColorKey(String key) {
        this.colorKey = key;
        applyColor();
    }
}
