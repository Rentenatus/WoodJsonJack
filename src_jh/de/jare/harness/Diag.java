/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

/**
 * Temporary diagnostic logging: every line is prefixed {@code [DIAG.<EDIT_VERSION>]}.
 * EDIT_VERSION is set MANUALLY by the editor per code-change round, so console output maps
 * to exactly one change version. TEMPORARY - remove before production.
 *
 * @author Janusch Rentenatus
 */
public final class Diag {

    /** Bump manually with every edit round. */
    public static int EDIT_VERSION = 6;

    private Diag() {
    }

    public static int runNo() {
        return EDIT_VERSION;
    }
}
