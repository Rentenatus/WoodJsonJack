/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.project;

import de.jare.jsoncasted.project.Partition;
import de.jare.jsoncasted.project.PartitionType;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * Node of the project sidebar (concept: CONCEPT_ProjectView.md, sections 2 and 3). The sidebar is
 * a projection: virtual structure (partitions) mapped onto physical locations. Partitions sit on
 * level 1 only; everything below a partition is physical subfolder structure, loaded lazily on
 * first expansion.
 *
 * @author Janusch Rentenatus
 */
public class ProjectTreeNode extends DefaultMutableTreeNode {

    public enum Kind {
        PROJECT, PARTITION, FOLDER, FILE
    }

    private static final Object PLACEHOLDER = "loading...";

    private final Kind kind;
    private final Partition partition;
    private final File file;
    private final String label;
    private boolean loaded;

    public ProjectTreeNode(String label) {
        this.kind = Kind.PROJECT;
        this.partition = null;
        this.file = null;
        this.label = label != null ? label : "Project";
    }

    public ProjectTreeNode(Partition partition, File resolvedFolder) {
        this.kind = Kind.PARTITION;
        this.partition = partition;
        this.file = resolvedFolder;
        this.label = partition != null && partition.getName() != null ? partition.getName() : "?";
        addPlaceholder();
    }

    public ProjectTreeNode(File file, boolean folder) {
        this.kind = folder ? Kind.FOLDER : Kind.FILE;
        this.partition = null;
        this.file = file;
        this.label = file.getName();
        if (folder) {
            addPlaceholder();
        }
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
        if (file != null && file.isDirectory()) {
            for (ProjectTreeNode child : listChildren()) {
                add(child);
            }
        }
        model.nodeStructureChanged(this);
    }

    /**
     * Lists the physical content of the mounted folder: directories first, then files, both
     * case-insensitively sorted by name. The projection is honest: it shows what lies there,
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
        for (File dir : dirs) {
            children.add(new ProjectTreeNode(dir, true));
        }
        for (File plain : files) {
            children.add(new ProjectTreeNode(plain, false));
        }
        return children;
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
        if (file != null) {
            return file.getAbsolutePath();
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
