/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings.project;

/**
 * One entry of the user global project history: the display name of a remembered project and
 * the absolute path of its root. The own type (instead of the legacy {@link ProjektEntry})
 * keeps the history self contained: no-arg constructor and setters, as the reflection based
 * JSON round trip of the settings service expects it.
 *
 * @author Janusch Rentenatus
 */
public class HistoryEntry {

    private String projectName;
    private String projectPath;

    public HistoryEntry() {
    }

    public HistoryEntry(String projectName, String projectPath) {
        this.projectName = projectName;
        this.projectPath = projectPath;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }
}
