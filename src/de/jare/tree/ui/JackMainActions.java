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
import static de.jare.jsoncasted.lang.JsonTerms.SELF_SYNONYM;
import static de.jare.jsoncasted.lang.JsonTerms.THIS_SYNONYM;
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
            final String descriptionFilePath = tree.getDescriptionFilePath();
            if (descriptionFilePath != null && tree.getJsonModelDescriptor() == null) {
                File descrFile = new File(descriptionFilePath);

                if (!descrFile.exists()) {
                    descrFile = choseDescriptionFile(descriptionFilePath, file.getName());
                }
                if (descrFile != null && descrFile.exists()) {
                    JsonTreeConverter.loadDescrAndConvertRessourceToEditTree(tree, descrFile.getCanonicalPath(), descrFile);
                }
            }
            woodWindow.addEditorTab(file, tree);
        } catch (IOException | JsonParseException e) {
            woodWindow.showErrorDialog("Fehler beim Öffnen der Datei: " + e.getMessage());
        }
    }

    /**
     * @param descriptionFilePath
     * @param org
     * @return
     */
    public File choseDescriptionFile(String descriptionFilePath, String org) {

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Descripcion of: " + org);
        chooser.setFileFilter(new FileNameExtensionFilter("JSON-Dateien", "json"));

        int result = chooser.showOpenDialog(woodWindow);
        return result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }

}
