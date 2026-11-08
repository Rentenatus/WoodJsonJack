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
