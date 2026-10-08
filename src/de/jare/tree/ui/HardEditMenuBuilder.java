/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.HardEditAdvisor;
import de.jare.jsoncasted.editor.core.ParseMode;
import static de.jare.tree.control.listeners.ContentListener.EDIT_ADD_ANNOTATION;
import static de.jare.tree.control.listeners.ContentListener.EDIT_ADD_NODE;
import static de.jare.tree.control.listeners.ContentListener.EDIT_ADD_PREPARED;
import de.jare.tree.control.JackMasterControl;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

/**
 * Builds the add menus of the tree popup and the main menu from the parse mode (parse mode concept, section 4): a
 * single generic item in soft mode, the permissible model types of the {@link HardEditAdvisor} as sub items in
 * hard mode. Existing children are excluded by the advisor; empty proposals leave the caller to disable the menu.
 *
 * @author Janusch Rentenatus
 */
public final class HardEditMenuBuilder {

    private HardEditMenuBuilder() {
    }

    /**
     * Rebuilds the add-node menu for the selection.
     *
     * @param menu the menu to repopulate
     * @param master the master control firing the content commands
     * @param tree the edit tree of the selection, or null
     * @param selected the selected node, or null
     */
    public static void populateAddNodeMenu(JMenu menu, JackMasterControl master, EditTree tree,
            EditNodeAbstract selected) {
        menu.removeAll();
        if (isHardMode(tree)) {
            final List<HardEditAdvisor.ChildProposal> proposals
                    = HardEditAdvisor.permissibleChildren(selected, tree.getJsonModelDescriptor());
            for (HardEditAdvisor.ChildProposal proposal : proposals) {
                menu.add(preparedItem(master, proposal));
            }
            menu.setToolTipText(proposals.isEmpty()
                    ? HardEditAdvisor.explainEmptyChildren(selected, tree.getJsonModelDescriptor())
                    : null);
            return;
        }
        final JMenuItem genericItem = new JMenuItem("new node");
        genericItem.addActionListener(e -> master.fireContentCommand(EDIT_ADD_NODE, menu));
        menu.add(genericItem);
    }

    /**
     * Rebuilds the add-annotation menu for the selection.
     *
     * @param menu the menu to repopulate
     * @param master the master control firing the content commands
     * @param tree the edit tree of the selection, or null
     * @param selected the selected node, or null
     */
    public static void populateAddAnnotationMenu(JMenu menu, JackMasterControl master, EditTree tree,
            EditNodeAbstract selected) {
        menu.removeAll();
        if (isHardMode(tree)) {
            final List<HardEditAdvisor.ChildProposal> proposals
                    = HardEditAdvisor.permissibleAnnotations(selected, tree.getJsonModelDescriptor());
            for (HardEditAdvisor.ChildProposal proposal : proposals) {
                menu.add(preparedItem(master, proposal));
            }
            menu.setToolTipText(proposals.isEmpty()
                    ? HardEditAdvisor.explainEmptyAnnotations(selected, tree.getJsonModelDescriptor())
                    : null);
            return;
        }
        final JMenuItem genericItem = new JMenuItem("annotation (doc)");
        genericItem.addActionListener(e -> master.fireContentCommand(EDIT_ADD_ANNOTATION, menu));
        menu.add(genericItem);
    }

    /**
     * Checks whether the hard mode offers any node child for the selection.
     *
     * @param tree the edit tree of the selection, or null
     * @param selected the selected node, or null
     * @return true when the hard proposals are non-empty
     */
    public static boolean hasAddNodeProposals(EditTree tree, EditNodeAbstract selected) {
        return isHardMode(tree)
                && !HardEditAdvisor.permissibleChildren(selected, tree.getJsonModelDescriptor()).isEmpty();
    }

    /**
     * Checks whether the hard mode offers any annotation for the selection.
     *
     * @param tree the edit tree of the selection, or null
     * @param selected the selected node, or null
     * @return true when the hard proposals are non-empty
     */
    public static boolean hasAddAnnotationProposals(EditTree tree, EditNodeAbstract selected) {
        return isHardMode(tree)
                && !HardEditAdvisor.permissibleAnnotations(selected, tree.getJsonModelDescriptor()).isEmpty();
    }

    /**
     * Shows the generic soft mode item in place of the hard mode sub menu, or vice versa: soft mode offers the generic
     * add directly, hard mode only the permissible types as sub menu (parse mode concept, section 4).
     *
     * @param genericItem the direct menu item of the soft mode
     * @param hardMenu the sub menu of the hard mode
     * @param hardMode true when the selection is in hard parse mode
     */
    public static void switchModeItems(JMenuItem genericItem, JMenu hardMenu, boolean hardMode) {
        genericItem.setVisible(!hardMode);
        hardMenu.setVisible(hardMode);
        // temporary debug aid: append the active parse mode to the add menu labels
        genericItem.setText(baseLabel(genericItem.getText()) + " (soft)");
        hardMenu.setText(baseLabel(hardMenu.getText()) + " (hard)");
    }

    /**
     * Strips the temporary mode suffix from an add menu label.
     *
     * @param label the current label, with or without mode suffix
     * @return the label without the mode suffix
     */
    private static String baseLabel(String label) {
        if (label != null && (label.endsWith(" (soft)") || label.endsWith(" (hard)"))) {
            return label.substring(0, label.length() - " (soft)".length());
        }
        return label;
    }

    private static JMenuItem preparedItem(JackMasterControl master, HardEditAdvisor.ChildProposal proposal) {
        final JMenuItem item = new JMenuItem(proposal.getLabel());
        item.addActionListener(e -> master.fireContentCommand(EDIT_ADD_PREPARED, proposal.getPreparedChild()));
        return item;
    }

    private static boolean isHardMode(EditTree tree) {
        return tree != null && tree.getParseMode() == ParseMode.HARD_PARSE
                && tree.getJsonModelDescriptor() != null;
    }
}
