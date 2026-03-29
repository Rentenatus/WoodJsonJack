/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings.project;

public class ProjektEntry {

    private String projectName;

    private String projectPath;

    public ProjektEntry(String projectName, String projectPath) {
        this.projectName = projectName;
        this.projectPath = projectPath;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getProjectPath() {
        return projectPath;
    }

}
