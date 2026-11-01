package de.jare.tree.ui;

import de.jare.jsoncasted.editor.command.RenameNodeCommand;
import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditStatus;
import de.jare.tree.control.JackUndoManager;
import de.jare.tree.control.fastlog.FastLog;
import de.jare.tree.control.fastlog.FastLogEntry;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.util.List;

/**
 * Guard for renames in the hard parse mode: the rename executes, the synchronous hard
 * parse judges it (parseNow runs inside the command). An ERROR - on the renamed node or
 * in its subtree - rolls the rename back as an undo, the tree keeps the old name. The
 * rename parses the node itself, never the parent: a duplicate field name in the parent
 * is no parse verdict but an invalid object state, the guard checks the cardinality
 * itself and rolls such a rename back alike. The fast log explains the refusal, and the
 * fast log message window shows the entry, so a refused change cannot be overlooked.
 * Outside the hard parse mode nothing is guarded: the soft parser follows
 * asynchronously, its problems reach the fast log through the parse walk.
 *
 * @author Janusch Rentenatus
 */
final class JsonJackHardRenameGuard {

    private JsonJackHardRenameGuard() {
    }

    /**
     * Executes the rename and guards the hard parse result. Nothing is rolled back when
     * the parse accepts the rename (or the tree parses soft).
     *
     * @param undoMan the undo manager of the master control
     * @param fastLog the fast log of the master control, may be {@code null}
     * @param node the node to rename
     * @param newName the chosen name
     * @return true when the rename survived, false when the hard parse refused it
     */
    static boolean executeGuarded(JackUndoManager undoMan, FastLog fastLog,
            EditNode node, String newName) {
        final String oldName = node.getName();
        undoMan.executeCommand(new RenameNodeCommand(node, newName));
        if (!(node instanceof EditNodeAbstract absNode)) {
            return true;
        }
        // The rename parses the node itself, never the parent: a duplicate field name in
        // the parent is no parse verdict but an invalid object state - checked here.
        final boolean duplicate = carriesDuplicateName(node.getParent(), newName);
        final boolean parseError = hasParseError(absNode);
        if (!duplicate && !parseError) {
            return true;
        }
        // The synchronous hard parse flagged the rename (or the duplicate check did):
        // roll it back, the tree keeps the old name. The refusal must not be silent.
        final String reason = duplicate
                ? "the parent already carries a field '" + newName + "' - duplicate field "
                        + "names are no valid object state"
                : absNode.getEditMessage();
        undoMan.undo();
        showRefusal(fastLog, oldName, newName, reason);
        return false;
    }

    /**
     * Checks whether more than one child property of the parent carries the given name -
     * the renamed node itself counts as the one legitimate carrier, a second one is a
     * duplicate. The parser judges field bindings, the cardinality of the parent is
     * checked here.
     *
     * @param parent the parent of the renamed node
     * @param name the new name
     * @return true when the name exists more than once among the field properties
     */
    private static boolean carriesDuplicateName(EditNode parent, String name) {
        if (!(parent instanceof EditNodeAbstract owner)) {
            return false;
        }
        int carriers = 0;
        for (int i = 0; i < owner.getChildCount(); i++) {
            if (owner.getChildAt(i) instanceof EditNodeProperty property
                    && name.equals(property.getName()) && ++carriers > 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether the renamed node or one of its descendants carries an ERROR after
     * the parse: the rename may fit the node itself but break its children.
     *
     * @param node the renamed node
     * @return true when the subtree carries a parse error
     */
    private static boolean hasParseError(EditNodeAbstract node) {
        if (node.getEditStatus() == EditStatus.ERROR) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChildAt(i) instanceof EditNodeAbstract child && hasParseError(child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Explains the refused rename in the fast log and shows the entry in the fast log
     * message window - without an active window (headless, tests) the log entry alone
     * carries the refusal.
     */
    private static void showRefusal(FastLog fastLog, String oldName, String newName,
            String reason) {
        if (fastLog == null || !fastLog.tryWrite("editor",
                "[hard parse] rename of '" + oldName + "' to '" + newName
                + "' rejected: "
                + (reason == null || reason.isEmpty()
                        ? "the change does not match the model" : reason)
                + " - the rename was rolled back, the tree keeps '" + oldName + "'")) {
            return;
        }
        final List<FastLogEntry> entries = fastLog.getEntries("editor");
        if (entries.isEmpty()) {
            return;
        }
        final Frame owner = activeFrame();
        if (owner == null) {
            return;
        }
        new JackFastLogMessageDialog(owner, entries.get(entries.size() - 1)).setVisible(true);
    }

    /**
     * The active window as dialog owner, or null when no frame is active (the caller
     * then goes without a dialog).
     */
    private static Frame activeFrame() {
        final Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        return active instanceof Frame frame ? frame : null;
    }
}
