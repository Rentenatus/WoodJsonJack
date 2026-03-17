/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.data.JsonTreeNodeData;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;

public class JsonTreeCellRenderer implements TreeCellRenderer {

    private final JPanel panel = new JPanel(new BorderLayout());
    private final JLabel editLabel = new JLabel();
    private final JLabel infoLabel = new JLabel();

    public JsonTreeCellRenderer() {
        panel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        panel.add(editLabel, BorderLayout.WEST);
        panel.add(infoLabel, BorderLayout.EAST);

        editLabel.setOpaque(false);
        infoLabel.setOpaque(false);
    }

    @Override
    public Component getTreeCellRendererComponent(
            JTree tree, Object value, boolean selected,
            boolean expanded, boolean leaf, int row, boolean hasFocus) {

        JsonTreeNodeData data = null;
        if (value instanceof DefaultMutableTreeNode dmtn
                && dmtn.getUserObject() instanceof JsonTreeNodeData d) {
            data = d;
        }

        if (data != null) {
            editLabel.setText(data.getEditText());
            editLabel.setForeground(data.getForecolor());
            infoLabel.setText(data.getInfoText());
        } else {
            editLabel.setText(String.valueOf(value));
            editLabel.setForeground(tree.getForeground());
            infoLabel.setText("");
        }

        // Selektion/Focus nach JTree-Defaults nachbilden
        Color bg, fg;
        if (selected) {
            bg = UIManager.getColor("Tree.selectionBackground");
            fg = UIManager.getColor("Tree.selectionForeground");
        } else {
            bg = tree.getBackground();
            fg = tree.getForeground();
        }

        panel.setBackground(bg);
        panel.setOpaque(true);

        // Edit-Label bekommt f�r Lesbarkeit die gleiche Grundfarbe wie der Tree
        if (!selected) {
            editLabel.setForeground(data != null ? data.getForecolor() : fg);
        } else {
            editLabel.setForeground(fg);
        }
        infoLabel.setForeground(selected
                ? fg
                : UIManager.getColor("Label.disabledForeground"));

        return panel;
    }
}
