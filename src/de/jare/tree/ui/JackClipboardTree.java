/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.clipboard.ClipboardManager;
import de.jare.jsoncasted.editor.clipboard.ClipboardStash;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.tree.control.JackMasterControl;
import de.jare.tree.control.listeners.TreeFocusComponent;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/**
 * Clipboard Tree Implementation für JackEditTree mit ClipboardStash Unterstützung.
 * Zeigt den Inhalt des aktuellen Stash an und erlaubt das Wechseln zwischen Stashes.
 */
public class JackClipboardTree extends JTree {

    private final ClipboardManager clipboardManager;
    private JackEditTree sourceTree;
    private String currentStashName;
    private final ClipboardManager.ClipboardChangeListener clipboardChangeListener = this::onClipboardChanged;

    public JackClipboardTree(ClipboardManager clipboardManager) {
        super(new DefaultMutableTreeNode("Clipboard"));
        this.clipboardManager = clipboardManager;
        this.currentStashName = ClipboardManager.CLIPBOARD_STASH_NAME;
        setEditable(false);
        setRootVisible(true);
        setShowsRootHandles(true);
        
        // Registriere Listener für Clipboard-Änderungen (höhere Priorität für Inhaltsupdate)
        clipboardManager.addClipboardChangeListener(4, clipboardChangeListener);
        
        // Standardmäßig den Clipboard Stash anzeigen
        showStashContent(currentStashName);
    }

    public JackClipboardTree(ClipboardManager clipboardManager, JackMasterControl master) {
        this(clipboardManager);
    }

    public void setSourceTree(JackEditTree sourceTree) {
        this.sourceTree = sourceTree;
    }

    public JackEditTree getSourceTree() {
        return sourceTree;
    }

    /**
     * Wechselt den angezeigten Stash und zeigt dessen Inhalt an.
     * 
     * @param stashName der Name des Stash, der angezeigt werden soll
     */
    public void switchStash(String stashName) {
        if (stashName == null || stashName.equals(currentStashName)) {
            return;
        }
        this.currentStashName = stashName;
        showStashContent(stashName);
    }

    /**
     * Zeigt den Inhalt des angegebenen Stash im Tree an.
     * 
     * @param stashName der Name des Stash
     */
    public void showStashContent(String stashName) {
        ClipboardStash stash = clipboardManager.getStash(stashName);
        if (stash == null) {
            return;
        }

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) getModel().getRoot();
        root.removeAllChildren();
        
        EditNodeAbstract[] nodes = stash.getNodes();
        if (nodes != null && nodes.length > 0) {
            for (EditNodeAbstract node : nodes) {
                if (node != null) {
                    root.add(buildTreeNodeFromEditNode(node));
                }
            }
        }
        
        ((DefaultTreeModel) getModel()).reload();
        if (getRowCount() > 0) {
            expandRow(0);
        }
        
        // Root-Name aktualisieren
        root.setUserObject("Clipboard - " + stashName);
        ((DefaultTreeModel) getModel()).nodeChanged(root);
    }

    /**
     * Listener-Callback für Clipboard-Änderungen.
     * Wird aufgerufen, wenn sich ein Stash ändert.
     *
     * @param stashName der Name des geänderten Stash, oder null für alle
     */
    private void onClipboardChanged(String stashName) {
        // Aktualisiere die Anzeige, wenn der geänderte Stash der aktuelle ist
        // oder wenn alle Stashes betroffen sind (stashName == null)
        if (stashName == null || stashName.equals(currentStashName)) {
            SwingUtilities.invokeLater(() -> refreshCurrentStash());
        }
    }

    /**
     * Erstellt einen JTree-Knoten aus einem EditNodeAbstract.
     * 
     * @param node der EditNodeAbstract
     * @return der erstellte DefaultMutableTreeNode
     */
    private DefaultMutableTreeNode buildTreeNodeFromEditNode(EditNodeAbstract node) {
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
        
        for (int i = 0; i < node.getChildCount(); i++) {
            EditNodeAbstract child = node.getChildAt(i);
            if (child != null) {
                treeNode.add(buildTreeNodeFromEditNode(child));
            }
        }
        
        return treeNode;
    }

    /**
     * Kopiert die aktuelle Auswahl in den angegebenen Stash.
     * 
     * @param stashName der Name des Ziel-Stash
     * @param cut ob es sich um einen Cut- oder Copy-Vorgang handelt
     */
    public void copySelectionToStash(String stashName, boolean cut) {
        if (sourceTree == null) {
            return;
        }

        TreePath[] paths = sourceTree.getTree().getSelectionPaths();
        if (paths == null || paths.length == 0) {
            return;
        }

        long[] nodeIds = new long[paths.length];
        for (int i = 0; i < paths.length; i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) paths[i].getLastPathComponent();
            Object userObject = node.getUserObject();
            if (userObject instanceof EditNodeAbstract editNode) {
                nodeIds[i] = editNode.getEditId();
            }
        }

        if (cut) {
            clipboardManager.cutToStash(stashName, sourceTree.getModel().getEditTree(), nodeIds);
        } else {
            clipboardManager.copyToStash(stashName, sourceTree.getModel().getEditTree(), nodeIds);
        }

        // Aktualisiere die Anzeige, falls der aktuelle Stash betroffen ist
        if (stashName.equals(currentStashName)) {
            showStashContent(stashName);
        }
    }

    /**
     * Fügt den Inhalt des aktuellen Stash an der angegebenen Position ein.
     * 
     * @param trigger der auslösende TreeFocusComponent
     * @param path der Ziel-Pfad
     */
    public void pasteFromCurrentStash(TreeFocusComponent trigger, TreePath path) {
        if (path == null || trigger == null) {
            return;
        }

        DefaultMutableTreeNode target = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object targetUo = target.getUserObject();
        
        if (!(targetUo instanceof EditNode targetData)) {
            return;
        }
        ClipboardStash stash = clipboardManager.getStash(currentStashName);
        if (stash == null || stash.isEmpty()) {
            return;
        }

        EditNodeAbstract[] nodes = stash.getNodes();
        
        // Prüfe, ob die Nodes eingefügt werden können
        if (!canPasteTo(targetData, nodes)) {
            UIManager.getLookAndFeel().provideErrorFeedback(this);
            return;
        }

        // Füge die Nodes ein
        long parentId = targetData.getEditId();
        long[] pastedIds = clipboardManager.pasteFromStash(currentStashName, 
                sourceTree.getModel().getEditTree(), parentId, -1);

        // Selektiere die eingefügten Nodes
        if (pastedIds != null && pastedIds.length > 0) {
            // Finde die eingefügten Nodes im Tree
            for (long id : pastedIds) {
                EditNodeAbstract pastedNode = sourceTree.getModel().getEditTree().findNodeById(id);
                if (pastedNode != null) {
                    // Hier könnte man die Selektion setzen, aber das wird normalerweise 
                    // durch den Command Handler erledigt
                }
            }
        }
    }

    /**
     * Prüft, ob die Nodes des aktuellen Stash an der Zielposition eingefügt werden können.
     * 
     * @param targetData das Ziel-EditNode
     * @return true, wenn das Einfügen möglich ist
     */
    public boolean canPasteTo(EditNode targetData) {
        if (targetData == null) {
            return false;
        }

        ClipboardStash stash = clipboardManager.getStash(currentStashName);
        if (stash == null || stash.isEmpty()) {
            return false;
        }

        return canPasteTo(targetData, stash.getNodes());
    }

    private boolean canPasteTo(EditNode targetData, EditNodeAbstract[] nodes) {
        if (targetData == null || nodes == null || nodes.length == 0) {
            return false;
        }

        for (EditNodeAbstract candidate : nodes) {
            if (candidate == null) {
                continue;
            }
            if (!targetData.canBeChildOf(candidate)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Aktualisiert die Anzeige des aktuellen Stash.
     */
    public void refreshCurrentStash() {
        showStashContent(currentStashName);
    }

    /**
     * Gibt den aktuellen Stash-Namen zurück.
     * 
     * @return der Name des aktuellen Stash
     */
    public String getCurrentStashName() {
        return currentStashName;
    }

    /**
     * Gibt den ClipboardManager zurück.
     * 
     * @return der ClipboardManager
     */
    public ClipboardManager getClipboardManager() {
        return clipboardManager;
    }

    /**
     * Löscht den Inhalt des aktuellen Stash.
     */
    public void clearCurrentStash() {
        clipboardManager.clearStash(currentStashName);
        showStashContent(currentStashName);
    }

    /**
     * Erstellt einen neuen Stash mit dem angegebenen Namen.
     * 
     * @param name der Name des neuen Stash
     */
    public void createNewStash(String name) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        clipboardManager.createStash(name);
    }

    /**
     * Löscht den Stash mit dem angegebenen Namen.
     * 
     * @param name der Name des zu löschenden Stash
     */
    public void removeStash(String name) {
        if (name == null || name.equals(currentStashName)) {
            return;
        }
        clipboardManager.removeStash(name);
    }

    /**
     * Gibt alle verfügbaren Stash-Namen zurück.
     * 
     * @return Array mit allen Stash-Namen
     */
    public String[] getAllStashNames() {
        return clipboardManager.getStashNames();
    }

    /**
     * Gibt den aktuellen Stash zurück.
     * 
     * @return der aktuelle ClipboardStash
     */
    public ClipboardStash getCurrentStash() {
        return clipboardManager.getStash(currentStashName);
    }
}
