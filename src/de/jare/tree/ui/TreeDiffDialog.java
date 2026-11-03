/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.diff.DeltaOp;
import de.jare.jsoncasted.diff.DiffReport;
import de.jare.jsoncasted.diff.TreeDiffer;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Shows the result of the interactive tree diff (use case U4 of doc/TREE_DIFF.md in jsonCasted): a plain, monospaced
 * list of the delta ops of {@link TreeDiffer#diff}, one op per line — radiating ops carry their flag. An empty
 * report is the semantic no-op: both sides carry the same content, no matter what the editors display around it.
 *
 * @author Janusch Rentenatus
 */
public class TreeDiffDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /**
     * Runs the diff of the two sides and shows the report. The diff itself is a pure function — neither side is
     * touched.
     *
     * @param owner the owner window, may be null.
     * @param left the left side root, not null.
     * @param right the right side root, not null.
     */
    public TreeDiffDialog(Window owner, EditNodeAbstract left, EditNodeAbstract right) {
        this(owner, null, left, right);
    }

    /**
     * Runs the diff of the two sides and shows the report. The diff itself is a pure function — neither side is
     * touched. The "Show in trees" button opens the W2 diff view in the editor tabs (concept
     * CONCEPT_TreeDiffView.md); it needs an anchor component inside the main window to find them.
     *
     * @param owner the owner window, may be null.
     * @param anchor a component inside the main window for the view handover, or null (button disabled).
     * @param left the left side root, not null.
     * @param right the right side root, not null.
     */
    public TreeDiffDialog(Window owner, java.awt.Component anchor, EditNodeAbstract left, EditNodeAbstract right) {
        super(owner, "Tree Diff: " + TreeDiffSides.describe(left) + " \u2194 " + TreeDiffSides.describe(right));
        setModalityType(ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        final DiffReport report = TreeDiffer.diff(left, right);
        final String summary = report.isEmpty()
                ? "No differences \u2014 the trees are semantically identical."
                : report.getOps().size() + " delta op(s), "
                + report.getRadiatingOps().size() + " radiating.";
        final JLabel summaryLabel = new JLabel("  " + summary);
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        add(summaryLabel, BorderLayout.NORTH);

        final JTextArea text = new JTextArea();
        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        if (report.isEmpty()) {
            text.setBackground(new Color(238, 248, 238));
        } else {
            text.setText(report.getOps().stream().map(DeltaOp::toString)
                    .collect(Collectors.joining("\n")));
            text.setCaretPosition(0);
        }
        final JScrollPane scroll = new JScrollPane(text);
        scroll.setPreferredSize(new Dimension(720, 420));
        add(scroll, BorderLayout.CENTER);

        final JPanel buttons = new JPanel();
        final JButton showInTrees = new JButton("Show in trees");
        showInTrees.setToolTipText("Open the W2 diff view in the editor tabs (marks on both trees)");
        showInTrees.setEnabled(anchor != null);
        showInTrees.addActionListener(e -> {
            if (DiffViewController.openW2View(anchor, left, right)) {
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
}
