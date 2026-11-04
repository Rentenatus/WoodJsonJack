/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditProject;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.EditTreeWriter;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import de.jare.jsoncasted.editor.core.SaveGuard;
import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.lang.JsonResource;
import de.jare.tree.control.JackMasterControl;
import de.jare.tree.control.model.JackTreeModel;
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
            master.getFastLog().tryWrite("io", "Loaded " + file.getName());
        } catch (IOException | JsonParseException e) {
            master.getFastLog().tryWrite("io", "Load failed: " + file.getName() + " - " + e.getMessage());
            woodWindow.showErrorDialog("Error opening file: " + e.getMessage());
        }
    }

    /**
     * Opens a project session (concept doc/JSON_PROJECT.md, phase 4): ONE parse project load, every resource
     * converted exactly once, the main file as editable tab, the sub resources as read only views on the shared
     * trees - B exists once, every tab sees the same EditTree.
     *
     * @param file the project's main resource file
     */
    public void loadJsonProject(File file) {
        try {
            final EditProject project = EditProject.open(file);
            woodWindow.openProject(file, project);
            master.getFastLog().tryWrite("io", "Loaded project " + file.getName()
                    + " (" + project.getSortedSynonyms().size() + " resources)");
        } catch (IOException | JsonParseException e) {
            master.getFastLog().tryWrite("io", "Project load failed: " + file.getName() + " - " + e.getMessage());
            woodWindow.showErrorDialog("Error opening project: " + e.getMessage());
        }
    }

    /**
     * Opens the tree diff view of the working copy against the file on disk (concepts doc/TreeDiffView.md and
     * doc/JSON_PROJECT.md save quadrant made visible): left = the live, editable working copy (ours), right =
     * the on the fly loaded disk version as the frozen theirs copy. With a retained import state the W3
     * classification runs - apply list = the external changes, conflicts = both sides edited, no-op = did the
     * same - and cherry picks take disk changes into the working copy (one undo step each); without a base
     * state the W2 comparison shows.
     *
     * @param trigger logging tag of the caller
     */
    public void diffActiveAgainstDisk(String trigger) {
        diskDiff(trigger, null);
    }

    /**
     * Opens the disk diff with a FORCED kind - the save conflict offers the plain W2 user diff alongside the
     * W3 merge view: left = the user's version, right = the disk version, only the direct comparison marks,
     * no merge classification (nothing to pick from, nothing to decide - look, then decide in the dialog).
     *
     * @param trigger logging tag of the caller
     * @param forcedKind W2 for the plain user diff, or null for the automatic kind (W3 with a base state)
     */
    public void diskDiff(String trigger, DiffViewController.Kind forcedKind) {
        final EditTree ours = woodWindow.getActiveEditTree();
        final File file = woodWindow.getActiveFile();
        if (ours == null) {
            woodWindow.showErrorDialog("No active editor tree to compare.");
            return;
        }
        if (file == null || !file.exists()) {
            woodWindow.showErrorDialog("The active tab has no file on disk - nothing to compare.");
            return;
        }
        try {
            final EditTree disk = JsonTreeConverter.fromJsonFile(file);
            if (disk == null || disk.getRoot() == null) {
                woodWindow.showErrorDialog("Could not load the disk version of " + file.getName() + ".");
                return;
            }
            final DiffViewController.Kind kind = forcedKind != null ? forcedKind
                    : (ours.getBaseSnapshot() != null
                        ? DiffViewController.Kind.W3 : DiffViewController.Kind.W2);
            final JackEditTreeContainer container = woodWindow.getActiveContainer();
            if (container == null) {
                return;
            }
            DiffViewController.showContainerTab(container);
            container.getDiffController().open(ours, disk.getRoot(), kind);
            master.getFastLog().tryWrite("io", trigger + ": diff " + kind.getBadge()
                    + " working copy vs disk (" + file.getName() + ")");
        } catch (IOException | JsonParseException e) {
            master.getFastLog().tryWrite("io", "Disk diff failed: " + e.getMessage());
            woodWindow.showErrorDialog("Diff against disk failed: " + e.getMessage());
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
        chooser.setDialogTitle("Save JSON file...");
        chooser.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));
        if (chooser.showSaveDialog(woodWindow) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = chooser.getSelectedFile();
        if (!target.getName().toLowerCase().endsWith(".json")) {
            target = new File(target.getParentFile(), target.getName() + ".json");
        }
        if (target.exists()) {
            final int answer = JOptionPane.showConfirmDialog(woodWindow,
                    "The file already exists. Overwrite?", "Save As...",
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
     * Writes the tree of the active editor tab with the tree writer (composite keys, transient filter) - behind
     * the save guard of the project concept (doc/JSON_PROJECT.md): the two state axes of the active tree decide
     * whether the plain overwrite is allowed. Only the EDITED+OUTDATED conflict asks: merge over the existing
     * 3-way rails (base = retained import state, ours = the tree, theirs = the external file), overwrite anyway,
     * or discard the own edits.
     *
     * @param target the file to write
     * @return true when the tree was written, false on error or without an active tree
     */
    private boolean writeActiveTree(File target) {
        final EditTree tree = woodWindow.getActiveEditTree();
        if (tree == null) {
            return false;
        }
        if (!saveGuardAllows(tree, target)) {
            return false;
        }
        try {
            EditTreeWriter.toJsonFile(target, tree);
            refreshMergeBaseAfterSave(tree, target);
            master.getFastLog().tryWrite("io", "Saved " + target.getName());
            return true;
        } catch (IOException e) {
            master.getFastLog().tryWrite("io", "Save failed: " + target.getName() + " - " + e.getMessage());
            woodWindow.showErrorDialog("Error saving file: " + e.getMessage());
            return false;
        }
    }

    /**
     * The save guard quadrant (concept doc/JSON_PROJECT.md, SaveGuard): CLEAN+CURRENT and EDITED+CURRENT allow
     * the plain save; CLEAN+OUTDATED offers the re-import (no user work at risk, the external change wins);
     * EDITED+OUTDATED is the real conflict - never a blind overwrite, the decision runs on the existing merge
     * rails and asks.
     *
     * @param tree the tree about to be written
     * @param target the file it would be written to
     * @return true when the save may proceed (possibly after a merge or an explicit overwrite)
     */
    private boolean saveGuardAllows(EditTree tree, File target) {
        switch (SaveGuard.decide(tree)) {
            case NOTHING_TO_DO:
            case PROCEED_SAVE:
                return true;
            case REIMPORT: {
                final int answer = JOptionPane.showConfirmDialog(woodWindow,
                        "The file changed externally; your tree has no unsaved changes.\n"
                        + "Reload the file (the external change wins)?",
                        "File changed externally", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (answer == JOptionPane.YES_OPTION) {
                    reloadActiveTab(target);
                }
                return false;
            }
            case CONFLICT:
            default:
                return conflictDecision(tree, target);
        }
    }

    /**
     * The EDITED+OUTDATED conflict decision: the 3-way diff view of the working copy against the disk version
     * (the merge IS the cherry picking there - conflicts show as conflict marks, take theirs equals a pick,
     * keep mine equals leaving it), overwrite anyway (the external change is lost), discard the own edits
     * (reload) or cancel.
     *
     * @param tree the tree with unsaved edits
     * @param target the externally changed file
     * @return true only when the chosen path ends in a plain save of the tree (overwrite)
     */
    private boolean conflictDecision(EditTree tree, File target) {
        final Object[] options = {"Merge (3-way)", "User diff (W2)", "Overwrite anyway",
            "Discard my edits", "Cancel"};
        final int answer = JOptionPane.showOptionDialog(woodWindow,
                "The file changed externally while you have unsaved edits.\n"
                + "Merge opens the 3-way diff view (base = import state, ours = your tree, theirs = the disk)"
                + " - the cherry picks there are the merge.\n"
                + "User diff shows the plain comparison (your version vs. the disk), the pure look.\n"
                + "Overwrite the external change, or discard your edits?",
                "Save conflict", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                null, options, options[0]);
        switch (answer) {
            case 0:
                diffActiveAgainstDisk("save conflict");
                return false;
            case 1:
                diskDiff("save conflict user diff", DiffViewController.Kind.W2);
                return false;
            case 2:
                return true;
            case 3:
                reloadActiveTab(target);
                return false;
            default:
                return false;
        }
    }

    /**
     * Replaces the active tab's tree with a fresh import of the file (CLEAN+OUTDATED re-import and "discard my
     * edits"): no user work at risk in both cases, the external file state wins.
     */
    private void reloadActiveTab(File target) {
        try {
            final EditTree fresh = JsonTreeConverter.fromJsonFile(target);
            if (fresh == null) {
                return;
            }
            final JackEditTreeContainer container = woodWindow.getActiveContainer();
            if (container == null) {
                return;
            }
            container.getLeftTree().getTree().setModel(new JackTreeModel(fresh));
            container.getLeftTree().refreshResourceInfo();
            master.getFastLog().tryWrite("io", "Re-imported " + target.getName());
        } catch (IOException | JsonParseException e) {
            woodWindow.showErrorDialog("Reload failed: " + e.getMessage());
        }
    }

    /**
     * R4 of the tree diff concept (doc/TREE_DIFF.md in jsonCasted): the saved file is the new merge base, so the
     * next 3-way analysis runs against what is on disk. A failed refresh is logged, never fatal — the old base
     * stays.
     *
     * @param tree the tree that was just written
     * @param target the file it was written to
     */
    private void refreshMergeBaseAfterSave(EditTree tree, File target) {
        try {
            final JsonResource saved = JsonParserService.parse(target, JsonDebugLevel.SIMPLE);
            tree.adoptSavedBase(saved);
        } catch (IOException | JsonParseException e) {
            master.getFastLog().tryWrite("io",
                    "Merge base refresh failed: " + target.getName() + " - " + e.getMessage());
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
        chooser.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));

        int result = chooser.showOpenDialog(woodWindow);
        return result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }

}
