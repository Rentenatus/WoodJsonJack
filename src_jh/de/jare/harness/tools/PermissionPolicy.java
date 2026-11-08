/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.tools;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * The agent permission level (CONCEPT_Harness.md §13): all reading tools run in
 * {@code automode} (never need approval); writing tools are split by category
 * (editor, disk-in, disk-out, external, system) with a per-category setting
 * nein | fragen | automode. {@code destructive} tools are always at least "fragen".
 * At "fragen" a UI approval query appears (the callback; default denies).
 *
 * @author Janusch Rentenatus
 */
public class PermissionPolicy {

    public enum Setting {
        NEIN, FRAGEN, AUTOMODE
    }

    private final Map<HarnessTool.WriteCategory, Setting> settings = new EnumMap<>(HarnessTool.WriteCategory.class);

    /** UI approval callback: (tool, category, reason) -> approved. Default: deny. */
    private BiFunction<HarnessTool, String, Boolean> approvalCallback = (tool, reason) -> false;

    public PermissionPolicy() {
        for (HarnessTool.WriteCategory c : HarnessTool.WriteCategory.values()) {
            settings.put(c, Setting.FRAGEN); // safe default: ask
        }
    }

    public PermissionPolicy set(HarnessTool.WriteCategory category, Setting setting) {
        settings.put(category, setting);
        return this;
    }

    public Setting get(HarnessTool.WriteCategory category) {
        return settings.getOrDefault(category, Setting.FRAGEN);
    }

    public PermissionPolicy onApprovalRequest(BiFunction<HarnessTool, String, Boolean> callback) {
        this.approvalCallback = callback;
        return this;
    }

    /**
     * Decides whether a tool may run.
     *
     * @return null when allowed, otherwise the denial reason
     */
    public String check(HarnessTool tool, String reason) {
        if (tool.risk() == HarnessTool.RiskLevel.READ) {
            return null; // reading is always automode
        }
        Setting s = settings.get(tool.writeCategory());
        if (tool.risk() == HarnessTool.RiskLevel.DESTRUCTIVE && s == Setting.AUTOMODE) {
            s = Setting.FRAGEN; // destructive is always at least fragen
        }
        return switch (s) {
            case AUTOMODE -> null;
            case NEIN -> "denied by policy (nein): " + tool.name();
            case FRAGEN -> Boolean.TRUE.equals(approvalCallback.apply(tool, reason))
                    ? null : "denied by user/policy (fragen): " + tool.name();
        };
    }
}
