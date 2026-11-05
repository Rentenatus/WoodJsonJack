/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.project;

import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.io.JsonNodeWriter;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.JsonWriteException;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.lang.JsonNode;
import static de.jare.jsoncasted.lang.JsonTerms.TERM_WOOD_PROVIDERS;
import de.jare.jsoncasted.model.JsonBuildException;
import de.jare.jsoncasted.project.BundleDescriptor;
import de.jare.jsoncasted.project.ProjectFileService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Executes the bundle clone (concept: CONCEPT_ProjectView.md, section 5 - implementation plan
 * phase 6): a new sibling under the sibling convention (first free name), the folder copy carries
 * the descriptor whose {@code name} is rewritten to the new folder name. No ID remapping (Strategy
 * B, section 7): the Wood object IDs and provider synonyms stay exactly as they are.
 *
 * <p>
 * Two modes on the same machinery: <b>shallow</b> (default off) copies the folder plainly - the
 * files stay byte-identical, external {@code _woodProviders} references stay in place and keep
 * resolving to the original's files (the sibling convention preserves the relative structure).
 * <b>deep</b> relocations every external provider file into the clone folder and rewrites the
 * references to the copies - "clone = make independent". A failure rolls the partial copy back -
 * no half clone survives.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public final class BundleCloner {

    private static final String PROVIDER_SYNONYM_KEY = "synonym";
    private static final String PROVIDER_FILENAME_KEY = "filename";
    private static final ProjectFileService PROJECT_SERVICE = new ProjectFileService();

    private BundleCloner() {
        // static helper - no instances
    }

    /**
     * Clones the bundle folder into its first free sibling (concept section 5).
     *
     * @param sourceFolder the bundle folder to clone, not null.
     * @param deep true for the deep mode (linked files copied into the clone, references
     * rewritten), false for the shallow mode (referenced in place).
     * @return the new bundle folder.
     * @throws IOException when the destination is taken, a file cannot be copied, the descriptor
     * cannot be rewritten or a deep relocation collides - the partial copy is rolled back.
     */
    public static File clone(File sourceFolder, boolean deep) throws IOException {
        Objects.requireNonNull(sourceFolder, "sourceFolder must not be null");
        if (!sourceFolder.isDirectory()) {
            throw new IOException("Not a bundle folder: " + sourceFolder.getAbsolutePath());
        }
        final BundleClonePlanner.ClonePlan plan = BundleClonePlanner.plan(sourceFolder, deep);
        if (plan.isRefused()) {
            throw new IOException(plan.refusalReason());
        }
        final File target = plan.getTargetFolder();
        try {
            copyFolder(sourceFolder, target);
            rewriteDescriptorName(target);
            if (deep) {
                relocateExternalProviders(target);
            }
        } catch (IOException | RuntimeException e) {
            deleteRecursively(target);
            throw e;
        }
        return target;
    }

    /**
     * The plain folder copy of both modes - the descriptor travels with it (all its paths are
     * relative, still valid wherever the folder moves).
     */
    private static void copyFolder(File source, File target) throws IOException {
        if (!target.mkdir()) {
            throw new IOException("Could not create folder: " + target.getAbsolutePath());
        }
        final File[] entries = source.listFiles();
        if (entries == null) {
            return;
        }
        for (File entry : entries) {
            final File copy = new File(target, entry.getName());
            if (entry.isDirectory()) {
                copyFolder(entry, copy);
            } else {
                Files.copy(entry.toPath(), copy.toPath());
            }
        }
    }

    /**
     * Relabel of the copied descriptor: {@code name} becomes the new folder name (concept section
     * 5, the only rewrite besides the deep mode's references).
     */
    private static void rewriteDescriptorName(File target) throws IOException {
        try {
            final BundleDescriptor descriptor = PROJECT_SERVICE.loadBundleDescriptor(target);
            descriptor.setName(target.getName());
            PROJECT_SERVICE.saveBundleDescriptor(target, descriptor);
        } catch (JsonParseException | JsonWriteException | JsonBuildException e) {
            throw new IOException("The copied bundle descriptor could not be relabeled: "
                    + e.getMessage(), e);
        }
    }

    /**
     * Deep mode: every external {@code _woodProviders} file (resolved against the referencing
     * JSON it lies outside the clone folder) moves into the clone folder root - flat, under its
     * plain name; the reference is rewritten to the copied file, so the clone is independent. A
     * relocating file colliding with an existing clone file refuses the whole clone (strict - no
     * silent overwrite); a dangling reference is left untouched (the validator's case).
     */
    private static void relocateExternalProviders(File target) throws IOException {
        for (File json : jsonFilesUnder(target)) {
            final JsonNode root;
            try {
                root = parseRootOf(json);
            } catch (JsonParseException e) {
                throw new IOException("Could not parse " + json.getName()
                        + " of the clone for the deep relocation: " + e.getMessage(), e);
            }
            if (root == null) {
                continue;
            }
            boolean changed = false;
            for (JsonNode providerArray : findProviderArrays(root)) {
                for (JsonNode provider : arrayOf(providerArray)) {
                    changed |= relocateOneProvider(provider, json, target);
                }
            }
            if (changed) {
                try {
                    JsonNodeWriter.write(root, json);
                } catch (JsonParseException e) {
                    throw new IOException("Could not rewrite " + json.getName()
                            + " of the clone: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * One provider entry: external file copied flat into the clone root, {@code filename}
     * rewritten to the relative path of the copy.
     *
     * @return true when the referencing JSON changed.
     */
    private static boolean relocateOneProvider(JsonNode provider, File referencingJson, File target)
            throws IOException {
        final Map<String, JsonNode> members = provider.asObjectValues();
        if (members == null) {
            return false;
        }
        final JsonNode filenameNode = members.get(PROVIDER_FILENAME_KEY);
        final JsonNode synonymNode = members.get(PROVIDER_SYNONYM_KEY);
        if (filenameNode == null || synonymNode == null) {
            return false;
        }
        final String filename = filenameNode.asText();
        if (filename == null || filename.isBlank()) {
            return false;
        }
        final File referenced = new File(referencingJson.getParentFile(), filename);
        if (isInside(target, referenced)) {
            return false; // inside the clone already - the folder copy carries it
        }
        if (!referenced.isFile()) {
            return false; // dangling reference - the validator's case, not the cloner's
        }
        final File relocated = new File(target, referenced.getName());
        if (relocated.exists()) {
            throw new IOException("Deep relocation collides: '" + referenced.getName()
                    + "' already exists in the clone - rename one side first");
        }
        Files.copy(referenced.toPath(), relocated.toPath());
        provider.put(PROVIDER_FILENAME_KEY,
                JsonNode.stringNode(relativeReference(referencingJson, relocated)));
        return true;
    }

    //
    // helpers - parsing, traversal, path math, rollback
    //

    private static JsonNode parseRootOf(File json) throws IOException, JsonParseException {
        final de.jare.jsoncasted.lang.JsonResource resource
                = JsonParserService.parse(json, JsonDebugLevel.SIMPLE);
        return resource != null ? resource.getRoot() : null;
    }

    private static List<File> jsonFilesUnder(File folder) {
        final List<File> files = new ArrayList<>();
        collectJsonFiles(folder, files);
        return files;
    }

    private static void collectJsonFiles(File folder, List<File> sink) {
        final File[] entries = folder.listFiles();
        if (entries == null) {
            return;
        }
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collectJsonFiles(entry, sink);
            } else if (entry.getName().toLowerCase().endsWith(".json")) {
                sink.add(entry);
            }
        }
    }

    private static void findProviderArrays(JsonNode node, List<JsonNode> sink) {
        if (node == null) {
            return;
        }
        final Map<String, JsonNode> members = node.asObjectValues();
        if (members != null) {
            final JsonNode providers = members.get(TERM_WOOD_PROVIDERS);
            if (providers != null && providers.asArray() != null) {
                sink.add(providers);
            }
            for (JsonNode child : members.values()) {
                findProviderArrays(child, sink);
            }
            return;
        }
        for (JsonNode child : arrayOf(node)) {
            findProviderArrays(child, sink);
        }
    }

    private static List<JsonNode> findProviderArrays(JsonNode root) {
        final List<JsonNode> sink = new ArrayList<>();
        findProviderArrays(root, sink);
        return sink;
    }

    private static List<JsonNode> arrayOf(JsonNode node) {
        final List<JsonNode> empty = new ArrayList<>(0);
        if (node == null || node.asArray() == null) {
            return empty;
        }
        return node.asArray();
    }

    private static boolean isInside(File folder, File candidate) {
        final Path folderPath = folder.toPath().toAbsolutePath().normalize();
        return candidate.toPath().toAbsolutePath().normalize().startsWith(folderPath);
    }

    private static String relativeReference(File referencingJson, File referenced) {
        final Path from = referencingJson.getParentFile().toPath().toAbsolutePath().normalize();
        final Path to = referenced.toPath().toAbsolutePath().normalize();
        return from.relativize(to).toString().replace('\\', '/');
    }

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
