/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.settings;

import de.jare.tree.settings.theme.Theme;
import de.jare.tree.settings.theme.ThemeSuite;

/**
 * Central model for managing theme data across all UI components.
 * Maintains activeTheme (persisted) and workTheme (temporary working copy).
 */
public class ThemesModel {

    private ThemeSuite themeSuite;
    private Theme activeTheme;
    private Theme workTheme;

    /**
     * Creates a new ThemesModel with the given theme suite.
     * @param themeSuite the theme suite containing all available themes
     */
    public ThemesModel(ThemeSuite themeSuite) {
        this.themeSuite = themeSuite;
    }

    /**
     * Sets the active theme and creates a working copy.
     * @param theme the theme to set as active
     */
    public void setActiveTheme(Theme theme) {
        this.activeTheme = theme;
        this.workTheme = theme != null ? theme.deepCopy() : null;
    }

    /**
     * Gets the active theme (persisted version).
     * @return the active theme, or null if none is set
     */
    public Theme getActiveTheme() {
        return activeTheme;
    }

    /**
     * Gets the working theme (temporary copy for modifications).
     * @return the working theme, or null if none is set
     */
    public Theme getWorkTheme() {
        return workTheme;
    }

    /**
     * Accepts changes from workTheme to activeTheme.
     * This is equivalent to "Apply" - saves modifications.
     */
    public void accept() {
        if (activeTheme == null || workTheme == null) {
            return;
        }
        activeTheme.setColors(workTheme.getColors());
        activeTheme.setFonts(workTheme.getFonts());
        activeTheme.setThemeId(workTheme.getThemeId());
        activeTheme.setThemeName(workTheme.getThemeName());
    }

    /**
     * Restores workTheme from activeTheme.
     * This is equivalent to "Restore" - discards modifications.
     */
    public void restore() {
        if (activeTheme == null) {
            return;
        }
        workTheme = activeTheme.deepCopy();
    }

    /**
     * Gets the theme suite containing all available themes.
     * @return the theme suite
     */
    public ThemeSuite getThemeSuite() {
        return themeSuite;
    }

    /**
     * Sets the theme suite.
     * @param themeSuite the theme suite to set
     */
    public void setThemeSuite(ThemeSuite themeSuite) {
        this.themeSuite = themeSuite;
    }
    
    /**
     * Gets all available themes from the theme suite.
     * @return iterable of all themes
     */
    public Iterable<Theme> getAllThemes() {
        return themeSuite.getAvailableThemes();
    }

    /**
     * Checks if there are unsaved changes.
     * @return true if workTheme differs from activeTheme, false otherwise
     */
    public boolean hasUnsavedChanges() {
        if (activeTheme == null || workTheme == null) {
            return false;
        }
        return !activeTheme.equals(workTheme);
    }
}
