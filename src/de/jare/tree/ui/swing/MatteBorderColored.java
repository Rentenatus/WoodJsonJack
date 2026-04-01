/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.swing;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.MatteBorder;

/**
 * A MatteBorder that dynamically reads color from UIManager defaults.
 * Automatically applies the current L&F color without manual updates.
 */
public class MatteBorderColored extends MatteBorder {

    private String colorKey;

    /**
     * Create a colored matte border
     *
     * @param top top border thickness
     * @param left left border thickness
     * @param bottom bottom border thickness
     * @param right right border thickness
     * @param colorKey UIManager key for border color (e.g., "Panel.foreground")
     */
    public MatteBorderColored(int top, int left, int bottom, int right, String colorKey) {
        super(top, left, bottom, right, Color.BLACK);
        this.colorKey = colorKey;
        applyColor();
    }

    /**
     * Create a colored matte border with uniform thickness
     */
    public MatteBorderColored(int thickness, String colorKey) {
        super(thickness, thickness, thickness, thickness, Color.BLACK);
        this.colorKey = colorKey;
        applyColor();
    }

    /**
     * Create a colored matte border with icon/component
     */
    public MatteBorderColored(Icon tileIcon, String colorKey) {
        super(tileIcon);
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
                this.color = (Color) color;
            }
        }
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
 
        // Ensure graphics has the correct color
        if (color != null) {
            g.setColor(color);
        }
        super.paintBorder(c, g, x, y, width, height);
    }

    public void setColorKey(String key) {
        this.colorKey = key;
        applyColor();
    }
}
