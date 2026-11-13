/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditBundle;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.EditTreeWriter;
import de.jare.jsoncasted.editor.core.JsonTreeConverter;
import de.jare.jsoncasted.editor.core.SaveGuard;
import de.jare.debug.JsonDebugLevel;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.JsonWriteException;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.lang.JsonResource;
import de.jare.jsoncasted.project.Partition;
import de.jare.jsoncasted.project.PartitionType;
import de.jare.jsoncasted.project.ProjectDocument;
import de.jare.jsoncasted.project.ProjectFileService;
import de.jare.tree.control.JackMasterControl;
import de.jare.tree.control.model.JackTreeModel;
import java.io.File;
import java.io.IOException;
import java.util.List;
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
     * Opens a bundle session (concept doc/JSON_BUNDLE.md, phase 4): ONE parse bundle load, every resource
     * converted exactly once, the main file as editable tab, the sub resources as read only views on the shared
     * trees - B exists once, every tab sees the same EditTree.
     *
     * @param file the bundle's main resource file
     */
    public void loadJsonBundle(File file) {
        loadJsonBundle(file, false);
    }

    /**
     * Opens a bundle session with the main tree's editability decided by the caller: an
     * ISOLATED source (concept docs/koncepts/CONCEPT_ProjectView.md, phase 9 - an old version to look at)
     * opens completely read-only, no editor action writes into it.
     *
     * @param file the bundle's main resource file
     * @param mainReadonly true when the main tree opens as a read-only view
     */
    public void loadJsonBundle(File file, boolean mainReadonly) {
        try {
            final EditBundle bundle = EditBundle.open(file);
            woodWindow.openBundle(file, bundle, mainReadonly);
            master.getFastLog().tryWrite("io", "Loaded bundle " + file.getName()
                    + " (" + bundle.getSortedSynonyms().size() + " resources"
                    + (mainReadonly ? ", read-only view" : "") + ")");
        } catch (IOException | JsonParseException e) {
            master.getFastLog().tryWrite("io", "Bundle load failed: " + file.getName() + " - " + e.getMessage());
            woodWindow.showErrorDialog("Error opening bundle: " + e.getMessage());
        }
    }

    /**
     * Lets the user choose a project folder and opens it (concept: docs/koncepts/CONCEPT_ProjectView.md,
     * sections 1 and 8). The folder must contain {@code .wood/project.json}; the document is
     * validated at load time and refused with the problems found.
     */
    public void chooseAndOpenProject() {
        JFileChooser folderChooser = new JFileChooser();
        folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        folderChooser.setDialogTitle("Open wood project (folder with .wood/project.json)");
        folderChooser.setAcceptAllFileFilterUsed(false);

        int result = folderChooser.showOpenDialog(woodWindow);
        if (result == JFileChooser.APPROVE_OPTION) {
            openProject(folderChooser.getSelectedFile());
        }
    }

    /**
     * Lets the user pick a folder and makes it a wood project (concept docs/koncepts/CONCEPT_ProjectView.md,
     * section 1): {@code .wood/project.json} with an empty partition list is created, then the
     * project opens in the sidebar. A folder that already carries a project document is refused -
     * creating never overwrites. The save variant of the folder chooser deliberately, because it
     * offers the "create new folder" button.
     */
    public void newProject() {
        JFileChooser folderChooser = new JFileChooser();
        folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        folderChooser.setDialogTitle("New wood project (folder that gets .wood/project.json)");
        folderChooser.setAcceptAllFileFilterUsed(false);

        int result = folderChooser.showSaveDialog(woodWindow);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File folder = folderChooser.getSelectedFile();
        if (folder == null) {
            return;
        }
        if (new ProjectFileService().isProjectRoot(folder)) {
            woodWindow.showErrorDialog("This folder is already a wood project:\n"
                    + folder.getAbsolutePath() + "\n(.wood/project.json exists; creating never overwrites)");
            return;
        }
        createProjectAt(folder);
    }

    /**
     * Creates an empty project document ({@code .wood/project.json}, name = the folder name, no
     * partitions yet) in the given folder and opens it as the active project. Both entry points
     * share this method: the "New Project..." menu action and live checks.
     *
     * @param folder the new project root, must not contain a project document yet
     */
    public void createProjectAt(File folder) {
        if (folder == null || folder.isFile()) {
            return;
        }
        try {
            ProjectDocument document = new ProjectDocument(folder.getName());
            new ProjectFileService().saveProject(folder, document);
            master.getFastLog().tryWrite("io", "Created project " + folder.getName());
            openProject(folder);
        } catch (IOException | JsonParseException | JsonWriteException e) {
            master.getFastLog().tryWrite("io", "Project creation failed: " + folder.getName()
                    + " - " + e.getMessage());
            woodWindow.showErrorDialog("Error creating project: " + e.getMessage());
        }
    }

    /**
     * Opens a project folder in the sidebar: loads {@code .wood/project.json}, validates the
     * document (binding rules of the concept) and projects it as partition level.
     *
     * @param folder the candidate project root
     */
    public void openProject(File folder) {
        if (folder == null) {
            return;
        }
        ProjectFileService projectService = new ProjectFileService();
        if (!projectService.isProjectRoot(folder)) {
            woodWindow.showErrorDialog("Not a wood project: "
                    + folder.getAbsolutePath() + "\n(no .wood/project.json found)");
            return;
        }
        try {
            ProjectDocument document = projectService.loadProject(folder);
            List<String> problems = projectService.validateProject(folder, document);
            if (!problems.isEmpty()) {
                woodWindow.showErrorDialog("The project document is invalid:\n\n"
                        + String.join("\n", problems));
                return;
            }
            woodWindow.getProjectSidebar().showProject(folder, document);
            woodWindow.rememberProjectInHistory(document.getName(), folder);
            master.getFastLog().tryWrite("io", "Loaded project " + folder.getName()
                    + " (" + document.getRoots().size() + " partitions)");
        } catch (IOException | JsonParseException | de.jare.jsoncasted.model.JsonBuildException e) {
            master.getFastLog().tryWrite("io", "Project load failed: " + folder.getName() + " - " + e.getMessage());
            woodWindow.showErrorDialog("Error opening project: " + e.getMessage());
        }
    }

    /**
     * Opens the tree diff view of the working copy against the file on disk (concepts doc/TreeDiffView.md and
     * doc/JSON_BUNDLE.md save quadrant made visible): left = the live, editable working copy (ours), right =
     * the on the fly loaded disk version as the frozen theirs copy. With a retained import state the W3
     * classification runs - apply list = the external changes, conflicts = both sides edited, no-op = did the
     * same - and cherry picks take disk changes into the working copy (one undo step each); without a base
     * state the W2 comparison shows.
     *
     * @param trigger logging tag of the caller
     */
    public void diffActiveAgainstDisk(String trigger) {
        diskDiff(trigger, null, woodWindow.getActiveContainer());
    }

    /**
     * Menu path of "Project > Add Folder > (type)": asks for the folder name (the type name as
     * the suggestion) and adds a partition of the given role to the open project (concept
     * docs/koncepts/CONCEPT_ProjectView.md, section 2 - the role table). Folder on disk and project document
     * grow together; an existing folder can be mounted this way, creating never overwrites.
     *
     * @param type the partition role of the new folder
     */
    public void addProjectFolder(PartitionType type) {
        if (!hasOpenProject()) {
            return;
        }
        String suggestion = type.getName();
        Object answer = JOptionPane.showInputDialog(woodWindow,
                "Name of the " + suggestion + " folder in the project root:",
                "Add " + suggestion + " folder", JOptionPane.PLAIN_MESSAGE, null, null, suggestion);
        if (answer == null) {
            return; // cancelled
        }
        boolean added = addProjectFolderAt(String.valueOf(answer), type);
        if (!added) {
            woodWindow.showErrorDialog("The folder was not added:\n"
                    + String.valueOf(answer) + "\n(the name is taken or invalid - the fast log has the details)");
        }
    }

    /**
     * Core of "Project > Add Folder": creates the folder in the project root when missing and
     * appends a partition entry to the project document. Refused without any change when the
     * name is not a plain folder name or when another partition already mounts that folder.
     *
     * @param folderName the plain folder name inside the project root
     * @param type the partition role of the new folder
     * @return true when the partition was added; false when refused (nothing changed)
     */
    public boolean addProjectFolderAt(String folderName, PartitionType type) {
        de.jare.tree.ui.project.ProjectSidebar sidebar = woodWindow.getProjectSidebar();
        ProjectDocument document = sidebar.getDocument();
        File projectRoot = sidebar.getProjectRoot();
        if (document == null || projectRoot == null || type == null) {
            return false;
        }
        if (folderName == null || !folderName.matches("[A-Za-z0-9._\\-]+")) {
            master.getFastLog().tryWrite("io", "Folder not added: '" + folderName
                    + "' is not a plain folder name.");
            return false;
        }
        File folder = new File(projectRoot, folderName);
        try {
            String canonical = folder.getCanonicalPath();
            for (Partition partition : document.getRoots()) {
                if (partition != null && canonical.equals(partition.resolve(projectRoot).getCanonicalPath())) {
                    master.getFastLog().tryWrite("io", "Folder not added: partition '"
                            + partition.getName() + "' already mounts " + canonical);
                    return false;
                }
            }
            if (folder.exists() && !folder.isDirectory()) {
                master.getFastLog().tryWrite("io", "Folder not added: " + canonical
                        + " exists and is not a folder.");
                return false;
            }
            if (!folder.exists() && !folder.mkdir()) {
                master.getFastLog().tryWrite("io", "Folder not added: " + canonical
                        + " could not be created.");
                return false;
            }
            document.getRoots().add(new Partition(folderName, type, "./" + folderName, null));
            new ProjectFileService().saveProject(projectRoot, document);
        } catch (IOException | JsonParseException | JsonWriteException e) {
            master.getFastLog().tryWrite("io", "Folder not added: " + folderName + " - " + e.getMessage());
            return false;
        }
        sidebar.refreshProject();
        master.getFastLog().tryWrite("io", "Added " + type.getName() + " folder '" + folderName + "'");
        return true;
    }

    /**
     * Menu path of "Project > Add Folder > bundle": asks for the name and creates an empty
     * bundle (folder with descriptor and main file) inside the first json partition - bundles
     * are discovered by the scan of json partitions (concept section 3).
     */
    public void addProjectBundle() {
        if (!hasOpenProject()) {
            return;
        }
        Object answer = JOptionPane.showInputDialog(woodWindow,
                "Name of the bundle (created inside the json partition):",
                "Add bundle folder", JOptionPane.PLAIN_MESSAGE, null, null, "freshBundle");
        if (answer == null) {
            return; // cancelled
        }
        boolean added = addProjectBundleAt(String.valueOf(answer));
        if (!added) {
            woodWindow.showErrorDialog("The bundle was not added:\n"
                    + String.valueOf(answer) + "\n(the name is taken, invalid or no json partition - the fast log has the details)");
        }
    }

    /**
     * Core of "Project > Add Folder > bundle" for the menu: the first json partition is the
     * host (bundles are discovered by the scan of json partitions, concept section 3).
     *
     * @param bundleName the plain name of the new bundle folder
     * @return true when the bundle was created; false when refused or failed (nothing left over)
     */
    public boolean addProjectBundleAt(String bundleName) {
        de.jare.tree.ui.project.ProjectSidebar sidebar = woodWindow.getProjectSidebar();
        ProjectDocument document = sidebar.getDocument();
        File projectRoot = sidebar.getProjectRoot();
        if (document == null || projectRoot == null) {
            return false;
        }
        for (Partition partition : document.getRoots()) {
            if (partition != null && partition.getType() == PartitionType.JSON) {
                boolean added = addProjectBundleIn(partition.resolve(projectRoot), bundleName);
                if (added) {
                    sidebar.refreshProject();
                }
                return added;
            }
        }
        master.getFastLog().tryWrite("io", "Bundle not added: the project has no json partition "
                + "- add a json folder first (Project > Add Folder > json).");
        return false;
    }

    /**
     * Popup path "New bundle here" on a folder inside a json partition: the clicked folder is
     * the host - bundles nest, so any json-world folder can receive one. When the user
     * cancelled, nothing happens; when the name was refused, the why lives in the fast log.
     *
     * @param hostFolder the clicked folder that receives the bundle subfolder
     */
    public boolean addBundleTo(File hostFolder) {
        Object answer = JOptionPane.showInputDialog(woodWindow,
                "Name of the bundle (created inside " + hostFolder.getName() + "):",
                "New bundle folder", JOptionPane.PLAIN_MESSAGE, null, null, "freshBundle");
        if (answer == null) {
            return false; // cancelled
        }
        boolean added = addProjectBundleIn(hostFolder, String.valueOf(answer));
        if (!added) {
            woodWindow.showErrorDialog("The bundle was not added:\n"
                    + String.valueOf(answer) + "\n(the name is taken or invalid - the fast log has the details)");
        }
        return added;
    }

    /**
     * Core of the bundle folder: creates the bundle folder below the given host folder with a
     * minimal descriptor (main file only, no imports) and an empty JSON main file. Rollback
     * like the materializer: a failure leaves no half bundle folder.
     *
     * @param hostFolder the folder that receives the new bundle subfolder
     * @param bundleName the plain name of the new bundle folder
     * @return true when the bundle was created; false when refused or failed (nothing left over)
     */
    public boolean addProjectBundleIn(File hostFolder, String bundleName) {
        if (hostFolder == null || !hostFolder.isDirectory()) {
            master.getFastLog().tryWrite("io", "Bundle not added: the host folder does not exist.");
            return false;
        }
        if (bundleName == null || !bundleName.matches("[A-Za-z0-9._\\-]+")) {
            master.getFastLog().tryWrite("io", "Bundle not added: '" + bundleName
                    + "' is not a plain folder name.");
            return false;
        }
        File bundleFolder = new File(hostFolder, bundleName);
        if (bundleFolder.exists()) {
            master.getFastLog().tryWrite("io", "Bundle not added: " + bundleFolder.getAbsolutePath()
                    + " already exists.");
            return false;
        }
        if (!bundleFolder.mkdir()) {
            master.getFastLog().tryWrite("io", "Bundle not added: " + bundleFolder.getAbsolutePath()
                    + " could not be created.");
            return false;
        }
        String mainName = bundleName + ".json";
        boolean complete = false;
        try {
            try {
                de.jare.jsoncasted.project.BundleDescriptor descriptor
                        = new de.jare.jsoncasted.project.BundleDescriptor(bundleName, mainName);
                descriptor.getFiles().add(mainName);
                new ProjectFileService().saveBundleDescriptor(bundleFolder, descriptor);
                java.nio.file.Files.writeString(new File(bundleFolder, mainName).toPath(), "{}");
            } catch (IOException | JsonParseException | JsonWriteException e) {
                throw new IOException(e);
            }
            complete = true;
        } catch (IOException e) {
            master.getFastLog().tryWrite("io", "Bundle not added: " + bundleName + " - " + e.getMessage());
            deleteRecursively(bundleFolder);
            return false;
        } finally {
            if (!complete) {
                deleteRecursively(bundleFolder);
            }
        }
        master.getFastLog().tryWrite("io", "Added bundle '" + bundleName + "' into " + hostFolder.getName());
        return true;
    }

    /**
     * Popup path "New subfolder": asks for the name and creates a plain subfolder. The projection
     * shows it as a folder node; no partition role is involved (that is Project > Add Folder).
     *
     * @param folder the folder that receives the subfolder
     * @return true when the subfolder was created; false when cancelled or refused
     */
    public boolean addSubfolderTo(File folder) {
        Object answer = JOptionPane.showInputDialog(woodWindow,
                "Name of the new subfolder:", "New folder",
                JOptionPane.PLAIN_MESSAGE, null, null, "newFolder");
        if (answer == null) {
            return false; // cancelled
        }
        boolean added = addSubfolderAt(folder, String.valueOf(answer));
        if (!added) {
            woodWindow.showErrorDialog("The subfolder was not added:\n"
                    + String.valueOf(answer) + "\n(the name is taken or invalid - the fast log has the details)");
        }
        return added;
    }

    /**
     * Core of the plain subfolder: refuses existing names and invalid spellings, creates nothing
     * else. Read-only mounts are the caller's responsibility (the popup does not offer it there).
     *
     * @param folder the parent folder
     * @param name the plain subfolder name
     * @return true when the folder exists afterwards and was fresh
     */
    public boolean addSubfolderAt(File folder, String name) {
        if (folder == null || !folder.isDirectory()) {
            return false;
        }
        if (name == null || !name.matches("[A-Za-z0-9._\\-]+")) {
            master.getFastLog().tryWrite("io", "Folder not added: '" + name
                    + "' is not a plain folder name.");
            return false;
        }
        File sub = new File(folder, name);
        if (sub.exists()) {
            master.getFastLog().tryWrite("io", "Folder not added: " + sub.getAbsolutePath()
                    + " already exists.");
            return false;
        }
        if (!sub.mkdir()) {
            master.getFastLog().tryWrite("io", "Folder not added: " + sub.getAbsolutePath()
                    + " could not be created.");
            return false;
        }
        master.getFastLog().tryWrite("io", "Added folder '" + name + "' into " + folder.getName());
        return true;
    }

    /**
     * Popup path for a typed file (New JSON file / New Java file / ...): asks for the name and
     * writes the file with the role's template below the given folder. The template may carry
     * the placeholder {@code {stem}} - it is replaced by the typed name without its extension
     * (the Java skeleton names its class like the file).
     *
     * @param folder the parent folder
     * @param title the dialog title
     * @param suggestedName the pre-filled file name (role typical)
     * @param template the file content template, or null for an empty file
     * @return true when the file was written; false when cancelled or refused
     */
    public boolean addFileTo(File folder, String title, String suggestedName, String template) {
        Object answer = JOptionPane.showInputDialog(woodWindow,
                "Name of the new file:", title,
                JOptionPane.PLAIN_MESSAGE, null, null, suggestedName);
        if (answer == null) {
            return false; // cancelled
        }
        String fileName = String.valueOf(answer);
        String content = template;
        if (content != null && content.contains("{stem}")) {
            String stem = fileName.toLowerCase().lastIndexOf('.') > 0
                    ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            content = content.replace("{stem}", stem);
        }
        boolean added = addFileAt(folder, fileName, content);
        if (!added) {
            woodWindow.showErrorDialog("The file was not added:\n"
                    + fileName + "\n(the name is taken or invalid - the fast log has the details)");
        }
        return added;
    }

    /**
     * Core of the file creation: plain name gate, refuse existing, one template write - the
     * copy of the materializer's honesty rules for single files.
     *
     * @param folder the parent folder
     * @param fileName the plain file name (with extension)
     * @param template the content, or null for an empty file
     * @return true when the file was written; false when refused or failed (nothing left over)
     */
    public boolean addFileAt(File folder, String fileName, String template) {
        if (folder == null || !folder.isDirectory()) {
            return false;
        }
        if (fileName == null || !fileName.matches("[A-Za-z0-9._\\-]+")) {
            master.getFastLog().tryWrite("io", "File not added: '" + fileName
                    + "' is not a plain file name.");
            return false;
        }
        File file = new File(folder, fileName);
        if (file.exists()) {
            master.getFastLog().tryWrite("io", "File not added: " + file.getAbsolutePath()
                    + " already exists.");
            return false;
        }
        try {
            java.nio.file.Files.writeString(file.toPath(), template != null ? template : "");
        } catch (IOException e) {
            master.getFastLog().tryWrite("io", "File not added: " + fileName + " - " + e.getMessage());
            file.delete();
            return false;
        }
        master.getFastLog().tryWrite("io", "Added file '" + fileName + "' into " + folder.getName());
        return true;
    }

    /**
     * Popup path "New JSON resource" on a bundle: asks for the name and creates the file AND
     * declares it in the bundle descriptor (the bundle subtree renders the descriptor's meaning,
     * concept section 4 - an undeclared file would hang below "undeclared" instead).
     *
     * @param bundleFolder the bundle folder (contains .woodbundle.json)
     */
    public boolean addBundleResourceTo(File bundleFolder) {
        Object answer = JOptionPane.showInputDialog(woodWindow,
                "Name of the new JSON resource (declared in the bundle descriptor):",
                "New bundle resource", JOptionPane.PLAIN_MESSAGE, null, null, "newResource.json");
        if (answer == null) {
            return false; // cancelled
        }
        boolean added = addBundleResourceAt(bundleFolder, String.valueOf(answer));
        if (!added) {
            woodWindow.showErrorDialog("The resource was not added:\n"
                    + String.valueOf(answer) + "\n(the name is taken or invalid - the fast log has the details)");
        }
        return added;
    }

    /**
     * Core of the bundle resource: writes the JSON file and appends it to the descriptor's file
     * list. Nothing is written when the name is refused; the file write and the descriptor
     * update happen in that order, a failed descriptor update removes the fresh file again -
     * no half state survives.
     *
     * @param bundleFolder the bundle folder (contains .woodbundle.json)
     * @param fileName the plain resource file name
     * @return true when file and descriptor agree afterwards; false when refused or failed
     */
    public boolean addBundleResourceAt(File bundleFolder, String fileName) {
        if (bundleFolder == null || !bundleFolder.isDirectory()) {
            return false;
        }
        ProjectFileService service = new ProjectFileService();
        if (fileName == null || !fileName.matches("[A-Za-z0-9._\\-]+")) {
            master.getFastLog().tryWrite("io", "Resource not added: '" + fileName
                    + "' is not a plain file name.");
            return false;
        }
        File file = new File(bundleFolder, fileName);
        if (file.exists()) {
            master.getFastLog().tryWrite("io", "Resource not added: " + file.getAbsolutePath()
                    + " already exists.");
            return false;
        }
        try {
            de.jare.jsoncasted.project.BundleDescriptor descriptor
                    = service.loadBundleDescriptor(bundleFolder);
            java.nio.file.Files.writeString(file.toPath(), "{}");
            boolean complete = false;
            try {
                if (!descriptor.getFiles().contains(fileName)) {
                    descriptor.getFiles().add(fileName);
                }
                service.saveBundleDescriptor(bundleFolder, descriptor);
                complete = true;
            } finally {
                if (!complete) {
                    file.delete();
                }
            }
        } catch (IOException | JsonParseException | de.jare.jsoncasted.model.JsonBuildException
                | JsonWriteException e) {
            master.getFastLog().tryWrite("io", "Resource not added: " + fileName + " - " + e.getMessage());
            file.delete();
            return false;
        }
        master.getFastLog().tryWrite("io", "Added bundle resource '" + fileName + "'");
        return true;
    }

    /**
     * Popup path "Set as main file...": the guard dialog before the descriptor rewrite - it
     * names the bundle and the file that is the main right now, so the swap is an informed
     * decision instead of a surprise refocus of the bundle's open behavior.
     *
     * @param bundleFolder the bundle folder (contains .woodbundle.json)
     * @param fileName the candidate main file
     * @return true when the main was swapped; false when it already was, was cancelled or failed
     */
    public boolean setBundleMain(File bundleFolder, String fileName) {
        de.jare.jsoncasted.project.BundleDescriptor descriptor;
        try {
            descriptor = new ProjectFileService().loadBundleDescriptor(bundleFolder);
        } catch (IOException | JsonParseException | de.jare.jsoncasted.model.JsonBuildException e) {
            woodWindow.showErrorDialog("The bundle descriptor could not be read:\n" + e.getMessage());
            return false;
        }
        if (fileName.equals(descriptor.getMain())) {
            JOptionPane.showMessageDialog(woodWindow,
                    "'" + fileName + "' is already the main file of bundle '"
                    + descriptor.getName() + "'.",
                    "Set as main file", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        int answer = JOptionPane.showConfirmDialog(woodWindow,
                "Set '" + fileName + "' as the main file of bundle '" + descriptor.getName() + "'?\n\n"
                + "Current main: '" + descriptor.getMain() + "'\n\n"
                + "The bundle opens its main file - opening bundles and the\n"
                + "sidebar's double click will focus the new one afterwards.",
                "Set as main file", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return false;
        }
        return setBundleMainAt(bundleFolder, fileName);
    }

    /**
     * Core of "Set as main file": rewrites the descriptor's {@code main} and joins the file into
     * the declared files list when it is not declared yet (the validator demands the main to be
     * part of the list). Nothing is written when the file already is the main.
     *
     * @param bundleFolder the bundle folder (contains .woodbundle.json)
     * @param fileName the new main file name
     * @return true when the descriptor names the new main afterwards
     */
    public boolean setBundleMainAt(File bundleFolder, String fileName) {
        if (bundleFolder == null || fileName == null || fileName.isBlank()) {
            return false;
        }
        ProjectFileService service = new ProjectFileService();
        try {
            de.jare.jsoncasted.project.BundleDescriptor descriptor
                    = service.loadBundleDescriptor(bundleFolder);
            if (fileName.equals(descriptor.getMain())) {
                return false;
            }
            if (!descriptor.getFiles().contains(fileName)) {
                descriptor.getFiles().add(fileName);
            }
            descriptor.setMain(fileName);
            service.saveBundleDescriptor(bundleFolder, descriptor);
        } catch (IOException | JsonParseException | de.jare.jsoncasted.model.JsonBuildException
                | JsonWriteException e) {
            master.getFastLog().tryWrite("io", "Main file not swapped: " + fileName
                    + " - " + e.getMessage());
            woodWindow.showErrorDialog("The main file could not be set:\n" + e.getMessage());
            return false;
        }
        master.getFastLog().tryWrite("io", "Main file of bundle " + bundleFolder.getName()
                + " is now " + fileName);
        return true;
    }

    /**
     * Core of "Register in bundle" (the ghost glyph disappears): declares an existing file of
     * the bundle folder in its descriptor's files list. No content is written - the file lies
     * there already, only the bundle's truth adopts it. A repeated call is refused.
     *
     * @param bundleFolder the bundle folder (contains .woodbundle.json)
     * @param fileName the existing file to declare
     * @return true when the descriptor lists the file afterwards; false when refused
     */
    public boolean registerBundleResourceAt(File bundleFolder, String fileName) {
        if (bundleFolder == null || !bundleFolder.isDirectory() || fileName == null
                || fileName.isBlank()) {
            return false;
        }
        ProjectFileService service = new ProjectFileService();
        try {
            de.jare.jsoncasted.project.BundleDescriptor descriptor
                    = service.loadBundleDescriptor(bundleFolder);
            if (descriptor.hasFile(fileName)) {
                master.getFastLog().tryWrite("io", "Not registered: '" + fileName
                        + "' is already declared in the bundle.");
                return false;
            }
            if (!new File(bundleFolder, fileName).isFile()) {
                master.getFastLog().tryWrite("io", "Not registered: '" + fileName
                        + "' does not exist in the bundle folder.");
                return false;
            }
            descriptor.getFiles().add(fileName);
            service.saveBundleDescriptor(bundleFolder, descriptor);
        } catch (IOException | JsonParseException | de.jare.jsoncasted.model.JsonBuildException
                | JsonWriteException e) {
            master.getFastLog().tryWrite("io", "Not registered: " + fileName + " - " + e.getMessage());
            return false;
        }
        master.getFastLog().tryWrite("io", "Registered '" + fileName + "' in bundle "
                + bundleFolder.getName());
        return true;
    }

    private boolean hasOpenProject() {
        if (woodWindow.getProjectSidebar().hasProject()) {
            return true;
        }
        woodWindow.showErrorDialog("No project is open (Project > Open Project... first).");
        return false;
    }

    private void deleteRecursively(File folder) {
        if (folder == null || !folder.exists()) {
            return;
        }
        File[] children = folder.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    deleteRecursively(child);
                } else if (!child.delete()) {
                    return;
                }
            }
        }
        folder.delete();
    }

    /**
     * Opens the disk diff for the given tab (the tab popup entry), with a FORCED kind - the save conflict
     * offers the plain W2 user diff alongside the W3 merge view: left = the user's version, right = the disk
     * version, only the direct comparison marks, no merge classification.
     *
     * @param trigger logging tag of the caller
     * @param forcedKind W2 for the plain user diff, or null for the automatic kind (W3 with a base state)
     * @param container the tab whose tree is compared
     */
    public void diskDiff(String trigger, DiffViewController.Kind forcedKind,
            de.jare.tree.ui.JackEditTreeContainer container) {
        final EditTree ours = woodWindow.treeOf(container);
        final File file = woodWindow.fileOf(container);
        if (ours == null) {
            woodWindow.showErrorDialog("No editor tree to compare.");
            return;
        }
        if (file == null || !file.exists()) {
            woodWindow.showErrorDialog("This tab has no file on disk - nothing to compare.");
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
        saveTab(woodWindow.getActiveContainer());
    }

    /**
     * Saves the tree of the given editor tab into its file. Without a known file (the tree was not loaded
     * from disk) the save-as dialog is shown instead.
     *
     * @param container the tab to save, may be null (nothing happens then)
     */
    public void saveTab(de.jare.tree.ui.JackEditTreeContainer container) {
        if (container == null) {
            return;
        }
        final File target = woodWindow.fileOf(container);
        if (target == null) {
            saveTabAs(container);
            return;
        }
        writeTree(target, container);
    }

    /**
     * Saves the tree of the active editor tab into a file chosen in a save dialog. The chosen file is remembered
     * for the tab so the next save overwrites it.
     */
    public void saveActiveFileAs() {
        saveTabAs(woodWindow.getActiveContainer());
    }

    /**
     * Saves the tree of the given editor tab into a file chosen in a save dialog. The chosen file is remembered
     * for the tab so the next save overwrites it.
     *
     * @param container the tab to save, may be null (nothing happens then)
     */
    public void saveTabAs(de.jare.tree.ui.JackEditTreeContainer container) {
        if (container == null) {
            return;
        }
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
        if (writeTree(target, container)) {
            woodWindow.setFileOf(container, target);
        }
    }

    /**
     * Writes the tree of the given editor tab with the tree writer (composite keys, transient filter) - behind
     * the save guard of the bundle concept (doc/JSON_BUNDLE.md): the two state axes of the tree decide
     * whether the plain overwrite is allowed. Only the EDITED+OUTDATED conflict asks: merge over the existing
     * 3-way rails (base = retained import state, ours = the tree, theirs = the external file), overwrite anyway,
     * or discard the own edits.
     *
     * @param target the file to write
     * @param container the tab whose tree is written
     * @return true when the tree was written, false on error or without a tree
     */
    private boolean writeTree(File target, de.jare.tree.ui.JackEditTreeContainer container) {
        final EditTree tree = woodWindow.treeOf(container);
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
            // the save refreshed the fingerprint - the (outdated) markers may go
            woodWindow.refreshOutdatedMarkers();
            // project document round trip (concept docs/koncepts/CONCEPT_ProjectView.md section 12, phase 4):
            // a save of .wood/project.json re-projects the sidebar
            if (woodWindow.getProjectSidebar().isProjectDocumentFile(target)) {
                woodWindow.getProjectSidebar().refreshProject();
            }
            return true;
        } catch (IOException e) {
            master.getFastLog().tryWrite("io", "Save failed: " + target.getName() + " - " + e.getMessage());
            woodWindow.showErrorDialog("Error saving file: " + e.getMessage());
            return false;
        }
    }

    /**
     * The save guard quadrant (concept doc/JSON_BUNDLE.md, SaveGuard): CLEAN+CURRENT and EDITED+CURRENT allow
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
                diskDiff("save conflict user diff", DiffViewController.Kind.W2,
                        woodWindow.getActiveContainer());
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
