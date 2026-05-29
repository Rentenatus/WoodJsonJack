/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.clipboard.ClipboardManager;
import de.jare.jsoncasted.editor.clipboard.ClipboardStash;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Panel zur Steuerung der Clipboard-Stashes mit Wechselmöglichkeit.
 * Enthält einen JackClipboardTree und Steuerungselemente für Stash-Management.
 */
public class JackClipboardPanel extends JPanel {

    private final JackClipboardTree clipboardTree;
    private final ClipboardManager clipboardManager;
    private JComboBox<String> stashComboBox;
    private JButton newStashButton;
    private JButton deleteStashButton;
    private JButton refreshButton;
    private JButton clearButton;

    public JackClipboardPanel(ClipboardManager clipboardManager, JackEditTree sourceTree) {
        this.clipboardManager = clipboardManager;
        this.clipboardTree = new JackClipboardTree(clipboardManager);
        this.clipboardTree.setSourceTree(sourceTree);

        // Registriere Listener für Stash-Listen-Änderungen
        clipboardManager.addClipboardChangeListener(stashName -> {
            // Aktualisiere die ComboBox bei Änderungen
            SwingUtilities.invokeLater(() -> {
                updateStashList();
                // Wenn ein spezifischer Stash geändert wurde, wähle ihn aus
                if (stashName != null) {
                    stashComboBox.setSelectedItem(stashName);
                }
            });
        });

        setLayout(new BorderLayout());
        
        // Erstelle das Steuerungspanel
        JPanel controlPanel = createControlPanel();
        
        // Füge die Komponenten zusammen
        add(controlPanel, BorderLayout.NORTH);
        add(new JScrollPane(clipboardTree), BorderLayout.CENTER);
        
        // Initialisiere die Stash-ComboBox
        updateStashList();
        
        // Wähle den aktiven Stash des ClipboardManagers aus
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
        
        // Panel für Stash-Auswahl und Buttons
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        // Stash-Auswahl mit ComboBox
        stashComboBox = new JComboBox<>();
        stashComboBox.setPreferredSize(new Dimension(200, 25));
        stashComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedStash = (String) stashComboBox.getSelectedItem();
                if (selectedStash != null) {
                    // Wechsle den aktiven Stash im ClipboardManager
                    clipboardManager.switchToStash(selectedStash);
                    // Aktualisiere die Anzeige
                    clipboardTree.switchStash(selectedStash);
                }
            }
        });
        
        // Button-Panel
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
        
        // Kombiniere die Komponenten
        topPanel.add(stashComboBox);
        topPanel.add(buttonPanel);
        
        panel.add(topPanel, BorderLayout.CENTER);
        
        return panel;
    }

    /**
     * Erstellt einen neuen Stash mit einem Dialog.
     */
    private void createNewStash() {
        String name = JOptionPane.showInputDialog(this, "Enter new stash name:", 
                                                  "Create Stash", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            try {
                clipboardManager.createStash(name);
                updateStashList();
                // Neuen Stash auswählen
                stashComboBox.setSelectedItem(name);
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), 
                                              "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Löscht den aktuell ausgewählten Stash.
     */
    private void deleteSelectedStash() {
        String selected = (String) stashComboBox.getSelectedItem();
        if (selected == null) {
            return;
        }

        // Prüfe, dass nicht der letzte Stash gelöscht wird
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
                // Merke den aktuellen Stash
                String currentStash = clipboardTree.getCurrentStashName();
                
                // Lösche den Stash
                clipboardManager.removeStash(selected);
                
                // Aktualisiere die ComboBox
                updateStashList();
                
                // Falls der gelöschte Stash der aktuelle war, wechsle zum ersten verfügbaren
                if (selected.equals(currentStash)) {
                    if (stashComboBox.getItemCount() > 0) {
                        stashComboBox.setSelectedIndex(0);
                    }
                } else {
                    // Behalte die aktuelle Auswahl bei, falls noch vorhanden
                    stashComboBox.setSelectedItem(currentStash);
                }
                
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), 
                                              "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Aktualisiert die ComboBox der verfügbaren Stashes.
     */
    public void updateStashList() {
        String[] stashNames = clipboardManager.getStashNames();
        stashComboBox.removeAllItems();
        for (String name : stashNames) {
            stashComboBox.addItem(name);
        }
    }

    /**
     * Gibt den JackClipboardTree zurück.
     * 
     * @return der JackClipboardTree
     */
    public JackClipboardTree getClipboardTree() {
        return clipboardTree;
    }

    /**
     * Gibt den ClipboardManager zurück.
     * 
     * @return der ClipboardManager
     */
    public ClipboardManager getClipboardManager() {
        return clipboardManager;
    }
}
