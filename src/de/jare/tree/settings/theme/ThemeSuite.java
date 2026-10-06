/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings.theme;

import java.util.ArrayList;
import java.util.List;

public class ThemeSuite {

    private List<Theme> availableThemes;
    private WindowLayout windowLayout;

    public ThemeSuite() {
        this.availableThemes = new ArrayList<>();
    }

    public List<Theme> getAvailableThemes() {
        return availableThemes;
    }

    public void setAvailableThemes(List<Theme> availableThemes) {
        this.availableThemes = availableThemes;
    }

    public WindowLayout getWindowLayout() {
        return windowLayout;
    }

    public void setWindowLayout(WindowLayout windowLayout) {
        this.windowLayout = windowLayout;
    }

    public Theme getOrCreate(String themeId) {
        for (Theme th : availableThemes) {
            if (th.getThemeId().equals(themeId)) {
                fillMissingSchemeKeys(th);
                return th;
            }
        }
        Theme th = new Theme(themeId, themeId);
        th.getColors().resetDefault();
        th.getFonts().resetDefault();
        availableThemes.add(th);
        return th;
    }

    /**
     * Themes loaded from a persisted suite carry only the keys of their save day - keys added later (like the
     * annotation color) would be missing and the renderer would fall back to no color at all. Missing scheme
     * keys are filled from the defaults; existing values stay untouched.
     *
     * @param theme the theme to complete
     */
    private void fillMissingSchemeKeys(Theme theme) {
        final ColorScheme colorDefaults = new ColorScheme();
        colorDefaults.resetDefault();
        final FontSettings fontDefaults = new FontSettings();
        fontDefaults.resetDefault();
        for (String key : AScheme.SCHEME_LIST) {
            if (!theme.getColors().hasColor(key)) {
                theme.getColors().setColor(key, colorDefaults.getColor(key));
            }
            if (theme.getFonts().getFont(key) == null) {
                theme.getFonts().setFont(key, fontDefaults.getFont(key));
            }
        }
    }

}
