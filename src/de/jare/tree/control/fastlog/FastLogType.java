/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.fastlog;

/**
 * Deskriptor eines Fast-Log-Typs. Jeder Typ entspricht einem System, das in den
 * FastLog schreiben darf oder nicht. Ueber {@code writable} wird gesteuert, ob
 * {@link FastLog#write(String, String)} fuer diesen Typ Eintraege annimmt.
 *
 * @author Janusch Rentenatus
 */
public class FastLogType {

    private final String id;
    private final String label;
    private final boolean writable;

    /**
     * Erzeugt einen neuen Fast-Log-Typ.
     *
     * @param id eindeutige ID des Typs, z. B. "parser"
     * @param label Anzeigename des Typs, z. B. in der Typ-Liste der UI
     * @param writable true, wenn Systeme in diesen Typ schreiben duerfen
     */
    public FastLogType(String id, String label, boolean writable) {
        this.id = id;
        this.label = label;
        this.writable = writable;
    }

    /**
     * @return die eindeutige ID des Typs
     */
    public String getId() {
        return id;
    }

    /**
     * @return der Anzeigename des Typs
     */
    public String getLabel() {
        return label;
    }

    /**
     * @return true, wenn Systeme in diesen Typ schreiben duerfen
     */
    public boolean isWritable() {
        return writable;
    }

    @Override
    public String toString() {
        return label;
    }

}
