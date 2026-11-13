/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings.project;

import de.jare.tree.settings.SettingsService;
import java.io.File;
import java.nio.file.Files;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * The user global project history (UX layer of CONCEPT_ProjectView.md): most recent first,
 * no duplicates per folder, a size cap - and a JSON round trip through the settings service.
 *
 * @author Janusch Rentenatus
 */
public class ProjectHistoryNGTest {

    @Test
    public void testRememberPutsNewProjectFirst() {
        ProjectHistory history = new ProjectHistory();
        history.remember("first", "D:/somewhere/first");
        history.remember("second", "D:/somewhere/second");

        Assert.assertEquals(history.getProjects().size(), 2);
        Assert.assertEquals(history.getProjects().get(0).getProjectName(), "second");
        Assert.assertEquals(history.getProjects().get(1).getProjectName(), "first");
    }

    @Test
    public void testReopenJumpsToFrontInsteadOfDuplicating() {
        ProjectHistory history = new ProjectHistory();
        history.remember("first", "D:/somewhere/first");
        history.remember("second", "D:/somewhere/second");
        history.remember("first again", "D:/somewhere/first");

        Assert.assertEquals(history.getProjects().size(), 2);
        Assert.assertEquals(history.getProjects().get(0).getProjectName(), "first again");
        Assert.assertEquals(history.getProjects().get(1).getProjectName(), "second");
    }

    @Test
    public void testSameFolderWithDifferentSpellingIsOneProject() {
        ProjectHistory history = new ProjectHistory();
        history.remember("lower", "d:/somewhere/first");
        history.remember("upper", "D:/Somewhere/../Somewhere/first");

        Assert.assertEquals(history.getProjects().size(), 1);
        Assert.assertEquals(history.getProjects().get(0).getProjectName(), "upper");
    }

    @Test
    public void testMissingPathIsIgnored() {
        ProjectHistory history = new ProjectHistory();
        history.remember("gone", " ");
        history.remember("nullish", null);

        Assert.assertTrue(history.getProjects().isEmpty());
    }

    @Test
    public void testSizeCapDropsTheOldest() {
        ProjectHistory history = new ProjectHistory();
        for (int i = 0; i < ProjectHistory.MAX_ENTRIES + 5; i++) {
            history.remember("p" + i, "D:/projects/p" + i);
        }

        Assert.assertEquals(history.getProjects().size(), ProjectHistory.MAX_ENTRIES);
        Assert.assertEquals(history.getProjects().get(0).getProjectName(),
                "p" + (ProjectHistory.MAX_ENTRIES + 4));
        Assert.assertEquals(history.getProjects().get(history.getProjects().size() - 1).getProjectName(),
                "p5");
    }

    @Test
    public void testRoundTripThroughTheSettingsService() throws Exception {
        File folder = Files.createTempDirectory("woodhistory").toFile();
        File file = new File(folder, "projects.json");
        SettingsService service = new SettingsService();
        ProjectHistory original = new ProjectHistory();
        original.remember("alpha", "D:/projects/alpha");
        original.remember("beta", "D:/projects/beta");
        service.saveProjectHistory(file, original);

        Assert.assertTrue(file.exists(), "the history file must be written");

        ProjectHistory reloaded = service.loadProjectHistory(file);
        Assert.assertEquals(reloaded.getProjects().size(), 2);
        Assert.assertEquals(reloaded.getProjects().get(0).getProjectName(), "beta");
        Assert.assertEquals(reloaded.getProjects().get(1).getProjectName(), "alpha");
        Assert.assertEquals(reloaded.getProjects().get(0).getProjectPath(), "D:/projects/beta");
    }
}
