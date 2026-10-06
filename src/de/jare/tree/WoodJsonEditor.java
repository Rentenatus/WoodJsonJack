/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree;

import de.jare.tree.settings.SettingsService;
import de.jare.tree.settings.WoodSettings;
import de.jare.tree.settings.theme.LafCatalog;
import de.jare.tree.ui.WoodWindow;
import javax.swing.SwingUtilities;

/**
 *
 * @author Janusch Rentenatus
 */
public class WoodJsonEditor {

    public static void main(String[] args) {
        final WoodSettings settings = new SettingsService().loadWoodSettings(false);
        LafCatalog.apply(settings);
        SwingUtilities.invokeLater(WoodWindow::new);
    }

}
