/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings.project;

import java.util.ArrayList;
import java.util.List;

/**
 * The user global project history (UX layer of docs/koncepts/CONCEPT_ProjectView.md): every project that was
 * opened or newly created lands at the front of this list, older entries move down. It lives
 * outside any single project as {@code user-home/.wood/projects.json} - the wood universe of the
 * user, next to but independent of the editor settings.
 *
 * @author Janusch Rentenatus
 */
public class ProjectHistory {

    /** How many projects the history keeps - the oldest beyond this count falls off. */
    public static final int MAX_ENTRIES = 10;

    private List<HistoryEntry> projects;

    public ProjectHistory() {
        this.projects = new ArrayList<>();
    }

    public List<HistoryEntry> getProjects() {
        if (projects == null) {
            projects = new ArrayList<>();
        }
        return projects;
    }

    public void setProjects(List<HistoryEntry> projects) {
        this.projects = projects;
    }

    /**
     * Moves a project to the front of the history or inserts it there when it is new. The same
     * absolute path is one project, no matter under which name or drive letter it appears; a
     * re-opened project therefore jumps to the top instead of duplicating.
     *
     * @param projectName the display name (the project document's name)
     * @param projectPath the project root folder
     * @return this history, for chaining
     */
    public ProjectHistory remember(String projectName, String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return this;
        }
        HistoryEntry known = null;
        for (HistoryEntry entry : getProjects()) {
            if (entry != null && sameFolder(entry.getProjectPath(), projectPath)) {
                known = entry;
                break;
            }
        }
        getProjects().remove(known);
        HistoryEntry fresh = new HistoryEntry(projectName, projectPath);
        getProjects().add(0, fresh);
        while (getProjects().size() > MAX_ENTRIES) {
            getProjects().remove(getProjects().size() - 1);
        }
        return this;
    }

    /**
     * Compares two folder paths for identity: canonical spelling (dot segments and double
     * separators dissolved) and - per {@link java.io.File#compareTo} - with the platform's case
     * sensitivity. On Windows {@code D:/Projects/Alpha} and {@code d:/projects/alpha} are one
     * project; drive case, dot segments and separators must not duplicate a history entry.
     *
     * @param pathA the first folder path
     * @param pathB the second folder path
     * @return true when both paths name the same folder
     */
    private static boolean sameFolder(String pathA, String pathB) {
        if (pathA == null || pathB == null) {
            return false;
        }
        try {
            java.io.File a = new java.io.File(pathA).getCanonicalFile();
            java.io.File b = new java.io.File(pathB).getCanonicalFile();
            return a.compareTo(b) == 0;
        } catch (java.io.IOException ex) {
            return new java.io.File(pathA).getAbsolutePath()
                    .equalsIgnoreCase(new java.io.File(pathB).getAbsolutePath());
        }
    }
}
