/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured result of one calculator call. The KI never orchestrates - it answers
 * exactly one encapsulated question with structured data (CONCEPT_Harness.md §1, §2).
 *
 * @author Janusch Rentenatus
 */
public class CalcResult {

    private final Kind kind;
    private Status status = Status.DONE;
    private Integer score;
    private String decision = "";
    private String message = "";
    private HarnessAction action;   // the OO steering object; derived from decision if not set explicitly
    private LoopAction loopAction;  // the FLOW channel (§15.6); derived from the action's Control if not set explicitly
    private final Map<String, String> payload = new LinkedHashMap<>();

    public CalcResult(Kind kind) {
        this.kind = kind;
    }

    public static CalcResult of(Kind kind, String decision) {
        CalcResult r = new CalcResult(kind);
        r.decision = decision;
        return r;
    }

    public Kind getKind() {
        return kind;
    }

    public Status getStatus() {
        return status;
    }

    public CalcResult status(Status status) {
        this.status = status;
        return this;
    }

    public Integer getScore() {
        return score;
    }

    public CalcResult score(int score) {
        this.score = score;
        return this;
    }

    public String getDecision() {
        return decision;
    }

    public CalcResult decision(String decision) {
        this.decision = decision;
        return this;
    }

    /**
     * The steering action the driver executes (object-oriented, no decision-string parsing).
     * If not set explicitly, it is derived from the legacy decision string + payload.
     */
    public HarnessAction getAction() {
        return action != null ? action
                : de.jare.harness.main.actions.Actions.fromDecision(decision, payload, message);
    }

    public CalcResult action(HarnessAction action) {
        this.action = action;
        if (action != null && (decision == null || decision.isEmpty())) {
            this.decision = action.type();
        }
        return this;
    }

    /**
     * The loop-flow action (CONCEPT_Harness.md §15.6): where the loop goes after this item.
     * If not set explicitly, derived from the legacy decision / the domain action's Control.
     * Never null - the default is {@code LoopContinue}.
     */
    public LoopAction getLoopAction() {
        if (loopAction != null) {
            return loopAction;
        }
        if (action != null) {
            return de.jare.harness.main.actions.LoopActions.fromControl(controlOf(action));
        }
        return de.jare.harness.main.actions.LoopActions.fromDecision(decision, message);
    }

    private static HarnessAction.Control controlOf(HarnessAction a) {
        // the Control is what the action's execute() returns on the driver; for derivation we
        // ask the built-in factory - unknown custom actions fall back to CONTINUE
        return switch (a.type()) {
            case "abort" -> HarnessAction.Control.STOP;
            case "retry", "incomplete" -> HarnessAction.Control.LOOP;
            default -> HarnessAction.Control.CONTINUE;
        };
    }

    public CalcResult loopAction(LoopAction loopAction) {
        this.loopAction = loopAction;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public CalcResult message(String message) {
        this.message = message;
        return this;
    }

    public Map<String, String> getPayload() {
        return payload;
    }

    public CalcResult put(String key, String value) {
        payload.put(key, value);
        return this;
    }
}
