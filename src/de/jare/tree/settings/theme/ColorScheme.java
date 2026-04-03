/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings.theme;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.swing.UIManager;

public class ColorScheme {

    public static String colorToHex(Color color) {
        return String.format("#%02X%02X%02X",
                color.getRed(),
                color.getGreen(),
                color.getBlue());
    }

    final public static String[] COLOR_LIST = new String[]{
        "light.fore.okay",
        "light.fore.warning",
        "light.fore.error",
        "dark.fore.okay",
        "dark.fore.warning",
        "dark.fore.error",
    };

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
        colorMap.put("light.fore.okay", new Color(0, 195, 0));
        colorMap.put("light.fore.warning", new Color(127, 127, 0));
        colorMap.put("light.fore.error", new Color(195, 0, 0));
        colorMap.put("light.fore.property", new Color(0, 0, 195));
        colorMap.put("light.fore.object", new Color(31, 31, 31));
        colorMap.put("dark.fore.okay", new Color(142, 255, 142));
        colorMap.put("dark.fore.warning", new Color(255, 255, 128));
        colorMap.put("dark.fore.error", new Color(255, 142, 142));
        colorMap.put("dark.fore.property", new Color(142, 142, 255));
        colorMap.put("dark.fore.object", new Color(251, 251, 251));
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
