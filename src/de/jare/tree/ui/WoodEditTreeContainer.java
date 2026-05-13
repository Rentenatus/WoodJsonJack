/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * [http://www.eclipse.org/legal/epl-v20.html](http://www.eclipse.org/legal/epl-v20.html)
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.tree.control.MasterControl;

import javax.swing.*;
import java.awt.*;

/**
 * Container für zwei WoodEditTree-Instanzen, die nebeneinander angezeigt werden
 * können. Die Checkbox steuert, ob die rechte Instanz angezeigt wird.
 */
public class WoodEditTreeContainer extends JPanel {
    
    private static final int COLLAPSED_DIVIDER_SIZE = 1;
    private static final int EXPANDED_DIVIDER_SIZE = 8;
    private static final Dimension COLLAPSED_MIN_SIZE = new Dimension(0, 0);
    private static final Dimension EXPANDED_MIN_SIZE = new Dimension(50, 50);
    
    private final WoodEditTree leftTree;
    private final WoodEditTree rightTree;
    private final JSplitPane splitPane;

    /**
     * Erstellt einen Container mit zwei WoodEditTree-Instanzen.
     *
     * @param master Der MasterControl für beide Bäume.
     * @param leftRootName Der Name des Root-Knotens für den linken Baum.
     * @param rightRootName Der Name des Root-Knotens für den rechten Baum.
     * @param propNames Optionale Property-Namen für beide Bäume.
     */
    public WoodEditTreeContainer(MasterControl master, String leftRootName, String rightRootName, String... propNames) {
        this.leftTree = new WoodEditTree(master, leftRootName, propNames);
        this.rightTree = new WoodEditTree(master, rightRootName, propNames);
        this.rightTree.setResourceInfo("Keine abhängige Ressourcen.");
        
        // Register EditTree with master control for editor integration
        if (master != null) {
            master.setEditTree(this.leftTree.getEditTree());
        }
        
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
     * Aktiviert oder deaktiviert die Gegenüberstellung der beiden Bäume.
     *
     * @param enabled Wenn true, wird die rechte Instanz angezeigt.
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
     * Gibt den linken Baum zurück.
     *
     * @return Der linke WoodEditTree.
     */
    public WoodEditTree getLeftTree() {
        return leftTree;
    }

    /**
     * Gibt den rechten Baum zurück.
     *
     * @return Der rechte WoodEditTree.
     */
    public WoodEditTree getRightTree() {
        return rightTree;
    }

    /**
     * Setzt den Text für die Ressourceninfo im linken Baum.
     *
     * @param text Der Text für die Ressourceninfo.
     */
    public void setLeftResourceInfo(String text) {
        leftTree.setResourceInfo(text);
    }

    /**
     * Setzt den Text für die Ressourceninfo im rechten Baum.
     *
     * @param text Der Text für die Ressourceninfo.
     */
    public void setRightResourceInfo(String text) {
        rightTree.setResourceInfo(text);
    }
}
