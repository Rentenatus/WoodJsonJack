/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.memory;

import de.jare.harness.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The WorkResponse (Bearbeitungsantwort) - the current, still-changing ANSWER of the run, owned by
 * the Quest and shown live in the UI via the host SPI. This is not the final persisted result;
 * it changes with every calculator call (docs/working/DESIGN_Quest_WorkOrder.md).
 *
 * @author Janusch Rentenatus
 */
public class WorkResponse {

    private String text = "";
    private Integer score;
    private Status status = Status.PENDING;
    private long updatedTs = System.currentTimeMillis();
    private final List<Consumer<WorkResponse>> listeners = new ArrayList<>();

    /** Orator: fires with this response on every update. */
    public void addListener(Consumer<WorkResponse> listener) {
        if (listener != null) {
            synchronized (this) {
                listeners.add(listener);
            }
        }
    }

    public synchronized WorkResponse update(String text, Integer score, Status status) {
        if (text != null) {
            this.text = text;
        }
        if (score != null) {
            this.score = score;
        }
        if (status != null) {
            this.status = status;
        }
        this.updatedTs = System.currentTimeMillis();
        for (Consumer<WorkResponse> l : new ArrayList<>(listeners)) {
            l.accept(this);
        }
        return this;
    }

    public synchronized String getText() {
        return text;
    }

    public synchronized Integer getScore() {
        return score;
    }

    public synchronized Status getStatus() {
        return status;
    }

    public synchronized long getUpdatedTs() {
        return updatedTs;
    }
}
