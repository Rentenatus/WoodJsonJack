/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * [http://www.eclipse.org/legal/epl-v20.html](http://www.eclipse.org/legal/epl-v20.html)
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.control.JackMasterControl;
import de.jare.tree.settings.WoodSettings;
import de.jare.tree.settings.theme.AScheme;
import java.awt.*;
import javax.swing.*;

/**
 * Container for two JackEditTree instances that can be shown side by side.
 * The checkbox controls whether the right instance is shown.
 */
public class JackEditTreeContainer extends JPanel {

    private static final int COLLAPSED_DIVIDER_SIZE = 1;
    private static final int EXPANDED_DIVIDER_SIZE = 8;
    /** The split divider in diff mode: the pair reads as ONE merged view, not two glued trees. */
    private static final int DIFF_DIVIDER_SIZE = 2;
    private static final Dimension COLLAPSED_MIN_SIZE = new Dimension(0, 0);
    private static final Dimension EXPANDED_MIN_SIZE = new Dimension(50, 50);
    /**
     * The size this container may demand before the tab scroll pane switches on its fallback scroll bars: the
     * trees scroll in their own panes, the outer bar is only for genuinely small windows. The natural preferred
     * size would be driven by the widest tree row and ALL rows together - the bar would never go away.
     */
    private static final Dimension PREFERRED_TAB_SIZE = new Dimension(1000, 620);

    private final JackEditTree leftTree;
    private final JackEditTree rightTree;
    private final JSplitPane splitPane;
    private final JPanel diffBanner;
    private final JLabel bannerKindLabel;
    private final JLabel bannerPairLabel;
    private final JButton bannerReportButton;
    private final JButton bannerTakeAllButton;
    private final JButton bannerRediffButton;
    private final JButton bannerCloseButton;
    private DiffViewController diffController;
    private boolean syncingToggles;
    private String lastStalenessText;

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
                e -> { if (!syncingToggles) toggleLinkView(this.leftTree.getLinkCheckBox().isSelected()); }
        );
        this.rightTree.getLinkCheckBox().addActionListener(
                e -> { if (!syncingToggles) toggleLinkView(this.rightTree.getLinkCheckBox().isSelected()); }
        );

        // The diff segment of the right pane mode (concept CONCEPT_TreeDiffView.md): geometry is the role, this
        // container's live left tree is ours, the remembered side freezes as theirs. Selecting Dependents or
        // deselecting Diff closes the diff view.
        this.leftTree.getDiffCheckBox().addActionListener(e -> onDiffToggle(this.leftTree.getDiffCheckBox().isSelected()));
        this.rightTree.getDiffCheckBox().addActionListener(e -> onDiffToggle(this.rightTree.getDiffCheckBox().isSelected()));
        TreeDiffSides.addChangeListener(this::refreshDiffSegments);

        // The cherry red banner of the diff view (concept section 6/7): the mode surface above the split pane -
        // kind badge, pair description with staleness summary and the diff actions (report, take-all, re-diff,
        // close) live here while the view is open; the tree headers keep only what is per-tree. The Report button
        // is the one entry to the list dialogs: the popup opens the view directly, the dialogs are its list side.
        bannerKindLabel = new JLabel();
        bannerKindLabel.setFont(bannerKindLabel.getFont().deriveFont(Font.BOLD));
        bannerPairLabel = new JLabel();
        bannerPairLabel.setFont(bannerPairLabel.getFont().deriveFont(Font.BOLD));
        bannerReportButton = new JButton("Report...");
        bannerReportButton.setToolTipText("Show the delta ops of this pair as a precise list (report dialog)");
        bannerTakeAllButton = new JButton("Take all incoming");
        bannerTakeAllButton.setToolTipText("Apply all incoming theirs changes as one undo step");
        bannerRediffButton = new JButton("Re-diff");
        bannerRediffButton.setToolTipText("Run the same pair again with a fresh copy and fresh marks");
        bannerCloseButton = new JButton("x");
        bannerCloseButton.setToolTipText("Close the diff view and switch back to the dependents view");
        bannerReportButton.addActionListener(e -> getDiffController().showReport(
                SwingUtilities.getWindowAncestor(this)));
        bannerTakeAllButton.addActionListener(e -> takeAllIncoming());
        bannerRediffButton.addActionListener(e -> getDiffController().rediff());
        bannerCloseButton.addActionListener(e -> {
            getDiffController().close();
            toggleLinkView(true);
        });
        final JPanel bannerWest = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        bannerWest.setOpaque(false);
        bannerWest.add(bannerKindLabel);
        bannerWest.add(bannerPairLabel);
        final JPanel bannerEast = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        bannerEast.setOpaque(false);
        bannerEast.add(bannerReportButton);
        bannerEast.add(bannerTakeAllButton);
        bannerEast.add(bannerRediffButton);
        bannerEast.add(bannerCloseButton);
        diffBanner = new JPanel(new BorderLayout());
        diffBanner.add(bannerWest, BorderLayout.WEST);
        diffBanner.add(bannerEast, BorderLayout.EAST);
        diffBanner.setVisible(false);

        this.splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftTree, rightTree);
        this.splitPane.setResizeWeight(0.5);
        this.splitPane.setContinuousLayout(true);
        this.splitPane.setOneTouchExpandable(false);

        this.leftTree.setMinimumSize(EXPANDED_MIN_SIZE);
        this.rightTree.setMinimumSize(COLLAPSED_MIN_SIZE);

        setLayout(new BorderLayout());
        add(diffBanner, BorderLayout.NORTH);
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
        }
        syncingToggles = false;
        refreshDiffBanner();
    }

    /**
     * Refreshes the cherry red banner from the controller: visible while the diff view is open, with the kind
     * badge, the pair description plus staleness summary and the diff actions (report in both kinds, take-all
     * only in W3).
     */
    private void refreshDiffBanner() {
        final DiffViewController controller = getDiffController();
        if (!controller.isOpen()) {
            diffBanner.setVisible(false);
            revalidate();
            return;
        }
        applyBannerColors();
        bannerKindLabel.setText(" " + controller.getKind().getBadge() + " ");
        final int stale = controller.countStaleLeftMarks();
        final String suffix = stale > 0
                ? "  \u2014  " + stale + " of " + controller.getLeftMarkCount() + " marks outdated" : "";
        final String text = (controller.getPairText() == null ? "" : controller.getPairText()) + suffix;
        if (!text.equals(lastStalenessText)) {
            lastStalenessText = text;
            bannerPairLabel.setText(text);
        }
        bannerTakeAllButton.setEnabled(controller.getKind() == DiffViewController.Kind.W3);
        if (!diffBanner.isVisible()) {
            diffBanner.setVisible(true);
            revalidate();
        }
    }

    /**
     * Applies the banner theme colors: dark cherry red with white text in BOTH schemes - the banner does not
     * follow the system colors (theme keys back.diff.banner / fore.diff.banner.text carry identical values).
     */
    private void applyBannerColors() {
        final String prefix = WoodSettings.INSTANCE.getColorPrefix();
        final Color back = WoodSettings.INSTANCE.getShownTheme().getColor(prefix + AScheme.BACK_DIFF_BANNER);
        final Color text = WoodSettings.INSTANCE.getShownTheme().getColor(prefix + AScheme.FORE_DIFF_BANNER_TEXT);
        if (back != null) {
            diffBanner.setBackground(back);
        }
        if (text != null) {
            bannerKindLabel.setForeground(text);
            bannerPairLabel.setForeground(text);
        }
    }

    /**
     * Updates the staleness summary of the open view's banner (concept CONCEPT_TreeDiffView.md section 5): the
     * left tree's tick counts its stale marks and hands the numbers over; the banner recomputes its line from the
     * controller state. Re-diff clears the line by itself (fresh marks).
     *
     * @param stale the number of left marks whose node changed since the diff.
     * @param total the total number of left marks.
     */
    void updateDiffStaleness(int stale, int total) {
        if (getDiffController().isOpen()) {
            refreshDiffBanner();
        }
    }

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
     * Expands the right pane for the diff view without firing a mode switch: the diff mode shows the pane like the
     * dependents view does, but with the merged thin divider - the pair reads as ONE view. The link checkbox means
     * "right pane visible" (either mode), so it is checked here - under the sync guard, otherwise its listener
     * would treat the open diff as a switch back to dependents.
     */
    void showDiffPane() {
        syncingToggles = true;
        leftTree.getLinkCheckBox().setSelected(true);
        rightTree.getLinkCheckBox().setSelected(true);
        syncingToggles = false;
        rightTree.setVisible(true);
        rightTree.setMinimumSize(EXPANDED_MIN_SIZE);
        splitPane.setDividerSize(DIFF_DIVIDER_SIZE);
        splitPane.setEnabled(true);
        SwingUtilities.invokeLater(() -> {
            splitPane.setResizeWeight(0.5);
            splitPane.setDividerLocation(0.5);
        });
        revalidate();
        repaint();
    }

    /**
     * Enables or disables the side by side view of the two trees. The checkbox is pure visibility: while a diff
     * view is open, unchecking only hides the right pane - the diff (copy, marks, banner) survives and comes back
     * unchanged on re-check. The diff itself ends only through the Diff checkbox or the banner's x.
     *
     * @param enabled when true, the right instance is shown.
     */
    private void toggleLinkView(boolean enabled) {
        leftTree.getLinkCheckBox().setSelected(enabled);
        rightTree.getLinkCheckBox().setSelected(enabled);

        if (diffController != null && diffController.isOpen()) {
            if (enabled) {
                // the right pane reappears with the frozen copy, the thin divider included
                showDiffPane();
            } else {
                collapseRightPane();
            }
            revalidate();
            repaint();
            return;
        }

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
            collapseRightPane();
        }

        revalidate();
        repaint();
    }

    /**
     * Collapses the right pane: minimum size zero, the thin dead divider, all width to the left tree.
     */
    private void collapseRightPane() {
        rightTree.setMinimumSize(COLLAPSED_MIN_SIZE);
        splitPane.setDividerSize(COLLAPSED_DIVIDER_SIZE);
        splitPane.setEnabled(false);

        SwingUtilities.invokeLater(() -> {
            splitPane.setDividerLocation(1.0);
            splitPane.setResizeWeight(1.0);
        });
    }

    /**
     * Caps the demanded size at {@link #PREFERRED_TAB_SIZE}: the tab scroll pane compares this with its viewport -
     * above the cap the fallback scroll bars appear, below the container simply fills the window.
     *
     * @return the capped preferred size, never null.
     */
    @Override
    public Dimension getPreferredSize() {
        final Dimension natural = super.getPreferredSize();
        return new Dimension(
                Math.min(natural.width, PREFERRED_TAB_SIZE.width),
                Math.min(natural.height, PREFERRED_TAB_SIZE.height));
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
