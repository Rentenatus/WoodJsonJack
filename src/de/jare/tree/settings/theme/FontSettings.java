/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings.theme;

import java.awt.Font;
import javax.swing.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class FontSettings {

    private Map<String, Font> fontMap = new HashMap<>();

    public void setFont(String key, Font font) {
        fontMap.put(key, font);

    }

    public void forEachFont(BiConsumer<String, Font> action) {
        fontMap.forEach(action);
    }

    public Font getFont(String key) {
        return fontMap.get(key);
    }

    public boolean hasFont(String key) {
        return fontMap.containsKey(key);
    }

    // Überträgt die gespeicherten Fonts in den UIManager
    public void accept() {
        for (Map.Entry<String, Font> entry : fontMap.entrySet()) {
            UIManager.put(entry.getKey(), entry.getValue());
        }
    }

    // Lädt alle aktuellen Font-Defaults aus dem UIManager
    public void resetDefault() {
        fontMap.clear();
        for (Object key : UIManager.getDefaults().keySet()) {
            Object value = UIManager.getDefaults().get(key);
            if (key instanceof String && value instanceof Font) {
                fontMap.put((String) key, (Font) value);
            }
        }
    }

    // Skaliert alle Fonts um den angegebenen Faktor
    public void scale(float factor) {
        for (Map.Entry<String, Font> entry : fontMap.entrySet()) {
            Font f = entry.getValue();
            Font scaled = f.deriveFont(f.getSize2D() * factor);
            fontMap.put(entry.getKey(), scaled);
        }
    }
}
