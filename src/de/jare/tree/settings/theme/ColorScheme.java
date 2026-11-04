/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings.theme;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.UIManager;

public class ColorScheme implements AScheme {

    public static String colorToHex(Color color) {
        return String.format("#%02X%02X%02X",
                color.getRed(),
                color.getGreen(),
                color.getBlue());
    }

    private final Map<String, Color> colorMap = new HashMap<>();

    public void forEachColor(BiConsumer<String, Color> action) {
        colorMap.forEach(action);
    }

    public Map<String, Color> getColorMap() {
        return colorMap;
    }

    public void setColor(String key, Color color) {
        colorMap.put(key, color);
    }

    public Color getColor(String key) {
        return colorMap.get(key);
    }

    public boolean hasColor(String key) {
        return colorMap.containsKey(key);
    }

    public void accept() {
        for (Map.Entry<String, Color> entry : colorMap.entrySet()) {
            UIManager.put(entry.getKey(), entry.getValue());
        }
    }

    public void resetDefault() {
        colorMap.clear();
        colorMap.put(LIGHT_FORE_OKAY, new Color(0, 195, 0));
        colorMap.put(LIGHT_FORE_WARNING, new Color(127, 127, 0));
        colorMap.put(LIGHT_FORE_ERROR, new Color(195, 0, 0));
        colorMap.put(LIGHT_FORE_PROPERTY, new Color(64, 0, 195));
        colorMap.put(LIGHT_FORE_ARRAY, new Color(0, 96, 128));
        colorMap.put(LIGHT_FORE_OBJECT, new Color(31, 31, 31));
        colorMap.put(LIGHT_FORE_ANNOTATION, new Color(170, 85, 0));
        colorMap.put(DARK_FORE_OKAY, new Color(142, 255, 142));
        colorMap.put(DARK_FORE_WARNING, new Color(255, 255, 128));
        colorMap.put(DARK_FORE_ERROR, new Color(255, 142, 142));
        colorMap.put(DARK_FORE_PROPERTY, new Color(166, 142, 255));
        colorMap.put(DARK_FORE_ARRAY, new Color(142, 238, 255));
        colorMap.put(DARK_FORE_OBJECT, new Color(251, 251, 251));
        colorMap.put(DARK_FORE_ANNOTATION, new Color(255, 178, 102));
        // change-state family of the tree diff view
        colorMap.put(LIGHT_FORE_DIFF_ADD, new Color(0, 140, 70));
        colorMap.put(LIGHT_FORE_DIFF_REMOVE, new Color(178, 34, 34));
        colorMap.put(LIGHT_FORE_DIFF_MODIFY, new Color(200, 130, 0));
        colorMap.put(LIGHT_FORE_DIFF_MOVE, new Color(128, 0, 200));
        colorMap.put(LIGHT_FORE_DIFF_CONFLICT, new Color(200, 0, 200));
        colorMap.put(LIGHT_FORE_DIFF_RADIATING, new Color(255, 128, 0));
        colorMap.put(LIGHT_FORE_DIFF_EDITED, new Color(140, 110, 70));
        // the banner is dark cherry red with white text in BOTH schemes - it does not follow the system colors
        colorMap.put(LIGHT_FORE_DIFF_BANNER_TEXT, new Color(255, 255, 255));
        colorMap.put(LIGHT_BACK_DIFF_BANNER, new Color(122, 28, 45));
        colorMap.put(DARK_FORE_DIFF_ADD, new Color(120, 255, 160));
        colorMap.put(DARK_FORE_DIFF_REMOVE, new Color(255, 120, 120));
        colorMap.put(DARK_FORE_DIFF_MODIFY, new Color(255, 210, 100));
        colorMap.put(DARK_FORE_DIFF_MOVE, new Color(200, 160, 255));
        colorMap.put(DARK_FORE_DIFF_CONFLICT, new Color(255, 120, 255));
        colorMap.put(DARK_FORE_DIFF_RADIATING, new Color(255, 180, 100));
        colorMap.put(DARK_FORE_DIFF_EDITED, new Color(215, 200, 170));
        colorMap.put(DARK_FORE_DIFF_BANNER_TEXT, new Color(255, 255, 255));
        colorMap.put(DARK_BACK_DIFF_BANNER, new Color(122, 28, 45));
    }

    public ColorScheme deepCopy() {
        ColorScheme copy = new ColorScheme();
        this.colorMap.forEach((key, value) -> {
            copy.setColor(key, value);
        });
        return copy;
    }

    public void invert() {
        for (Map.Entry<String, Color> entry : colorMap.entrySet()) {
            Color c = entry.getValue();
            Color inverted = new Color(255 - c.getRed(), 255 - c.getGreen(), 255 - c.getBlue(), c.getAlpha());
            colorMap.put(entry.getKey(), inverted);
        }
    }

}
