/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.spi;

import de.jare.harness.HarnessEngine;
import de.jare.harness.HarnessInstance;

/**
 * The view SPI: the harness core only knows this interface; the embedding application provides
 * the concrete view (WJJ: a Swing dialog, later: a TUI). No Swing/AWT types cross this boundary.
 *
 * @author Janusch Rentenatus
 */
public interface HarnessView {

    /** Bind the view to a finished (or running) instance + engine and show it. */
    void bind(HarnessInstance instance, HarnessEngine engine);

    void setVisible(boolean visible);
}
