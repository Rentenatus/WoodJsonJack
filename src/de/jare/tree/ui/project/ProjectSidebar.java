/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.project;

import de.jare.jsoncasted.project.Partition;
import de.jare.jsoncasted.project.ProjectDocument;
import de.jare.tree.ui.WoodWindow;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
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

    public ProjectSidebar(WoodWindow window) {
        super(new BorderLayout());
        this.window = window;

        nameLabel = new JLabel("No project open");
        nameLabel.setToolTipText("The open wood project (.wood/project.json)");

        JButton openButton = new JButton("Open...");
        openButton.setToolTipText("Open a project folder (requires .wood/project.json)");
        openButton.addActionListener(e -> window.getMainActions().chooseAndOpenProject());
        refreshButton = new JButton("Refresh");
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
                lazyLoad(event.getPath());
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

    private void lazyLoad(Object node) {
        if (node instanceof ProjectTreeNode) {
            ((ProjectTreeNode) node).ensureLoaded(model);
        }
    }

    /**
     * Double-click semantics (concept section 3): JSON opens through the existing bundle
     * machinery, every other file opens externally in v1. Folders toggle their expansion.
     *
     * @param node the selected node
     */
    private void openNode(Object node) {
        if (!(node instanceof ProjectTreeNode)) {
            return;
        }
        ProjectTreeNode projectNode = (ProjectTreeNode) node;
        switch (projectNode.getKind()) {
            case FILE:
                File file = projectNode.getFile();
                if (file != null && isJson(file)) {
                    window.getMainActions().loadJsonBundle(file);
                } else {
                    openExternal(file);
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
        if (node.getKind() == ProjectTreeNode.Kind.FILE) {
            JMenuItem open = new JMenuItem(isJson(node.getFile()) ? "Open bundle" : "Open externally");
            open.addActionListener(ev -> openNode(node));
            popup.add(open);
            popup.addSeparator();
        }
        JMenuItem refresh = new JMenuItem("Refresh");
        refresh.setToolTipText("Re-read this subtree from disk");
        refresh.addActionListener(ev -> {
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
