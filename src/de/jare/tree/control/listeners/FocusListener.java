/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.listeners;

/**
 * Listener interface for focus changes on tree editor components.
 * <p>
 * Implementations are notified when an editor component gains or loses focus.
 * This is typically used to update UI state or perform actions when the active
 * editor changes.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public interface FocusListener {

    /**
     * Called when the editor component gains focus.
     * Implementations should update their state to reflect that this editor
     * is now the active component.
     */
    void onFocusGained();

    /**
     * Called when the editor component loses focus.
     * Implementations should update their state to reflect that this editor
     * is no longer the active component.
     */
    void onFocusLost();
}
