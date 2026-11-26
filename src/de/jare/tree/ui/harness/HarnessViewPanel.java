/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.harness;

import de.jare.harness.CallLogEntry;
import de.jare.harness.HarnessEngine;
import de.jare.harness.HarnessInstance;
import de.jare.harness.Status;
import de.jare.harness.TaskData;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

/**
 * The harness window (CONCEPT_Harness.md §3, §15): shows the task tree live with status
 * colors and score badges; a click on a node shows its calculator protocol
 * (metrics-only call log) in the detail pane. The engine pushes notifications through
 * {@link HarnessEngine#addListener}; the view rebuilds on the EDT.
 *
 * @author Janusch Rentenatus
 */
public class HarnessViewPanel extends JPanel implements de.jare.harness.spi.HarnessView {

    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final JTextArea detail;
    private HarnessInstance instance;

    public HarnessViewPanel() {
        super(new BorderLayout());
        rootNode = new DefaultMutableTreeNode("harness");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setCellRenderer(new StatusRenderer());
        detail = new JTextArea();
        detail.setEditable(false);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(tree), new JScrollPane(detail));
        split.setDividerLocation(320);
        add(split, BorderLayout.CENTER);

        tree.addTreeSelectionListener(e -> showSelectedProtocol());
    }

    /**
     * Binds the view to a running instance and engine notifications. The task tree is a plain
     * TaskData tree (CONCEPT_Harness.md §3) - the panel listens to its change events (bubble up
     * to the instance) and rebuilds its own read-only JTree from the TaskData objects.
     */
    // §18.6: the instance's change orator holds listeners weakly - this panel is the
    // subscriber, so it keeps its own listener in a field
    private java.util.function.Consumer<TaskData> changeListener;

    public void bind(HarnessInstance instance, HarnessEngine engine) {
        this.instance = instance;
        engine.addListener((task, msg) -> SwingUtilities.invokeLater(this::refresh));
        changeListener = t -> SwingUtilities.invokeLater(this::refresh);
        instance.addChangeListener(changeListener);
        refresh();
    }

    public void refresh() {
        if (instance == null || instance.getRoot() == null) {
            return;
        }
        rootNode.removeAllChildren();
        rootNode.add(buildNode(instance.getRoot()));
        treeModel.reload();
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private static DefaultMutableTreeNode buildNode(TaskData t) {
        DefaultMutableTreeNode n = new DefaultMutableTreeNode(t);
        // the projected calculator steps (data movement: input -> payload -> loopAction)
        for (TaskData.CalcCall call : t.getCalls()) {
            StringBuilder label = new StringBuilder();
            label.append(call.getCalc()).append(" -> ").append(call.getDecision())
                    .append(" [").append(call.getStatus()).append("]");
            if (call.getScore() != null) {
                label.append(" score=").append(call.getScore());
            }
            if (!call.getLoopActionType().isEmpty()) {
                label.append(" loop=").append(call.getLoopActionType());
            }
            DefaultMutableTreeNode callNode = new DefaultMutableTreeNode(label.toString());
            for (var p : call.getPayload().entrySet()) {
                callNode.add(new DefaultMutableTreeNode(p.getKey() + " = " + p.getValue()));
            }
            n.add(callNode);
        }
        for (TaskData c : t.getChildren()) {
            n.add(buildNode(c));
        }
        return n;
    }

    private void showSelectedProtocol() {
        Object sel = tree.getLastSelectedPathComponent();
        if (!(sel instanceof DefaultMutableTreeNode dn) || !(dn.getUserObject() instanceof TaskData t)) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("task ").append(t.getId()).append("  ").append(t.getTaskType())
                .append("  status=").append(t.getStatus())
                .append("  score=").append(t.getScore() == null ? "-" : t.getScore())
                .append("  attempt=").append(t.getAttempt()).append('\n');
        if (t.getArtifact() != null) {
            sb.append("artifact: ").append(t.getArtifact()).append('\n');
        }
        sb.append("--- calculator log (metrics only) ---\n");
        for (CallLogEntry e : t.getCalculatorLog()) {
            sb.append(String.format("#%d %-10s slot=%s score=%s status=%s decision=%s%n",
                    e.getAufrufId(), e.getKind(), e.getSlotId(),
                    e.getScore() == null ? "-" : e.getScore(), e.getStatus(), e.getDecision()));
        }
        detail.setText(sb.toString());
        detail.setCaretPosition(0);
    }

    /** Status colors + score badge in the tree label. */
    private static final class StatusRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            JLabel label = (JLabel) super.getTreeCellRendererComponent(
                    tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode dn && dn.getUserObject() instanceof TaskData t) {
                String score = t.getScore() == null ? "" : " [" + t.getScore() + "]";
                label.setText("#" + t.getId() + " " + t.getTitle() + score);
                label.setForeground(colorFor(t.getStatus()));
            }
            return label;
        }

        private static Color colorFor(Status s) {
            return switch (s) {
                case PENDING -> Color.GRAY;
                case RUNNING -> new Color(0x1565C0);
                case RETRY -> new Color(0xE65100);
                case DONE -> new Color(0x2E7D32);
                case FAILED -> new Color(0xC62828);
                case ABORTED -> new Color(0x6A1B9A);
                case INVALID -> new Color(0x757575);
            };
        }
    }
}
