/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import de.jare.jsoncasted.editor.core.EditNodeAbstract;
import de.jare.jsoncasted.editor.core.EditTree;
import de.jare.jsoncasted.editor.core.HardEditAdvisor;
import de.jare.tree.control.JackMasterControl;
import de.jare.tree.control.fastlog.FastLog;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes to the fast log type "ui" why the add menus of the soft or hard edit menu
 * stay disabled. Only the widgets of the active mode are logged: in soft mode
 * the generic items, in hard mode the typed sub menus. All disabled
 * points of one logic run are summarized in a single log entry. For
 * empty hard mode proposals the explanation of the {@link HardEditAdvisor} is
 * appended to the respective point.
 *
 * @author Janusch Rentenatus
 */
public final class EditMenuEnablementLogger {

    private EditMenuEnablementLogger() {
    }

    /**
     * Logs the disable reasons of the add menus of an edit menu (popup or
     * main menu), summarized in a single entry in the fast log type "ui".
     * When everything stays enabled, nothing is logged.
     *
     * @param master the master control with the fast log
     * @param menuName identification of the menu, e.g. "edit popup" or "main menu"
     * @param hard true when the selection is in hard parse mode with a model descriptor
     * @param isReadonly true when no editor is selected or the editor is read-only
     * @param nodeExists true when a node is selected
     * @param enableAddRename true when generic adding is allowed
     * @param canParentAnnotation true when the selected node can parent an annotation
     * @param editTree the edit tree of the selection, or null
     * @param selected the selected node, or null
     */
    public static void logAddMenus(JackMasterControl master, String menuName, boolean hard,
            boolean isReadonly, boolean nodeExists, boolean enableAddRename,
            boolean canParentAnnotation, EditTree editTree, EditNodeAbstract selected) {
        final FastLog fastLog = master.getFastLog();
        final String mode = hard ? "hard" : "soft";
        final List<String> disabledPoints = new ArrayList<>();

        final String baseReason;
        if (!nodeExists) {
            baseReason = "no node selected";
        } else if (isReadonly) {
            baseReason = "editor is read-only";
        } else {
            baseReason = null;
        }

        if (!hard) {
            if (!enableAddRename) {
                disabledPoints.add("add node (" + baseReason + ")");
            }
            if (!enableAddRename || !canParentAnnotation) {
                disabledPoints.add("add annotation ("
                        + (baseReason != null ? baseReason : "selected node cannot parent an annotation") + ")");
            }
        } else if (!enableAddRename) {
            disabledPoints.add("add node menu (" + baseReason + ")");
            disabledPoints.add("add annotation menu (" + baseReason + ")");
        } else {
            if (!HardEditMenuBuilder.hasAddNodeProposals(editTree, selected)) {
                disabledPoints.add("add node menu (no permissible child types for this selection ("
                        + HardEditAdvisor.explainEmptyChildren(selected, editTree.getJsonModelDescriptor()) + "))");
            }
            if (!canParentAnnotation) {
                disabledPoints.add("add annotation menu (selected node cannot parent an annotation)");
            } else if (!HardEditMenuBuilder.hasAddAnnotationProposals(editTree, selected)) {
                disabledPoints.add("add annotation menu (no permissible annotations for this selection ("
                        + HardEditAdvisor.explainEmptyAnnotations(selected, editTree.getJsonModelDescriptor()) + "))");
            }
        }

        if (!disabledPoints.isEmpty()) {
            fastLog.tryWrite("ui", "[" + mode + "] " + menuName + ": disabled add menus: "
                    + String.join("; ", disabledPoints));
        }
    }

}
