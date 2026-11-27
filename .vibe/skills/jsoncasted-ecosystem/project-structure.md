# Project Structure Reference: jsonCasted Ecosystem

Three Java projects that build on each other, authored by Janusch Rentenatus.
Shared package root: `de.jare`

## Dependency graph

```
jsonCasted (base library)
    │
    ├──→ jsonCasted_edit (editor framework, depends on jsonCasted)
    │        │
    │        └──→ WoodJsonJack (Swing UI, depends on both jsonCasted + jsonCasted_edit)
```

All three share the package prefix `de.jare.jsoncasted.*` (jsonCasted and jsonCasted_edit)
and `de.jare.tree.*` (WoodJsonJack).

---

## 1. jsonCasted — Base Library

| Property | Value |
|----------|-------|
| **Git path** | `D:\git_jsonCasted` |
| **IDE workspace** | `D:\sdk_workspaces\jsonCasted` (NetBeans, Ant) |
| **Build system** | Maven (`pom.xml`) |
| **Group/Artifact** | `de.jare.jsoncasted:jsoncasted:0.1.0-SNAPSHOT` |
| **Java version** | 17 |
| **Test framework** | TestNG 7.8.0 |
| **Source dir** | `src/` |
| **Test dir** | `test/` |
| **Test assets** | `test_assets/` (config JSONs, output dir) |
| **Java files** | 169 total |
| **MCP types** | 140 |
| **MCP build** | MAVEN |
| **MCP alias** | `jsonCasted` |

### Package structure (src/)

```
de.jare.debug/                      Debug utilities
  ├── DebugTuple.java
  └── JsonDebugLevel.java

de.jare.jsoncasted.io/               I/O layer: parsing + writing
  ├── JsonParser.java                Main parser entry point
  ├── JsonItemWriter.java
  ├── JsonNodeWriter.java
  ├── JsonObjectWriter.java
  ├── JsonCastingLevel.java
  ├── JsonItemDefinition.java
  ├── JsonParseException.java
  ├── JsonWriteException.java
  ├── JsonValidationMethod.java
  ├── convertservice/                Conversion services
  │   ├── ConvertService.java
  │   ├── JsonItemCloner.java
  │   ├── JsonNodeConverter.java
  │   ├── JsonObjectConverter.java
  │   ├── RootConverter.java
  │   ├── WoodElementResolver.java
  │   ├── WoodProxyResolver.java
  │   └── WoodResolution.java
  ├── parserservice/                 Streaming parser implementation
  │   ├── JsonParserService.java
  │   ├── CastingParser.java
  │   ├── ListParser.java
  │   ├── ObjectParser.java
  │   ├── RootParser.java
  │   ├── StringParser.java
  │   ├── ParseStreamReader.java
  │   └── WoodIdFinder.java
  └── writer/                        Serialization
      ├── WriteNodePath.java
      ├── WriteStrategy.java
      ├── getter/
      │   ├── GetterFieldInfo.java
      │   ├── ListGetter.java
      │   └── ObjectGetter.java
      ├── record/
      │   ├── DefinitionsContext.java
      │   └── DefinitionsContextObjectRecord.java
      ├── strategy/
      │   ├── DefinitionalStrategy.java
      │   └── PrintStrategy.java
      └── walker/
          ├── ItemCircleScannerWalker.java
          ├── ItemWriteWalker.java
          ├── ListWriteWalker.java
          ├── MapWriteWalker.java
          ├── NodeWriteWalker.java
          ├── ObjectCircleScannerWalker.java
          ├── ObjectWriteWalker.java
          └── RootObjectWriteWalker.java

de.jare.jsoncasted.item/             JSON item types
  ├── JsonItem.java
  ├── JsonList.java
  ├── JsonObject.java
  ├── JsonValue.java
  ├── OneItemIterator.java
  └── builder/
      ├── BuilderService.java
      └── JsonBuilder.java

de.jare.jsoncasted.lang/             Language/AST core
  ├── JsonInstance.java
  ├── JsonNode.java
  ├── JsonNodeType.java
  ├── JsonResource.java
  ├── JsonSystem.java
  ├── JsonTerms.java
  ├── JsonExceptionEntry.java
  ├── LinkNodeEntry.java
  ├── LinkingSet.java
  └── calculator/
      ├── JsonWoodProviderScanner.java
      ├── JsonWoodProviderScanResult.java
      ├── JsonWoodProviderTinker.java
      └── JsonWoodProviderTinkerResult.java

de.jare.jsoncasted.model/            Model descriptors
  ├── JsonModel.java
  ├── JsonType.java
  ├── JsonRepo.java
  ├── JsonRepoModel.java
  ├── JsonModellClassBuilder.java
  ├── JsonModelTypeTraverser.java
  ├── JsonBuildException.java
  ├── JsonCollectionType.java
  ├── JsonEnumTemplate.java
  ├── JsonTypeVisibility.java
  ├── FieldKind.java
  ├── builder/                       Primitive type builders
  │   ├── JsonBooleanBuilder.java
  │   ├── JsonBooleanObjBuilder.java
  │   ├── JsonDoubleBuilder.java
  │   ├── JsonDoubleObjBuilder.java
  │   ├── JsonEnumByNameBuilder.java
  │   ├── JsonFloatBuilder.java
  │   ├── JsonFloatObjBuilder.java
  │   ├── JsonIntBuilder.java
  │   ├── JsonIntegerObjBuilder.java
  │   ├── JsonLongBuilder.java
  │   ├── JsonLongObjBuilder.java
  │   ├── JsonMapBuilder.java
  │   ├── JsonReflectBuilder.java
  │   └── JsonStringBuilder.java
  ├── descriptor/                    Type/field descriptors
  │   ├── JsonModelDescriptor.java
  │   ├── JsonModelDescriptorAsFile.java
  │   ├── JsonDefinitionsDescriptor.java
  │   ├── JsonTypeDescriptor.java
  │   ├── JsonFieldDescriptor.java
  │   ├── JsonFieldTypeNote.java
  │   └── def/
  │       ├── JsonModelDescriptorDefinition.java
  │       └── JsonModelDescriptorDefinitionAsFile.java
  ├── item/                         Model items (classes, definitions, fields)
  │   ├── JsonClass.java
  │   ├── JsonDefinitions.java
  │   ├── JsonField.java
  │   ├── JsonInter.java
  │   ├── JsonInterface.java
  │   ├── JsonMap.java
  │   ├── JsonCParam.java
  │   └── JsonUnknown.java
  └── validation/                   Model validation framework
      ├── ValidationContext.java
      ├── ValidationDiagnostic.java
      ├── ValidationResult.java
      ├── ValidationRunner.java
      ├── ValidatorContributor.java
      ├── ValidatorRegistry.java
      ├── Severity.java
      ├── CoreValidatorContributor.java
      ├── DefinitionsValidator.java
      ├── FieldValidator.java
      ├── ModelValidator.java
      ├── TypeValidator.java
      ├── defdefault/
      │   ├── DuplicateChildNameValidator.java
      │   └── EmptyDefinitionsNameValidator.java
      ├── fielddefault/
      │   ├── EmptyFieldNameValidator.java
      │   ├── FieldTypeMissingValidator.java
      │   ├── ReferenceFieldMissingTypeValidator.java
      │   └── UnregisteredReferenceTypeValidator.java
      └── modeldefault/
          └── DuplicateClassNameValidator.java
      └── typedefault/
          └── EmptyTypeNameValidator.java

de.jare.jsoncasted.wood/             Wood provider system
  ├── WoodProvider.java
  ├── WoodProviderBox.java
  └── def/
      └── WoodProviderDefinition.java

de.jare.jsoncasted.tools/            Utility tools
  ├── ConfigFileFinder.java
  └── SimpleStringSplitter.java

de.jare.jsonconfig/                  JSON config helpers
  ├── JsonConfigHelper.java
  ├── def/
  │   └── JsonConfigDefinition.java
  └── item/
      ├── ConfigFeature.java
      ├── ConfigLogging.java
      ├── ConfigProfile.java
      ├── ConfigProfileType.java
      └── ConfigRoot.java

de.jare.jsonpost/                    HTTP POST client
  ├── PostJsonClient.java
  └── PostJsonConnection.java

de.jare.tree.control/                Tree controller (used by WoodJsonJack)
  └── Orator.java
```

### Test structure (test/)

```
de.jare.impltest/                    Implementation tests
  ├── TestBoxNGTest.java / 2 / 3
  └── lib/                           Test fixtures (enums, values, definitions)

de.jare.jsoncasted.iotest/           I/O tests
  ├── JsonObjectWriterNGTest.java
  └── WoodResolutionDescriptionFileNGTest.java

de.jare.jsoncasted.model.descriptor/
  └── JsonModelDescriptorNGTest.java

de.jare.jsoncasted.parser/           Parser tests
  ├── JsonParserNodeInputNGTest.java
  └── bak/                           Backup parsers (CastingParserBak, etc.)

de.jare.jsoncasted.parserwriter/
  └── bak/

de.jare.jsonconfig.def/
  └── JsonConfigFactoryNGTest.java
```

---

## 2. jsonCasted_edit — Editor Framework

| Property | Value |
|----------|-------|
| **Git path** | `D:\git_jsonCasted_edit` |
| **IDE workspace** | `D:\sdk_workspaces\jsonCasted_edit` (NetBeans, Ant) |
| **Build system** | None (RAW — IDE classpath only) |
| **Source dir** | `src/` |
| **Test dir** | `test/` |
| **Test assets** | `test_assets/` (config JSONs, test-output) |
| **Java files** | 70 total |
| **MCP types** | 58 |
| **MCP build** | RAW |
| **MCP alias** | `jsonCasted_edit` |
| **Depends on** | `jsonCasted` (imports `io`, `lang`, `model`, `item` packages) |

### Key imports from jsonCasted

```
de.jare.jsoncasted.io.JsonParser
de.jare.jsoncasted.io.JsonParseException
de.jare.jsoncasted.io.convertservice.WoodElementResolver / WoodResolution
de.jare.jsoncasted.io.parserservice.JsonParserService
de.jare.jsoncasted.item.builder.JsonBuilder
de.jare.jsoncasted.lang.JsonNode / JsonNodeType / JsonResource / JsonTerms
de.jare.jsoncasted.model.JsonBuildException
de.jare.jsoncasted.model.descriptor.JsonFieldDescriptor / JsonModelDescriptor / JsonTypeDescriptor
```

### Package structure (src/)

```
de.jare.jsoncasted.editor/           Editor top-level
  ├── TreeEditorAbstract.java        Abstract tree editor base
  ├── TreeEditorModel.java           Tree editor model
  ├── clipboard/                     Clipboard management
  │   ├── ClipboardManager.java
  │   └── ClipboardStash.java
  ├── command/                       Edit commands (undo/redo)
  │   ├── AbstractEditCommand.java
  │   ├── AbstractToStashCommand.java
  │   ├── AddNodeCommand.java
  │   ├── CommandAction.java
  │   ├── CommandAvailability.java
  │   ├── CommandResult.java
  │   ├── CopyToStashCommand.java
  │   ├── CutToStashCommand.java
  │   ├── DeleteNodeCommand.java
  │   ├── EditCommand.java
  │   ├── EditCommandEntry.java
  │   ├── JackUpdateAction.java
  │   ├── MoveNodeCommand.java
  │   ├── PasteFromStashCommand.java
  │   ├── RenameNodeCommand.java
  │   ├── SetAttributeCommand.java
  │   └── SetValueCommand.java
  ├── core/                          Core editor model
  │   ├── EditNode.java              Interface for editable nodes
  │   ├── EditNodeAbstract.java      Abstract base
  │   ├── EditNodeObject.java         Object node (uses getName() for type assignment)
  │   ├── EditNodeProperty.java       Property node
  │   ├── EditNodePropertyArr.java    Array property node
  │   ├── EditTree.java               Edit tree container + parse queue
  │   ├── EditLinkingSet.java         Link management
  │   ├── EditProviderBox.java        Provider box
  │   ├── EditStatus.java             Status enum
  │   ├── EditTimes.java
  │   ├── IdGenerator.java
  │   ├── JackAttribut.java           Attribute representation
  │   ├── JsonTreeConverter.java      JSON <-> EditNode conversion
  │   ├── ParseState.java             Parse state enum
  │   ├── SimpleEntry.java
  │   ├── TypeParserService.java      On-the-fly type parser (calls getName())
  │   ├── TypeParserListener.java
  │   └── validation/                 Editor validation framework
  │       ├── CoreEditValidatorContributor.java
  │       ├── EditNodeDiagnostic.java
  │       ├── EditNodeObjectTypeValidator.java
  │       ├── EditNodePropertyFieldValidator.java
  │       ├── EditNodeRequiredFieldsValidator.java
  │       ├── EditNodeTypeHierarchyValidator.java
  │       ├── EditNodeValidator.java
  │       ├── EditTreeModelConsistencyValidator.java
  │       ├── EditTreeValidator.java
  │       ├── EditValidationRunner.java
  │       ├── Severity.java
  │       ├── ValidationContext.java
  │       ├── ValidationResult.java
  │       ├── ValidatorContributor.java
  │       └── ValidatorRegistry.java
  └── events/                        Event system
      ├── EditEvent.java
      ├── EventBus.java
      ├── HistoryEvent.java
      ├── HistoryListener.java
      └── HistoryManager.java
```

### Test structure (test/)

```
de.jare.jsoncasted.editor/
  ├── ATestTools.java
  ├── ClipboardArrayTestNG.java
  ├── ClipboardEditMixTestNG.java
  ├── ClipboardIntegrationTestNG.java
  ├── TreeEditorArraysNGTest.java
  ├── TreeEditorContentNGTest.java
  └── core/
      ├── EditNodeAbstractNGTest.java
      ├── EditNodeCoreNGTest.java
      ├── TypeParserServiceNGTest.java
      └── validation/
          ├── EditValidationRunnerNGTest.java
          ├── ValidationContextNGTest.java
          └── ValidationResultNGTest.java
```

---

## 3. WoodJsonJack — Swing UI Application

| Property | Value |
|----------|-------|
| **Git path** | `D:\git_WoodJsonJack` |
| **IDE workspace** | `D:\sdk_workspaces\WoodJsonJack` (NetBeans, Ant) |
| **Build system** | None (RAW — Eclipse `.classpath` only) |
| **Classpath** | References `D:\git_jsonCasted\src` and `D:\git_jsonCasted\target\classes` |
| **Source dir** | `src/` |
| **Test dir** | `test/` |
| **Assets** | `assets/icons/` (12 PNG icons for undo/redo/selection) |
| **Java files** | 59 total |
| **MCP types** | 58 |
| **MCP build** | RAW |
| **MCP alias** | `WoodJsonJack` |
| **Depends on** | `jsonCasted` (io, lang, model, item) + `jsonCasted_edit` (editor, command, core, events) |

### Key imports from jsonCasted (base)

```
de.jare.jsoncasted.io.JsonParser / JsonObjectWriter / JsonCastingLevel / JsonItemDefinition
de.jare.jsoncasted.io.JsonParseException / JsonWriteException
de.jare.jsoncasted.io.convertservice.WoodResolution
de.jare.jsoncasted.item.JsonItem
de.jare.jsoncasted.item.builder.JsonBuilder
de.jare.jsoncasted.lang.JsonInstance
de.jare.jsoncasted.model.JsonModel / JsonBuildException
de.jare.jsoncasted.model.descriptor.JsonModelDescriptor / JsonTypeDescriptor / JsonFieldDescriptor
de.jare.jsoncasted.model.descriptor.def.JsonModelDescriptorDefinition
de.jare.jsoncasted.model.item.JsonClass / JsonMap
```

### Key imports from jsonCasted_edit

```
de.jare.jsoncasted.editor.TreeEditorAbstract
de.jare.jsoncasted.editor.clipboard.ClipboardManager / ClipboardStash
de.jare.jsoncasted.editor.command.* (AddNode, Copy, Cut, Delete, Move, Paste, Rename, etc.)
de.jare.jsoncasted.editor.core.* (EditNode, EditNodeObject, EditNodeProperty, EditTree, JackAttribut, etc.)
de.jare.jsoncasted.editor.core.JsonTreeConverter / ParseState / EditStatus
de.jare.jsoncasted.editor.events.* (HistoryEvent, HistoryListener, HistoryManager)
```

### Package structure (src/)

```
de.jare.tree/                        Application entry point
  └── WoodJsonEditor.java            Main class

de.jare.tree.control/                Controller layer
  ├── JackMasterControl.java         Main controller
  ├── JackUndoManager.java           Undo/redo manager
  ├── JackUndoManagerModel.java
  ├── SelectionStackEntry.java
  ├── SelectionStackManager.java
  ├── SelectionStackManagerModel.java
  ├── WoodUtils.java
  ├── listeners/
  │   ├── ContentListener.java
  │   ├── FocusListener.java
  │   ├── TreeFocusComponent.java
  │   ├── TreeFocusListener.java
  │   └── UndoRedoListener.java
  └── model/
      └── JackTreeModel.java         Tree model adapter

de.jare.tree.settings/               Settings & preferences
  ├── AgentPreferences.java
  ├── SettingsService.java
  ├── UserPreferences.java
  ├── WoodSettings.java
  ├── def/
  │   └── JsonWoodSettingsDefinition.java
  ├── project/
  │   ├── FolderType.java
  │   ├── ProjectSettings.java
  │   ├── ProjektEntry.java
  │   └── RootSetting.java
  └── theme/
      ├── AScheme.java
      ├── ColorScheme.java
      ├── FontSettings.java
      ├── Theme.java
      ├── ThemeSuite.java
      └── WindowLayout.java

de.jare.tree.ui/                     Swing UI components
  ├── JackClipboardPanel.java
  ├── JackClipboardTree.java
  ├── JackEditPopup.java
  ├── JackEditTree.java              Editable JTree
  ├── JackEditTreeContainer.java
  ├── JackMainActions.java
  ├── JackMainMenu.java
  ├── JackTreeNodeTransferHandler.java
  ├── JackUndoButtonPanel.java
  ├── JackUndoPanel.java
  ├── JackUndoTableModel.java
  ├── JackUpperToolbar.java
  ├── JsonJackAttrDetailDialog.java
  ├── JsonJackAttrTableModel.java
  ├── JsonJackTreeCellEditor.java
  ├── JsonJackTreeCellRenderer.java
  ├── SearchResultPanel.java
  ├── SearchResults.java
  ├── SearchToolbar.java
  ├── WoodProjektTree.java
  ├── WoodWindow.java               Main window
  └── settings/
      ├── PreferencesDialog.java
      ├── ThemesModel.java
      ├── ThemesPreferencesColorTable.java
      ├── ThemesPreferencesFontTable.java
      ├── ThemesPreferencesList.java
      ├── ThemesPreferencesPane.java
      ├── ThemesPreferencesPreviewPane.java
      └── WoodPreferencesPane.java
```

### Assets

```
assets/icons/
  ├── link_view.png      no_link.png
  ├── redo.png / redo_gr.png
  ├── selback.png / selback_gr.png
  ├── selforw.png / selforw_gr.png
  ├── skip_redo.png / skip_redo_gr.png
  └── undo.png / undo_gr.png
```

---

## Cross-project architecture summary

```
jsonCasted                    jsonCasted_edit              WoodJsonJack
─────────────                 ────────────────              ───────────────
io/ parsing+writing    ←──    editor/core/            ←──   tree/ui/
   JsonParser                   TypeParserService            WoodWindow
   JsonObjectWriter             EditNodeObject               JackEditTree
   parserservice/               EditTree                     JsonJackTreeCellRenderer
                                JsonTreeConverter

model/ descriptors       ←──    editor/core/            ←──   tree/control/
   JsonModelDescriptor          tryAssignType()              JackMasterControl
   JsonTypeDescriptor           (uses getName() as           JackUndoManager
   JsonFieldDescriptor           type lookup key)            SelectionStackManager

model/ validation        ←──    editor/core/validation  ←──   (uses validators
   ValidatorRegistry            EditValidationRunner          via TreeEditorAbstract)
   ValidationResult             EditNodeObjectTypeValidator

lang/ AST               ←──    editor/core/            ←──   tree/ui/
   JsonNode                     JsonTreeConverter            JsonJackTreeCellEditor
   JsonNodeType                 EditNodePropertyArr          (calls getName()
   JsonInstance                 EditNodeProperty              for cell display)
```

## Key insight: getName() as the parse pivot

`EditNodeObject.getName()` (returning `objektValue`) serves as the type-lookup key
at two functional points:

1. **`EditNodeObject.tryAssignType()`** — resolves the node's own type via
   `descriptor.getType(name)` → `getTypePerceptive(name)`
2. **`TypeParserService.tryInferParentTypes()`** — uses the child node's name as
   a field name to infer the parent's type via `getTypesContainingField(model, fieldName)`

This makes the node name the central pivot between the editor tree and the model
descriptor system.
