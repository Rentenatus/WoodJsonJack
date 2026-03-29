/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings.project;

import java.util.ArrayList;
import java.util.List;

public class ProjectSettings extends ProjektEntry {

    List<RootSetting> rootSettings;
    List<String> openedFiles;

    public ProjectSettings(String projectName, String projectPath) {
        super(projectName, projectPath);
        this.rootSettings = new ArrayList<>();
        this.openedFiles = new ArrayList<>();
    }

    public ProjectSettings(ProjektEntry projekt) {
        super(projekt.getProjectName(), projekt.getProjectPath());
        this.rootSettings = new ArrayList<>();
        this.openedFiles = new ArrayList<>();
    }

    public List<RootSetting> getRootSettings() {
        return rootSettings;
    }

    public void setRootSettings(List<RootSetting> rootSettings) {
        this.rootSettings = rootSettings;
    }

    public List<String> getOpenedFiles() {
        return openedFiles;
    }

    public void setOpenedFiles(List<String> openedFiles) {
        this.openedFiles = openedFiles;
    }
}
