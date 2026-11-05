/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.project;

import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.model.JsonBuildException;
import de.jare.jsoncasted.project.Partition;
import de.jare.jsoncasted.project.PartitionType;
import de.jare.jsoncasted.project.ProjectDocument;
import de.jare.jsoncasted.project.ProjectFileService;
import de.jare.tree.ui.UiIcons;
import de.jare.tree.ui.WoodWindow;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 * The project sidebar: the third panel far left of the Wood editor (concept:
 * CONCEPT_ProjectView.md, sections 1, 2, 3 and 8). It renders the project document as a
 * projection tree: partitions on level 1, physical structure below, lazy loading on expansion,
 * read-mostly state - opening a JSON file reuses the existing tab machinery.
 *
 * @author Janusch Rentenatus
 */
public class ProjectSidebar extends JPanel {

    private final WoodWindow window;
    private final JLabel nameLabel;
    private final JTree tree;
    private final DefaultTreeModel model;
    private final JButton refreshButton;

    private File projectRoot;
    private ProjectDocument document;
    private ProjectTreeNode root;
    // project layer io without state of its own (concept sections 1, 2 and 4)
    private final ProjectFileService projectService = new ProjectFileService();

    public ProjectSidebar(WoodWindow window) {
        super(new BorderLayout());
        this.window = window;

        nameLabel = new JLabel("No project open");
        nameLabel.setToolTipText("The open wood project (.wood/project.json)");

        // icon-only buttons (UiIcons resolves through the installed LaF, so they follow the theme)
        JButton openButton = new JButton(UiIcons.openProjectIcon());
        openButton.setToolTipText("Open a project folder (requires .wood/project.json)");
        openButton.addActionListener(e -> window.getMainActions().chooseAndOpenProject());
        refreshButton = new JButton(UiIcons.refreshIcon());
        refreshButton.setToolTipText("Re-read the physical structure below the mounts");
        refreshButton.addActionListener(e -> refreshAll());
        refreshButton.setEnabled(false);

        JPanel header = new JPanel(new BorderLayout());
        header.add(nameLabel, BorderLayout.CENTER);
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        buttonRow.add(openButton);
        buttonRow.add(refreshButton);
        header.add(buttonRow, BorderLayout.EAST);

        root = new ProjectTreeNode("No project open");
        model = new DefaultTreeModel(root);
        tree = new JTree(model);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        ToolTipManager.sharedInstance().registerComponent(tree);
        tree.setCellRenderer(new ProjectTreeCellRenderer());

        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
                lazyLoad(event.getPath().getLastPathComponent());
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
                // collapsing changes nothing
            }
        });

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        openNode(path.getLastPathComponent());
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybePopup(e);
            }
        });

        add(header, BorderLayout.NORTH);
        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    /**
     * Shows a loaded and validated project document. The partitions are the eager level 1 of
     * the tree; everything below them loads lazily.
     *
     * @param projectRoot the folder that contains .wood/project.json
     * @param document the validated document to project
     */
    public void showProject(File projectRoot, ProjectDocument document) {
        this.projectRoot = projectRoot;
        this.document = document;

        String name = document.getName() != null && !document.getName().isBlank()
                ? document.getName() : projectRoot.getName();
        nameLabel.setText(name);
        nameLabel.setToolTipText(projectRoot.getAbsolutePath());
        root = new ProjectTreeNode(name);
        for (Partition partition : document.getRoots()) {
            root.add(new ProjectTreeNode(partition, partition.resolve(projectRoot)));
        }
        model.setRoot(root);
        tree.expandPath(new TreePath(root.getPath()));
        refreshButton.setEnabled(true);
    }

    /**
     * Clears the projection - the right pane state of the editor is not touched (the project
     * view owns no edit state, concept section 8).
     */
    public void closeProject() {
        projectRoot = null;
        document = null;
        root = new ProjectTreeNode("No project open");
        model.setRoot(root);
        nameLabel.setText("No project open");
        nameLabel.setToolTipText("The open wood project (.wood/project.json)");
        refreshButton.setEnabled(false);
    }

    public boolean hasProject() {
        return document != null;
    }

    public void refreshAll() {
        if (document != null && projectRoot != null) {
            showProject(projectRoot, document);
        }
    }

    /**
     * The project document round trip (concept section 12, phase 4): re-reads
     * {@code .wood/project.json} of the open project and re-projects it. A document that became
     * invalid is refused with the problems found - the sidebar keeps the last valid state
     * until a correct document is saved.
     */
    public void refreshProject() {
        if (projectRoot == null) {
            return;
        }
        try {
            ProjectDocument reloaded = projectService.loadProject(projectRoot);
            List<String> problems = projectService.validateProject(projectRoot, reloaded);
            if (!problems.isEmpty()) {
                window.showErrorDialog("The project document has problems:\n\n"
                        + String.join("\n", problems)
                        + "\n\nThe sidebar keeps the last valid state; fix the document and save again.");
                return;
            }
            showProject(projectRoot, reloaded);
        } catch (IOException | JsonParseException | JsonBuildException | RuntimeException e) {
            window.showErrorDialog("Could not re-read " + projectService.getProjectDocumentFile(projectRoot)
                    + ":\n" + e.getMessage()
                    + "\n\nThe sidebar keeps the last valid state.");
        }
    }

    /**
     * @return the project document file of the open project (.wood/project.json), or null
     * when no project is open
     */
    public File getProjectDocumentFile() {
        return projectRoot != null ? projectService.getProjectDocumentFile(projectRoot) : null;
    }

    /**
     * @return the root folder of the open project, or null when none is open
     */
    public File getProjectRoot() {
        return projectRoot;
    }

    /**
     * @return the projected project document of the open project, or null when none is open
     */
    public ProjectDocument getDocument() {
        return document;
    }

    /**
     * Opens the project document in a tab (concept section 8: it is JSON, the same round
     * trip as any other JSON file). Offered from the project node and from bundle nodes.
     */
    private void openProjectDocument() {
        if (getProjectDocumentFile() != null) {
            window.getMainActions().loadJsonBundle(getProjectDocumentFile());
        }
    }

    /**
     * @param candidate the file a save just wrote
     * @return true when the candidate is the project document of the open project - the file
     * whose save must refresh the sidebar
     */
    public boolean isProjectDocumentFile(File candidate) {
        File projectDocumentFile = getProjectDocumentFile();
        return candidate != null && projectDocumentFile != null
                && candidate.getAbsolutePath().equalsIgnoreCase(projectDocumentFile.getAbsolutePath());
    }

    private void lazyLoad(Object node) {
        if (node instanceof ProjectTreeNode) {
            ((ProjectTreeNode) node).ensureLoaded(model);
        }
    }

    /**
     * Double-click semantics (concept sections 3 and 4): JSON opens through the existing bundle
     * machinery, a bundle opens its main file, every other file opens externally in v1. Folders
     * toggle their expansion.
     *
     * @param node the selected node
     */
    /**
     * Double-click semantics (concept sections 3 and 4): JSON opens through the existing bundle
     * machinery, a bundle opens its main file, every other file opens externally in v1. Folders
     * toggle their expansion. The machinery reads-write decision honors the read-only mounts
     * (phase 9) and the descriptor-carried file protection (phase 20): a fenced file opens as
     * a read-only view.
     *
     * @param node the selected node
     */
    public void openNode(Object node) {
        if (!(node instanceof ProjectTreeNode)) {
            return;
        }
        ProjectTreeNode projectNode = (ProjectTreeNode) node;
        switch (projectNode.getKind()) {
            case FILE:
                File file = projectNode.getFile();
                if (file != null && isJson(file)) {
                    // a readonly mount (isolated source, gen, target) opens as a view only -
                    // phase 9: an old version to look at never becomes editable; a protected
                    // file (phase 20: the fence in its bundle descriptor) opens as a view too
                    window.getMainActions().loadJsonBundle(file,
                            projectNode.isReadonlyMount() || protectedInBundle(projectNode));
                } else {
                    openExternal(file);
                }
                break;
            case BUNDLE:
                File mainFile = projectNode.getMainFile();
                if (mainFile == null) {
                    window.showErrorDialog("This bundle has no readable main file.");
                    break;
                }
                if (isJson(mainFile)) {
                    boolean mainProtected = projectNode.getDescriptor() != null
                            && projectNode.getDescriptor().isProtected(mainFile.getName());
                    window.getMainActions().loadJsonBundle(mainFile,
                            projectNode.isReadonlyMount() || mainProtected);
                } else {
                    openExternal(mainFile);
                }
                break;
            case PROJECT:
                // the root is already expanded - nothing to open
                break;
            default:
                TreePath path = new TreePath(projectNode.getPath());
                if (tree.isExpanded(path)) {
                    tree.collapsePath(path);
                } else {
                    tree.expandPath(path);
                }
                break;
        }
    }

    /**
     * The type-appropriate "New" entries of a folder node (concept section 2: the role decides
     * the behavior): a json partition offers JSON files and nested bundles, src/test offer Java
     * files, assets offer a plain file - everywhere a subfolder. Read-only mounts (gen, target,
     * isolated) get nothing: nobody creates into a stand they only look at. A bundle gets the
     * one thing that makes sense inside it: a declared JSON resource.
     *
     * @param popup the popup under construction
     * @param node the clicked node
     */
    private void appendNewItems(JPopupMenu popup, ProjectTreeNode node) {
        if (!hasProject()) {
            return;
        }
        final ProjectTreeNode.Kind kind = node.getKind();
        if (kind != ProjectTreeNode.Kind.PARTITION && kind != ProjectTreeNode.Kind.FOLDER
                && kind != ProjectTreeNode.Kind.BUNDLE) {
            return;
        }
        if (node.isReadonlyMount()) {
            return;
        }
        if (kind == ProjectTreeNode.Kind.BUNDLE) {
            JMenuItem resource = new JMenuItem("New JSON resource...");
            resource.setToolTipText("Create a JSON file and declare it in the bundle descriptor");
            resource.addActionListener(ev -> {
                if (window.getMainActions().addBundleResourceTo(node.getFile())) {
                    reloadSubtree(node);
                }
            });
            popup.add(resource);
            popup.addSeparator();
            return;
        }
        final PartitionType type = node.enclosingPartitionType();
        if (type == PartitionType.JSON) {
            JMenuItem jsonFile = new JMenuItem("New JSON file...");
            jsonFile.setToolTipText("An empty JSON file below " + node);
            jsonFile.addActionListener(ev -> {
                if (window.getMainActions().addFileTo(node.getFile(),
                        "New JSON file", "newFile.json", "{}")) {
                    reloadSubtree(node);
                }
            });
            popup.add(jsonFile);
            JMenuItem bundleHere = new JMenuItem("New bundle...");
            bundleHere.setToolTipText("An empty bundle folder below " + node + " (bundles can nest)");
            bundleHere.addActionListener(ev -> {
                if (window.getMainActions().addBundleTo(node.getFile())) {
                    reloadSubtree(node);
                }
            });
            popup.add(bundleHere);
        } else if (type == PartitionType.SRC || type == PartitionType.TEST) {
            JMenuItem javaFile = new JMenuItem("New Java file...");
            javaFile.setToolTipText("A Java skeleton below " + node);
            javaFile.addActionListener(ev -> {
                if (window.getMainActions().addFileTo(node.getFile(),
                        "New Java file", "NewClass.java", "public class {stem} {\n}\n")) {
                    reloadSubtree(node);
                }
            });
            popup.add(javaFile);
        } else if (type == PartitionType.ASSETS) {
            JMenuItem plainFile = new JMenuItem("New file...");
            plainFile.setToolTipText("An empty file below " + node);
            plainFile.addActionListener(ev -> {
                if (window.getMainActions().addFileTo(node.getFile(),
                        "New file", "newFile.txt", null)) {
                    reloadSubtree(node);
                }
            });
            popup.add(plainFile);
        }
        JMenuItem subfolder = new JMenuItem("New subfolder...");
        subfolder.setToolTipText("A plain folder below " + node);
        subfolder.addActionListener(ev -> {
            if (window.getMainActions().addSubfolderTo(node.getFile())) {
                reloadSubtree(node);
            }
        });
        popup.add(subfolder);
        popup.addSeparator();
    }

    /**
     * Re-reads one subtree after a create action: the clicked node reloads, the user keeps the
     * expansion context instead of the whole tree collapsing.
     *
     * @param node the parent node of the fresh content
     */
    private void reloadSubtree(ProjectTreeNode node) {
        node.reset();
        node.ensureLoaded(model);
        tree.expandPath(new TreePath(node.getPath()));
    }

    /**
     * The descriptor-carried protection (concept phase 20): a JSON file fences off in its
     * bundle's descriptor - it opens read-only until the fence is removed, the flag travels
     * with the bundle (git, clones) and every assistant reading the descriptor sees it.
     *
     * @param node the file node to ask for
     * @return true when its bundle descriptor fences this file
     */
    private boolean protectedInBundle(ProjectTreeNode node) {
        if (node.getParent() instanceof ProjectTreeNode parentBundle
                && parentBundle.getKind() == ProjectTreeNode.Kind.BUNDLE
                && parentBundle.getDescriptor() != null
                && node.getFile() != null) {
            return parentBundle.getDescriptor().isProtected(node.getFile().getName());
        }
        return false;
    }

    /**
     * Toggles the read-only fence of a bundle file and persists the descriptor. The fence
     * applies to the open tabs at once (protect flips them read-only, removing flips them
     * back) so the protection never waits for the next open to take effect.
     *
     * @param bundleNode the bundle node whose descriptor carries the fence
     * @param fileNode the file node inside the bundle
     */
    private void toggleBundleFileProtection(ProjectTreeNode bundleNode, ProjectTreeNode fileNode) {
        de.jare.jsoncasted.project.BundleDescriptor descriptor = bundleNode.getDescriptor();
        if (descriptor == null || fileNode.getFile() == null) {
            return;
        }
        String fileName = fileNode.getFile().getName();
        boolean nowProtected = !descriptor.isProtected(fileName);
        try {
            if (nowProtected) {
                descriptor.protect(fileName);
            } else {
                descriptor.unprotect(fileName);
            }
            projectService.saveBundleDescriptor(bundleNode.getFile(), descriptor);
        } catch (IOException | de.jare.jsoncasted.io.JsonParseException
                | de.jare.jsoncasted.io.JsonWriteException e) {
            window.showErrorDialog("The protection could not be persisted:\n" + e.getMessage());
            return;
        }
        window.setCodeProtection(fileNode.getFile(), nowProtected);
        reloadSubtree(bundleNode);
    }

    private void maybePopup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null || !(path.getLastPathComponent() instanceof ProjectTreeNode)) {
            return;
        }
        tree.setSelectionPath(path);
        ProjectTreeNode node = (ProjectTreeNode) path.getLastPathComponent();

        JPopupMenu popup = new JPopupMenu();
        if (node.getKind() == ProjectTreeNode.Kind.PROJECT && hasProject()) {
            // the round trip of the project document (concept section 12, phase 4)
            JMenuItem openDocument = new JMenuItem("Open project document");
            openDocument.setToolTipText("Open .wood/project.json in a tab - it is JSON");
            openDocument.addActionListener(ev -> openProjectDocument());
            popup.add(openDocument);
            popup.addSeparator();

            // the root is the project level: the same folder roles as the Project menu
            JMenu addFolderMenu = new JMenu("Add Folder");
            addFolderMenu.setToolTipText("Create a folder of a project role inside the project root");
            for (PartitionType type : PartitionType.VALUES) {
                if (type == PartitionType.ISOLATED) {
                    continue; // an old world is never created empty
                }
                JMenuItem typeItem = new JMenuItem(type.getName());
                typeItem.addActionListener(ev -> window.getMainActions().addProjectFolder(type));
                addFolderMenu.add(typeItem);
            }
            JMenuItem bundleRootItem = new JMenuItem("bundle");
            bundleRootItem.setToolTipText("An empty bundle inside the first json partition");
            bundleRootItem.addActionListener(ev -> window.getMainActions().addProjectBundle());
            addFolderMenu.addSeparator();
            addFolderMenu.add(bundleRootItem);
            popup.add(addFolderMenu);
            popup.addSeparator();
        }
        appendNewItems(popup, node);
        if (node.getKind() == ProjectTreeNode.Kind.FILE) {
            JMenuItem open = new JMenuItem(isJson(node.getFile()) ? "Open bundle" : "Open externally");
            open.addActionListener(ev -> openNode(node));
            popup.add(open);
            // a JSON file inside its bundle can become the main - the guard dialog names the
            // bundle and the current main before the descriptor rewrite happens
            if (isJson(node.getFile())
                    && node.getParent() instanceof ProjectTreeNode parentBundle
                    && parentBundle.getKind() == ProjectTreeNode.Kind.BUNDLE
                    && !node.isReadonlyMount()) {
                JMenuItem setMain = new JMenuItem("Set as main file...");
                setMain.setToolTipText("Make this file the bundle's main file (the open target)");
                setMain.addActionListener(ev -> {
                    if (window.getMainActions().setBundleMain(parentBundle.getFile(),
                            node.getFile().getName())) {
                        reloadSubtree(parentBundle);
                    }
                });
                popup.add(setMain);
                // the read-only fence of phase 20: descriptor carried, survives git and clones,
                // visible to every assistant that reads the descriptor
                String fileName = node.getFile().getName();
                boolean fenced = parentBundle.getDescriptor() != null
                        && parentBundle.getDescriptor().isProtected(fileName);
                JMenuItem protectItem = new JMenuItem(fenced
                        ? "Remove protection" : "Protect file (temporary read-only)");
                protectItem.setToolTipText("The fence lives in the bundle descriptor: the file "
                        + "opens as a read-only view until the protection is removed");
                protectItem.addActionListener(ev ->
                        toggleBundleFileProtection(parentBundle, node));
                popup.add(protectItem);
                // the ghost's way out (phase 21): an undeclared JSON file of the bundle folder
                // registers into the descriptor - the glyph disappears, the file becomes truth
                if (parentBundle.getDescriptor() != null
                        && !parentBundle.getDescriptor().hasFile(fileName)) {
                    JMenuItem registerItem = new JMenuItem("Register in bundle");
                    registerItem.setToolTipText("Declare this file in the bundle descriptor - "
                            + "it stops haunting as an undeclared leftover");
                    registerItem.addActionListener(ev -> {
                        if (window.getMainActions().registerBundleResourceAt(
                                parentBundle.getFile(), fileName)) {
                            reloadSubtree(parentBundle);
                        }
                    });
                    popup.add(registerItem);
                }
            }
            popup.addSeparator();
        }
        if (node.getKind() == ProjectTreeNode.Kind.BUNDLE) {
            JMenuItem openMain = new JMenuItem("Open main file");
            openMain.setToolTipText("Open the bundle through its declared main file");
            openMain.addActionListener(ev -> openNode(node));
            popup.add(openMain);
            // the bundle document is JSON just like the project document (concept section 8)
            // - the round trip opens the descriptor in a tab, read-only when the mount is
            JMenuItem openBundleDocument = new JMenuItem("Open bundle document");
            openBundleDocument.setToolTipText("Open .woodbundle.json in a tab - it is JSON");
            openBundleDocument.addActionListener(ev -> window.getMainActions().loadJsonBundle(
                    projectService.getBundleDescriptorFile(node.getFile()), node.isReadonlyMount()));
            popup.add(openBundleDocument);
            // clone and reroll are identity-world actions: they exist only in json partitions.
            // An isolated source (phase 9) is a view-only old world - nobody clones or rerolls
            // a stand, and it never writes back.
            final boolean identityWorld = node.enclosingPartitionType() == PartitionType.JSON
                    && !node.isReadonlyMount();
            if (identityWorld) {
                JMenuItem cloneItem = new JMenuItem("Clone bundle...");
                cloneItem.setToolTipText("Duplicate this bundle next to the original (concept section 5)");
                cloneItem.addActionListener(ev -> BundleCloneDialog.show(window, node.getFile(),
                        (source, deep) -> cloneBundle(node, source, deep)));
                popup.add(cloneItem);
            }
            if (hasProject() && projectRoot != null) {
                JMenuItem importsItem = new JMenuItem("Imports...");
                importsItem.setToolTipText("The descriptor's imports with their resolutions (concept section 6)");
                importsItem.addActionListener(ev -> BundleImportsDialog.show(window, projectRoot, node.getFile()));
                popup.add(importsItem);
                if (identityWorld) {
                    JMenuItem rerollItem = new JMenuItem("Reroll IDs...");
                    rerollItem.setToolTipText("Fresh identities for this bundle - disjoint from the whole project (concept section 7.1)");
                    rerollItem.addActionListener(ev -> RerollIdsDialog.show(window, projectRoot, document,
                            node.getFile(), node.getMainFile()));
                    popup.add(rerollItem);
                }
            }
            popup.addSeparator();
        }
        if (isMaterializable(node)) {
            JMenuItem materialize = new JMenuItem("Materialize bundle...");
            materialize.setToolTipText("Create the bundle folder around this single JSON file (concept section 4)");
            materialize.addActionListener(ev -> materialize(node));
            popup.add(materialize);
            popup.addSeparator();
        }
        JMenuItem refresh = new JMenuItem("Refresh");
        refresh.setToolTipText("Re-read this subtree from disk");
        refresh.addActionListener(ev -> {
            if (node.getKind() == ProjectTreeNode.Kind.PROJECT) {
                // the root is the project itself: the whole document re-reads, the sidebar keeps
                // the last valid state on problems (concept section 12, phase 4)
                if (hasProject()) {
                    refreshProject();
                }
                return;
            }
            node.reset();
            if (node.getKind() == ProjectTreeNode.Kind.FOLDER
                    || node.getKind() == ProjectTreeNode.Kind.PARTITION) {
                node.ensureLoaded(model);
                tree.expandPath(new TreePath(node.getPath()));
            }
        });
        popup.add(refresh);

        JMenuItem reveal = new JMenuItem("Reveal in explorer");
        reveal.setToolTipText("Show the physical location on disk");
        reveal.addActionListener(ev -> revealInExplorer(node));
        popup.add(reveal);

        popup.show(tree, e.getX(), e.getY());
    }

    /**
     * The clone execution (concept section 5, phase 6): folder copy into the first free sibling,
     * descriptor relabel, deep relocation on demand - the result re-projects on the spot, the
     * new bundle appears next to the original exactly where the tree shows it.
     *
     * @param node the cloned bundle node (its parent re-projects).
     * @param sourceFolder the bundle folder of the node.
     * @param deep deep or shallow mode of the dialog.
     */
    private void cloneBundle(ProjectTreeNode node, File sourceFolder, boolean deep) {
        final File target;
        try {
            target = BundleCloner.clone(sourceFolder, deep);
        } catch (IOException ex) {
            window.showErrorDialog("Could not clone the bundle:\n\n" + ex.getMessage());
            return;
        }
        JOptionPane.showMessageDialog(this, "Cloned to\n" + target.getAbsolutePath(),
                "Clone bundle", JOptionPane.INFORMATION_MESSAGE);
        // the sibling exists on disk - re-project this subtree so the new bundle shows up
        if (node.getParent() instanceof ProjectTreeNode) {
            ProjectTreeNode parent = (ProjectTreeNode) node.getParent();
            if (parent.getKind() == ProjectTreeNode.Kind.PARTITION
                    || parent.getKind() == ProjectTreeNode.Kind.FOLDER) {
                parent.reset();
                parent.ensureLoaded(model);
                tree.expandPath(new TreePath(parent.getPath()));
            }
        }
    }

    /**
     * The single-file fallback materialization (concept section 4, rule 4): a bare JSON
     * directly inside a json partition becomes an explicit bundle - folder plus descriptor.
     * Runs only on explicit user action; afterwards the partition is re-projected.
     *
     * @param node the bare JSON file node
     */
    private void materialize(ProjectTreeNode node) {
        File jsonFile = node.getFile();
        String stem = jsonFile.getName().replaceFirst("(?i)\\.json$", "");
        int answer = JOptionPane.showConfirmDialog(this, "Materialize the implicit bundle around \""
                + jsonFile.getName() + "\"?\n\nCreates the sibling folder \"" + stem
                + "\", moves the file into it and writes the bundle descriptor.",
                "Materialize bundle", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            BundleMaterializer.materialize(jsonFile);
        } catch (IOException ex) {
            window.showErrorDialog("Could not materialize the bundle: " + ex.getMessage());
            return;
        }
        // the implicit state is gone - re-project this partition from disk
        ProjectTreeNode partitionNode = node.isDirectlyBelowPartition()
                ? (ProjectTreeNode) node.getParent() : null;
        if (partitionNode != null) {
            partitionNode.reset();
            partitionNode.ensureLoaded(model);
            tree.expandPath(new TreePath(partitionNode.getPath()));
        }
    }

    /**
     * @param node the node under the popup
     * @return true when the node is a bare JSON directly inside a json partition - the
     * materialization candidate of concept section 4, rule 4
     */
    private boolean isMaterializable(ProjectTreeNode node) {
        return node.getKind() == ProjectTreeNode.Kind.FILE
                && isJson(node.getFile())
                && node.enclosingPartitionType() == PartitionType.JSON
                && node.isDirectlyBelowPartition()
                && !node.isReadonlyMount();
    }

    private void revealInExplorer(ProjectTreeNode node) {
        File target = node.getFile();
        if (target == null) {
            if (projectRoot != null && projectRoot.isDirectory()) {
                target = projectRoot;
            }
        }
        if (target == null) {
            window.showErrorDialog("This node has no physical location to reveal.");
            return;
        }
        try {
            if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                Desktop.getDesktop().browseFileDirectory(target.isDirectory() ? target : target.getParentFile());
            } else {
                Desktop.getDesktop().open(target.isDirectory() ? target : target.getParentFile());
            }
        } catch (IOException | SecurityException ex) {
            window.showErrorDialog("Could not reveal in explorer: " + ex.getMessage());
        }
    }

    private void openExternal(File file) {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            Desktop.getDesktop().open(file);
        } catch (IOException | SecurityException ex) {
            window.showErrorDialog("Could not open externally: " + ex.getMessage());
        }
    }

    private boolean isJson(File file) {
        return file != null && file.getName().toLowerCase().endsWith(".json");
    }

}
