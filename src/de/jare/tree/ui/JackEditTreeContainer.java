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
        
        this.splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftTree, rightTree);
        this.splitPane.setResizeWeight(0.5);
        this.splitPane.setContinuousLayout(true);
        this.splitPane.setOneTouchExpandable(false);
        
        this.leftTree.setMinimumSize(EXPANDED_MIN_SIZE);
        this.rightTree.setMinimumSize(COLLAPSED_MIN_SIZE);
        
        setLayout(new BorderLayout());
        add(splitPane, BorderLayout.CENTER);
        
        toggleLinkView(false);
    }

    /**
     * Enables or disables the side by side view of the two trees.
     *
     * @param enabled when true, the right instance is shown.
     */
    private void toggleLinkView(boolean enabled) {
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
