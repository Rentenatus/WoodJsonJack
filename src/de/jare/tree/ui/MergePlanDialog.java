/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.diff.ConflictRecord;
import de.jare.jsoncasted.diff.DeltaOp;
import de.jare.jsoncasted.diff.MergePlan;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Shows the 3-way analysis of the interactive tree diff (use case U1 of doc/TREE_DIFF.md in jsonCasted): what the
 * external side changed without touching ours (apply), what both sides did identically (no-op) and what needs a
 * decision (conflicts) — the merge plan of {@link de.jare.jsoncasted.editor.merge.EditTreeMerger#analyze}, pure
 * analysis, nothing is applied.
 *
 * @author Janusch Rentenatus
 */
public class MergePlanDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /**
     * Runs the 3-way analysis and shows the plan. Base is the import state of the ours tree, ours is the ours side,
     * theirs the external version.
     *
     * @param owner the owner window, may be null.
     * @param plan the merge plan to show, never {@code null}.
     * @param oursRoot the ours side root, may be null (label only).
     * @param theirsRoot the theirs side root, may be null (label only).
     */
    public MergePlanDialog(Window owner, MergePlan plan, EditNodeAbstract oursRoot, EditNodeAbstract theirsRoot) {
        this(owner, null, plan, oursRoot, theirsRoot);
    }

    /**
     * Runs the 3-way analysis and shows the plan. Base is the import state of the ours tree, ours is the ours side,
     * theirs the external version. The "Show in trees" button opens the W3 diff view in the editor tabs (concept
     * CONCEPT_TreeDiffView.md); it needs an anchor component inside the main window to find them.
     *
     * @param owner the owner window, may be null.
     * @param anchor a component inside the main window for the view handover, or null (button disabled).
     * @param plan the merge plan to show, never {@code null}.
     * @param oursRoot the ours side root, may be null (label only).
     * @param theirsRoot the theirs side root, may be null (label only).
     */
    public MergePlanDialog(Window owner, java.awt.Component anchor, MergePlan plan,
            EditNodeAbstract oursRoot, EditNodeAbstract theirsRoot) {
        super(owner, "W3 Diff: ours " + TreeDiffSides.describe(oursRoot)
                + " / theirs " + TreeDiffSides.describe(theirsRoot)
                + " (base: import state of ours)");
        setModalityType(ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        final long radiating = plan.getConflicts().stream().filter(ConflictRecord::isRadiating).count()
                + plan.getApplyOps().stream().filter(DeltaOp::isRadiating).count();
        final String summary = plan.getOursReport().getOps().size() + " own change(s), " + plan
                + (radiating > 0 ? ", " + radiating + " radiating." : ".");
        final JLabel summaryLabel = new JLabel("  " + summary);
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        add(summaryLabel, BorderLayout.NORTH);

        final List<String> lines = new ArrayList<>();
        section(lines, "Ours (own changes vs base, already in this tree)",
                plan.getOursReport().getOps().stream().map(DeltaOp::toString).toList());
        section(lines, "Apply (theirs changes not touching ours)",
                plan.getApplyOps().stream().map(DeltaOp::toString).toList());
        section(lines, "No-op (both sides did the same)",
                plan.getNoOps().stream().map(DeltaOp::toString).toList());
        section(lines, "Conflicts (need a decision)",
                plan.getConflicts().stream().map(ConflictRecord::toString).toList());

        final JTextArea text = new JTextArea(String.join("\n", lines));
        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        if (plan.getApplyOps().isEmpty() && plan.getConflicts().isEmpty()) {
            text.setBackground(new Color(238, 248, 238));
        } else if (!plan.getConflicts().isEmpty()) {
            text.setBackground(new Color(252, 244, 238));
        }
        text.setCaretPosition(0);
        final JScrollPane scroll = new JScrollPane(text);
        scroll.setPreferredSize(new Dimension(760, 440));
        add(scroll, BorderLayout.CENTER);

        final JPanel buttons = new JPanel();
        final JButton showInTrees = new JButton("Show in trees");
        showInTrees.setToolTipText("Open the W3 diff view in the editor tabs (marks, cherry-picking)");
        showInTrees.setEnabled(anchor != null && oursRoot != null && theirsRoot != null);
        showInTrees.addActionListener(e -> {
            if (DiffViewController.openW3View(anchor, oursRoot, theirsRoot)) {
                dispose();
            }
        });
        buttons.add(showInTrees);
        final JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        buttons.add(close);
        add(buttons, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    private static void section(List<String> lines, String header, List<String> entries) {
        lines.add(header);
        if (entries.isEmpty()) {
            lines.add("    (none)");
        } else {
            for (String entry : entries) {
                lines.add("    " + entry);
            }
        }
        lines.add("");
    }
}
