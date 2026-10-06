/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings.theme;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import de.jare.tree.settings.WoodSettings;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;

/**
 * Catalog of the FlatLaf look and feels available for the two color modes. Persisted as class
 * names in the wood settings; a missing or unknown name falls back to the default of the mode.
 *
 * @author Janusch Rentenatus
 */
public class LafCatalog {

    public static class LafEntry {

        private final String displayName;
        private final Class<? extends LookAndFeel> lafClass;

        public LafEntry(String displayName, Class<? extends LookAndFeel> lafClass) {
            this.displayName = displayName;
            this.lafClass = lafClass;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getClassName() {
            return lafClass.getName();
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static final LafEntry[] LIGHT_SCHEMAS = {
        new LafEntry("FlatLaf Light", FlatLightLaf.class),
        new LafEntry("FlatLaf IntelliJ", FlatIntelliJLaf.class),
        new LafEntry("FlatLaf macOS Light", FlatMacLightLaf.class)
    };

    private static final LafEntry[] DARK_SCHEMAS = {
        new LafEntry("FlatLaf Dark", FlatDarkLaf.class),
        new LafEntry("FlatLaf Darcula", FlatDarculaLaf.class),
        new LafEntry("FlatLaf macOS Dark", FlatMacDarkLaf.class)
    };

    public static LafEntry[] getLightSchemas() {
        return LIGHT_SCHEMAS.clone();
    }

    public static LafEntry[] getDarkSchemas() {
        return DARK_SCHEMAS.clone();
    }

    public static LafEntry defaultLight() {
        return LIGHT_SCHEMAS[0];
    }

    public static LafEntry defaultDark() {
        return DARK_SCHEMAS[0];
    }

    /**
     * Finds the entry with the given class name in the schema list of the given mode. Unknown
     * or missing names fall back to the default entry of that mode.
     *
     * @param className the persisted class name of the look and feel
     * @param darkMode the color mode the class name belongs to
     * @return the entry of that class name or the default of the mode
     */
    public static LafEntry findByClassName(String className, boolean darkMode) {
        final LafEntry[] schemas = darkMode ? DARK_SCHEMAS : LIGHT_SCHEMAS;
        if (className != null) {
            for (LafEntry entry : schemas) {
                if (entry.getClassName().equals(className)) {
                    return entry;
                }
            }
        }
        return darkMode ? defaultDark() : defaultLight();
    }

    /**
     * Installs the look and feel of the current color mode of the given settings and refreshes
     * all open windows.
     *
     * @param settings the settings holding the schema choices and the mode
     */
    public static void apply(WoodSettings settings) {
        final boolean darkMode = settings.isDarkMode();
        final LafEntry entry = findByClassName(
                darkMode ? settings.getDarkLaf() : settings.getLightLaf(), darkMode);
        try {
            FlatLaf.setup(entry.lafClass.getDeclaredConstructor().newInstance());
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
            Logger.getGlobal().log(Level.SEVERE, "Could not install look and feel: " + entry.getClassName(), ex);
            return;
        }
        refreshAllUi();
    }

    /**
     * Updates the component tree of all open windows, e.g. after a theme or color mode change.
     */
    public static void refreshAllUi() {
        for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
        }
    }
}
