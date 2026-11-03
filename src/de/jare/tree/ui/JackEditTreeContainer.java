/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * [http://www.eclipse.org/legal/epl-v20.html](http://www.eclipse.org/legal/epl-v20.html)
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.control.JackMasterControl;
import java.awt.*;
import javax.swing.*;

/**
 * Container for two JackEditTree instances that can be shown side by side.
 * The checkbox controls whether the right instance is shown.
 */
public class JackEditTreeContainer extends JPanel {
    
    private static final int COLLAPSED_DIVIDER_SIZE = 1;
    private static final int EXPANDED_DIVIDER_SIZE = 8;
    private static final Dimension COLLAPSED_MIN_SIZE = new Dimension(0, 0);
    private static final Dimension EXPANDED_MIN_SIZE = new Dimension(50, 50);
    
    private final JackEditTree leftTree;
    private final JackEditTree rightTree;
    private final JSplitPane splitPane;
    private DiffViewController diffController;
    private boolean syncingToggles;

    /**
     * Creates a container with two JackEditTree instances.
     *
     * @param master the master control for both trees.
     * @param leftRootName the name of the root node for the left tree.
     * @param rightRootName the name of the root node for the right tree.
     * @param propNames optional property names for both trees.
     */
    public JackEditTreeContainer(JackMasterControl master, String leftRootName, String rightRootName, String... propNames) {
        this.leftTree = new JackEditTree(master, leftRootName, propNames);
        this.rightTree = new JackEditTree(master, rightRootName, propNames);
        this.rightTree.setReadonly(true);

        this.leftTree.getLinkCheckBox().addActionListener(
                e -> toggleLinkView(this.leftTree.getLinkCheckBox().isSelected())
        );
        this.rightTree.getLinkCheckBox().addActionListener(
                e -> toggleLinkView(this.rightTree.getLinkCheckBox().isSelected())
        );

        // The diff segment of the right pane mode (concept CONCEPT_TreeDiffView.md): geometry is the role, this
        // container's live left tree is ours, the remembered side freezes as theirs. Selecting Dependents or
        // deselecting Diff closes the diff view.
        this.leftTree.getDiffCheckBox().addActionListener(e -> onDiffToggle(this.leftTree.getDiffCheckBox().isSelected()));
        this.rightTree.getDiffCheckBox().addActionListener(e -> onDiffToggle(this.rightTree.getDiffCheckBox().isSelected()));
        this.leftTree.getRediffButton().addActionListener(e -> getDiffController().rediff());
        this.rightTree.getRediffButton().addActionListener(e -> getDiffController().rediff());
        this.leftTree.getTakeAllButton().addActionListener(e -> takeAllIncoming());
        this.rightTree.getTakeAllButton().addActionListener(e -> takeAllIncoming());
        TreeDiffSides.addChangeListener(this::refreshDiffSegments);

        this.splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftTree, rightTree);
        this.splitPane.setResizeWeight(0.5);
        this.splitPane.setContinuousLayout(true);
        this.splitPane.setOneTouchExpandable(false);

        this.leftTree.setMinimumSize(EXPANDED_MIN_SIZE);
        this.rightTree.setMinimumSize(COLLAPSED_MIN_SIZE);

        setLayout(new BorderLayout());
        add(splitPane, BorderLayout.CENTER);

        toggleLinkView(false);
        refreshDiffSegments();
    }

    /**
     * Handles the Diff segment of the pane mode: selected opens the diff view of this container (ours = this
     * container's left tree, theirs = the remembered side, W3 when ours carries an import state, else W2);
     * deselected closes it and falls back to the dependents view.
     */
    private void onDiffToggle(boolean selected) {
        if (syncingToggles) {
            return;
        }
        if (selected) {
            if (!openDiffFromSegment()) {
                syncingToggles = true;
                leftTree.getDiffCheckBox().setSelected(false);
                rightTree.getDiffCheckBox().setSelected(false);
                syncingToggles = false;
            }
        } else {
            getDiffController().close();
            // close() restores the pane; show the dependents view as the mode after a diff
            toggleLinkView(true);
        }
    }

    /**
     * Opens the diff view from the segment control: this container is ours, the remembered side is theirs.
     *
     * @return true when the view opened; false when no pair exists (nothing marked or the marked root is this
     * container's own root) - the caller reverts the toggle.
     */
    private boolean openDiffFromSegment() {
        final de.jare.jsoncasted.editor.core.EditTree oursTree = leftTree.getModel().getEditTree();
        final de.jare.jsoncasted.editor.core.EditNodeAbstract theirs
                = TreeDiffSides.getRemembered();
        if (oursTree == null || theirs == null || theirs == oursTree.getRoot()) {
            return false;
        }
        final DiffViewController.Kind kind = oursTree.getBaseSnapshot() != null
                ? DiffViewController.Kind.W3 : DiffViewController.Kind.W2;
        if (getDiffController().isOpen()) {
            getDiffController().close();
        }
        getDiffController().open(oursTree, theirs, kind);
        if (getDiffController().isOpen()) {
            // this container just paired with the marked side: the marked side can run the same pair again
            TreeDiffSides.rememberPartner(oursTree.getRoot(), leftTree.getModel());
        }
        return getDiffController().isOpen();
    }

    /**
     * Runs the take-all of the open W3 view and reports the outcome (concept CONCEPT_TreeDiffView.md section 7):
     * the batch applies as one undo step; radiating changes wait for individual picks.
     */
    private void takeAllIncoming() {
        final String result = getDiffController().takeAllIncoming();
        if (result != null) {
            javax.swing.JOptionPane.showMessageDialog(this, result, "Take all incoming",
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Refreshes the Diff segments of both tree headers from the marked side and the view state: enabled when a
     * different tree is marked, selected while the diff view is open, and the tooltip names the pair and kind.
     */
    void refreshDiffSegments() {
        final de.jare.jsoncasted.editor.core.EditNodeAbstract theirs = TreeDiffSides.getRemembered();
        final de.jare.jsoncasted.editor.core.EditTree oursTree
                = leftTree.getModel() != null ? leftTree.getModel().getEditTree() : null;
        final boolean enabled = theirs != null && oursTree != null && theirs != oursTree.getRoot();
        final DiffViewController controller = getDiffController();
        final boolean open = controller.isOpen();
        final boolean w3 = open && controller.getKind() == DiffViewController.Kind.W3;
        String tooltip = "Show the tree diff view in this tab (mark a side in the root popup first)";
        if (enabled) {
            final DiffViewController.Kind kind = oursTree.getBaseSnapshot() != null
                    ? DiffViewController.Kind.W3 : DiffViewController.Kind.W2;
            tooltip = kind.getLabel() + " against " + TreeDiffSides.describe(theirs)
                    + (TreeDiffSides.isRememberedAsLeft() ? " (marked as left)" : " (marked as right)");
        }
        syncingToggles = true;
        for (JackEditTree tree : new JackEditTree[]{leftTree, rightTree}) {
            tree.getDiffCheckBox().setEnabled(enabled);
            tree.getDiffCheckBox().setSelected(open);
            tree.getDiffCheckBox().setToolTipText(tooltip);
            tree.getTakeAllButton().setEnabled(w3);
        }
        syncingToggles = false;
    }

    /**
     * Updates the staleness summary of the open view's header (concept CONCEPT_TreeDiffView.md section 5): the
     * left tree's tick counts its stale marks and hands the numbers over; the right pane header shows the pair
     * description plus "N of M marks outdated" once marks go stale. Re-diff clears the line by itself (fresh marks).
     *
     * @param stale the number of left marks whose node changed since the diff.
     * @param total the total number of left marks.
     */
    void updateDiffStaleness(int stale, int total) {
        if (!getDiffController().isOpen() || getDiffController().getPairText() == null) {
            return;
        }
        final String suffix = stale > 0 ? "  \u2014  " + stale + " of " + total + " marks outdated" : "";
        final String text = getDiffController().getPairText() + suffix;
        if (!text.equals(lastStalenessText)) {
            lastStalenessText = text;
            rightTree.setDiffInfo(text);
        }
    }

    private String lastStalenessText;

    /**
     * Returns the diff view controller of this container, created lazily.
     *
     * @return the controller, never null.
     */
    public DiffViewController getDiffController() {
        if (diffController == null) {
            diffController = new DiffViewController(this);
        }
        return diffController;
    }

    /**
     * Expands the right pane for the diff view without touching the mode checkboxes: the diff mode shows the pane
     * like the dependents view does.
     */
    void showDiffPane() {
        rightTree.setVisible(true);
        rightTree.setMinimumSize(EXPANDED_MIN_SIZE);
        splitPane.setDividerSize(EXPANDED_DIVIDER_SIZE);
        splitPane.setEnabled(true);
        SwingUtilities.invokeLater(() -> {
            splitPane.setResizeWeight(0.5);
            splitPane.setDividerLocation(0.5);
        });
        revalidate();
        repaint();
    }

    /**
     * Enables or disables the side by side view of the two trees.
     *
     * @param enabled when true, the right instance is shown.
     */
    private void toggleLinkView(boolean enabled) {
        // selecting the dependents view is a mode switch: an open diff view closes first
        if (enabled && diffController != null && diffController.isOpen()) {
            diffController.close();
        }
        leftTree.getLinkCheckBox().setSelected(enabled);
        rightTree.getLinkCheckBox().setSelected(enabled);
        
        if (enabled) {
            rightTree.setVisible(true);
            rightTree.setMinimumSize(EXPANDED_MIN_SIZE);
            splitPane.setDividerSize(EXPANDED_DIVIDER_SIZE);
            splitPane.setEnabled(true);
            
            SwingUtilities.invokeLater(() -> {
                splitPane.setResizeWeight(0.5);
                splitPane.setDividerLocation(0.5);
            });
        } else {
            rightTree.setMinimumSize(COLLAPSED_MIN_SIZE);
            splitPane.setDividerSize(COLLAPSED_DIVIDER_SIZE);
            splitPane.setEnabled(false);
            
            SwingUtilities.invokeLater(() -> {
                splitPane.setDividerLocation(1.0);
                splitPane.setResizeWeight(1.0);
            });
            
        }
        
        revalidate();
        repaint();
    }

    /**
     * Returns the left tree.
     *
     * @return the left JackEditTree.
     */
    public JackEditTree getLeftTree() {
        return leftTree;
    }

    /**
     * Returns the right tree.
     *
     * @return the right JackEditTree.
     */
    public JackEditTree getRightTree() {
        return rightTree;
    }

    /**
     * Sets the read-only mode for both trees.
     *
     * @param readonly when true, both trees are read-only.
     */
    public void setReadonly(boolean readonly) {
        leftTree.setReadonly(readonly);
        rightTree.setReadonly(true);
    }

    /**
     * Returns whether the trees are in read-only mode.
     *
     * @return true when the trees are read-only.
     */
    public boolean isReadonly() {
        return leftTree.isReadonly();
    }
}
