/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings;

import de.jare.jsoncasted.item.JsonItem;
import de.jare.jsoncasted.item.builder.JsonBuilder;
import de.jare.jsoncasted.model.JsonBuildException;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.JsonParser;
import de.jare.jsoncasted.io.JsonWriter;
import de.jare.tree.settings.def.JsonConfigDefinition;
import de.jare.tree.settings.project.ProjectSettings;
import de.jare.tree.settings.theme.ThemeSuite;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Jansuch Rentenatus
 */
public class SettingsService {

    private final JsonConfigDefinition definition;

    public SettingsService() {
        this.definition = JsonConfigDefinition.getInstance();
    }

    private File getUserHomeDir() {
        return new File(System.getProperty("user.home"));
    }

    private File getGlobalSettingsDir() {
        return new File(getUserHomeDir(), ".woodjj");
    }

    private File getWoodSettingsFile() {
        return new File(getGlobalSettingsDir(), "settings.json");
    }

    private File getThemeSuiteFile() {
        return new File(getGlobalSettingsDir(), "themes.json");
    }

    public File getProjectSettingsFile(File projectDir) {
        return new File(new File(projectDir, ".woodjj"), "project.json");
    }

    public WoodSettings loadWoodSettings(boolean resetDefaults) {
        try {
            File file = getWoodSettingsFile();
            if (resetDefaults || file == null || !file.exists()) {
                return resetWoodSettings(file);
            }
            try {
                JsonItem item = JsonParser.parse(file, definition, definition.getWoodSettingsRoot());
                return (WoodSettings) JsonBuilder.buildInstance(definition.getModel(), true, item);
            } catch (JsonParseException | JsonBuildException | NullPointerException ex) {
                WoodSettings defaults = createDefaultWoodSettings();
                saveWoodSettings(file, defaults);
                return defaults;
            }
        } catch (IOException | JsonParseException ex1) {
            Logger.getGlobal().log(Level.SEVERE, null, ex1);
        }
        return createDefaultWoodSettings();
    }

    public ThemeSuite loadThemeSuite(boolean resetDefaults) {
        try {
            File file = getThemeSuiteFile();
            if (resetDefaults || file == null || !file.exists()) {
                return resetThemeSuite(file);
            }

            try {
                JsonItem item = JsonParser.parse(file, definition, definition.getThemeSuiteRoot());
                return (ThemeSuite) JsonBuilder.buildInstance(definition.getModel(), true, item);
            } catch (JsonParseException | JsonBuildException | NullPointerException ex) {
                ThemeSuite defaults = createDefaultThemeSuite();
                saveThemeSuite(file, defaults);
                return defaults;
            }

        } catch (IOException | JsonParseException ex1) {
            Logger.getGlobal().log(Level.SEVERE, null, ex1);
        }
        return createDefaultThemeSuite();
    }

    public ProjectSettings loadProjectSettings(File file) throws IOException, JsonParseException {
        if (file == null || !file.exists()) {
            return resetProjectSettings(file);
        }

        try {
            JsonItem item = JsonParser.parse(file, definition, definition.getProjectSettingsRoot());
            return (ProjectSettings) JsonBuilder.buildInstance(definition.getModel(), true, item);
        } catch (JsonParseException | JsonBuildException | NullPointerException ex) {
            ProjectSettings defaults = createDefaultProjectSettings("New Project", file.getPath());
            saveProjectSettings(file, defaults);
            return defaults;
        }
    }

    public void saveWoodSettings(File file, WoodSettings settings) throws IOException, JsonParseException {
        ensureParentDirectory(file);
        JsonWriter.write(settings, file, definition, definition.getWoodSettingsRoot());
    }

    public void saveThemeSuite(File file, ThemeSuite suite) throws IOException, JsonParseException {
        ensureParentDirectory(file);
        JsonWriter.write(suite, file, definition, definition.getThemeSuiteRoot());
    }

    public void saveProjectSettings(File file, ProjectSettings settings) throws IOException, JsonParseException {
        ensureParentDirectory(file);
        JsonWriter.write(settings, file, definition, definition.getProjectSettingsRoot());
    }

    public WoodSettings resetWoodSettings(File file) throws IOException {
        WoodSettings defaults = createDefaultWoodSettings();
        try {
            saveWoodSettings(file, defaults);
        } catch (JsonParseException ex) {
            Logger.getGlobal().log(Level.SEVERE, null, ex);
        }
        return defaults;
    }

    public ThemeSuite resetThemeSuite(File file) throws IOException {
        ThemeSuite defaults = createDefaultThemeSuite();
        try {
            saveThemeSuite(file, defaults);
        } catch (JsonParseException ex) {
            Logger.getGlobal().log(Level.SEVERE, null, ex);
        }
        return defaults;
    }

    public ProjectSettings resetProjectSettings(File file) throws IOException {
        ProjectSettings defaults = createDefaultProjectSettings("New Project", file.getPath());
        try {
            saveProjectSettings(file, defaults);
        } catch (JsonParseException ex) {
            Logger.getGlobal().log(Level.SEVERE, null, ex);
        }
        return defaults;
    }

    protected WoodSettings createDefaultWoodSettings() {
        return new WoodSettings();
    }

    protected ThemeSuite createDefaultThemeSuite() {
        ThemeSuite suite = new ThemeSuite();
        return suite;
    }

    protected ProjectSettings createDefaultProjectSettings(String projectName, String projectPath) {
        return new ProjectSettings(projectName, projectPath);
    }

    private void ensureParentDirectory(File file) throws IOException {
        if (file == null) {
            throw new IOException("File must not be null.");
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directory: " + parent.getAbsolutePath());
        }
    }
}
