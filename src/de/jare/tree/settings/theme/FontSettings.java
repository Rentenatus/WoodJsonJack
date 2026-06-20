/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings.theme;

import java.awt.Font;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.swing.*;

public class FontSettings implements AScheme {

    private Map<String, Font> fontMap = new HashMap<>();
    private boolean isGrouped = false;

    public void setFont(String key, Font font) {
        fontMap.put(key, font);

    }

    public void forEachFont(BiConsumer<String, Font> action) {
        fontMap.forEach(action);
    }

    public Map<String, Font> getFontMap() {
        return fontMap;
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
        Font font = (Font) UIManager.getDefaults().get("EditorPane.font");
        fontMap.put(LIGHT_FORE_OKAY, font);
        fontMap.put(LIGHT_FORE_WARNING, font);
        fontMap.put(LIGHT_FORE_ERROR, font);
        fontMap.put(LIGHT_FORE_PROPERTY, font);
        fontMap.put(LIGHT_FORE_OBJECT, font);
        fontMap.put(DARK_FORE_OKAY, font);
        fontMap.put(DARK_FORE_WARNING, font);
        fontMap.put(DARK_FORE_ERROR, font);
        fontMap.put(DARK_FOREP_ROPERTY, font);
        fontMap.put(DARK_FORE_OBJECT, font);
    }

    // Skaliert alle Fonts um den angegebenen Faktor
    public void scale(float factor) {
        for (Map.Entry<String, Font> entry : fontMap.entrySet()) {
            Font f = entry.getValue();
            Font scaled = f.deriveFont(f.getSize2D() * factor);
            fontMap.put(entry.getKey(), scaled);
        }
    }

    public FontSettings deepCopy() {
        FontSettings copy = new FontSettings();
        this.fontMap.forEach((key, value) -> {
            copy.setFont(key, value);
        });
        copy.isGrouped = this.isGrouped;
        return copy;
    }

    private Font calculateAverageFont(Set<Font> fonts) {
        if (fonts.isEmpty()) {
            return new Font("Dialog", Font.PLAIN, 12);
        }

        // Use the first font as base
        Font baseFont = fonts.iterator().next();
        String fontName = baseFont.getName();
        int fontStyle = baseFont.getStyle();

        // Calculate average size
        float sizeSum = 0;
        for (Font font : fonts) {
            sizeSum += font.getSize();
        }
        int averageSize = Math.round(sizeSum / fonts.size());

        return new Font(fontName, fontStyle, averageSize);
    }

    public boolean isGrouped() {
        return isGrouped;
    }

}
