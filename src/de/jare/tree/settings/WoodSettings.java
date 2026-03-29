/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings;

import de.jare.tree.settings.project.ProjektEntry;
import de.jare.tree.settings.theme.Theme;
import de.jare.tree.settings.theme.WindowLayout;

import java.util.List;

public class WoodSettings {

    // UI
    private String themeId;

    /**
     * Transiente Variable, die das aktuell angezeigte Theme hält. Wird nicht serialisiert, sondern bei Bedarf aus der themeId neu geladen.
     */
    private Theme shownTheme;


    // Projekte
    private List<ProjektEntry> knownProjects;

    // Editor-Defaults
    private AgentPreferences agentPreferences;

}
