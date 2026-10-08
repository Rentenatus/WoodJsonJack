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

/**
 * Schreibt in den FastLog-Typ "ui", warum die Add-Menues des Soft- oder Hard-Edit-Menu
 * deaktiviert bleiben. Es werden nur die Widgets des aktiven Modus geloggt: im Soft-Mode
 * die generischen Items, im Hard-Mode die typisierten Untermenues. Bei leeren Hard-Mode-
 * Vorschlaegen wird die Erklaerung des {@link HardEditAdvisor} an die Meldung angehaengt.
 *
 * @author Janusch Rentenatus
 */
public final class EditMenuEnablementLogger {

    private EditMenuEnablementLogger() {
    }

    /**
     * Loggt die Deaktivierungsgruende der Add-Menues eines Edit-Menu (Popup oder
     * Hauptmenue) in den FastLog-Typ "ui".
     *
     * @param master der Master-Control mit dem FastLog
     * @param menuName Kennzeichnung des Menu, z. B. "edit popup" oder "main menu"
     * @param hard true, wenn die Selektion im Hard-Parse-Mode mit Modell-Deskriptor ist
     * @param isReadonly true, wenn kein Editor selektiert oder der Editor read-only ist
     * @param nodeExists true, wenn ein Knoten selektiert ist
     * @param enableAddRename true, wenn generisches Hinzufuegen erlaubt ist
     * @param canParentAnnotation true, wenn der selektierte Knoten eine Annotation aufnehmen kann
     * @param editTree der EditTree der Selektion oder null
     * @param selected der selektierte Knoten oder null
     */
    public static void logAddMenus(JackMasterControl master, String menuName, boolean hard,
            boolean isReadonly, boolean nodeExists, boolean enableAddRename,
            boolean canParentAnnotation, EditTree editTree, EditNodeAbstract selected) {
        final FastLog fastLog = master.getFastLog();
        final String mode = hard ? "hard" : "soft";

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
                fastLog.tryWrite("ui", "[" + mode + "] " + menuName + ": add node disabled: " + baseReason);
            }
            if (!enableAddRename || !canParentAnnotation) {
                fastLog.tryWrite("ui", "[" + mode + "] " + menuName + ": add annotation disabled: "
                        + (baseReason != null ? baseReason : "selected node cannot parent an annotation"));
            }
            return;
        }
        if (!enableAddRename) {
            fastLog.tryWrite("ui", "[" + mode + "] " + menuName + ": add node menu disabled: " + baseReason);
            fastLog.tryWrite("ui", "[" + mode + "] " + menuName + ": add annotation menu disabled: " + baseReason);
            return;
        }
        if (!HardEditMenuBuilder.hasAddNodeProposals(editTree, selected)) {
            fastLog.tryWrite("ui", "[" + mode + "] " + menuName + ": add node menu disabled: "
                    + "no permissible child types for this selection ("
                    + HardEditAdvisor.explainEmptyChildren(selected, editTree.getJsonModelDescriptor()) + ")");
        }
        if (!canParentAnnotation) {
            fastLog.tryWrite("ui", "[" + mode + "] " + menuName + ": add annotation menu disabled: "
                    + "selected node cannot parent an annotation");
        } else if (!HardEditMenuBuilder.hasAddAnnotationProposals(editTree, selected)) {
            fastLog.tryWrite("ui", "[" + mode + "] " + menuName + ": add annotation menu disabled: "
                    + "no permissible annotations for this selection ("
                    + HardEditAdvisor.explainEmptyAnnotations(selected, editTree.getJsonModelDescriptor()) + ")");
        }
    }

}
