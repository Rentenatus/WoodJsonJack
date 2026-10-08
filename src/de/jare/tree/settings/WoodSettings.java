/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings;

import de.jare.tree.settings.project.ProjektEntry;
import de.jare.tree.settings.theme.LafCatalog;
import de.jare.tree.settings.theme.Theme;
import de.jare.tree.settings.theme.ThemeSuite;
import java.util.List;

/**
 *
 * @author Jansuch Rentenatus
 */
public class WoodSettings {

    public final static WoodSettings INSTANCE = new WoodSettings();

    // UI
    private String themeId;

    /**
     * Transient variable holding the currently shown theme. Not serialized;
     * reloaded from the themeId on demand.
     */
    private Theme shownTheme;

    // Color schema
    private String lightLaf;
    private String darkLaf;
    private boolean darkMode;

    // projects
    private List<ProjektEntry> knownProjects;

    // editor defaults
    private AgentPreferences agentPreferences;
    private UserPreferences userPreferences;

    public WoodSettings() {
        this.agentPreferences = new AgentPreferences();
        this.userPreferences = new UserPreferences();
        this.shownTheme = new Theme();
        this.shownTheme.resetDefault();
        this.lightLaf = LafCatalog.defaultLight().getClassName();
        this.darkLaf = LafCatalog.defaultDark().getClassName();
        this.darkMode = false;
    }

    public AgentPreferences getAgentPreferences() {
        return agentPreferences;
    }

    public void setAgentPreferences(AgentPreferences agentPreferences) {
        this.agentPreferences = agentPreferences;
    }

    public UserPreferences getUserPreferences() {
        return userPreferences;
    }

    public void setUserPreferences(UserPreferences userPreferences) {
        this.userPreferences = userPreferences;
    }

    public void useThemeSuite(ThemeSuite themeSuite) {
        if (themeId == null) {
            themeId = "swing";
        }
        shownTheme = themeSuite.getOrCreate(themeId);
    }

    public String getThemeId() {
        return themeId;
    }

    public void setThemeId(String themeId) {
        this.themeId = themeId;
    }

    public Theme getShownTheme() {
        return shownTheme;
    }

    public List<ProjektEntry> getKnownProjects() {
        return knownProjects;
    }

    public void setKnownProjects(List<ProjektEntry> knownProjects) {
        this.knownProjects = knownProjects;
    }

    public String getLightLaf() {
        return lightLaf;
    }

    public void setLightLaf(String lightLaf) {
        this.lightLaf = lightLaf;
    }

    public String getDarkLaf() {
        return darkLaf;
    }

    public void setDarkLaf(String darkLaf) {
        this.darkLaf = darkLaf;
    }

    public boolean isDarkMode() {
        return darkMode;
    }

    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
    }

    /**
     * Color key prefix of the active color mode. The renderer asks theme colors with this
     * prefix, so the same theme serves the light and the dark half of its color scheme.
     *
     * @return "dark." or "light."
     */
    public String getColorPrefix() {
        return darkMode ? "dark." : "light.";
    }

    /**
     * Copies all persisted fields of the given settings into this instance. Used to transfer
     * the loaded settings into the static singleton all renderers read from.
     *
     * @param source the settings to copy from
     */
    public void adopt(WoodSettings source) {
        this.themeId = source.themeId;
        this.lightLaf = source.lightLaf;
        this.darkLaf = source.darkLaf;
        this.darkMode = source.darkMode;
        this.knownProjects = source.knownProjects;
        this.agentPreferences = source.agentPreferences;
        this.userPreferences = source.userPreferences;
    }

}
