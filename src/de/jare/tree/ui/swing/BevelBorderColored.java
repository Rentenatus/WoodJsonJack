/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.swing;

import javax.swing.border.BevelBorder;
import javax.swing.*;
import java.awt.*;

/**
 * A BevelBorder that dynamically reads colors from UIManager defaults.
 * Automatically applies the current L&F colors without manual updates.
 */
public class BevelBorderColored extends BevelBorder {

    private String highlightOuterColorKey;
    private String highlightInnerColorKey;
    private String shadowOuterColorKey;
    private String shadowInnerColorKey;

    /**
     * Create a colored bevel border
     *
     * @param bevelType BevelBorder.RAISED or BevelBorder.LOWERED
     * @param highlightOuterColorKey UIManager key for highlight outer color
     * @param highlightInnerColorKey UIManager key for highlight inner color
     * @param shadowOuterColorKey UIManager key for shadow outer color
     * @param shadowInnerColorKey UIManager key for shadow inner color
     */
    public BevelBorderColored(int bevelType, String highlightOuterColorKey, String highlightInnerColorKey,
            String shadowOuterColorKey, String shadowInnerColorKey) {
        super(bevelType);
        this.highlightOuterColorKey = highlightOuterColorKey;
        this.highlightInnerColorKey = highlightInnerColorKey;
        this.shadowOuterColorKey = shadowOuterColorKey;
        this.shadowInnerColorKey = shadowInnerColorKey;
        applyColors();
    }

    /**
     * Create a raised colored bevel border with standard keys
     */
    public BevelBorderColored(String highlightOuterColorKey, String highlightInnerColorKey,
            String shadowOuterColorKey, String shadowInnerColorKey) {
        super(RAISED);
        this.highlightOuterColorKey = highlightOuterColorKey;
        this.highlightInnerColorKey = highlightInnerColorKey;
        this.shadowOuterColorKey = shadowOuterColorKey;
        this.shadowInnerColorKey = shadowInnerColorKey;
        applyColors();
    }

    /**
     * Apply current UIManager colors
     */
    public void applyColors() {
        if (highlightOuterColorKey != null) {
            Object color = UIManager.get(highlightOuterColorKey);
            if (color instanceof Color) {
                highlightOuter = (Color) color;
            }
        }

        if (highlightInnerColorKey != null) {
            Object color = UIManager.get(highlightInnerColorKey);
            if (color instanceof Color) {
                highlightInner = (Color) color;
            }
        }

        if (shadowOuterColorKey != null) {
            Object color = UIManager.get(shadowOuterColorKey);
            if (color instanceof Color) {
                shadowOuter = (Color) color;
            }
        }

        if (shadowInnerColorKey != null) {
            Object color = UIManager.get(shadowInnerColorKey);
            if (color instanceof Color) {
                shadowInner = (Color) color;
            }
        }
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
 
        // Ensure graphics has the correct colors
        if (highlightOuter != null) {
            g.setColor(highlightOuter);
        }
        super.paintBorder(c, g, x, y, width, height);
    }

    public void setHighlightOuterColorKey(String key) {
        this.highlightOuterColorKey = key;
        applyColors();
    }

    public void setHighlightInnerColorKey(String key) {
        this.highlightInnerColorKey = key;
        applyColors();
    }

    public void setShadowOuterColorKey(String key) {
        this.shadowOuterColorKey = key;
        applyColors();
    }

    public void setShadowInnerColorKey(String key) {
        this.shadowInnerColorKey = key;
        applyColors();
    }
}
