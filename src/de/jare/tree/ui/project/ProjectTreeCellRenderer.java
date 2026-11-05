/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui.project;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeCellRenderer;

/**
 * Renderer of the project sidebar: partitions carry their role as suffix so the virtual level 1
 * reads as what it is - mounts, not folders. Read-only mounts render gray, files keep the
 * plain look. All colors come from the current look and feel; no theme keys in v1 (concept
 * section 8).
 *
 * @author Janusch Rentenatus
 */
public class ProjectTreeCellRenderer extends DefaultTreeCellRenderer {

    public ProjectTreeCellRenderer() {
        // no state - the look and feel decides the colors
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
            boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        if (value instanceof ProjectTreeNode) {
            ProjectTreeNode node = (ProjectTreeNode) value;
            setText(node.toString());
            setToolTipText(node.toTooltip());
            if (node.getKind() == ProjectTreeNode.Kind.PARTITION && node.getPartition() != null
                    && node.getPartition().getType() != null) {
                setText(node.toString() + " [" + node.getPartition().getType().getName() + "]");
            }
            if (node.getKind() == ProjectTreeNode.Kind.BUNDLE) {
                setText(node.toString() + " [bundle]");
            }
            if (sel) {
                setForeground(null);
            } else if (node.isReadonlyMount()) {
                Color disabled = UIManager.getColor("Label.disabledForeground");
                setForeground(disabled != null ? disabled : Color.GRAY);
            } else {
                setForeground(null);
            }
        }
        return this;
    }

}
