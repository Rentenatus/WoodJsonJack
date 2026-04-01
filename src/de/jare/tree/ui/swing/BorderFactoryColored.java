/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.swing;

import javax.swing.border.*;

/**
 * Factory class for creating colored borders that respect UIManager theme
 * colors. All borders created here will automatically update when the theme
 * changes.
 */
public class BorderFactoryColored {

    /**
     * Create a colored titled border
     *
     * @param title the border title
     * @param titleColorKey UIManager key for title color (e.g.,
     * "Label.foreground")
     * @param titleFontKey UIManager key for title font (e.g., "Label.font")
     * @return a new TitledBorderColored
     */
    public static TitledBorderColored createTitledBorder(String title, String titleColorKey, String titleFontKey) {
        return new TitledBorderColored(title, titleColorKey, titleFontKey);
    }

    /**
     * Create a colored titled border with inner border
     *
     * @param border the inner border
     * @param title the border title
     * @param titleColorKey UIManager key for title color
     * @param titleFontKey UIManager key for title font
     * @return a new TitledBorderColored
     */
    public static TitledBorderColored createTitledBorder(Border border, String title, String titleColorKey, String titleFontKey) {
        return new TitledBorderColored(border, title, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, titleColorKey, titleFontKey);
    }

    /**
     * Create a colored line border
     *
     * @param colorKey UIManager key for border color (e.g., "Panel.foreground")
     * @param thickness border thickness
     * @return a new LineBorderColored
     */
    public static LineBorderColored createLineBorder(String colorKey, int thickness) {
        return new LineBorderColored(colorKey, thickness);
    }

    /**
     * Create a colored line border with default thickness of 1
     *
     * @param colorKey UIManager key for border color
     * @return a new LineBorderColored
     */
    public static LineBorderColored createLineBorder(String colorKey) {
        return new LineBorderColored(colorKey);
    }

    /**
     * Create a colored etched border (raised)
     *
     * @param highlightColorKey UIManager key for highlight color
     * @param shadowColorKey UIManager key for shadow color
     * @return a new EtchedBorderColored
     */
    public static EtchedBorderColored createEtchedBorder(String highlightColorKey, String shadowColorKey) {
        return new EtchedBorderColored(EtchedBorder.RAISED, highlightColorKey, shadowColorKey);
    }

    /**
     * Create a colored etched border
     *
     * @param etchType EtchedBorder.RAISED or EtchedBorder.LOWERED
     * @param highlightColorKey UIManager key for highlight color
     * @param shadowColorKey UIManager key for shadow color
     * @return a new EtchedBorderColored
     */
    public static EtchedBorderColored createEtchedBorder(int etchType, String highlightColorKey, String shadowColorKey) {
        return new EtchedBorderColored(etchType, highlightColorKey, shadowColorKey);
    }

    /**
     * Create a colored matte border with uniform thickness
     *
     * @param thickness border thickness
     * @param colorKey UIManager key for border color
     * @return a new MatteBorderColored
     */
    public static MatteBorderColored createMatteBorder(int thickness, String colorKey) {
        return new MatteBorderColored(thickness, colorKey);
    }

    /**
     * Create a colored matte border with different sides
     *
     * @param top top border thickness
     * @param left left border thickness
     * @param bottom bottom border thickness
     * @param right right border thickness
     * @param colorKey UIManager key for border color
     * @return a new MatteBorderColored
     */
    public static MatteBorderColored createMatteBorder(int top, int left, int bottom, int right, String colorKey) {
        return new MatteBorderColored(top, left, bottom, right, colorKey);
    }

    /**
     * Create a colored bevel border (raised)
     *
     * @param highlightOuterColorKey UIManager key for highlight outer color
     * @param highlightInnerColorKey UIManager key for highlight inner color
     * @param shadowOuterColorKey UIManager key for shadow outer color
     * @param shadowInnerColorKey UIManager key for shadow inner color
     * @return a new BevelBorderColored
     */
    public static BevelBorderColored createBevelBorder(String highlightOuterColorKey, String highlightInnerColorKey,
            String shadowOuterColorKey, String shadowInnerColorKey) {
        return new BevelBorderColored(BevelBorder.RAISED, highlightOuterColorKey, highlightInnerColorKey,
                shadowOuterColorKey, shadowInnerColorKey);
    }

    /**
     * Create a colored bevel border
     *
     * @param bevelType BevelBorder.RAISED or BevelBorder.LOWERED
     * @param highlightOuterColorKey UIManager key for highlight outer color
     * @param highlightInnerColorKey UIManager key for highlight inner color
     * @param shadowOuterColorKey UIManager key for shadow outer color
     * @param shadowInnerColorKey UIManager key for shadow inner color
     * @return a new BevelBorderColored
     */
    public static BevelBorderColored createBevelBorder(int bevelType, String highlightOuterColorKey, String highlightInnerColorKey,
            String shadowOuterColorKey, String shadowInnerColorKey) {
        return new BevelBorderColored(bevelType, highlightOuterColorKey, highlightInnerColorKey,
                shadowOuterColorKey, shadowInnerColorKey);
    }
}
