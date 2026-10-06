/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.EditTreeWriter;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.tree.control.JackMasterControl;
import java.io.File;
import java.io.IOException;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
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
     * Saves the tree of the active editor tab into its file. Without a known file (the tree was not loaded
     * from disk) the save-as dialog is shown instead.
     */
    public void saveActiveFile() {
        final File target = woodWindow.getActiveFile();
        if (target == null) {
            saveActiveFileAs();
            return;
        }
        writeActiveTree(target);
    }

    /**
     * Saves the tree of the active editor tab into a file chosen in a save dialog. The chosen file is remembered
     * for the tab so the next save overwrites it.
     */
    public void saveActiveFileAs() {
        final JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("JSON-Datei speichern...");
        chooser.setFileFilter(new FileNameExtensionFilter("JSON-Dateien", "json"));
        if (chooser.showSaveDialog(woodWindow) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = chooser.getSelectedFile();
        if (!target.getName().toLowerCase().endsWith(".json")) {
            target = new File(target.getParentFile(), target.getName() + ".json");
        }
        if (target.exists()) {
            final int answer = JOptionPane.showConfirmDialog(woodWindow,
                    "Die Datei existiert bereits. Ueberschreiben?", "Speichern unter...",
                    JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }
        if (writeActiveTree(target)) {
            woodWindow.setActiveFile(target);
        }
    }

    /**
     * Writes the tree of the active editor tab with the tree writer (composite keys, transient filter).
     *
     * @param target the file to write
     * @return true when the tree was written, false on error or without an active tree
     */
    private boolean writeActiveTree(File target) {
        final EditTree tree = woodWindow.getActiveEditTree();
        if (tree == null) {
            return false;
        }
        try {
            EditTreeWriter.toJsonFile(target, tree);
            return true;
        } catch (IOException e) {
            woodWindow.showErrorDialog("Fehler beim Speichern der Datei: " + e.getMessage());
            return false;
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
