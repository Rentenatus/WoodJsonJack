package de.jare.tree.settings.def;

import de.jare.jsoncasted.lang.JsonInstance;
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

import static de.jare.jsoncasted.model.JsonCollectionType.ARRAY;
import static de.jare.jsoncasted.model.JsonCollectionType.LIST;

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

        JsonMap stringArrayMap = model.newRawJsonMap((new JsonInstance()).getClass(), asString, ARRAY);

        JsonClass folderType = model.newJsonEnumByName(FolderType.class);

        JsonClass agentPreferences = model.newJsonReflect(AgentPreferences.class);
        JsonClass userPreferences = model.newJsonReflect(UserPreferences.class);
        JsonClass windowLayout = model.newJsonReflect(WindowLayout.class);

        JsonClass colorScheme = model.newJsonReflect(ColorScheme.class);
        JsonClass fontSettings = model.newJsonReflect(FontSettings.class);

        JsonClass theme = model.newJsonReflect(Theme.class);
        theme.addField("id", asString);
        theme.addField("displayName", asString);
        theme.addField("dark", asBoolean);
        theme.addField("colorScheme", colorScheme);
        theme.addField("fontSettings", fontSettings);

        themeSuiteRoot = model.newJsonReflect(ThemeSuite.class);
        themeSuiteRoot.addField("shownTheme", asString);
        themeSuiteRoot.addField("themes", theme, LIST);

        JsonClass projektEntry = model.newJsonReflect(ProjektEntry.class);
        projektEntry.addField("name", asString);
        projektEntry.addField("path", asString);

        JsonClass rootSetting = model.newJsonReflect(RootSetting.class);
        rootSetting.addField("folderType", folderType);
        rootSetting.addField("folderPath", asString);

        projectSettingsRoot = model.newJsonReflect(ProjectSettings.class, projektEntry);
        projectSettingsRoot.addField("rootSettings", rootSetting, LIST);
        projectSettingsRoot.addField("openedFiles", asString, ARRAY);

        woodSettingsRoot = model.newJsonReflect(WoodSettings.class);
        woodSettingsRoot.addField("themeId", asString);
        woodSettingsRoot.addField("shownTheme", asString);
        woodSettingsRoot.addField("knownProjects", projektEntry, LIST);
        woodSettingsRoot.addField("agentPreferences", agentPreferences);
        woodSettingsRoot.addField("userPreferences", userPreferences);
        woodSettingsRoot.addField("windowLayout", windowLayout);
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