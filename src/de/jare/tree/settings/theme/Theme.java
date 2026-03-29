/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings.theme;

public class Theme {

    private String themeId;
    private String themeName;
    private ColorScheme colors;
    private FontSettings fonts;

    public Theme() {
        this.themeId = "new";
        this.themeName = "new";
        this.colors = new ColorScheme();
        this.fonts = new FontSettings();
    }

    public Theme(String themeId, String themeName) {
        this.themeId = themeId;
        this.themeName = themeName;
        this.colors = new ColorScheme();
        this.fonts = new FontSettings();
    }

    public String getThemeId() {
        return themeId;
    }

    public void setThemeId(String themeId) {
        this.themeId = themeId;
    }

    public String getThemeName() {
        return themeName;
    }

    public void setThemeName(String themeName) {
        this.themeName = themeName;
    }

    public ColorScheme getColors() {
        return colors;
    }

    public void setColors(ColorScheme colors) {
        this.colors = colors;
    }

    public FontSettings getFonts() {
        return fonts;
    }

    public void setFonts(FontSettings fonts) {
        this.fonts = fonts;
    }

}
