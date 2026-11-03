/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.diff.ConflictReason;
import de.jare.jsoncasted.diff.ConflictRecord;
import de.jare.jsoncasted.diff.DeltaOp;
import de.jare.jsoncasted.diff.MergePlan;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.events.HistoryManager;
import de.jare.jsoncasted.editor.merge.EditTreeMerger;
import de.jare.jsoncasted.editor.merge.MergeOutcome;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * The conflict decision of the 3-way merge (use case U1 of doc/TREE_DIFF.md in jsonCasted): the non-overlapping
 * theirs deltas apply in any case; each conflict is a decision between keeping the own change (the default) and
 * taking theirs. The Merge button applies everything chosen in ONE transaction — one undo step (gap G2).
 * Structural theirs changes (MOVE/REORDER, v1) cannot be taken over yet; their radio stays disabled.
 *
 * @author Janusch Rentenatus
 */
public class MergeDecisionDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final EditTree oursTree;
    private final EditNodeAbstract theirsRoot;
    private final MergePlan plan;
    private final HistoryManager history;
    private final List<JRadioButton> takeTheirsButtons = new ArrayList<>();

    /**
     * Shows the decision dialog for the given merge plan.
     *
     * @param owner the owner window, may be null.
     * @param oursTree the tree to merge into, never {@code null}.
     * @param theirsRoot the root of the external version, never {@code null}.
     * @param plan the analysis plan, never {@code null}.
     * @param history the history to book the merge transaction on, or {@code null}.
     */
    public MergeDecisionDialog(Window owner, EditTree oursTree, EditNodeAbstract theirsRoot,
            MergePlan plan, HistoryManager history) {
        this(owner, null, oursTree, theirsRoot, plan, history);
    }

    /**
     * Shows the decision dialog for the given merge plan. The "Show in trees" button opens the W3 diff view in the
     * editor tabs (concept CONCEPT_TreeDiffView.md); it needs an anchor component inside the main window to find
     * them.
     *
     * @param owner the owner window, may be null.
     * @param anchor a component inside the main window for the view handover, or null (button disabled).
     * @param oursTree the tree to merge into, never {@code null}.
     * @param theirsRoot the root of the external version, never {@code null}.
     * @param plan the analysis plan, never {@code null}.
     * @param history the history to book the merge transaction on, or {@code null}.
     */
    public MergeDecisionDialog(Window owner, java.awt.Component anchor, EditTree oursTree,
            EditNodeAbstract theirsRoot, MergePlan plan, HistoryManager history) {
        super(owner, "W3 Merge: ours " + TreeDiffSides.describe(oursTree.getRoot())
                + " \u2190 theirs " + TreeDiffSides.describe(theirsRoot));
        this.oursTree = oursTree;
        this.theirsRoot = theirsRoot;
        this.plan = plan;
        this.history = history;
        setModalityType(ModalityType.DOCUMENT_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        final StringBuilder summary = new StringBuilder("  ");
        summary.append(plan.getApplyOps().size()).append(" external change(s) will be applied, ");
        summary.append(plan.getConflicts().size()).append(" conflict(s) need a decision");
        if (!plan.getConflicts().isEmpty()) {
            summary.append(" — keep yours (default) or take theirs");
        }
        summary.append('.');
        final JLabel summaryLabel = new JLabel(summary.toString());
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(summaryLabel, BorderLayout.NORTH);

        final Box content = Box.createVerticalBox();
        if (!plan.getApplyOps().isEmpty()) {
            content.add(section("Apply (theirs changes not touching ours)",
                    plan.getApplyOps().stream().map(DeltaOp::toString).toList()));
        }
        if (!plan.getConflicts().isEmpty()) {
            content.add(conflictSection());
        }
        if (plan.getApplyOps().isEmpty() && plan.getConflicts().isEmpty()) {
            final JTextArea empty = new JTextArea("Nothing to merge — the trees carry the same content.");
            empty.setEditable(false);
            empty.setBackground(new Color(238, 248, 238));
            content.add(empty);
        }
        final JScrollPane scroll = new JScrollPane(content);
        scroll.setPreferredSize(new Dimension(760, 420));
        add(scroll, BorderLayout.CENTER);

        final JPanel buttons = new JPanel();
        final JButton showInTrees = new JButton("Show in trees");
        showInTrees.setToolTipText("Open the W3 diff view in the editor tabs (marks, cherry-picking)");
        showInTrees.setEnabled(anchor != null);
        showInTrees.addActionListener(e -> {
            if (DiffViewController.openW3View(anchor, oursTree, theirsRoot)) {
                dispose();
            }
        });
        buttons.add(showInTrees);
        final JButton merge = new JButton(plan.getConflicts().isEmpty() ? "Merge" : "Merge with decisions");
        merge.addActionListener(e -> apply());
        final JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        buttons.add(merge);
        buttons.add(cancel);
        add(buttons, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    private static Box section(String header, List<String> entries) {
        final Box box = Box.createVerticalBox();
        box.add(label(header));
        for (String entry : entries) {
            box.add(label("    " + entry));
        }
        box.add(Box.createVerticalStrut(6));
        return box;
    }

    private Box conflictSection() {
        final Box box = Box.createVerticalBox();
        box.add(label("Conflicts (need a decision)"));
        for (ConflictRecord conflict : plan.getConflicts()) {
            final Box row = Box.createVerticalBox();
            row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(200, 200, 200)),
                    BorderFactory.createEmptyBorder(4, 8, 4, 8)));
            row.add(label("    " + conflict));
            final Box choices = Box.createHorizontalBox();
            final JRadioButton keepOurs = new JRadioButton("keep mine", true);
            final JRadioButton takeTheirs = new JRadioButton("take theirs");
            if (conflict.getReason() == ConflictReason.MOVE_OR_REORDER) {
                takeTheirs.setEnabled(false);
                takeTheirs.setToolTipText("structural changes are not applicable yet (v1)");
            }
            takeTheirsButtons.add(takeTheirs);
            final ButtonGroup group = new ButtonGroup();
            group.add(keepOurs);
            group.add(takeTheirs);
            choices.add(keepOurs);
            choices.add(Box.createHorizontalStrut(16));
            choices.add(takeTheirs);
            row.add(choices);
            box.add(row);
        }
        box.add(Box.createVerticalStrut(6));
        return box;
    }

    private static JLabel label(String text) {
        final JLabel label = new JLabel(text);
        label.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        label.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        return label;
    }

    /**
     * Applies the apply list plus every "take theirs" decision in one transaction and shows the outcome. Keeping a
     * conflict means: nothing to do, the own state already carries the decision.
     */
    private void apply() {
        final List<DeltaOp> deltas = new ArrayList<>(plan.getApplyOps());
        final List<ConflictRecord> conflicts = plan.getConflicts();
        for (int i = 0; i < conflicts.size(); i++) {
            if (takeTheirsButtons.get(i).isSelected()) {
                deltas.add(conflicts.get(i).getTheirsDelta());
            }
        }
        final MergeOutcome outcome = deltas.isEmpty()
                ? new MergeOutcome(plan, false, List.of())
                : EditTreeMerger.applyDeltas(oursTree, theirsRoot, plan, deltas, history);
        dispose();
        final StringBuilder message = new StringBuilder();
        message.append(outcome.isApplied()
                ? "Merge applied — one undo step." : "Nothing was applied.");
        if (!outcome.getProblems().isEmpty()) {
            message.append("\nProblems:\n");
            for (String problem : outcome.getProblems()) {
                message.append("  ").append(problem).append('\n');
            }
        }
        final int kept = (int) takeTheirsButtons.stream().filter(b -> !b.isSelected()).count();
        if (kept > 0) {
            message.append("\n").append(kept).append(" conflict(s) kept as they are.");
        }
        JOptionPane.showMessageDialog(getOwner(), message.toString(), "W3 Merge",
                outcome.isApplied() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
    }
}
