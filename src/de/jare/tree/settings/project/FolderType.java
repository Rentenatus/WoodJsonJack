/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings.project;

public enum FolderType {
    SRC("src"),
    REF("ref"),
    LOG("log"),
    DATA("data"),
    OTHER("other");

    private final String id;

    FolderType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static FolderType fromId(String id) {
        if (id == null) {
            return OTHER;
        }
        switch (id.toLowerCase()) {
            case "src":
                return SRC;
            case "ref":
                return REF;
            case "log":
                return LOG;
            case "data":
                return DATA;
            default:
                return OTHER;
        }
    }

    @Override
    public String toString() {
        return id;
    }
}
