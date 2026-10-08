/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.ParseMode;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.tree.control.JackMasterControl;
import static de.jare.tree.control.listeners.ContentListener.EDIT_ADD_ANNOTATION;
import static de.jare.tree.control.listeners.ContentListener.EDIT_ADD_NODE;
import static de.jare.tree.control.listeners.ContentListener.EDIT_COPY;
import static de.jare.tree.control.listeners.ContentListener.EDIT_CUT;
import static de.jare.tree.control.listeners.ContentListener.EDIT_DELETE_NODE;
import static de.jare.tree.control.listeners.ContentListener.EDIT_PASTE;
import static de.jare.tree.control.listeners.ContentListener.EDIT_PASTE_UNDERNEATH;
import static de.jare.tree.control.listeners.ContentListener.EDIT_RENAME_NODE;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import java.awt.event.KeyEvent;
import java.io.File;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;

public class JackMainMenu extends JMenuBar {

    private final WoodWindow woodWindow;
    private final JackMasterControl master;
    private final JackMainActions mainActions;
    private final JMenuItem pasteItem;
    private final JMenuItem pasteUnderneathItem;
    private final JMenuItem deleteNodeItem;
    private final JMenuItem cutItem;
    private final JMenu addNodeMenu;
    private final JMenu addAnnotationMenu;
    private final JMenuItem addNodeItem;
    private final JMenuItem addAnnotationItem;
    private final JMenuItem renameNodeItem;
    private boolean lastRootSelected;
    private Object lastSelectedNode;
    private TreeFocusComponent lastSelectedEditor;

    public JackMainMenu(WoodWindow mainFrame, JackMasterControl master) {
        this.woodWindow = mainFrame;
        this.master = master;
        this.mainActions = new JackMainActions(mainFrame, master);

        // Projekt-Menü
        JMenu projectMenu = new JMenu("Projekt");
        projectMenu.setMnemonic(KeyEvent.VK_P);

        JMenuItem newItem = new JMenuItem("Neu");
        JMenuItem openItem = new JMenuItem("Öffnen...");
        JMenuItem saveItem = new JMenuItem("Speichern");
        JMenuItem saveAsItem = new JMenuItem("Speichern unter...");
        JMenuItem exitItem = new JMenuItem("Beenden");

        exitItem.addActionListener(e -> woodWindow.dispose());
        openItem.addActionListener(e -> openJsonFile());
        saveItem.addActionListener(e -> mainActions.saveActiveFile());
        saveAsItem.addActionListener(e -> mainActions.saveActiveFileAs());

        projectMenu.add(newItem);
        projectMenu.add(openItem);
        projectMenu.add(saveItem);
        projectMenu.add(saveAsItem);
        projectMenu.addSeparator();
        projectMenu.add(exitItem);

        JMenuItem copyItem = new JMenuItem("Copy");
        cutItem = new JMenuItem("Cut");
        pasteItem = new JMenuItem("paste into this node");
        pasteUnderneathItem = new JMenuItem("Paste underneath this node.");

        copyItem.addActionListener(e -> master.fireContentCommand(EDIT_COPY, this));
        cutItem.addActionListener(e -> master.fireContentCommand(EDIT_CUT, this));
        pasteItem.addActionListener(e -> master.fireContentCommand(EDIT_PASTE, this));
        pasteUnderneathItem.addActionListener(e -> master.fireContentCommand(EDIT_PASTE_UNDERNEATH, this));

        projectMenu.addSeparator();
        projectMenu.add(copyItem);
        projectMenu.add(cutItem);
        projectMenu.add(pasteItem);
        projectMenu.add(pasteUnderneathItem);

        // Edit-Menü
        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic(KeyEvent.VK_E);

        addNodeMenu = new JMenu("Node hinzufügen");
        addAnnotationMenu = new JMenu("Annotation hinzufügen");
        deleteNodeItem = new JMenuItem("Node löschen");
        renameNodeItem = new JMenuItem("Node umbenennen");

        addNodeItem = new JMenuItem("Node hinzufügen");
        addAnnotationItem = new JMenuItem("Annotation hinzufügen");
        deleteNodeItem.addActionListener(e -> master.fireContentCommand(EDIT_DELETE_NODE, this));
        renameNodeItem.addActionListener(e -> master.fireContentCommand(EDIT_RENAME_NODE, this));
        addNodeItem.addActionListener(e -> master.fireContentCommand(EDIT_ADD_NODE, this));
        addAnnotationItem.addActionListener(e -> master.fireContentCommand(EDIT_ADD_ANNOTATION, this));

        editMenu.add(addNodeItem);
        editMenu.add(addNodeMenu);
        editMenu.add(addAnnotationItem);
        editMenu.add(addAnnotationMenu);
        editMenu.add(deleteNodeItem);
        editMenu.add(renameNodeItem);
        editMenu.addSeparator();

        JMenuItem reparseItem = new JMenuItem("Re-parse Types");
        reparseItem.addActionListener(e -> triggerReparse());
        editMenu.add(reparseItem);

        JMenu optionsMenu = new JMenu("Options");
        JMenuItem preferencesItem = new JMenuItem("Preferences");
        JCheckBoxMenuItem darkModeItem = new JCheckBoxMenuItem("Dark Mode");

        preferencesItem.addActionListener(e -> openPreferences());
        darkModeItem.addActionListener(e -> woodWindow.setDarkMode(darkModeItem.isSelected()));
        woodWindow.addDarkModeListener(darkModeItem::setSelected);

        optionsMenu.add(preferencesItem);
        optionsMenu.addSeparator();
        optionsMenu.add(darkModeItem);

        // Info-Menü
        JMenu infoMenu = new JMenu("Info");
        infoMenu.setMnemonic(KeyEvent.VK_I);

        JMenuItem aboutItem = new JMenuItem("Über...");
        aboutItem.addActionListener(e
                -> JOptionPane.showMessageDialog(woodWindow,
                        "Tree Editor\n© 2026",
                        "Über",
                        JOptionPane.INFORMATION_MESSAGE
                )
        );
        infoMenu.add(aboutItem);

        add(projectMenu);
        add(editMenu);
        add(optionsMenu);
        add(infoMenu);

        master.addSelectionListener(7, new TreeFocusListener() {
            @Override
            public void onNodeSelected(DefaultMutableTreeNode node, Object trigger, boolean rootSelected) {
                lastSelectedNode = node;
                lastRootSelected = rootSelected;
                if (trigger instanceof TreeFocusComponent treeFocusComponent) {
                    lastSelectedEditor = treeFocusComponent;
                }
                updateMenuEnabledState(rootSelected, node instanceof DefaultMutableTreeNode);
            }

            @Override
            public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
                lastSelectedEditor = editor;
                updateMenuEnabledState(false, true);
            }
        });

        master.getClipboardManager().addClipboardChangeListener(9,
                stashName -> updatePasteEnabled());

        // The parse mode may change without a selection change (mode combo): rebuild the menus when it does.
        master.addParseModeListener(6, (source, newMode)
                -> updateMenuEnabledState(lastRootSelected, lastSelectedNode instanceof DefaultMutableTreeNode));

    }

    private void updateMenuEnabledState(boolean rootSelected, boolean nodeExists) {
        boolean isReadonly = lastSelectedEditor == null || lastSelectedEditor.isReadonly();
        boolean enableCutDelete = !isReadonly && !rootSelected && nodeExists;
        boolean enableAddRename = !isReadonly && nodeExists;

        final EditTree editTree = activeEditTree();
        final EditNodeAbstract selected = selectedData();
        final boolean hard = editTree != null && editTree.getParseMode() == ParseMode.HARD_PARSE
                && editTree.getJsonModelDescriptor() != null;
        HardEditMenuBuilder.populateAddNodeMenu(addNodeMenu, master, editTree, selected);
        HardEditMenuBuilder.populateAddAnnotationMenu(addAnnotationMenu, master, editTree, selected);

        deleteNodeItem.setEnabled(enableCutDelete);
        cutItem.setEnabled(enableCutDelete);
        renameNodeItem.setEnabled(enableAddRename);
        // Soft mode offers the generic adds directly, hard mode only the permissible types as sub menu.
        HardEditMenuBuilder.switchModeItems(addNodeItem, addNodeMenu, hard);
        HardEditMenuBuilder.switchModeItems(addAnnotationItem, addAnnotationMenu, hard);
        final boolean parentAnnotation = canParentAnnotation();
        addNodeItem.setEnabled(enableAddRename);
        addAnnotationItem.setEnabled(enableAddRename && parentAnnotation);
        addNodeMenu.setEnabled(enableAddRename && (!hard || HardEditMenuBuilder.hasAddNodeProposals(editTree, selected)));
        addAnnotationMenu.setEnabled(enableAddRename && parentAnnotation
                && (!hard || HardEditMenuBuilder.hasAddAnnotationProposals(editTree, selected)));

        EditMenuEnablementLogger.logAddMenus(master, "main menu", hard, isReadonly, nodeExists,
                enableAddRename, parentAnnotation, editTree, selected);

        updatePasteEnabled();
    }

    /**
     * Checks whether the last selected node can parent an annotation (owning object or field property).
     *
     * @return true when an annotation may be added to the selection
     */
    private boolean canParentAnnotation() {
        final EditNodeAbstract selected = selectedData();
        return selected != null && selected.canBeParentOfAnnotation();
    }

    /**
     * Returns the edit data of the last selected node.
     *
     * @return the selected EditNodeAbstract, or null
     */
    private EditNodeAbstract selectedData() {
        if (lastSelectedNode instanceof DefaultMutableTreeNode dmtn
                && dmtn.getUserObject() instanceof EditNodeAbstract data) {
            return data;
        }
        return null;
    }

    /**
     * Returns the edit tree of the last selected editor.
     *
     * @return the EditTree of the selection, or null
     */
    private EditTree activeEditTree() {
        return lastSelectedEditor instanceof JackEditTree editTree
                ? editTree.getModel().getEditTree() : null;
    }

    private void updatePasteEnabled() {
        boolean isReadonly = lastSelectedEditor != null && lastSelectedEditor.isReadonly();
        if (isReadonly) {
            pasteItem.setEnabled(false);
            pasteUnderneathItem.setEnabled(false);
            return;
        }

        boolean canPaste = false;
        boolean canPasteUnderneath = false;
        if (lastSelectedNode instanceof DefaultMutableTreeNode dmtn) {
            Object uo = dmtn.getUserObject();
            if (uo instanceof EditNode targetData) {
                canPaste = master.getClipboardManager().canPasteTo(targetData);
            }
            // For paste underneath, check if parent exists and can accept paste
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) dmtn.getParent();
            if (parent != null) {
                Object parentUo = parent.getUserObject();
                if (parentUo instanceof EditNode parentData) {
                    canPasteUnderneath = master.getClipboardManager().canPasteTo(parentData);
                }
            }
        }
        pasteItem.setEnabled(canPaste);
        pasteUnderneathItem.setEnabled(canPasteUnderneath);
    }

    private void openPreferences() {
        woodWindow.openPreferences();
    }

    private void triggerReparse() {
        if (lastSelectedEditor instanceof JackEditTree editTree) {
            EditTree tree = editTree.getModel().getEditTree();
            if (tree != null && tree.isParserRunning()) {
                tree.triggerFullReparse();
                JOptionPane.showMessageDialog(woodWindow,
                        "Re-parsing started. Watch the tree view for the status.",
                        "Re-parse",
                        JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(woodWindow,
                        "No active parser for this editor.",
                        "Re-parse",
                        JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void openJsonFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("JSON Files", "json"));
        fileChooser.setDialogTitle("JSON-Datei öffnen");

        int result = fileChooser.showOpenDialog(woodWindow);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            mainActions.loadJsonFile(selectedFile);
        }
    }

}
