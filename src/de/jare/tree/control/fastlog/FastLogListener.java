/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.fastlog;

/**
 * Listener interface for changes of a {@link FastLog}. Implementations are
 * notified on the writing thread; UI implementations must marshal updates to
 * the event dispatch thread themselves.
 *
 * @author Janusch Rentenatus
 */
public interface FastLogListener {

    /**
     * Called when a new entry was written to the log.
     *
     * @param entry the new entry
     */
    void onEntryAdded(FastLogEntry entry);

    /**
     * Called when entries were cleared.
     *
     * @param typeId the cleared type, or null when all entries were cleared
     */
    void onCleared(String typeId);
}
