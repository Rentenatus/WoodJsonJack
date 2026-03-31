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
import java.util.Set;
import java.util.HashSet;

public class FontSettings {

    final public static Map<String, String[]> GROUP_MAPPING = Map.ofEntries(
            Map.entry("group.weak", new String[]{
        "ColorChooser.font",
        "EditorPane.font",
        "FormattedTextField.font",
        "OptionPane.font",
        "Panel.font",
        "PasswordField.font",
        "ScrollPane.font",
        "Table.font",
        "TableHeader.font",
        "TextArea.font",
        "TextField.font",
        "TextPane.font",
        "ToolTip.font",
        "Tree.font",
        "Viewport.font"
    }),
            Map.entry("group.strong", new String[]{
        "Button.font",
        "CheckBox.font",
        "CheckBoxMenuItem.font",
        "ComboBox.font",
        "DesktopIcon.font",
        "Label.font",
        "List.font",
        "Menu.font",
        "MenuBar.font",
        "MenuItem.font",
        "PopupMenu.font",
        "ProgressBar.font",
        "RadioButton.font",
        "RadioButtonMenuItem.font",
        "Slider.font",
        "Spinner.font",
        "TabbedPane.font",
        "TitledBorder.font",
        "ToggleButton.font",
        "ToolBar.font"
    }),
            Map.entry("group.accelerator", new String[]{
        "CheckBoxMenuItem.acceleratorFont",
        "Menu.acceleratorFont",
        "MenuItem.acceleratorFont",
        "RadioButtonMenuItem.acceleratorFont"
    }),
            Map.entry("group.title", new String[]{
        "InternalFrame.titleFont"
    })
    );

    private Map<String, Font> fontMap = new HashMap<>();

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
        for (Object key : UIManager.getDefaults().keySet()) {
            Object value = UIManager.getDefaults().get(key);
            if (key instanceof String && value instanceof Font) {
                fontMap.put((String) key, (Font) value);
            }
        }
        groupFonts();
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
        return copy;
    }

    public void groupFonts() {
        // First, collect all keys that should be grouped and calculate average fonts
        Map<String, Font> groupFonts = new HashMap<>();
        Set<String> keysToRemove = new HashSet<>();

        for (String groupKey : GROUP_MAPPING.keySet()) {
            String[] keys = GROUP_MAPPING.get(groupKey);

            // Collect all fonts from individual keys
            Set<Font> fonts = new HashSet<>();

            for (String key : keys) {
                if (hasFont(key)) {
                    fonts.add(getFont(key));
                    keysToRemove.add(key);
                }
            }

            if (!fonts.isEmpty()) {
                // Calculate average font
                Font averageFont = calculateAverageFont(fonts);
                groupFonts.put(groupKey, averageFont);
            }
        }

        // Clear all existing fonts
        fontMap.clear();

        // Add only group fonts
        for (Map.Entry<String, Font> entry : groupFonts.entrySet()) {
            setFont(entry.getKey(), entry.getValue());
        }
    }

    public void splitFonts() {
        // First, collect all group fonts to split
        Map<String, Font> groupsToSplit = new HashMap<>();

        for (String groupKey : GROUP_MAPPING.keySet()) {
            if (hasFont(groupKey)) {
                groupsToSplit.put(groupKey, getFont(groupKey));
            }
        }

        // Split all group fonts into individual keys
        for (Map.Entry<String, Font> entry : groupsToSplit.entrySet()) {
            String groupKey = entry.getKey();
            Font groupFont = entry.getValue();
            String[] keys = GROUP_MAPPING.get(groupKey);

            // Set individual fonts to group font
            for (String key : keys) {
                setFont(key, groupFont);
            }

            // Remove group font
            fontMap.remove(groupKey);
        }
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
}
