/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */

package de.jare.tree.control;

public interface EditParticipant {

    /**
     * Wird aufgerufen, wenn dieser Editor Fokus bekommt.
     */
    void onFocusGained();

    /**
     * Wird aufgerufen, wenn der Fokus auf einen anderen Editor wechselt.
     */
    void onFocusLost();

    /**
     * Men�-/Command-Ereignisse (Add, Delete, Rename, etc.).
     * @param commandId
     */
    void onCommand(String commandId);
}
