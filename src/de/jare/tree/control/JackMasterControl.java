/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control;

import de.jare.jsoncasted.editor.clipboard.ClipboardManager;
import de.jare.tree.control.listeners.ContentListener;
import de.jare.tree.control.listeners.FocusListener;
import de.jare.tree.control.listeners.TreeFocusComponent;
import de.jare.tree.control.listeners.TreeFocusListener;
import de.jare.tree.control.listeners.UndoRedoListener;

public class JackMasterControl {

    // Channels
    private final Orator<FocusListener> focusOrator = new Orator<>();
    private final Orator<TreeFocusListener> selectionOrator = new Orator<>();
    private final Orator<ContentListener> contentOrator = new Orator<>();

    // welcher Editor ist aktuell aktiv (Tab-basiert)?
    private Object activeEditor; // bewusst generisch
    private JackUndoManager undoMan;
    private SelectionStackManager selectionStack;
    private ClipboardManager clipboardManager;

    public JackMasterControl() {
        this.undoMan = new JackUndoManager();
        this.selectionStack = new SelectionStackManager();
        this.clipboardManager = new ClipboardManager();
        addSelectionListener(6, this.undoMan);
        addSelectionListener(8, this.selectionStack);
    }

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

    // Vom UI (z.B. JTabbedPane) gerufen, wenn ein Tab gewaehlt wird
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

    
    public void fireSelection(Object node, Object trigger, boolean rootSelected) {
        selectionOrator.say(l -> l.onNodeSelected(node, trigger, rootSelected));
    }

    public void fireContentCommand(String commandId, Object trigger) {
        contentOrator.say(l -> l.onCommand(commandId, trigger));
    }

    public Object getActiveEditor() {
        return activeEditor;
    }

    public JackUndoManager getUndoManager() {
        return undoMan;
    }

    public SelectionStackManager getSelectionStackManager() {
        return selectionStack;
    }

    public ClipboardManager getClipboardManager() {
        return clipboardManager;
    }

}
