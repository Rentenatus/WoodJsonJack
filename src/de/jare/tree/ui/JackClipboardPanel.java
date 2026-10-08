/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.clipboard.ClipboardManager;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

/**
 * Panel for controlling the clipboard stashes with switching support.
 * Contains a JackClipboardTree and controls for stash management.
 */
public class JackClipboardPanel extends JPanel {

    private final JackClipboardTree clipboardTree;
    private final ClipboardManager clipboardManager;
    private JComboBox<String> stashComboBox;
    private JButton newStashButton;
    private JButton deleteStashButton;
    private JButton refreshButton;
    private JButton clearButton;
    private final ClipboardManager.ClipboardChangeListener clipboardChangeListener;

    public JackClipboardPanel(ClipboardManager clipboardManager, JackEditTree sourceTree) {
        this.clipboardManager = clipboardManager;
        this.clipboardTree = new JackClipboardTree(clipboardManager);
        this.clipboardTree.setSourceTree(sourceTree);

        // strong reference to the listener for GC protection
        this.clipboardChangeListener = stashName -> {
            // update the combo box on changes
            SwingUtilities.invokeLater(() -> {
                updateStashList();
                // when a specific stash changed, select it
                if (stashName != null) {
                    stashComboBox.setSelectedItem(stashName);
                }
            });
        };

        // register listener for stash list changes (low priority for the UI list)
        clipboardManager.addClipboardChangeListener(2, clipboardChangeListener);

        setLayout(new BorderLayout());
        
        // create the control panel
        JPanel controlPanel = createControlPanel();
        
        // assemble the components
        add(controlPanel, BorderLayout.NORTH);
        add(new JScrollPane(clipboardTree), BorderLayout.CENTER);
        
        // initialize the stash combo box
        updateStashList();
        
        // select the active stash of the ClipboardManager
        String activeStash = clipboardManager.getActiveStashName();
        for (int i = 0; i < stashComboBox.getItemCount(); i++) {
            if (activeStash.equals(stashComboBox.getItemAt(i))) {
                stashComboBox.setSelectedIndex(i);
                clipboardTree.switchStash(activeStash);
                break;
            }
        }
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Clipboard Stash Control"));
        
        // panel for stash selection and buttons
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        // stash selection with combo box
        stashComboBox = new JComboBox<>();
        stashComboBox.setPreferredSize(new Dimension(200, 25));
        stashComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedStash = (String) stashComboBox.getSelectedItem();
                if (selectedStash != null) {
                    // switch the active stash in the ClipboardManager
                    // (switchToStash itself checks whether it is already the active one)
                    clipboardManager.switchToStash(selectedStash);
                    // update the display
                    clipboardTree.switchStash(selectedStash);
                }
            }
        });
        
        // button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        newStashButton = new JButton("New Stash");
        newStashButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                createNewStash();
            }
        });
        
        deleteStashButton = new JButton("Delete Stash");
        deleteStashButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteSelectedStash();
            }
        });
        
        refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clipboardTree.refreshCurrentStash();
            }
        });
        
        clearButton = new JButton("Clear");
        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clipboardTree.clearCurrentStash();
            }
        });
        
        buttonPanel.add(newStashButton);
        buttonPanel.add(deleteStashButton);
        buttonPanel.add(refreshButton);
        buttonPanel.add(clearButton);
        
        // combine the components
        topPanel.add(stashComboBox);
        topPanel.add(buttonPanel);
        
        panel.add(topPanel, BorderLayout.CENTER);
        
        return panel;
    }

    /**
     * Creates a new stash with a dialog.
     */
    private void createNewStash() {
        String name = JOptionPane.showInputDialog(this, "Enter new stash name:", 
                                                  "Create Stash", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            try {
                clipboardManager.createStash(name);
                updateStashList();
                // select the new stash
                stashComboBox.setSelectedItem(name);
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), 
                                              "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Deletes the currently selected stash.
     */
    private void deleteSelectedStash() {
        String selected = (String) stashComboBox.getSelectedItem();
        if (selected == null) {
            return;
        }

        // check that the last stash is not deleted
        String[] stashNames = clipboardManager.getStashNames();
        if (stashNames.length <= 1) {
            JOptionPane.showMessageDialog(this, 
                "Cannot delete the last stash.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int result = JOptionPane.showConfirmDialog(this,
                "Really delete stash: " + selected + "?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
        
        if (result == JOptionPane.YES_OPTION) {
            try {
                // first switch to another stash (the first that is not the selected one)
                String newActiveStash = null;
                for (String name : stashNames) {
                    if (!name.equals(selected)) {
                        newActiveStash = name;
                        break;
                    }
                }
                
                if (newActiveStash != null) {
                    clipboardManager.switchToStash(newActiveStash);
                }
                
                // then delete the selected stash
                clipboardManager.removeStash(selected);
                
                // update the combo box
                updateStashList();
                
                // select the new active stash
                if (newActiveStash != null) {
                    stashComboBox.setSelectedItem(newActiveStash);
                } else if (stashComboBox.getItemCount() > 0) {
                    stashComboBox.setSelectedIndex(0);
                }
                
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), 
                                              "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Updates the combo box of the available stashes.
     */
    public void updateStashList() {
        String[] stashNames = clipboardManager.getStashNames();
        stashComboBox.removeAllItems();
        for (String name : stashNames) {
            stashComboBox.addItem(name);
        }
    }

    /**
     * Returns the JackClipboardTree.
     * 
     * @return the JackClipboardTree
     */
    public JackClipboardTree getClipboardTree() {
        return clipboardTree;
    }

    /**
     * Returns the ClipboardManager.
     * 
     * @return the ClipboardManager
     */
    public ClipboardManager getClipboardManager() {
        return clipboardManager;
    }
}
