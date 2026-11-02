/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.clipboard.ClipboardManager;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.ParseMode;
import de.jare.tree.control.JackMasterControl;
import static de.jare.tree.control.listeners.ContentListener.EDIT_ADD_ANNOTATION;
import static de.jare.tree.control.listeners.ContentListener.EDIT_ADD_NODE;
import static de.jare.tree.control.listeners.ContentListener.EDIT_COPY;
import static de.jare.tree.control.listeners.ContentListener.EDIT_CUT;
import static de.jare.tree.control.listeners.ContentListener.EDIT_DELETE_NODE;
import static de.jare.tree.control.listeners.ContentListener.EDIT_PASTE;
import static de.jare.tree.control.listeners.ContentListener.EDIT_PASTE_UNDERNEATH;
import static de.jare.tree.control.listeners.ContentListener.EDIT_RENAME_NODE;
import de.jare.tree.control.listeners.ParseModeListener;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import java.awt.Component;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

public class JackEditPopup extends JPopupMenu {

    private final JMenuItem pasteItem;
    private final JMenuItem pasteUnderneathItem;
    private final JMenuItem deleteNodeItem;
    private final JMenuItem cutItem;
    private final JMenu addNodeMenu;
    private final JMenu addAnnotationMenu;
    private final JMenuItem addNodeItem;
    private final JMenuItem addAnnotationItem;
    private final JMenuItem renameNodeItem;
    private final JMenuItem diffLeftItem;
    private final JMenuItem diffRightItem;
    private final JMenuItem diffRunItem;
    private boolean lastRootSelected;
    private Component lastInvoker;
    private final JackMasterControl master;
    private Object lastSelectedNode;
    private TreeFocusComponent lastSelectedEditor;
    // The orator of the master control holds its listeners via weak references
    // only: the fields keep the listeners reachable, otherwise the garbage
    // collector silently deregisters them and the menu state freezes.
    private final TreeFocusListener selectionListener;
    private final ClipboardManager.ClipboardChangeListener clipboardListener;
    private final ParseModeListener parseModeListener;

    public JackEditPopup(JackMasterControl master) {
        this.master = master;
        addNodeMenu = new JMenu("Add Node");
        addAnnotationMenu = new JMenu("Add Annotation");
        deleteNodeItem = new JMenuItem("Delete Node");
        renameNodeItem = new JMenuItem("Rename Node");

        addNodeItem = new JMenuItem("Add Node");
        addAnnotationItem = new JMenuItem("Add Annotation");
        deleteNodeItem.addActionListener(e -> master.fireContentCommand(EDIT_DELETE_NODE, this));
        renameNodeItem.addActionListener(e -> master.fireContentCommand(EDIT_RENAME_NODE, this));
        addNodeItem.addActionListener(e -> master.fireContentCommand(EDIT_ADD_NODE, this));
        addAnnotationItem.addActionListener(e -> master.fireContentCommand(EDIT_ADD_ANNOTATION, this));

        add(addNodeItem);
        add(addNodeMenu);
        add(addAnnotationItem);
        add(addAnnotationMenu);
        add(deleteNodeItem);
        add(renameNodeItem);

        // Hard mode changes the offers with every selection and mode switch:
        // rebuild the dynamic sub menus each time the popup opens.
        addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                lastInvoker = getInvoker();
                updateMenuEnabledState(lastRootSelected, lastSelectedNode != null);
            }

            @Override
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                // NoOp
            }

            @Override
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                // NoOp
            }
        });

        JMenuItem copyItem = new JMenuItem("Copy");
        cutItem = new JMenuItem("Cut");
        pasteItem = new JMenuItem("paste into this node");
        pasteUnderneathItem = new JMenuItem("Paste underneath this node.");

        copyItem.addActionListener(e -> master.fireContentCommand(EDIT_COPY, this));
        cutItem.addActionListener(e -> master.fireContentCommand(EDIT_CUT, this));
        pasteItem.addActionListener(e -> master.fireContentCommand(EDIT_PASTE, this));
        pasteUnderneathItem.addActionListener(e -> master.fireContentCommand(EDIT_PASTE_UNDERNEATH, this));

        addSeparator();
        add(copyItem);
        add(cutItem);
        add(pasteItem);
        add(pasteUnderneathItem);

        // Tree diff (U4 debug feature, see doc/TREE_DIFF.md in jsonCasted): mark one root as LEFT or RIGHT, then
        // "Diff: with ..." on any other root runs the diff immediately.
        addSeparator();
        diffLeftItem = new JMenuItem("Diff: mark as left side");
        diffRightItem = new JMenuItem("Diff: mark as right side");
        diffRunItem = new JMenuItem("Diff (mark a side first)");
        diffLeftItem.addActionListener(e -> rememberDiffSide(true));
        diffRightItem.addActionListener(e -> rememberDiffSide(false));
        diffRunItem.addActionListener(e -> runDiff());
        add(diffLeftItem);
        add(diffRightItem);
        add(diffRunItem);

        selectionListener = new TreeFocusListener() {
            @Override
            public void onNodeSelected(DefaultMutableTreeNode node, Object trigger, boolean rootSelected) {
                lastSelectedNode = node;
                lastRootSelected = rootSelected;
                lastSelectedEditor = (trigger instanceof TreeFocusComponent) ? (TreeFocusComponent) trigger : null;
                updateMenuEnabledState(rootSelected, node != null);
            }

            @Override
            public void onEditorSelected(TreeFocusComponent editor, Object trigger) {
                lastSelectedEditor = editor;
                updateMenuEnabledState(false, true);
            }
        };
        master.addSelectionListener(8, selectionListener);

        clipboardListener = stashName -> updatePasteEnabled();
        master.getClipboardManager().addClipboardChangeListener(9, clipboardListener);

        // The parse mode may change without a selection change (mode combo): rebuild the menus when it does.
        parseModeListener = (source, newMode)
                -> updateMenuEnabledState(lastRootSelected, lastSelectedNode != null);
        master.addParseModeListener(6, parseModeListener);
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
        addAnnotationMenu.setEnabled(!isReadonly && parentAnnotation
                && (!hard || HardEditMenuBuilder.hasAddAnnotationProposals(editTree, selected)));

        EditMenuEnablementLogger.logAddMenus(master, "edit popup", hard, isReadonly, nodeExists,
                enableAddRename, parentAnnotation, editTree, selected);

        updateDiffItems(rootSelected, selected);

        updatePasteEnabled();
    }

    /**
     * Refreshes the tree diff items: marking is offered on the root only (always exactly one tree carries the LEFT
     * or RIGHT flag); when a side is marked, "Diff: with ..." runs immediately against the clicked root.
     *
     * @param rootSelected whether the root is selected
     * @param selected the selected node data, or null
     */
    private void updateDiffItems(boolean rootSelected, EditNodeAbstract selected) {
        final boolean atRoot = rootSelected && selected != null;
        diffLeftItem.setEnabled(atRoot);
        diffRightItem.setEnabled(atRoot);
        diffLeftItem.setText("Diff: mark as left side");
        diffRightItem.setText("Diff: mark as right side");

        final EditNodeAbstract marked = TreeDiffSides.getRemembered();
        final boolean ready = TreeDiffSides.hasRemembered() && atRoot && marked != selected;
        diffRunItem.setEnabled(ready);
        if (!TreeDiffSides.hasRemembered()) {
            diffRunItem.setText("Diff (mark a side first)");
        } else {
            final String desc = TreeDiffSides.describe(marked)
                    + (TreeDiffSides.isRememberedAsLeft() ? " (as left)" : " (as right)");
            diffRunItem.setText(marked == selected
                    ? "Diff: with " + desc + " — this is the marked side"
                    : "Diff: with " + desc);
        }
    }

    /**
     * Marks the selected root as one diff side, replacing any previously marked side.
     *
     * @param asLeft true for the LEFT flag, false for the RIGHT flag
     */
    private void rememberDiffSide(boolean asLeft) {
        final EditNodeAbstract selected = selectedData();
        if (selected == null) {
            return;
        }
        TreeDiffSides.remember(selected, asLeft);
        updateMenuEnabledState(lastRootSelected, lastSelectedNode != null);
    }

    /**
     * Runs the diff of the marked side and the clicked root immediately and shows the report dialog. The marked
     * side keeps its flag, so it can be compared against several editors in a row.
     */
    private void runDiff() {
        final EditNodeAbstract marked = TreeDiffSides.getRemembered();
        final EditNodeAbstract current = selectedData();
        if (marked == null || current == null || marked == current) {
            return;
        }
        final EditNodeAbstract left = TreeDiffSides.isRememberedAsLeft() ? marked : current;
        final EditNodeAbstract right = TreeDiffSides.isRememberedAsLeft() ? current : marked;
        final Window owner = lastInvoker != null
                ? javax.swing.SwingUtilities.getWindowAncestor(lastInvoker) : null;
        new TreeDiffDialog(owner, left, right).setVisible(true);
    }

    /**
     * Checks whether the currently selected node can parent an annotation (owning object or field property).
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

    /**
     * Helper method to register the popup on a JTree.
     *
     * @param tree
     * @param popup
     */
    public static void installOn(JTree tree, JackEditPopup popup) {

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                handlePopupTrigger(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                handlePopupTrigger(event);
            }

            private void handlePopupTrigger(MouseEvent event) {
                if (!event.isPopupTrigger()) {
                    return;
                }
                int x = event.getX();
                int y = event.getY();
                JTree tree = (JTree) event.getSource();
                TreePath path = tree.getPathForLocation(x, y);
                if (path == null) {
                    return;
                }

                TreePath[] selectedPaths = tree.getSelectionPaths();
                boolean alreadySelected = false;
                if (selectedPaths != null) {
                    for (TreePath selPath : selectedPaths) {
                        if (selPath.equals(path)) {
                            alreadySelected = true;
                            break;
                        }
                    }
                }

                // only when the clicked node is NOT yet selected,
                // we make a single selection – otherwise the multi selection is preserved.
                if (!alreadySelected) {
                    tree.setSelectionPath(path);
                }

                // show the popup 
                popup.show(tree, x, y);
            }
        });
    }

}
