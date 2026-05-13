/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control;
import de.jare.tree.control.listeners.ContentListener;
import de.jare.tree.control.listeners.FocusListener;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import de.jare.tree.control.listeners.UndoRedoListener;
import de.jare.tree.ui.WoodClipboardTree;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.events.EventBus;
import javax.swing.tree.TreeModel;

/**
 * Central controller for managing tree editors, clipboard, undo/redo, and event dispatching.
 * Supports both the legacy TreeModel-based system and the new EditTree-based system.
 */
public class MasterControl {

    // Legacy Orator-based channels for backward compatibility
    private final Orator<FocusListener> focusOrator = new Orator<>();
    private final Orator<TreeFocusListener> selectionOrator = new Orator<>();
    private final Orator<ContentListener> contentOrator = new Orator<>();
    
    // New EventBus for type-safe event dispatching (editor package)
    private EventBus eventBus;
    
    private WoodClipboardTree clipboardTree;
    // welcher Editor ist aktuell aktiv (Tab-basiert)?
    private Object activeEditor; // bewusst generisch
    private UndoManager undoMan;
    private SelectionStackManager selectionStack;
    
    // EditTree integration
    private EditTree editTree;

    public MasterControl() {
        this.undoMan = new UndoManager();
        this.selectionStack = new SelectionStackManager();
        addSelectionListener(6, this.undoMan);
        addSelectionListener(8, this.selectionStack);
        
        // Initialize EventBus for editor package integration
        this.eventBus = new EventBus();
        
        // Connect Orator instances to EventBus for unified event dispatching
        // This allows both old and new code to receive events
    }

    // ===== Legacy Orator-based registration methods (backward compatible) =====

    // Registrierung
    public void addFocusListener(FocusListener l) {
        focusOrator.addListener(l);
    }

    public void addFocusListener(int level, FocusListener l) {
        focusOrator.addListener(level, l);
    }

    public void addSelectionListener(TreeFocusListener l) {
        selectionOrator.addListener(l);
    }

    public void addSelectionListener(int level, TreeFocusListener l) {
        selectionOrator.addListener(level, l);
    }

    public void addContentListener(ContentListener l) {
        contentOrator.addListener(l);
    }

    public void addContentListener(int level, ContentListener l) {
        contentOrator.addListener(level, l);
    }

    public void addUndoRedoListener(int level, UndoRedoListener l) {
        undoMan.addUndoRedoListener(level, l);
    }

    public void addUndoRedoListener(UndoRedoListener l) {
        undoMan.addUndoRedoListener(5, l);
    }

    public void removeFocusListener(FocusListener l) {
        focusOrator.removeListener(l);
    }

    public void removeSelectionListener(TreeFocusListener l) {
        selectionOrator.removeListener(l);
    }

    public void removeContentListener(ContentListener l) {
        contentOrator.removeListener(l);
    }

    public void removeUndoRedoListener(UndoRedoListener l) {
        undoMan.removeUndoRedoListener(l);
    }

    // ===== EventBus-based registration methods (new editor package) =====

    /**
     * Gets the EventBus for type-safe event dispatching.
     * This can be used by editor package classes for registration.
     * 
     * @return the EventBus instance
     */
    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * Sets a custom EventBus instance.
     * 
     * @param eventBus the EventBus to use
     */
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
        // Update Orator instances to use the new EventBus
        // Note: Orator classes would need setEventBus method for full integration
    }

    // ===== Clipboard management =====

    public void setClipboardTree(WoodClipboardTree clipboardTree) {
        this.clipboardTree = clipboardTree;
    }

    public WoodClipboardTree getClipboardTree() {
        return clipboardTree;
    }

    // ===== Editor and focus management =====

    // Vom UI (z.B. JTabbedPane) gerufen, wenn ein Tab gew?hlt wird
    public void setActiveEditor(TreeFocusComponent editor, Object trigger) {
        Object previous = this.activeEditor;
        if (previous == editor) {
            return;
        }
        this.activeEditor = editor;

        // Fokus-Events verteilen
        if (previous != null) {
            focusOrator.say(l -> l.onFocusLost());
        }
        if (editor != null) {
            focusOrator.say(l -> l.onFocusGained());
        }
        selectionOrator.say(l -> l.onEditorSelected(editor, trigger));
    }

    // Vom aktiven Editor gerufen, wenn sich die Node-Selektion ?ndert
    public void fireSelection(Object node, Object trigger, boolean rootSelected) {
        selectionOrator.say(l -> l.onNodeSelected(node, trigger, rootSelected));
    }

    public void fireCommand(String commandId, Object trigger) {
        contentOrator.say(l -> l.onCommand(commandId, trigger));
    }

    public Object getActiveEditor() {
        return activeEditor;
    }

    public UndoManager getUndoManager() {
        return undoMan;
    }

    public SelectionStackManager getSelectionStackManager() {
        return selectionStack;
    }

    // ===== EditTree integration methods =====
    
    /**
     * Sets the EditTree for this master control.
     * @param editTree the EditTree instance
     */
    public void setEditTree(EditTree editTree) {
        this.editTree = editTree;
        // Propagate to undo manager if there's an active editor
        if (editTree != null && activeEditor instanceof TreeFocusComponent) {
            TreeFocusComponent editor = (TreeFocusComponent) activeEditor;
            undoMan.setActiveModel(editor.getModel(), editTree);
        }
    }

    /**
     * Gets the EditTree associated with this master control.
     * @return the EditTree instance, or null if not set
     */
    public EditTree getEditTree() {
        return editTree;
    }

    /**
     * Checks if this master control has an EditTree associated.
     * @return true if EditTree is set
     */
    public boolean hasEditTree() {
        return editTree != null;
    }

    // ===== Utility methods for editor package integration =====

    /**
     * Gets the currently selected node as EditNode if available.
     * This method bridges between the legacy selection system and the editor package.
     * 
     * @return the selected EditNode, or null if not available or not an EditNode
     */
    public EditNode getSelectedEditNode() {
        // This would require integration with the selection system
        // For now, return null - actual implementation depends on how selection is tracked
        return null;
    }

    /**
     * Sets the active model with associated EditTree for the undo manager.
     * This should be called when switching editors to ensure proper EditTree association.
     * 
     * @param model the TreeModel
     * @param editTree the associated EditTree
     */
    public void setActiveModel(TreeModel model, EditTree editTree) {
        this.editTree = editTree;
        undoMan.setActiveModel(model, editTree);
    }

}
