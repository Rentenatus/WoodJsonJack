/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.project;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The dry planning of a bundle clone (concept: docs/koncepts/CONCEPT_ProjectView.md, section 5): new sibling
 * under the sibling convention with the first free name suffix, the descriptor travels with
 * the copy and its {@code name} is rewritten. Two modes: deep (default) copies the linked
 * files into the clone and rewrites the links, shallow references them in place. This class
 * only plans - the execution follows with phase 6 of the implementation plan.
 *
 * @author Janusch Rentenatus
 */
public final class BundleClonePlanner {

    /**
     * The first suffix of the sibling convention (concept section 5): {@code config1_copy}.
     */
    public static final String COPY_SUFFIX = "_copy";

    private BundleClonePlanner() {
        // static helper - no instances
    }

    /**
     * A dry clone plan: what a clone of the source bundle would be - nothing of it executes.
     */
    public static class ClonePlan {

        private final File sourceFolder;
        private final File targetFolder;
        private final boolean deep;
        private final List<String> notes = new ArrayList<>();

        private ClonePlan(File sourceFolder, File targetFolder, boolean deep) {
            this.sourceFolder = sourceFolder;
            this.targetFolder = targetFolder;
            this.deep = deep;
            notes.add("The bundle folder is copied completely - the descriptor travels with it.");
            notes.add("The name in the descriptor is rewritten to \"" + targetFolder.getName() + "\".");
            if (deep) {
                notes.add("Deep: linked files are copied into the clone, the links are rewritten to the copies.");
            } else {
                notes.add("Shallow: linked files stay referenced in place - the links keep pointing at the original.");
            }
        }

        public File getSourceFolder() {
            return sourceFolder;
        }

        public File getTargetFolder() {
            return targetFolder;
        }

        public boolean isDeep() {
            return deep;
        }

        public List<String> getNotes() {
            return new ArrayList<>(notes);
        }

        /**
         * @return true when the copy would be refused: the destination exists or the source
         * has no parent folder to sit next to
         */
        public boolean isRefused() {
            return targetFolder == null || targetFolder.exists();
        }

        /**
         * @return the refusal reason when {@link #isRefused()} is true, or null
         */
        public String refusalReason() {
            if (targetFolder == null) {
                return "The source bundle has no parent folder - no sibling convention possible.";
            }
            if (targetFolder.exists()) {
                return "The destination already exists: " + targetFolder.getName();
            }
            return null;
        }

    }

    /**
     * Plans the clone (dry - no file system write): the sibling convention picks
     * {@code <name>_copy}, then {@code <name>_2}, {@code <name>_3}, ... - the first free name.
     *
     * @param sourceFolder the bundle to clone
     * @param deep true for the deep mode (default), false for the shallow mode
     * @return the dry plan of the clone
     */
    public static ClonePlan plan(File sourceFolder, boolean deep) {
        return new ClonePlan(sourceFolder, findFreeSiblingFolder(sourceFolder), deep);
    }

    /**
     * The sibling convention (concept section 5): the clone lands inside the same partition,
     * next to the original - first candidate {@code <name>_copy}, then {@code <name>_2},
     * {@code <name>_3}, ... until a free name is found.
     *
     * @param sourceFolder the folder whose sibling is sought
     * @return the free sibling folder, or null when the source has no parent
     */
    public static File findFreeSiblingFolder(File sourceFolder) {
        File parent = sourceFolder.getParentFile();
        if (parent == null) {
            return null;
        }
        String name = sourceFolder.getName();
        File candidate = new File(parent, name + COPY_SUFFIX);
        int count = 2;
        while (candidate.exists()) {
            candidate = new File(parent, name + "_" + count);
            count++;
        }
        return candidate;
    }

}
