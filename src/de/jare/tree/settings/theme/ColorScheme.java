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

    final public static Map<String, String[]> GROUP_MAPPING = Map.ofEntries(
            Map.entry("group.background.strong", new String[]{
        "Desktop.background",
        "EditorPane.background",
        "FormattedTextField.background",
        "List.background",
        "PasswordField.background",
        "Table.background",
        "TextArea.background",
        "TextField.background",
        "TextPane.background",
        "Tree.background",
        "Desktop.background",
        "window",
        "desktop"
    }),
            Map.entry("group.background.weak", new String[]{
        "CheckBox.background",
        "ComboBox.background",
        "InternalFrame.borderColor",
        "Label.background",
        "Menu.background",
        "MenuBar.background",
        "MenuItem.background",
        "OptionPane.background",
        "Panel.background",
        "PopupMenu.background",
        "ProgressBar.background",
        "RadioButton.background",
        "ScrollBar.background",
        "ScrollPane.background",
        "Spinner.background",
        "SplitPane.background",
        "TabbedPane.unselectedBackground",
        "TableHeader.background",
        "ToolBar.background",
        "ToolTip.backgroundInactive",
        "Viewport.background",
        "control",
        "inactiveCaption",
        "menu",
        "scrollbar",
        "windowBorder"
    }),
            Map.entry("group.foreground.primary", new String[]{
        "CheckBox.foreground",
        "ColorChooser.foreground",
        "ComboBox.foreground",
        "DesktopIcon.foreground",
        "EditorPane.foreground",
        "FormattedTextField.foreground",
        "Label.foreground",
        "List.foreground",
        "Menu.foreground",
        "MenuItem.foreground",
        "OptionPane.foreground",
        "OptionPane.messageForeground",
        "Panel.foreground",
        "PasswordField.foreground",
        "PopupMenu.foreground",
        "ScrollPane.foreground",
        "Separator.foreground",
        "Table.foreground",
        "TableHeader.foreground",
        "TextArea.foreground",
        "TextField.foreground",
        "TextPane.foreground",
        "ToolBar.foreground",
        "ToolTip.foreground",
        "Tree.foreground",
        "Viewport.foreground",
        "activeCaptionText",
        "controlText",
        "inactiveCaptionText",
        "infoText",
        "menuText",
        "textText",
        "windowText"
    }),
            Map.entry("group.foreground.disabled", new String[]{
        "CheckBox.disabledText",
        "CheckBoxMenuItem.disabledForeground",
        "ComboBox.disabledForeground",
        "Label.disabledForeground",
        "Menu.disabledForeground",
        "MenuItem.disabledForeground",
        "PasswordField.inactiveForeground",
        "TextArea.inactiveForeground",
        "TextField.inactiveForeground",
        "TextPane.inactiveForeground",
        "ToolTip.foregroundInactive",
        "textInactiveText"
    }),
            Map.entry("group.selection.background", new String[]{
        "CheckBox.select",
        "ComboBox.selectionBackground",
        "CheckBoxMenuItem.selectionBackground",
        "Menu.selectionBackground",
        "MenuItem.selectionBackground",
        "ProgressBar.selectionBackground",
        "RadioButton.select",
        "RadioButtonMenuItem.selectionBackground",
        "List.selectionBackground",
        "PasswordField.selectionBackground",
        "EditorPane.selectionBackground",
        "FormattedTextField.selectionBackground",
        "TextArea.selectionBackground",
        "TextField.selectionBackground",
        "TextPane.selectionBackground",
        "Table.selectionBackground",
        "Tree.selectionBackground",
        "textHighlight"
    }),
            Map.entry("group.selection.foreground", new String[]{
        "ComboBox.selectionForeground",
        "CheckBoxMenuItem.selectionForeground",
        "Menu.selectionForeground",
        "MenuItem.selectionForeground",
        "ProgressBar.selectionForeground",
        "RadioButtonMenuItem.selectionForeground",
        "List.selectionForeground",
        "PasswordField.selectionForeground",
        "EditorPane.selectionForeground",
        "FormattedTextField.selectionForeground",
        "TextArea.selectionForeground",
        "TextField.selectionForeground",
        "TextPane.selectionForeground",
        "Table.selectionForeground",
        "Tree.selectionForeground",
        "textHighlightText"
    }),
            Map.entry("group.focus", new String[]{
        "CheckBox.focus",
        "RadioButton.focus",
        "Slider.focus",
        "ToggleButton.focus"
    }),
            Map.entry("group.border.dark", new String[]{
        "RadioButton.darkShadow",
        "ToggleButton.darkShadow",
        "ScrollBar.darkShadow",
        "SplitPane.darkShadow",
        "TextField.darkShadow",
        "ToolBar.darkShadow"
    }),
            Map.entry("group.border.light", new String[]{
        "RadioButton.highlight",
        "RadioButton.light",
        "ToggleButton.highlight",
        "ToggleButton.light",
        "MenuBar.highlight",
        "ScrollBar.highlight",
        "Separator.highlight",
        "SplitPane.highlight",
        "TabbedPane.highlight",
        "TextField.highlight",
        "ToolBar.highlight",
        "controlHighlight",
        "controlLtHighlight"
    }),
            Map.entry("group.shadow", new String[]{
        "RadioButton.shadow",
        "ToggleButton.shadow",
        "ScrollBar.shadow",
        "Separator.shadow",
        "SplitPane.shadow",
        "TextField.shadow",
        "ToolBar.shadow"
    }),
            Map.entry("group.button.background.weak", new String[]{
        "Button.background"}),
            Map.entry("group.button.foreground.primary", new String[]{
        "Button.foreground"}),
            Map.entry("group.button.foreground.disabled", new String[]{
        "Button.disabledText"}),
            Map.entry("group.button.selection.background", new String[]{
        "Button.select"}),
            Map.entry("group.button.focus", new String[]{
        "Button.focus"}),
            Map.entry("group.button.border.dark", new String[]{
        "Button.darkShadow"}),
            Map.entry("group.button.border.light", new String[]{
        "Button.highlight",
        "Button.light"}),
            Map.entry("group.button.shadow", new String[]{
        "Button.shadow"})
    );

    private final Map<String, Color> colorMap = new HashMap<>();
    private boolean isGrouped = false;

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



    public boolean isGrouped() {
        return isGrouped;
    }

    /**
     * Returns a detailed (split) color scheme. If already split, returns this
     * instance. If grouped, returns a deep copy with colors split. This ensures
     * the caller always gets individual color keys, not groups.
     */
    public ColorScheme getDetailedScheme() {
        if (!isGrouped) {
            // Already detailed, return self
            return this;
        }
        // Create detailed copy
        ColorScheme detailed = this.deepCopy();
        detailed.splitColors();
        return detailed;
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
        isGrouped = false;
        groupColors();
    }

    public ColorScheme deepCopy() {
        ColorScheme copy = new ColorScheme();
        this.colorMap.forEach((key, value) -> {
            copy.setColor(key, value);
        });
        copy.isGrouped = this.isGrouped;
        return copy;
    }

    public void invert() {
        for (Map.Entry<String, Color> entry : colorMap.entrySet()) {
            Color c = entry.getValue();
            Color inverted = new Color(255 - c.getRed(), 255 - c.getGreen(), 255 - c.getBlue(), c.getAlpha());
            colorMap.put(entry.getKey(), inverted);
        }
    }

    public void groupColors() {
        // Skip if already grouped
        if (isGrouped) {
            return;
        }

        // First, collect all keys that should be grouped and calculate averages
        Map<String, Color> groupColors = new HashMap<>();
        Set<String> keysToRemove = new HashSet<>();

        for (String groupKey : GROUP_MAPPING.keySet()) {
            String[] keys = GROUP_MAPPING.get(groupKey);

            // Calculate average color
            int redSum = 0, greenSum = 0, blueSum = 0;
            int count = 0;

            for (String key : keys) {
                if (hasColor(key)) {
                    Color color = getColor(key);
                    redSum += color.getRed();
                    greenSum += color.getGreen();
                    blueSum += color.getBlue();
                    count++;
                    keysToRemove.add(key);
                }
            }

            if (count > 0) {
                int avgRed = redSum / count;
                int avgGreen = greenSum / count;
                int avgBlue = blueSum / count;
                Color averageColor = new Color(avgRed, avgGreen, avgBlue);
                groupColors.put(groupKey, averageColor);
            }
        }

        // Clear all existing colors
        colorMap.clear();

        // Add only group colors
        for (Map.Entry<String, Color> entry : groupColors.entrySet()) {
            setColor(entry.getKey(), entry.getValue());
        }

        isGrouped = true;
    }

    public void splitColors() {
        // Skip if already split
        if (!isGrouped) {
            return;
        }

        // First, collect all group colors to split
        Map<String, Color> groupsToSplit = new HashMap<>();

        for (String groupKey : GROUP_MAPPING.keySet()) {
            if (hasColor(groupKey)) {
                groupsToSplit.put(groupKey, getColor(groupKey));
            }
        }

        // Split all group colors into individual keys
        for (Map.Entry<String, Color> entry : groupsToSplit.entrySet()) {
            String groupKey = entry.getKey();
            Color groupColor = entry.getValue();
            String[] keys = GROUP_MAPPING.get(groupKey);

            // Set individual colors to group color
            for (String key : keys) {
                setColor(key, groupColor);
            }

            // Remove group color
            colorMap.remove(groupKey);
        }

        isGrouped = false;
    }
}
