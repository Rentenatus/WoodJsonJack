/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.project;

import de.jare.jsoncasted.project.BundleDescriptor;
import de.jare.jsoncasted.project.Partition;
import de.jare.jsoncasted.project.PartitionType;
import de.jare.jsoncasted.project.ProjectFileService;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * Node of the project sidebar (concept: CONCEPT_ProjectView.md, sections 2, 3 and 4). The sidebar is
 * a projection: virtual structure (partitions) mapped onto physical locations. Partitions sit on
 * level 1 only; everything below a partition is physical subfolder structure, loaded lazily on
 * first expansion. Inside a json partition the scan goes deeper in meaning: folders with a bundle
 * descriptor render as bundles (concept section 4).
 *
 * @author Janusch Rentenatus
 */
public class ProjectTreeNode extends DefaultMutableTreeNode {

    public enum Kind {
        PROJECT, PARTITION, FOLDER, FILE, BUNDLE
    }

    private static final Object PLACEHOLDER = "loading...";
    private static final ProjectFileService PROJECT_SERVICE = new ProjectFileService();

    private final Kind kind;
    private final Partition partition;
    // mutable on reset: a bundle reload re-reads the descriptor, so a main file swap or a
    // rename shows in the SAME node instead of surviving in a stale copy until re-scan
    private BundleDescriptor descriptor;
    private final File file;
    private String label;
    private boolean loaded;
    // a scan problem the projection degrades honestly (a broken bundle descriptor): the
    // node stays visible as what it fell back to, the warning says what is wrong with it
    private String warning;

    public ProjectTreeNode(String label) {
        this.kind = Kind.PROJECT;
        this.partition = null;
        this.descriptor = null;
        this.file = null;
        this.label = label != null ? label : "Project";
    }

    public ProjectTreeNode(Partition partition, File resolvedFolder) {
        this.kind = Kind.PARTITION;
        this.partition = partition;
        this.descriptor = null;
        this.file = resolvedFolder;
        this.label = partition != null && partition.getName() != null ? partition.getName() : "?";
        addPlaceholder();
    }

    public ProjectTreeNode(File file, boolean folder) {
        this.kind = folder ? Kind.FOLDER : Kind.FILE;
        this.partition = null;
        this.descriptor = null;
        this.file = file;
        this.label = file.getName();
        if (folder) {
            addPlaceholder();
        }
    }

    /**
     * A discovered bundle (concept section 4): a folder with a {@code .woodbundle.json}. The
     * descriptor decides the label (rule 3: {@code name} defaults to the folder name) and the
     * children: the declared files, the main file first.
     *
     * @param bundleFolder the folder that contains .woodbundle.json
     * @param descriptor the loaded bundle descriptor
     */
    public ProjectTreeNode(File bundleFolder, BundleDescriptor descriptor) {
        this.kind = Kind.BUNDLE;
        this.partition = null;
        this.descriptor = descriptor;
        this.file = bundleFolder;
        this.label = descriptor != null && descriptor.getName() != null && !descriptor.getName().isBlank()
                ? descriptor.getName() : bundleFolder.getName();
        addPlaceholder();
    }

    private void addPlaceholder() {
        add(new DefaultMutableTreeNode(PLACEHOLDER));
    }

    public Kind getKind() {
        return kind;
    }

    public Partition getPartition() {
        return partition;
    }

    public File getFile() {
        return file;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void reset() {
        loaded = false;
        // a bundle reload re-reads the descriptor: the node reflects the CURRENT disk state
        // (a just swapped main file leads the children again), not the state of its scan
        if (kind == Kind.BUNDLE && file != null && file.isDirectory()) {
            try {
                descriptor = PROJECT_SERVICE.loadBundleDescriptor(file);
                warning = null;
                label = descriptor != null && descriptor.getName() != null
                        && !descriptor.getName().isBlank()
                        ? descriptor.getName() : file.getName();
            } catch (Exception e) {
                warning = "Broken bundle descriptor (" + e.getMessage()
                        + ") - the node keeps the last readable state.";
            }
        }
        removeAllChildren();
        if (kind == Kind.FOLDER || kind == Kind.PARTITION) {
            addPlaceholder();
        }
    }

    /**
     * Loads the children of this node into the model - no-op for already loaded nodes and for
     * files. Partitions and folders list their physical content; the first level of a project
     * (the partitions) is loaded by the sidebar itself, not by expansion.
     *
     * @param model the tree model to notify
     */
    public void ensureLoaded(DefaultTreeModel model) {
        if (loaded || kind == Kind.FILE || kind == Kind.PROJECT) {
            return;
        }
        loaded = true;
        removeAllChildren();
        if (kind == Kind.BUNDLE && descriptor != null && file != null && file.isDirectory()) {
            for (ProjectTreeNode child : listBundleChildren()) {
                add(child);
            }
        } else if (file != null && file.isDirectory()) {
            for (ProjectTreeNode child : listChildren()) {
                add(child);
            }
        }
        model.nodeStructureChanged(this);
    }

    /**
     * Lists the physical content of the mounted folder: directories first, then files, both
     * case-insensitively sorted by name. Inside a json partition the directory scan discovers
     * bundles (concept section 4): a folder with a {@code .woodbundle.json} becomes a bundle
     * node, all others stay folders. The projection is honest: it shows what lies there,
     * hidden system entries (".", "..") included in the listing cost nothing because
     * {@link File#listFiles()} never returns them.
     *
     * @return the child nodes of this folder/partition
     */
    private List<ProjectTreeNode> listChildren() {
        List<ProjectTreeNode> children = new ArrayList<>();
        File[] entries = file.listFiles();
        if (entries == null) {
            return children;
        }
        List<File> dirs = new ArrayList<>();
        List<File> files = new ArrayList<>();
        for (File entry : entries) {
            if (entry.isDirectory()) {
                dirs.add(entry);
            } else {
                files.add(entry);
            }
        }
        Comparator<File> byName = Comparator.comparing(f -> f.getName().toLowerCase());
        dirs.sort(byName);
        files.sort(byName);
        // bundle-aware inside json partitions AND isolated sources: an isolated mount is the
        // view-only old world (concept CONCEPT_ProjectView.md, phase 9) - it renders like a json
        // partition (bundles, descriptors, the grouping), it just never takes part in the
        // identity world of the project
        final PartitionType enclosing = enclosingPartitionType();
        final boolean bundleAware = enclosing == PartitionType.JSON
                || enclosing == PartitionType.ISOLATED;
        for (File dir : dirs) {
            ProjectTreeNode bundle = bundleAware ? tryBundleNode(dir) : null;
            children.add(bundle != null ? bundle : new ProjectTreeNode(dir, true));
        }
        for (File plain : files) {
            children.add(new ProjectTreeNode(plain, false));
        }
        return children;
    }

    /**
     * Lists the children of a bundle: the declared files first - the main file leads, then the
     * descriptor order - then the undeclared physical files, then the subfolders (bundle-aware
     * again, so bundles can nest). The bundle subtree renders the descriptor's meaning, not a
     * plain directory listing.
     *
     * @return the child nodes of this bundle
     */
    private List<ProjectTreeNode> listBundleChildren() {
        List<ProjectTreeNode> children = new ArrayList<>();
        File[] entries = file.listFiles();
        if (entries == null) {
            return children;
        }
        Set<String> declared = new HashSet<>();
        File mainFile = getMainFile();
        if (mainFile != null) {
            children.add(new ProjectTreeNode(mainFile, false));
            declared.add(mainFile.getName());
        }
        for (String declaredName : descriptor.getFiles()) {
            if (declaredName == null || declared.contains(declaredName)) {
                continue;
            }
            declared.add(declaredName);
            File declaredFile = new File(file, declaredName);
            if (declaredFile.isFile()) {
                children.add(new ProjectTreeNode(declaredFile, false));
            }
        }
        List<File> plainFiles = new ArrayList<>();
        List<File> dirs = new ArrayList<>();
        for (File entry : entries) {
            if (declared.contains(entry.getName())
                    || ProjectFileService.BUNDLE_FILE.equals(entry.getName())) {
                continue;
            }
            if (entry.isDirectory()) {
                dirs.add(entry);
            } else {
                plainFiles.add(entry);
            }
        }
        Comparator<File> byName = Comparator.comparing(f -> f.getName().toLowerCase());
        plainFiles.sort(byName);
        dirs.sort(byName);
        for (File plain : plainFiles) {
            children.add(new ProjectTreeNode(plain, false));
        }
        for (File dir : dirs) {
            ProjectTreeNode bundle = tryBundleNode(dir);
            children.add(bundle != null ? bundle : new ProjectTreeNode(dir, true));
        }
        return children;
    }

    /**
     * Scans a folder for a bundle descriptor and builds the bundle node when one is found.
     * A broken descriptor degrades honestly but visibly: the folder renders as a plain folder
     * with a warning - the reason is neither silently swallowed nor guessed away.
     *
     * @param folder the folder to scan
     * @return the bundle node, or null when the folder has no descriptor
     */
    private ProjectTreeNode tryBundleNode(File folder) {
        if (!folder.isDirectory() || !PROJECT_SERVICE.getBundleDescriptorFile(folder).isFile()) {
            return null;
        }
        try {
            return new ProjectTreeNode(folder, PROJECT_SERVICE.loadBundleDescriptor(folder));
        } catch (Exception e) {
            ProjectTreeNode degraded = new ProjectTreeNode(folder, true);
            degraded.warning = "Broken bundle descriptor (" + e.getMessage()
                    + ") - the folder renders as a plain folder until the descriptor is fixed.";
            return degraded;
        }
    }

    /**
     * @return the scan warning of this node (a broken bundle descriptor it degraded from), or
     * null when the node renders as what it is
     */
    public String getWarning() {
        return warning;
    }

    /**
     * @return the descriptor of this bundle node, or null for every other kind
     */
    public BundleDescriptor getDescriptor() {
        return descriptor;
    }

    /**
     * The main file of this bundle - the entry point a double-click opens (concept section 4,
     * rule 2). Falls back to the first declared file that exists; null when the bundle has
     * no readable entry point.
     *
     * @return the main file of this bundle, or null
     */
    public File getMainFile() {
        if (descriptor == null || file == null) {
            return null;
        }
        if (descriptor.getMain() != null) {
            File mainFile = new File(file, descriptor.getMain());
            if (mainFile.isFile()) {
                return mainFile;
            }
        }
        for (String fileName : descriptor.getFiles()) {
            if (fileName == null) {
                continue;
            }
            File candidate = new File(file, fileName);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * @return true when this node is a direct child of a partition node (the only level where
     * the single-file fallback of concept section 4, rule 4 applies)
     */
    public boolean isDirectlyBelowPartition() {
        return getParentNode() != null && getParentNode().getKind() == Kind.PARTITION;
    }

    /**
     * @return the tooltip line of this node (mount path for partitions, path for the rest)
     */
    public String toTooltip() {
        if (kind == Kind.PARTITION && partition != null) {
            String details = partition.getType() != null ? partition.getType().getName() : "?";
            if (partition.isReadonly()) {
                details = details + ", readonly";
            }
            return label + " [" + details + "] -> " + (file != null ? file.getAbsolutePath() : "?");
        }
        if (kind == Kind.BUNDLE && descriptor != null) {
            String mainName = descriptor.getMain() != null ? descriptor.getMain() : "?";
            return label + " [bundle, main: " + mainName + ", files: "
                    + descriptor.getFiles().size() + "] -> " + (file != null ? file.getAbsolutePath() : "?");
        }
        if (file != null) {
            return warning != null
                    ? file.getAbsolutePath() + "\n" + warning
                    : file.getAbsolutePath();
        }
        return label;
    }

    @Override
    public String toString() {
        return label;
    }

    /**
     * @return true when this node belongs to a readonly mount (partition with readonly flag;
     * the flag is inherited by the whole subtree)
     */
    public boolean isReadonlyMount() {
        if (partition != null && partition.isReadonly()) {
            return true;
        }
        ProjectTreeNode parent = getParentNode();
        return parent != null && parent.isReadonlyMount();
    }

    private ProjectTreeNode getParentNode() {
        return getParent() instanceof ProjectTreeNode ? (ProjectTreeNode) getParent() : null;
    }

    /**
     * Derives the partition type controlling this node for behavior decisions - or null when
     * the node is not below a level-1 mount.
     *
     * @return the partition type of the enclosing partition
     */
    public PartitionType enclosingPartitionType() {
        ProjectTreeNode closer = this;
        while (closer != null) {
            if (closer.partition != null && closer.partition.getType() != null) {
                return closer.partition.getType();
            }
            closer = closer.getParentNode();
        }
        return null;
    }

}
