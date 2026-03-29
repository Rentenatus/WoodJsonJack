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

    public Theme getOrCreate(String themeId) {
        for (Theme th : availableThemes) {
            if (th.getThemeId().equals(themeId)) {
                return th;
            }
        }
        Theme th = new Theme(themeId, themeId);
        th.getColors().resetDefault();
        th.getFonts().resetDefault();
        availableThemes.add(th);
        return th;
    }

}
