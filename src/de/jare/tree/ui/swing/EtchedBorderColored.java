/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.swing;

import javax.swing.border.EtchedBorder;
import javax.swing.*;
import java.awt.*;

/**
 * An EtchedBorder that dynamically reads colors from UIManager defaults.
 * Automatically applies the current L&F colors without manual updates.
 */
public class EtchedBorderColored extends EtchedBorder {

    private String highlightColorKey;
    private String shadowColorKey;

    /**
     * Create a colored etched border
     *
     * @param etchType EtchedBorder.RAISED or EtchedBorder.LOWERED
     * @param highlightColorKey UIManager key for highlight color
     * @param shadowColorKey UIManager key for shadow color
     */
    public EtchedBorderColored(int etchType, String highlightColorKey, String shadowColorKey) {
        super(etchType);
        this.highlightColorKey = highlightColorKey;
        this.shadowColorKey = shadowColorKey;
        applyColors();
    }

    /**
     * Create a raised colored etched border with default keys
     */
    public EtchedBorderColored(String highlightColorKey, String shadowColorKey) {
        super(RAISED);
        this.highlightColorKey = highlightColorKey;
        this.shadowColorKey = shadowColorKey;
        applyColors();
    }

    /**
     * Apply current UIManager colors
     */
    public void applyColors() {
        if (highlightColorKey != null) {
            Object color = UIManager.get(highlightColorKey);
            if (color instanceof Color) {
                highlight = (Color) color;
            }
        }

        if (shadowColorKey != null) {
            Object color = UIManager.get(shadowColorKey);
            if (color instanceof Color) {
                shadow = (Color) color;
            }
        }
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
 
        // Ensure graphics has the correct colors
        if (highlight != null) {
            g.setColor(highlight);
        }
        super.paintBorder(c, g, x, y, width, height);
    }

    public void setHighlightColorKey(String key) {
        this.highlightColorKey = key;
        applyColors();
    }

    public void setShadowColorKey(String key) {
        this.shadowColorKey = key;
        applyColors();
    }
}
