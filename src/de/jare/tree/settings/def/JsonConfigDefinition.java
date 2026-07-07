/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.tree.settings.def;

import de.jare.jsoncasted.lang.JsonInstance;
import static de.jare.jsoncasted.model.JsonCollectionType.ARRAY;
import static de.jare.jsoncasted.model.JsonCollectionType.LIST;
import de.jare.jsoncasted.model.JsonModel;
import de.jare.jsoncasted.model.item.JsonClass;
import de.jare.jsoncasted.model.item.JsonMap;
import de.jare.jsoncasted.parserwriter.JsonCastingLevel;
import de.jare.jsoncasted.parserwriter.JsonItemDefinition;
import de.jare.tree.settings.AgentPreferences;
import de.jare.tree.settings.UserPreferences;
import de.jare.tree.settings.WoodSettings;
import de.jare.tree.settings.project.FolderType;
import de.jare.tree.settings.project.ProjectSettings;
import de.jare.tree.settings.project.ProjektEntry;
import de.jare.tree.settings.project.RootSetting;
import de.jare.tree.settings.theme.*;
import java.awt.Color;
import java.awt.Font;

public class JsonConfigDefinition implements JsonItemDefinition {

    public static final JsonConfigDefinition INSTANCE = new JsonConfigDefinition();

    public static JsonConfigDefinition getInstance() {
        return INSTANCE;
    }

    private final JsonModel model;

    private final JsonClass woodSettingsRoot;
    private final JsonClass themeSuiteRoot;
    private final JsonClass projectSettingsRoot;

    public JsonConfigDefinition() {
        model = new JsonModel("Wood");
        model.addBasicModel();

        final JsonClass asString = model.getJsonClass("String");
        final JsonClass asBoolean = model.getJsonClass("Boolean");
        final JsonClass asInt = model.getJsonClass("int");

        JsonClass font = model.newJsonReflect(Font.class);
        font.addCParam("name", asString);
        font.addCParam("style", asInt);
        font.addCParam("size", asInt);

        JsonMap fontMap = model.newRawJsonMapIndividually((new JsonInstance<Font>()).getClass(), (String) null, font);

        JsonClass color = model.newJsonReflect(Color.class);
        color.addCParam("r", asInt, "getRed");
        color.addCParam("g", asInt, "getBlue");
        color.addCParam("b", asInt, "getGreen");

        JsonMap colorMap = model.newRawJsonMapIndividually((new JsonInstance<Color>()).getClass(), (String) null, color);

        JsonClass folderType = model.newJsonEnumByName(FolderType.class);

        JsonClass userPreferences = model.newJsonReflect(UserPreferences.class);
        userPreferences.addField("uiLanguage", asString);
        userPreferences.addField("showTips", asBoolean);
        userPreferences.addField("autoSave", asBoolean);

        //JsonClass windowLayout = model.newJsonReflect(WindowLayout.class);
        JsonClass colorScheme = model.newJsonReflect(ColorScheme.class);
        colorScheme.addField("colorMap", colorMap);
        colorScheme.addField("dark", asBoolean);

        JsonClass fontSettings = model.newJsonReflect(FontSettings.class);
        fontSettings.addField("fontMap", fontMap);

        JsonClass theme = model.newJsonReflect(Theme.class);
        theme.addField("themeId", asString);
        theme.addField("themeName", asString);
        theme.addField("colors", colorScheme);
        theme.addField("fonts", fontSettings);

        themeSuiteRoot = model.newJsonReflect(ThemeSuite.class);
        themeSuiteRoot.addField("availableThemes", theme, LIST);

        JsonClass projektEntry = model.newJsonReflect(ProjektEntry.class);
        projektEntry.addField("projectName", asString);
        projektEntry.addField("projectPath", asString);

        JsonClass agentPreferences = model.newJsonReflect(AgentPreferences.class);
        agentPreferences.addField("apiKey", asString);
        agentPreferences.addField("defaultBehavior", asString);
        agentPreferences.addField("maxRetries", asInt);
        agentPreferences.addField("modelList", asString, LIST);
        agentPreferences.addField("prioritizedModel", asString);

        JsonClass rootSetting = model.newJsonReflect(RootSetting.class);
        rootSetting.addField("folderType", folderType);
        rootSetting.addField("folderName", asString);
        rootSetting.addField("folderPath", asString);

        projectSettingsRoot = model.newJsonReflect(ProjectSettings.class, projektEntry);
        projectSettingsRoot.addField("rootSettings", rootSetting, LIST);
        projectSettingsRoot.addField("openedFiles", asString, ARRAY);

        woodSettingsRoot = model.newJsonReflect(WoodSettings.class);
        woodSettingsRoot.addField("themeId", asString);
        woodSettingsRoot.addField("knownProjects", projektEntry, LIST);
        woodSettingsRoot.addField("agentPreferences", agentPreferences);
        woodSettingsRoot.addField("userPreferences", userPreferences);
    }

    @Override
    public JsonModel getModel() {
        return model;
    }

    public JsonClass getWoodSettingsRoot() {
        return woodSettingsRoot;
    }

    public JsonClass getThemeSuiteRoot() {
        return themeSuiteRoot;
    }

    public JsonClass getProjectSettingsRoot() {
        return projectSettingsRoot;
    }

    @Override
    public JsonCastingLevel getCastingLevel() {
        return JsonCastingLevel.NEVER;
    }
}
