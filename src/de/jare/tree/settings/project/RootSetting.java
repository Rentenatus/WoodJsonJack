/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings.project;

public class RootSetting {

    private String folderName;
    private String folderPath;
    private FolderType folderType;    // "src", "ref", "log", "data", "other"

    public RootSetting(String folderName, String folderPath, FolderType folderType) {
        this.folderName = folderName;
        this.folderPath = folderPath;
        this.folderType = folderType;
    }

    public String getFolderName() {
        return folderName;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public FolderType getFolderType() {
        return folderType;
    }

}
