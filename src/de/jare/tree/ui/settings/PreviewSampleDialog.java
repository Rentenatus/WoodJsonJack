/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * [http://www.eclipse.org/legal/epl-v20.html](http://www.eclipse.org/legal/epl-v20.html)
 * </copyright>
 */
package de.jare.tree.ui.settings;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

public class PreviewSampleDialog extends JDialog {

    private JComboBox<String> themeVariantComboBox1;

    public PreviewSampleDialog(Window owner) {
        super(owner, "Sample Elements Preview", Dialog.ModalityType.MODELESS);

        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        add(buildContent(), BorderLayout.CENTER);
        pack();
    }

    private JPanel buildContent() {
        JPanel wrapper = new JPanel(new BorderLayout(8, 8));
        wrapper.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel samplePanel = new JPanel(new GridLayout(1, 2, 8, 8));
        samplePanel.setBorder(BorderFactory.createTitledBorder("Sample Elements"));

        JPanel structurePanel = new JPanel(new GridLayout(2, 1, 6, 6));

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("project");
        DefaultMutableTreeNode src = new DefaultMutableTreeNode("src");
        src.add(new DefaultMutableTreeNode("Main.java"));
        src.add(new DefaultMutableTreeNode("ThemeService.java"));
        DefaultMutableTreeNode data = new DefaultMutableTreeNode("data");
        data.add(new DefaultMutableTreeNode("config.json"));
        root.add(src);
        root.add(data);

        JTree sampleTree = new JTree(root);
        JScrollPane treeScrollPane = new JScrollPane(sampleTree);
        treeScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        treeScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        treeScrollPane.setBorder(BorderFactory.createTitledBorder("Tree"));

        DefaultTableModel sampleTableModel = new DefaultTableModel(
                new Object[]{"Name", "Type"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        sampleTableModel.addRow(new Object[]{"Main.java", "Java"});
        sampleTableModel.addRow(new Object[]{"config.json", "JSON"});
        sampleTableModel.addRow(new Object[]{"theme-dark", "Theme"});

        JTable sampleTable = new JTable(sampleTableModel);
        JScrollPane sampleTableScrollPane = new JScrollPane(sampleTable);
        sampleTableScrollPane.setBorder(BorderFactory.createTitledBorder("Table"));

        structurePanel.add(treeScrollPane);
        structurePanel.add(sampleTableScrollPane);

        JPanel controlsPanel = new JPanel(new GridLayout(0, 1, 6, 6));

        JLabel infoLabel = new JLabel("Example label");
        infoLabel.setOpaque(true);
        infoLabel.setBackground(new Color(230, 230, 230));
        controlsPanel.add(infoLabel);

        JButton previewButton = new JButton("Preview Button");
        controlsPanel.add(previewButton);

        controlsPanel.add(new JTextField("Sample text field"));

        controlsPanel.add(new JLabel("Theme variant"));
        themeVariantComboBox1 = new JComboBox<>(new String[]{"Light", "Dark"});
        controlsPanel.add(themeVariantComboBox1);

        samplePanel.add(structurePanel);
        samplePanel.add(controlsPanel);

        wrapper.add(samplePanel, BorderLayout.CENTER);
        return wrapper;
    }
}