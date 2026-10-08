/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.fastlog;

/**
 * Ein einzelner Eintrag des FastLog. Die {@code nr} ist global ueber alle Typen
 * monoton steigend; Luecken entstehen, wenn alte Eintraege aus dem Ringpuffer
 * verdraengt werden und die Nummerierung trotzdem weiterlaeuft.
 *
 * @author Janusch Rentenatus
 */
public class FastLogEntry {

    private final long nr;
    private final String typeId;
    private final String message;
    private final long timestamp;

    /**
     * Erzeugt einen neuen Fast-Log-Eintrag.
     *
     * @param nr laufende Nummer des Eintrags (global, monoton steigend)
     * @param typeId ID des Fast-Log-Typs, in den geschrieben wurde
     * @param message Meldungstext, inklusive Zeilenumbruechen
     * @param timestamp Zeitpunkt des Eintrags in Millisekunden seit Epoche
     */
    public FastLogEntry(long nr, String typeId, String message, long timestamp) {
        this.nr = nr;
        this.typeId = typeId;
        this.message = message;
        this.timestamp = timestamp;
    }

    /**
     * @return die laufende Nummer des Eintrags
     */
    public long getNr() {
        return nr;
    }

    /**
     * @return die ID des Fast-Log-Typs
     */
    public String getTypeId() {
        return typeId;
    }

    /**
     * @return der Meldungstext
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return der Zeitpunkt des Eintrags in Millisekunden seit Epoche
     */
    public long getTimestamp() {
        return timestamp;
    }

}
