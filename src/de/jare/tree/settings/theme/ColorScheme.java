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
        "Button.background",
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
        "Button.foreground",
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
        "Button.disabledText",
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
        "Button.select",
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
        "Button.focus",
        "CheckBox.focus",
        "RadioButton.focus",
        "Slider.focus",
        "ToggleButton.focus"
    }),
            Map.entry("group.border.dark", new String[]{
        "Button.darkShadow",
        "RadioButton.darkShadow",
        "ToggleButton.darkShadow",
        "ScrollBar.darkShadow",
        "SplitPane.darkShadow",
        "TextField.darkShadow",
        "ToolBar.darkShadow"
    }),
            Map.entry("group.border.light", new String[]{
        "Button.highlight",
        "Button.light",
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
        "Button.shadow",
        "RadioButton.shadow",
        "ToggleButton.shadow",
        "ScrollBar.shadow",
        "Separator.shadow",
        "SplitPane.shadow",
        "TextField.shadow",
        "ToolBar.shadow"
    })
    );

    private final Map<String, Color> colorMap = new HashMap<>();
    private boolean dark;

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

    public boolean isDark() {
        return dark;
    }

    public void setDark(boolean dark) {
        this.dark = dark;
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
        copy.dark = this.dark;
        return copy;
    }

    public void invert() {
        for (Map.Entry<String, Color> entry : colorMap.entrySet()) {
            Color c = entry.getValue();
            Color inverted = new Color(255 - c.getRed(), 255 - c.getGreen(), 255 - c.getBlue(), c.getAlpha());
            colorMap.put(entry.getKey(), inverted);
        }
        dark = !dark;
    }
}
