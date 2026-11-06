/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.project;

import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.JsonWriteException;
import de.jare.jsoncasted.project.BundleDescriptor;
import de.jare.jsoncasted.project.ProjectFileService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Executes the materialization of the implicit single-file bundle (concept:
 * docs/koncepts/CONCEPT_ProjectView.md, section 4, rule 4): a bare {@code *.json} directly inside a json
 * partition is accepted as an implicit single-file bundle - the first structural action
 * materializes it. The file moves into a sibling folder named after its stem and a bundle
 * descriptor declares it. No implicit state survives an explicit action.
 *
 * @author Janusch Rentenatus
 */
public final class BundleMaterializer {

    private BundleMaterializer() {
        // static helper - no instances
    }

    /**
     * Materializes the bundle around the given bare JSON file: creates the sibling folder,
     * writes the {@code .woodbundle.json} and moves the file into it. The descriptor is written
     * BEFORE the move - so every failure leaves the original file in place and the rolled-back
     * folder carries at most the descriptor: no implicit state survives an explicit action,
     * and no half materialization survives a failure either.
     *
     * @param bareJsonFile the bare JSON directly inside a json partition
     * @return the new bundle folder
     * @throws IOException when the folder cannot be created, the descriptor cannot be written
     * or the file cannot be moved - the partial materialization is rolled back
     */
    public static File materialize(File bareJsonFile) throws IOException {
        return materialize(bareJsonFile, new ProjectFileService());
    }

    /**
     * The materialization with the given descriptor service (tests inject a failing one to
     * reach the rollback).
     *
     * @param bareJsonFile the bare JSON directly inside a json partition
     * @param service the service writing the bundle descriptor, not null
     * @return the new bundle folder
     * @throws IOException when the folder cannot be created, the descriptor cannot be written
     * or the file cannot be moved - the partial materialization is rolled back
     */
    static File materialize(File bareJsonFile, ProjectFileService service) throws IOException {
        String fullName = bareJsonFile.getName();
        String stem = fullName.toLowerCase().endsWith(".json")
                ? fullName.substring(0, fullName.length() - ".json".length()) : fullName;
        File bundleFolder = new File(bareJsonFile.getParentFile(), stem);
        if (bundleFolder.exists()) {
            throw new IOException("Folder already exists: " + bundleFolder.getAbsolutePath());
        }
        if (!bundleFolder.mkdir()) {
            throw new IOException("Could not create folder: " + bundleFolder.getAbsolutePath());
        }
        boolean moved = false;
        try {
            try {
                BundleDescriptor descriptor = new BundleDescriptor(stem, fullName);
                service.saveBundleDescriptor(bundleFolder, descriptor);
            } catch (JsonParseException | JsonWriteException e) {
                throw new IOException("The bundle descriptor could not be written: "
                        + e.getMessage(), e);
            }
            Files.move(bareJsonFile.toPath(), new File(bundleFolder, fullName).toPath());
            moved = true;
        } finally {
            if (!moved) {
                deleteRecursively(bundleFolder);
            }
        }
        return bundleFolder;
    }

    /**
     * The rollback of a failed materialization: the fresh folder disappears completely - the
     * bare JSON file never moved, so no half state survives anywhere.
     */
    private static void deleteRecursively(File folder) {
        final File[] entries = folder.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                deleteRecursively(entry);
            }
        }
        if (folder.exists()) {
            folder.delete();
        }
    }

}
