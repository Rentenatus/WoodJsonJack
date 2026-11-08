/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import java.io.File;
import java.nio.file.Path;

/**
 * The global (user-home) wood level of the harness (CONCEPT_Harness.md §8, §16): everything
 * under {@code ~/.wood/} - the aggregated global metrics {@code ~/.wood/harness/metrics.json},
 * the lessons {@code ~/.wood/harness/lessons.json} and the AI mindset {@code ~/.wood/mindset/}.
 * Never project-local, never in the project RAG. Tests can redirect the base via the system
 * property {@value #WOOD_HOME_PROPERTY}.
 *
 * @author Janusch Rentenatus
 */
public final class GlobalWood {

    /** System property to redirect the wood user home (tests). */
    public static final String WOOD_HOME_PROPERTY = "de.jare.harness.woodHome";

    public static final String HARNESS_DIR = ".wood/harness";
    public static final String MINDSET_DIR = ".wood/mindset";
    public static final String METRICS_FILE = "metrics.json";
    public static final String LESSONS_FILE = "lessons.json";

    private GlobalWood() {
    }

    /** The wood user home: the override property if set, else {@code user.home}. */
    public static File woodHome() {
        String override = System.getProperty(WOOD_HOME_PROPERTY);
        return override != null ? new File(override) : new File(System.getProperty("user.home"));
    }

    public static File harnessDir(File woodHome) {
        return new File(woodHome, HARNESS_DIR);
    }

    public static File metricsFile(File woodHome) {
        return new File(harnessDir(woodHome), METRICS_FILE);
    }

    public static File lessonsFile(File woodHome) {
        return new File(harnessDir(woodHome), LESSONS_FILE);
    }

    public static Path mindsetDir(File woodHome) {
        return new File(woodHome, MINDSET_DIR).toPath();
    }
}
