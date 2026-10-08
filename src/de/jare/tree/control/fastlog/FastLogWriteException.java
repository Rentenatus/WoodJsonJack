/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.fastlog;

/**
 * Exception, die {@link FastLog#write(String, String)} wirft, wenn ein Eintrag
 * nicht geschrieben werden darf, z. B. weil der Typ nicht registriert ist oder
 * der Typ nicht beschreibbar ist.
 *
 * @author Janusch Rentenatus
 */
public class FastLogWriteException extends Exception {

    /**
     * Erzeugt eine FastLogWriteException mit einer Fehlermeldung.
     *
     * @param message die Fehlermeldung
     */
    public FastLogWriteException(String message) {
        super(message);
    }

}
