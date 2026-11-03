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
import de.jare.tree.control.model.JackTreeModel;
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
    private final JMenuItem diffView2Item;
    private final JMenuItem diffView3Item;
    private final JMenuItem diffPickItem;
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

        // Tree diff (U1/U4, see doc/TREE_DIFF.md in jsonCasted): mark one root as LEFT or RIGHT, then "W2:" / "W3:"
        // open the diff view directly (concept CONCEPT_TreeDiffView.md) - one entry per pair kind, view first.
        // The precise op list of the open pair is the Report button of the cherry banner; the dialogs have no
        // popup entries of their own anymore.
        addSeparator();
        diffLeftItem = new JMenuItem("Diff: mark as left side");
        diffRightItem = new JMenuItem("Diff: mark as right side");
        diffView2Item = new JMenuItem("W2 (mark a side first)");
        diffView3Item = new JMenuItem("W3 (mark a side first)");
        diffLeftItem.addActionListener(e -> rememberDiffSide(true));
        diffRightItem.addActionListener(e -> rememberDiffSide(false));
        diffView2Item.addActionListener(e -> runDiffView(false));
        diffView3Item.addActionListener(e -> runDiffView(true));
        diffPickItem = new JMenuItem("Diff view: take this change");
        diffPickItem.addActionListener(e -> pickDiffChange());
        add(diffLeftItem);
        add(diffRightItem);
        add(diffView2Item);
        add(diffView3Item);
        add(diffPickItem);

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

        updateDiffItems();

        updatePasteEnabled();
    }

    /**
     * Refreshes the tree diff items. The target comes from the tree the popup opened on (the invoker), not from the
     * broadcast selection: the active editor is usually the left tree of the tab, so a click into the right tree
     * never reaches the selection listeners and the broadcast state stays stale — the diff items would be disabled
     * although the user just clicked a root.
     */
    private void updateDiffItems() {
        final EditNodeAbstract clicked = invokerNode();
        final EditNodeAbstract selected = clicked != null ? clicked : selectedData();
        final boolean atRoot = clicked != null
                ? invokerRootSelected()
                : lastRootSelected && selected != null;
        final boolean onFrozenCopy = invokerIsFrozenCopy();
        diffLeftItem.setEnabled(atRoot && !onFrozenCopy);
        diffRightItem.setEnabled(atRoot && !onFrozenCopy);
        diffLeftItem.setText("Diff: mark as left side");
        diffRightItem.setText("Diff: mark as right side");
        diffLeftItem.setToolTipText(onFrozenCopy
                ? "The frozen copy of the diff view cannot be a diff side" : null);
        diffRightItem.setToolTipText(onFrozenCopy
                ? "The frozen copy of the diff view cannot be a diff side" : null);

        final EditNodeAbstract marked = TreeDiffSides.getRemembered();
        final boolean hasMarked = TreeDiffSides.hasRemembered();
        // The pair counterpart: normally the marked side; on the marked side itself the last partner - a diff or
        // view that ran before makes the same pair runnable again from the marked tab too.
        final boolean onMarked = hasMarked && selected == marked;
        final EditNodeAbstract counterpart = onMarked ? TreeDiffSides.getPartner() : marked;
        final boolean ready = hasMarked && atRoot && counterpart != null && !onFrozenCopy;
        final boolean baseReady = ready && threeWayBaseAvailable();
        diffView2Item.setEnabled(ready);
        diffView3Item.setEnabled(baseReady);
        if (!hasMarked) {
            diffView2Item.setText("W2 (mark a side first)");
            diffView3Item.setText("W3 (mark a side first)");
        } else if (counterpart == null) {
            diffView2Item.setText("W2: this is the marked side (run from another tab first)");
            diffView3Item.setText("W3: this is the marked side (run from another tab first)");
        } else {
            // the counterpart's role is the opposite of the marked side's flag
            final String desc = TreeDiffSides.describe(counterpart)
                    + ((TreeDiffSides.isRememberedAsLeft() != onMarked) ? " (as left)" : " (as right)");
            diffView2Item.setText("W2: with " + desc);
            if (!threeWayBaseAvailable()) {
                diffView3Item.setText("W3: with " + desc + " (ours side has no import state)");
            } else {
                diffView3Item.setText("W3: with " + desc);
            }
        }
        updatePickItem();
    }

    /**
     * Refreshes the cherry-pick item of the open diff view (concept CONCEPT_TreeDiffView.md section 7): enabled on a
     * marked node of the right pane (the frozen theirs copy) when the pick is possible; the tooltip says why not
     * when it is not.
     */
    private void updatePickItem() {
        final DiffViewController controller = invokerDiffController();
        final EditNodeAbstract clicked = invokerNode();
        DiffMark mark = null;
        if (controller != null && clicked != null) {
            mark = controller.getRightMark(clicked);
        }
        final String hint = controller != null ? controller.pickHint(mark) : null;
        final boolean pickable = controller != null && clicked != null && hint == null;
        diffPickItem.setEnabled(pickable);
        diffPickItem.setToolTipText(hint != null ? hint
                : "Apply this theirs change into the ours tree (one undo step)");
        if (mark != null && mark.isRadiating()) {
            diffPickItem.setText("Diff view: take this change (radiating)");
        } else {
            diffPickItem.setText("Diff view: take this change");
        }
    }

    /**
     * Returns the diff view controller of the container the popup was opened in, or null.
     *
     * @return the controller of the invoker's container, or null.
     */
    private DiffViewController invokerDiffController() {
        if (lastInvoker == null) {
            return null;
        }
        final Object ancestor = javax.swing.SwingUtilities.getAncestorOfClass(
                JackEditTreeContainer.class, lastInvoker);
        return ancestor instanceof JackEditTreeContainer container ? container.getDiffController() : null;
    }

    /**
     * Takes the change of the clicked copy node into the ours tree (concept section 7). Radiating picks confirm
     * first - the dependent lookup (R10) is not built, the badge alone must say what radiates.
     */
    private void pickDiffChange() {
        final DiffViewController controller = invokerDiffController();
        final EditNodeAbstract clicked = invokerNode();
        if (controller == null || clicked == null) {
            return;
        }
        final DiffMark mark = controller.getRightMark(clicked);
        if (mark == null || controller.pickHint(mark) != null) {
            return;
        }
        if (mark.isRadiating()) {
            final Window owner = lastInvoker != null
                    ? javax.swing.SwingUtilities.getWindowAncestor(lastInvoker) : null;
            final int answer = JOptionPane.showConfirmDialog(owner,
                    "This change radiates into dependent resources (wood id / link target).\nApply it anyway?",
                    "Radiating change", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }
        final String result = controller.pick(clicked);
        if (result != null) {
            final Window owner = lastInvoker != null
                    ? javax.swing.SwingUtilities.getWindowAncestor(lastInvoker) : null;
            JOptionPane.showMessageDialog(owner, result, "Diff view: take this change",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Tells whether the popup was opened on the frozen copy of an open diff view (the right pane of the ours
     * tab). The copy is a snapshot, not a live tree - it must not become a diff side, its roots would pair a
     * detached state and later degenerate into comparing a tree with its own copy.
     *
     * @return true when the invoker is the frozen theirs copy.
     */
    private boolean invokerIsFrozenCopy() {
        if (lastInvoker == null) {
            return false;
        }
        final Object containerAncestor = javax.swing.SwingUtilities.getAncestorOfClass(
                JackEditTreeContainer.class, lastInvoker);
        if (!(containerAncestor instanceof JackEditTreeContainer container)
                || !container.getDiffController().isOpen()) {
            return false;
        }
        final Object treeAncestor = javax.swing.SwingUtilities.getAncestorOfClass(
                JackEditTree.class, lastInvoker);
        return treeAncestor == container.getRightTree();
    }

    /**
     * Returns the node the popup was opened on, derived from the invoker tree's selection — installOn selects the
     * clicked path before showing the popup, so this is exactly the right-clicked node.
     *
     * @return the clicked node, or null when the invoker is unknown.
     */
    private EditNodeAbstract invokerNode() {
        if (lastInvoker instanceof JTree tree
                && tree.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode dmtn
                && dmtn.getUserObject() instanceof EditNodeAbstract data) {
            return data;
        }
        return null;
    }

    /**
     * Tells whether the node the popup was opened on is the root of its tree.
     *
     * @return true when the clicked node is a root.
     */
    private boolean invokerRootSelected() {
        if (lastInvoker instanceof JTree tree
                && tree.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode dmtn) {
            return dmtn.getParent() == null;
        }
        return false;
    }

    /**
     * Returns the edit tree of the popup invoker — the tree the user actually clicked, which may differ from the
     * active editor (the left tree of the tab).
     *
     * @return the clicked tree, or null when unknown.
     */
    private EditTree invokerEditTree() {
        final JackTreeModel model = invokerModel();
        return model == null ? null : model.getEditTree();
    }

    /**
     * Returns the tree model of the popup invoker — the merge books its transaction on the clicked tree's history,
     * not on the active editor's.
     *
     * @return the clicked tree model, or null when unknown.
     */
    private JackTreeModel invokerModel() {
        if (lastInvoker == null) {
            return null;
        }
        final java.awt.Container ancestor
                = javax.swing.SwingUtilities.getAncestorOfClass(JackEditTree.class, lastInvoker);
        return ancestor instanceof JackEditTree editor ? editor.getModel() : null;
    }

    /**
     * The diff actions act on the tree the popup opened on; the broadcast state is only the fallback.
     *
     * @return the clicked node, or the last selected node.
     */
    private EditNodeAbstract diffTargetNode() {
        final EditNodeAbstract clicked = invokerNode();
        return clicked != null ? clicked : selectedData();
    }

    /**
     * The diff actions act on the tree the popup opened on; the broadcast state is only the fallback.
     *
     * @return the clicked tree, or the active editor's tree.
     */
    private EditTree diffTargetTree() {
        final EditTree clickedTree = invokerEditTree();
        return clickedTree != null ? clickedTree : activeEditTree();
    }

    /**
     * Marks the selected root as one diff side, replacing any previously marked side.
     *
     * @param asLeft true for the LEFT flag (the ours role), false for the RIGHT flag (the theirs role)
     */
    private void rememberDiffSide(boolean asLeft) {
        final EditNodeAbstract selected = diffTargetNode();
        if (selected == null) {
            return;
        }
        TreeDiffSides.remember(selected, invokerModel(), asLeft);
        updateMenuEnabledState(lastRootSelected, lastSelectedNode != null);
    }

    /**
     * Returns the pair root of the clicked root: the clicked root itself, or — when the popup opened on the marked
     * root — the marked side's last partner (the same pair again, runnable from the marked tab too). Null when the
     * pair does not exist.
     *
     * @return the pair root, or null.
     */
    private EditNodeAbstract pairRoot() {
        final EditNodeAbstract marked = TreeDiffSides.getRemembered();
        final EditNodeAbstract current = diffTargetNode();
        if (marked == null || current == null) {
            return null;
        }
        return current == marked ? TreeDiffSides.getPartner() : current;
    }

    /**
     * Returns the ours tree of the pair (the LEFT role): the marked side's tree when the flag is LEFT, else
     * the counterpart's tree — the clicked tab normally, the last partner on the marked tab itself.
     *
     * @return the ours tree, or null when the pair does not exist.
     */
    private EditTree threeWayOursTree() {
        final EditNodeAbstract marked = TreeDiffSides.getRemembered();
        final EditNodeAbstract clicked = diffTargetNode();
        if (marked == null || clicked == null) {
            return null;
        }
        if (TreeDiffSides.isRememberedAsLeft()) {
            return TreeDiffSides.getRememberedTree();
        }
        return clicked == marked ? TreeDiffSides.getPartnerTree() : diffTargetTree();
    }

    /**
     * Opens the tree diff view (concept CONCEPT_TreeDiffView.md): the view opens in the ours tab, the right pane of
     * that tab freezes theirs as a deep copy and both panes carry marks. The roles follow the flag: the LEFT flag is
     * ours, the RIGHT flag freezes as theirs.
     *
     * @param threeWay true for the W3 view (merge plan marks, cherry-pickable), false for W2 (comparison marks).
     */
    private void runDiffView(boolean threeWay) {
        final EditNodeAbstract marked = TreeDiffSides.getRemembered();
        final EditNodeAbstract clicked = diffTargetNode();
        final EditNodeAbstract other = pairRoot();
        if (marked == null || clicked == null || other == null) {
            return;
        }
        if (threeWay && !threeWayBaseAvailable()) {
            return;
        }
        final EditTree oursTree = threeWayOursTree();
        final EditNodeAbstract theirsRoot = TreeDiffSides.isRememberedAsLeft() ? other : marked;
        if (oursTree == null || oursTree.getRoot() == null || lastInvoker == null) {
            return;
        }
        if (clicked != marked) {
            TreeDiffSides.rememberPartner(clicked, invokerModel());
        }
        final JackEditTreeContainer oursContainer
                = DiffViewController.findOursContainer(oursTree.getRoot(), lastInvoker);
        if (oursContainer == null) {
            return;
        }
        DiffViewController.showContainerTab(oursContainer);
        oursContainer.getDiffController().open(oursTree, theirsRoot,
                threeWay ? DiffViewController.Kind.W3 : DiffViewController.Kind.W2);
    }

    /**
     * Tells whether the 3-way diff can run: the ours side (the LEFT role) must carry an import state as base.
     *
     * @return true when the base is available
     */
    private boolean threeWayBaseAvailable() {
        final EditTree oursTree = threeWayOursTree();
        return oursTree != null && oursTree.getBaseSnapshot() != null;
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
