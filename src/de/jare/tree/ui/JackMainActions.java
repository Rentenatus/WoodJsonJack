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
import de.jare.jsonconfig.def.JsonConfigDefinition;
import de.jare.tree.control.JackMasterControl;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Central class for main actions like loading and handling JSON files.
 * Handles the loading of main files and their associated description files
 * with path shift tolerance.
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
            Map<String, EditTree> descriptionTrees = loadDescriptionFiles(resolution, file);

            // 4. Add main tree to window
            woodWindow.addEditorTab(file, tree);

            // 5. Store description trees for later use (next step)
            woodWindow.setDescriptionTrees(file, descriptionTrees);

        } catch (IOException | JsonParseException e) {
            woodWindow.showErrorDialog("Fehler beim Öffnen der Datei: " + e.getMessage());
        }
    }

    /**
     * Attempts to find a description file with path shift tolerance:
     * 1. Direct path from descriptionFileMap
     * 2. Relative to the original file's directory
     * 3. Via file chooser dialog (user interaction)
     *
     * @param descriptionFilePath the path from the descriptionFileMap
     * @param originalFile the originally loaded JSON file
     * @return the found file, or null if user cancels
     */
    public File findDescriptionFile(String descriptionFilePath, File originalFile) {
        // 1. Try direct path
        File directFile = new File(descriptionFilePath);
        if (directFile.exists()) {
            return directFile;
        }

        // 2. Try relative to original file's directory
        if (originalFile != null && originalFile.getParentFile() != null) {
            File relativeFile = new File(originalFile.getParentFile(), descriptionFilePath);
            if (relativeFile.exists()) {
                return relativeFile;
            }
        }

        // 3. Open file chooser dialog for user
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Description-Datei wählen für: " + descriptionFilePath);
        chooser.setFileFilter(new FileNameExtensionFilter("JSON-Dateien", "json"));

        int result = chooser.showOpenDialog(woodWindow);
        if (result == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }

        // User canceled - no description
        return null;
    }

    /**
     * Loads all description files referenced in the WoodResolution.
     *
     * @param resolution the WoodResolution after parsing the main file
     * @param originalFile the originally loaded JSON file
     * @return Map of model names to their description EditTrees
     */
    public Map<String, EditTree> loadDescriptionFiles(WoodResolution resolution, File originalFile) {
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
                    EditTree descriptionTree = JsonTreeConverter.fromJsonFile(descriptionFile);
                    descriptionTrees.put(modelName, descriptionTree);
                    System.out.println("Description für '" + modelName + "' geladen: " + descriptionFile.getAbsolutePath());
                } else {
                    System.out.println("Description für '" + modelName + "' nicht gefunden: " + filePath);
                }
            } catch (IOException | JsonParseException e) {
                System.err.println("Fehler beim Laden der Description für '" + modelName + "': " + e.getMessage());
            }
        }

        return descriptionTrees;
    }
}
