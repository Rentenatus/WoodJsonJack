/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings.theme;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import javax.swing.UIManager;
import java.util.Map;

public class ColorScheme {

    private final Map<String, Color> colors = new HashMap<>();

    public void setColor(String key, Color color) {
        colors.put(key, color);
    }

    public Color getColor(String key) {
        return colors.get(key);
    }

    public boolean hasColor(String key) {
        return colors.containsKey(key);
    }

    public void accept() {
        for (Map.Entry<String, Color> entry : colors.entrySet()) {
            UIManager.put(entry.getKey(), entry.getValue());
        }
    }

    public void resetDefault() {
        colors.clear();
        for (Object key : UIManager.getDefaults().keySet()) {
            Object value = UIManager.getDefaults().get(key);
            if (key instanceof String && value instanceof Color) {
                colors.put((String) key, (Color) value);
            }
        }
    }

    public void invert() {
        for (Map.Entry<String, Color> entry : colors.entrySet()) {
            Color c = entry.getValue();
            Color inverted = new Color(255 - c.getRed(), 255 - c.getGreen(), 255 - c.getBlue(), c.getAlpha());
            colors.put(entry.getKey(), inverted);
        }
    }
}
