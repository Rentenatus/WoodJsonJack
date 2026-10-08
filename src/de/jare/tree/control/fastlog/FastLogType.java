/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.fastlog;

/**
 * Descriptor of a fast log type. Each type corresponds to a system that may
 * or may not write to the FastLog. {@code writable} controls whether
 * {@link FastLog#write(String, String)} accepts entries for this type.
 *
 * @author Janusch Rentenatus
 */
public class FastLogType {

    private final String id;
    private final String label;
    private final boolean writable;

    /**
     * Creates a new fast log type.
     *
     * @param id unique id of the type, e.g. "parser"
     * @param label display name of the type, e.g. in the type list of the UI
     * @param writable true when systems may write to this type
     */
    public FastLogType(String id, String label, boolean writable) {
        this.id = id;
        this.label = label;
        this.writable = writable;
    }

    /**
     * @return the unique id of the type
     */
    public String getId() {
        return id;
    }

    /**
     * @return the display name of the type
     */
    public String getLabel() {
        return label;
    }

    /**
     * @return true when systems may write to this type
     */
    public boolean isWritable() {
        return writable;
    }

    @Override
    public String toString() {
        return label;
    }

}
