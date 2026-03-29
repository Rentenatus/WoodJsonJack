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

public class ColorScheme {

    public static String colorToHex(Color color) {
        return String.format("#%02X%02X%02X",
                color.getRed(),
                color.getGreen(),
                color.getBlue());
    }

    private final Map<String, Color> colorMap = new HashMap<>();
    private boolean isDark;

    public void forEachColor(BiConsumer<String, Color> action) {
        colorMap.forEach(action);
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
        for (Object key : UIManager.getDefaults().keySet()) {
            Object value = UIManager.getDefaults().get(key);
            if (key instanceof String && value instanceof Color) {
                colorMap.put((String) key, (Color) value);
            }
        }
    }

    public ColorScheme deepCopy() {
        ColorScheme copy = new ColorScheme();
        this.colorMap.forEach((key, value) -> {
            copy.setColor(key, value);
        });
        copy.isDark = this.isDark;
        return copy;
    }

    public void invert() {
        for (Map.Entry<String, Color> entry : colorMap.entrySet()) {
            Color c = entry.getValue();
            Color inverted = new Color(255 - c.getRed(), 255 - c.getGreen(), 255 - c.getBlue(), c.getAlpha());
            colorMap.put(entry.getKey(), inverted);
        }
        isDark = !isDark;
    }
}
