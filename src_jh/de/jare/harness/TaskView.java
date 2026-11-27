/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import java.util.List;

/**
 * The READ-ONLY facade of a task (CONCEPT_Harness.md §18.1): calculators receive
 * {@code ctx.task()} as a {@code TaskView} and can never mutate the tree directly -
 * every mutation goes through a {@link TaskCommand} on the command channel
 * ({@code ctx.commands()}). Only getters, no setters - the encapsulation is enforced
 * by the interface, not by convention.
 *
 * @author Janusch Rentenatus
 */
public interface TaskView {

    long getId();

    String getTaskType();

    String getTitle();

    /** The prompt text for the models - not necessarily the title (reasoning subtasks keep the original prompt here). */
    String getPrompt();

    Status getStatus();

    Integer getScore();

    int getAttempt();

    boolean isSummaryTask();

    String getPromptSupplement();

    String getArtifact();

    TaskView getParent();

    List<? extends TaskView> getChildren();

    List<CallLogEntry> getCalculatorLog();

    List<TaskData.CalcCall> getCalls();

    int depth();
}
