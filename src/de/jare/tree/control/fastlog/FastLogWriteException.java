/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.fastlog;

/**
 * Exception thrown by {@link FastLog#write(String, String)} when an entry
 * must not be written, e.g. because the type is not registered or the type
 * is not writable.
 *
 * @author Janusch Rentenatus
 */
public class FastLogWriteException extends Exception {

    /**
     * Creates a FastLogWriteException with an error message.
     *
     * @param message the error message
     */
    public FastLogWriteException(String message) {
        super(message);
    }

}
