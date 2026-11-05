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
 * CONCEPT_ProjectView.md, section 4, rule 4): a bare {@code *.json} directly inside a json
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
     * moves the file into it and writes the {@code .woodbundle.json}.
     *
     * @param bareJsonFile the bare JSON directly inside a json partition
     * @return the new bundle folder
     * @throws IOException when the folder cannot be created, the file cannot be moved or the
     * descriptor cannot be written
     */
    public static File materialize(File bareJsonFile) throws IOException {
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
        try {
            Files.move(bareJsonFile.toPath(), new File(bundleFolder, fullName).toPath());
        } catch (IOException e) {
            bundleFolder.delete();
            throw e;
        }
        try {
            ProjectFileService service = new ProjectFileService();
            BundleDescriptor descriptor = new BundleDescriptor(stem, fullName);
            service.saveBundleDescriptor(bundleFolder, descriptor);
        } catch (JsonParseException | JsonWriteException e) {
            throw new IOException("File moved, but the bundle descriptor could not be written: "
                    + e.getMessage(), e);
        }
        return bundleFolder;
    }

}
