/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.fastlog;

/**
 * A single entry of the FastLog. The {@code nr} increases monotonically across
 * all types; gaps occur when old entries are evicted from the ring buffer
 * and the numbering still keeps counting up.
 *
 * @author Janusch Rentenatus
 */
public class FastLogEntry {

    private final long nr;
    private final String typeId;
    private final String message;
    private final long timestamp;

    /**
     * Creates a new fast log entry.
     *
     * @param nr running number of the entry (global, monotonically increasing)
     * @param typeId id of the fast log type that was written to
     * @param message message text, including line breaks
     * @param timestamp time of the entry in milliseconds since the epoch
     */
    public FastLogEntry(long nr, String typeId, String message, long timestamp) {
        this.nr = nr;
        this.typeId = typeId;
        this.message = message;
        this.timestamp = timestamp;
    }

    /**
     * @return the running number of the entry
     */
    public long getNr() {
        return nr;
    }

    /**
     * @return the id of the fast log type
     */
    public String getTypeId() {
        return typeId;
    }

    /**
     * @return the message text
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return the time of the entry in milliseconds since the epoch
     */
    public long getTimestamp() {
        return timestamp;
    }

}
