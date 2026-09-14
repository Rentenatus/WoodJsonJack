/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.JsonParser;
import de.jare.jsoncasted.io.convertservice.WoodResolution;
import de.jare.jsoncasted.item.JsonItem;
import de.jare.jsoncasted.item.builder.JsonBuilder;
import de.jare.jsoncasted.model.JsonBuildException;
import de.jare.jsoncasted.model.descriptor.JsonModelDescriptor;
import de.jare.jsoncasted.model.descriptor.def.JsonDescriptorDefinition;
import de.jare.jsonconfig.def.JsonConfigDefinition;
import de.jare.tree.control.JackMasterControl;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Central class for main actions like loading and handling JSON files. Handles
 * the loading of main files and their associated description files with path
 * shift tolerance.
 *
 * @author Janusch Rentenatus
 */
public class JackMainActions {

    private final WoodWindow woodWindow;
    private final JackMasterControl master;

    public JackMainActions(WoodWindow woodWindow, JackMasterControl master) {
        this.woodWindow = woodWindow;
        this.master = master;
    }

    /**
     * Loads a JSON file and its associated description files if present.
     *
     * @param file the JSON file to load
     */
    public void loadJsonFile(File file) {
        try {
            // 1. Load main file
            EditTree tree = JsonTreeConverter.fromJsonFile(file);
            if (tree == null) {
                return;
            }
            EditNode rootNode = tree.getRoot();
            if (rootNode == null) {
                return;
            }

            // 2. Create WoodResolution to extract description files
            JsonConfigDefinition definition = JsonConfigDefinition.getInstance();
            WoodResolution resolution = JsonParser.parse(file, definition, definition.getRootClass());

            // 3. Load description files with path shift tolerance
            Map<String, JsonModelDescriptor> descriptors = new HashMap<>();
            Map<String, EditTree> descriptionTrees = loadDescriptionFiles(resolution, file, descriptors);

            // 4. Set model descriptor on main tree (starts parser automatically)
            applyDescriptorToTree(tree, descriptors);

            // 5. Add main tree to window
            woodWindow.addEditorTab(file, tree);

            // 6. Store description trees and descriptors for later use
            woodWindow.setDescriptionTrees(file, descriptionTrees);
            woodWindow.setDescriptionDescriptors(file, descriptors);

        } catch (IOException | JsonParseException e) {
            woodWindow.showErrorDialog("Fehler beim Öffnen der Datei: " + e.getMessage());
        }
    }

    /**
     * Sets the first available JsonModelDescriptor on the EditTree, which
     * automatically starts the TypeParserService. If multiple descriptors
     * exist, the first becomes the main descriptor and the rest are added as
     * repo descriptors.
     *
     * @param tree the EditTree to configure
     * @param descriptors map of model names to their JsonModelDescriptor
     */
    private void applyDescriptorToTree(EditTree tree, Map<String, JsonModelDescriptor> descriptors) {
        if (tree == null || descriptors == null || descriptors.isEmpty()) {
            return;
        }
        JsonModelDescriptor primary = null;
        for (Map.Entry<String, JsonModelDescriptor> entry : descriptors.entrySet()) {
            if (primary == null) {
                primary = entry.getValue();
            } else {
                primary.addRepoDescriptor(entry.getKey(), entry.getValue());
            }
        }
        if (primary != null) {
            tree.setJsonModelDescriptor(primary);
            System.out.println("JsonModelDescriptor gesetzt, Parser gestartet: " + primary.getModelName());
        }
    }

    /**
     * Findet Description-Datei mit intelligenter Pfadauflösung.
     *
     * Priorität: 1. Absolute Pfade (C:\, /) 2. Relative Pfade mit Prefix (../,
     * ./) im Originalverzeichnis 3. Pfad OHNE "../"-Prefix im
     * Originalverzeichnis (für Edge-Cases wie "../file.json" → "file.json") 4.
     * User-Dialog
     *
     * @param descriptionFilePath der Pfad aus der descriptionFileMap
     * @param originalFile die ursprünglich geladene JSON-Datei
     * @return die gefundene Datei, oder null wenn User abbricht
     */
    public File findDescriptionFile(String descriptionFilePath, File originalFile) {
        if (descriptionFilePath == null || originalFile == null) {
            return null;
        }

        // 1. Absolute Pfade direkt testen
        try {
            Path descPath = Paths.get(descriptionFilePath);
            if (descPath.isAbsolute()) {
                File file = descPath.toFile();
                if (file.exists()) {
                    return file;
                }
            }
        } catch (Exception e) {
            // Ungültiger Pfad, ignorieren
        }

        // 2. Pfad des Originalverzeichnisses
        Path originalDir = Paths.get(originalFile.getAbsolutePath()).getParent();
        if (originalDir == null) {
            originalDir = Paths.get(System.getProperty("user.dir")); // Fallback: Arbeitsverzeichnis
        }

        // 2a. Mit vollem Pfad (inkl. ../) versuchen
        try {
            Path resolved = originalDir.resolve(descriptionFilePath).normalize();
            File file = resolved.toFile();
            if (file.exists()) {
                return file;
            }
        } catch (Exception e) {
            // Pfadfehler, ignorieren
        }

        // 2b. OHNE "../"-Prefix im Originalverzeichnis suchen
        String cleanPath = descriptionFilePath.replaceFirst("^\\.\\./", "");
        try {
            Path direct = originalDir.resolve(cleanPath);
            File file = direct.toFile();
            if (file.exists()) {
                return file;
            }
        } catch (Exception e) {
            // Ignorieren
        }

        // 3. User-Dialog
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Description-Datei wählen für: " + descriptionFilePath);
        chooser.setFileFilter(new FileNameExtensionFilter("JSON-Dateien", "json"));

        int result = chooser.showOpenDialog(woodWindow);
        return result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }

    /**
     * Loads all description files referenced in the WoodResolution. Each
     * description file is loaded twice: once as EditTree for the tree view, and
     * once as JsonModelDescriptor for the On-the-Fly parser.
     *
     * @param resolution the WoodResolution after parsing the main file
     * @param originalFile the originally loaded JSON file
     * @param descriptorsOut out-parameter: populated with model names to their
     * JsonModelDescriptor instances (may be null if not needed)
     * @return Map of model names to their description EditTrees
     */
    public Map<String, EditTree> loadDescriptionFiles(
            WoodResolution resolution, File originalFile,
            Map<String, JsonModelDescriptor> descriptorsOut) {
        Map<String, EditTree> descriptionTrees = new HashMap<>();

        if (resolution == null) {
            return descriptionTrees;
        }

        // Check if description files are present in the map
        Map<String, String> descriptionFileMap = resolution.getDescriptionFileMap();
        if (descriptionFileMap == null || descriptionFileMap.isEmpty()) {
            return descriptionTrees;
        }

        // Load each description file
        for (Map.Entry<String, String> entry : descriptionFileMap.entrySet()) {
            String modelName = entry.getKey();
            String filePath = entry.getValue();

            try {
                File descriptionFile = findDescriptionFile(filePath, originalFile);
                if (descriptionFile != null && descriptionFile.exists()) {
                    // 1. Load as EditTree for tree view
                    EditTree descriptionTree = JsonTreeConverter.fromJsonFile(descriptionFile);
                    descriptionTrees.put(modelName, descriptionTree);
                    System.out.println("Description für '" + modelName + "' geladen: " + descriptionFile.getAbsolutePath());

                    // 2. Load as JsonModelDescriptor for On-the-Fly parser
                    if (descriptorsOut != null) {
                        JsonModelDescriptor descriptor = parseDescriptor(descriptionFile);
                        if (descriptor != null) {
                            descriptorsOut.put(modelName, descriptor);
                        }
                    }
                } else {
                    System.out.println("Description für '" + modelName + "' nicht gefunden: " + filePath);
                }
            } catch (IOException | JsonParseException e) {
                System.err.println("Fehler beim Laden der Description für '" + modelName + "': " + e.getMessage());
            }
        }

        return descriptionTrees;
    }

    /**
     * Parses a description file into a JsonModelDescriptor using
     * JsonDescriptorDefinition as the meta-model.
     *
     * @param descriptionFile the description JSON file
     * @return the parsed JsonModelDescriptor, or null if parsing fails
     */
    private JsonModelDescriptor parseDescriptor(File descriptionFile) throws IOException {
        try {
            JsonDescriptorDefinition descDef = JsonDescriptorDefinition.getInstance();
            WoodResolution descResolution = JsonParser.parse(
                    descriptionFile, descDef, descDef.getRootClass());
            if (descResolution == null || descResolution.getAnswer() == null) {
                return null;
            }
            JsonItem item = descResolution.getAnswer();
            return (JsonModelDescriptor) JsonBuilder.buildInstance(
                    descDef.getModel(), true, item);
        } catch (JsonParseException | JsonBuildException | ClassCastException e) {
            System.err.println("Fehler beim Parsen des Descriptors aus '"
                    + descriptionFile.getName() + "': " + e.getMessage());
            return null;
        }
    }
}
