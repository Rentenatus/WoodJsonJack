/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree;

import com.formdev.flatlaf.FlatLightLaf;
import de.jare.tree.ui.WoodWindow;
import javax.swing.*;

/**
 *
 * @author Jansuch Rentenatus
 */
public class WoodJsonEditor {

    public static void main(String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(WoodWindow::new);
    }

}
